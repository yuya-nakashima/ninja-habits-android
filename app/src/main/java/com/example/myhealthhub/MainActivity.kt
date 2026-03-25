package com.example.myhealthhub

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit
import androidx.health.connect.client.PermissionController
import kotlin.collections.Set
import okhttp3.OkHttpClient
import okhttp3.Request
import android.util.Log
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import java.time.*
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {
    // ヘルスコネクトのクライアントを用意
    private val healthConnectClient by lazy { HealthConnectClient.getOrCreate(this) }
    private val JST: ZoneId = ZoneId.of("Asia/Tokyo")
    private val API_URL = "http://127.0.0.1:8000/ingest"

    // 読み取りたい権限のリスト
    private val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val statusText = findViewById<TextView>(R.id.statusText)
        val syncButton = findViewById<Button>(R.id.syncButton)

        // 権限をリクエストするためのランチャー
        val requestPermissionLauncher = registerForActivityResult(
            PermissionController.createRequestPermissionResultContract()
        ) { granted: Set<String> ->
            // デバッグ：実際にOSから返ってきた権限を全部出す
            val debugText = "返ってきた権限: ${granted.joinToString(", ")}"

            if (granted.containsAll(permissions)) {
                statusText.text = "許可されました！もう一度ボタンを！"
            } else {
                // 拒否された場合、中身を表示して原因を特定する
                statusText.text = "拒否されました。\n$debugText"
                println(debugText) // ログにも出す
            }
        }

        syncButton.setOnClickListener {
            lifecycleScope.launch {
                val granted = healthConnectClient.permissionController.getGrantedPermissions()
                if (granted.containsAll(permissions)) {
                    statusText.text = "データを読み取っています..."
                    backfillSteps(
                        statusText,
                        LocalDate.of(2026, 1, 28),
                        LocalDate.of(2026, 3, 3)
                    )
                } else {
                    statusText.text = "権限を確認中..."
                    // 再度リクエスト画面を出す
                    requestPermissionLauncher.launch(permissions)
                }
            }
        }
    }

    // 実際に歩数を読み取る関数
    private suspend fun readSteps(statusText: TextView) {
        fun mark(msg: String) {
            Log.d("MyHealthHub", msg)
            runOnUiThread { statusText.text = msg }
        }

        try {
            mark("STEP 1: reading Health Connect...")

            val startOfDay = Instant.now().truncatedTo(ChronoUnit.DAYS)
            val now = Instant.now()

            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startOfDay, now)
                )
            )

            val totalSteps = response.records.sumOf { it.count }
            mark("STEP 2: got steps=$totalSteps, sending...")

            val client = OkHttpClient()

            val json = """
            {
              "source": "health_connect",
              "metric": "steps",
              "start_at": "$startOfDay",
              "end_at": "$now",
              "value": $totalSteps,
              "unit": "count"
            }
            """.trimIndent()

            val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(API_URL)
                .post(body)
                .build()

            statusText.text = "送信中..."

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread {
                        statusText.text = "NETWORK ERROR: ${e.javaClass.simpleName}"
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val code = it.code
                        val resBody = it.body?.string()
                        runOnUiThread {
                            statusText.text = "送信完了 code=$code\n${resBody ?: "(null)"}"
                        }
                    }
                }
            })

        } catch (e: IOException) {
            Log.e("MyHealthHub", "NETWORK ERROR", e)
            runOnUiThread { statusText.text = "NETWORK ERROR: ${e.javaClass.simpleName}" }
        } catch (e: Exception) {
            Log.e("MyHealthHub", "ERROR", e)
            runOnUiThread { statusText.text = "ERROR: ${e.javaClass.simpleName}" }
        }
    }

    private suspend fun backfillSteps(
        statusText: TextView,
        startDate: LocalDate,      // 2026-01-28
        endDateExclusive: LocalDate // 2026-03-03（今日）を渡すと「昨日まで」になる
    ) {
        var d = startDate
        while (d.isBefore(endDateExclusive)) {
            val dayStart = d.atStartOfDay(JST).toInstant()
            val dayEnd = d.plusDays(1).atStartOfDay(JST).toInstant()

            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(dayStart, dayEnd)
                )
            )

            val total = response.records.sumOf { it.count }

            runOnUiThread { statusText.text = "Backfill: $d steps=$total (sending...)" }

            // サーバへ送るpayload（この形は今の /normalize に合わせやすい）
            val payload = """
        {
          "source": "health_connect",
          "metric": "steps",
          "start_at": "${dayStart}",
          "end_at": "${dayEnd}",
          "value": $total,
          "unit": "count",
          "day_jst": "${d}"
        }
        """.trimIndent()

            postJsonAsync(payload) // enqueueで送る（あなたのA案方式）

            d = d.plusDays(1)
        }

        runOnUiThread { statusText.text = "Backfill done: ${startDate}..${endDateExclusive.minusDays(1)}" }
    }

    private fun postJsonAsync(json: String) {
        val client = OkHttpClient()
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(API_URL).post(body).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { /* Logcatに出す */ }
            override fun onResponse(call: Call, response: Response) { response.close() }
        })
    }
}
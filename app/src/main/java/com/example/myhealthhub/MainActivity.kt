package com.example.myhealthhub

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {

    private val healthConnectClient by lazy { HealthConnectClient.getOrCreate(this) }
    private val JST: ZoneId = ZoneId.of("Asia/Tokyo")
    private val API_URL = "${BuildConfig.API_BASE_URL}/ingest"

    private val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val statusText = findViewById<TextView>(R.id.statusText)
        val syncButton = findViewById<Button>(R.id.syncButton)

        val requestPermissionLauncher = registerForActivityResult(
            PermissionController.createRequestPermissionResultContract()
        ) { granted: Set<String> ->
            if (granted.containsAll(permissions)) {
                lifecycleScope.launch { syncRecentSteps(statusText) }
            } else {
                statusText.text = "Health Connect の権限が必要です"
            }
        }

        // 起動時に自動同期
        lifecycleScope.launch {
            val granted = healthConnectClient.permissionController.getGrantedPermissions()
            if (granted.containsAll(permissions)) {
                syncRecentSteps(statusText)
            } else {
                requestPermissionLauncher.launch(permissions)
            }
        }

        val dashboardButton = findViewById<Button>(R.id.dashboardButton)
        dashboardButton.setOnClickListener {
            startActivity(Intent(this, DashboardActivity::class.java))
        }

        // 手動で再送するボタン
        syncButton.setOnClickListener {
            lifecycleScope.launch {
                val granted = healthConnectClient.permissionController.getGrantedPermissions()
                if (granted.containsAll(permissions)) {
                    syncRecentSteps(statusText)
                } else {
                    requestPermissionLauncher.launch(permissions)
                }
            }
        }
    }

    private suspend fun syncRecentSteps(statusText: TextView) {
        val today = LocalDate.now(JST)
        val yesterday = today.minusDays(1)
        val formatter = DateTimeFormatter.ISO_INSTANT
        val errors = mutableListOf<String>()

        for (date in listOf(yesterday, today)) {
            val dayStart: Instant = date.atStartOfDay(JST).toInstant()
            val dayEnd: Instant = if (date == today) {
                Instant.now()
            } else {
                date.plusDays(1).atStartOfDay(JST).toInstant()
            }

            runOnUiThread { statusText.text = "取得中: $date" }

            try {
                val result = healthConnectClient.aggregate(
                    AggregateRequest(
                        metrics = setOf(StepsRecord.COUNT_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(dayStart, dayEnd)
                    )
                )
                val total = result[StepsRecord.COUNT_TOTAL] ?: 0L

                Log.d("BodyDataLab", "$date steps=$total")

                val payload = """
                    {
                      "source": "health_connect",
                      "metric": "steps",
                      "start_at": "${formatter.format(dayStart)}",
                      "end_at": "${formatter.format(dayEnd)}",
                      "value": $total,
                      "unit": "count"
                    }
                """.trimIndent()

                val code = postJson(payload)
                if (code == null || code !in 200..299) {
                    errors.add("$date: 送信失敗 (code=$code)")
                    Log.e("BodyDataLab", "$date POST failed code=$code")
                } else {
                    runOnUiThread { statusText.text = "送信: $date ($total 歩)" }
                }

            } catch (e: Exception) {
                Log.e("BodyDataLab", "Error on $date", e)
                errors.add("$date: ${e.javaClass.simpleName}")
            }
        }

        runOnUiThread {
            statusText.text = if (errors.isEmpty()) {
                "同期完了 ($today)"
            } else {
                "一部エラー:\n${errors.joinToString("\n")}"
            }
        }
    }

    private suspend fun postJson(json: String): Int? = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
            val requestBuilder = Request.Builder().url(API_URL).post(body)
            if (BuildConfig.API_KEY.isNotEmpty()) {
                requestBuilder.addHeader("X-API-Key", BuildConfig.API_KEY)
            }
            client.newCall(requestBuilder.build()).execute().use { it.code }
        } catch (e: Exception) {
            Log.e("BodyDataLab", "POST failed", e)
            null
        }
    }
}

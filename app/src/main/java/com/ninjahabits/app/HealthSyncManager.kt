package com.ninjahabits.app

import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class HealthSyncManager(
    private val client: HealthConnectClient,
    private val apiUrl: String,
    private val apiKey: String,
) {
    private val JST: ZoneId = ZoneId.of("Asia/Tokyo")
    private val fmt = DateTimeFormatter.ISO_INSTANT
    private val http = OkHttpClient()

    val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
    )

    suspend fun syncAll(onStatus: (String) -> Unit) {
        val today = LocalDate.now(JST)
        val errors = mutableListOf<String>()

        for (date in listOf(today.minusDays(1), today)) {
            val start = date.atStartOfDay(JST).toInstant()
            val end = if (date == today) Instant.now() else date.plusDays(1).atStartOfDay(JST).toInstant()
            onStatus("同期中: $date")

            syncSteps(date, start, end, errors)
            syncRestingHeartRate(date, start, end, errors)
            syncHrv(date, start, end, errors)
            syncSpo2(date, start, end, errors)
            syncSleep(date, start, end, errors)
            syncCalories(date, start, end, errors)
        }

        onStatus(if (errors.isEmpty()) "同期完了 ($today)" else "一部エラー:\n${errors.joinToString("\n")}")
    }

    private suspend fun syncSteps(date: LocalDate, start: Instant, end: Instant, errors: MutableList<String>) {
        try {
            val result = client.aggregate(
                AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(start, end))
            )
            val total = result[StepsRecord.COUNT_TOTAL] ?: return
            post("steps", start, end, total.toDouble(), "count", errors, date)
        } catch (e: Exception) {
            errors.add("steps $date: ${e.javaClass.simpleName}")
        }
    }

    private suspend fun syncRestingHeartRate(date: LocalDate, start: Instant, end: Instant, errors: MutableList<String>) {
        try {
            val records = client.readRecords(
                ReadRecordsRequest(RestingHeartRateRecord::class, TimeRangeFilter.between(start, end))
            ).records
            if (records.isEmpty()) return
            val avg = records.map { it.beatsPerMinute.toDouble() }.average()
            post("heart_rate_resting", start, end, avg, "bpm", errors, date)
        } catch (e: Exception) {
            errors.add("heart_rate_resting $date: ${e.javaClass.simpleName}")
        }
    }

    private suspend fun syncHrv(date: LocalDate, start: Instant, end: Instant, errors: MutableList<String>) {
        try {
            val records = client.readRecords(
                ReadRecordsRequest(HeartRateVariabilityRmssdRecord::class, TimeRangeFilter.between(start, end))
            ).records
            if (records.isEmpty()) return
            val avg = records.map { it.heartRateVariabilityMillis }.average()
            post("hrv_rmssd", start, end, avg, "ms", errors, date)
        } catch (e: Exception) {
            errors.add("hrv_rmssd $date: ${e.javaClass.simpleName}")
        }
    }

    private suspend fun syncSpo2(date: LocalDate, start: Instant, end: Instant, errors: MutableList<String>) {
        try {
            val records = client.readRecords(
                ReadRecordsRequest(OxygenSaturationRecord::class, TimeRangeFilter.between(start, end))
            ).records
            if (records.isEmpty()) return
            val avg = records.map { it.percentage.value }.average()
            post("spo2", start, end, avg, "%", errors, date)
        } catch (e: Exception) {
            errors.add("spo2 $date: ${e.javaClass.simpleName}")
        }
    }

    private suspend fun syncSleep(date: LocalDate, start: Instant, end: Instant, errors: MutableList<String>) {
        try {
            val sessions = client.readRecords(
                ReadRecordsRequest(SleepSessionRecord::class, TimeRangeFilter.between(start, end))
            ).records
            if (sessions.isEmpty()) return

            var totalMs = 0L; var deepMs = 0L; var remMs = 0L; var lightMs = 0L
            for (s in sessions) {
                totalMs += s.endTime.toEpochMilli() - s.startTime.toEpochMilli()
                for (stage in s.stages) {
                    val d = stage.endTime.toEpochMilli() - stage.startTime.toEpochMilli()
                    when (stage.stage) {
                        SleepSessionRecord.STAGE_TYPE_DEEP -> deepMs += d
                        SleepSessionRecord.STAGE_TYPE_REM -> remMs += d
                        SleepSessionRecord.STAGE_TYPE_LIGHT,
                        SleepSessionRecord.STAGE_TYPE_SLEEPING -> lightMs += d
                    }
                }
            }
            val sessionStart = sessions.minOf { it.startTime }
            val sessionEnd = sessions.maxOf { it.endTime }
            fun toH(ms: Long) = ms / 3_600_000.0

            if (totalMs > 0) post("sleep_duration", sessionStart, sessionEnd, toH(totalMs), "hours", errors, date)
            if (deepMs > 0) post("sleep_deep", sessionStart, sessionEnd, toH(deepMs), "hours", errors, date)
            if (remMs > 0) post("sleep_rem", sessionStart, sessionEnd, toH(remMs), "hours", errors, date)
            if (lightMs > 0) post("sleep_light", sessionStart, sessionEnd, toH(lightMs), "hours", errors, date)
        } catch (e: Exception) {
            errors.add("sleep $date: ${e.javaClass.simpleName}")
        }
    }

    private suspend fun syncCalories(date: LocalDate, start: Instant, end: Instant, errors: MutableList<String>) {
        try {
            val result = client.aggregate(
                AggregateRequest(
                    setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL, TotalCaloriesBurnedRecord.ENERGY_TOTAL),
                    TimeRangeFilter.between(start, end)
                )
            )
            result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.let {
                post("active_calories", start, end, it.inKilocalories, "kcal", errors, date)
            }
            result[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.let {
                post("total_calories", start, end, it.inKilocalories, "kcal", errors, date)
            }
        } catch (e: Exception) {
            errors.add("calories $date: ${e.javaClass.simpleName}")
        }
    }

    private suspend fun post(
        metric: String, start: Instant, end: Instant,
        value: Double, unit: String,
        errors: MutableList<String>, date: LocalDate,
    ) {
        val json = """{"source":"health_connect","metric":"$metric","start_at":"${fmt.format(start)}","end_at":"${fmt.format(end)}","value":$value,"unit":"$unit"}"""
        val code = postJson(json)
        if (code == null || code !in 200..299) {
            errors.add("$metric $date: POST failed (code=$code)")
            Log.e("HealthSync", "$metric $date failed code=$code")
        }
    }

    private suspend fun postJson(json: String): Int? = withContext(Dispatchers.IO) {
        try {
            val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
            val req = Request.Builder().url(apiUrl).post(body)
            if (apiKey.isNotEmpty()) req.addHeader("X-API-Key", apiKey)
            http.newCall(req.build()).execute().use { it.code }
        } catch (e: Exception) {
            Log.e("HealthSync", "POST failed", e)
            null
        }
    }
}

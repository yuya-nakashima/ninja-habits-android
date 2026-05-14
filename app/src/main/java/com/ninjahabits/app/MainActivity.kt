package com.ninjahabits.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {

    private val healthConnectClient by lazy { HealthConnectClient.getOrCreate(this) }
    private val syncManager by lazy {
        HealthSyncManager(
            client = healthConnectClient,
            apiUrl = "${BuildConfig.API_BASE_URL}/ingest",
            apiKey = BuildConfig.API_KEY,
        )
    }
    private val prefs by lazy { getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    private val syncFmt = DateTimeFormatter.ofPattern("MM/dd HH:mm")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val statusText = findViewById<TextView>(R.id.statusText)
        val lastSyncText = findViewById<TextView>(R.id.lastSyncText)

        prefs.getString("last_sync", null)?.let {
            lastSyncText.text = "最終同期: $it"
        }

        val requestPermissionLauncher = registerForActivityResult(
            PermissionController.createRequestPermissionResultContract()
        ) { granted ->
            if (granted.containsAll(syncManager.permissions)) {
                lifecycleScope.launch { runSync(statusText, lastSyncText) }
            } else {
                statusText.text = "Health Connect の権限が必要です"
            }
        }

        lifecycleScope.launch {
            val granted = healthConnectClient.permissionController.getGrantedPermissions()
            if (granted.containsAll(syncManager.permissions)) {
                runSync(statusText, lastSyncText)
            } else {
                requestPermissionLauncher.launch(syncManager.permissions)
            }
        }

        findViewById<Button>(R.id.dashboardButton).setOnClickListener {
            startActivity(Intent(this, DashboardActivity::class.java))
        }
        findViewById<Button>(R.id.reflectionButton).setOnClickListener {
            startActivity(Intent(this, ReflectionActivity::class.java))
        }
        findViewById<Button>(R.id.wishListButton).setOnClickListener {
            startActivity(Intent(this, WishListActivity::class.java))
        }
    }

    private suspend fun runSync(statusText: TextView, lastSyncText: TextView) {
        syncManager.syncAll { msg ->
            runOnUiThread { statusText.text = msg }
        }
        val now = ZonedDateTime.now(ZoneId.of("Asia/Tokyo")).format(syncFmt)
        prefs.edit().putString("last_sync", now).apply()
        runOnUiThread { lastSyncText.text = "最終同期: $now" }
    }
}

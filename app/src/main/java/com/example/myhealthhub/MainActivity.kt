package com.example.myhealthhub

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val healthConnectClient by lazy { HealthConnectClient.getOrCreate(this) }
    private val syncManager by lazy {
        HealthSyncManager(
            client = healthConnectClient,
            apiUrl = "${BuildConfig.API_BASE_URL}/ingest",
            apiKey = BuildConfig.API_KEY,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val statusText = findViewById<TextView>(R.id.statusText)

        val requestPermissionLauncher = registerForActivityResult(
            PermissionController.createRequestPermissionResultContract()
        ) { granted ->
            if (granted.containsAll(syncManager.permissions)) {
                lifecycleScope.launch { syncManager.syncAll { runOnUiThread { statusText.text = it } } }
            } else {
                statusText.text = "Health Connect の権限が必要です"
            }
        }

        lifecycleScope.launch {
            val granted = healthConnectClient.permissionController.getGrantedPermissions()
            if (granted.containsAll(syncManager.permissions)) {
                syncManager.syncAll { runOnUiThread { statusText.text = it } }
            } else {
                requestPermissionLauncher.launch(syncManager.permissions)
            }
        }

        findViewById<Button>(R.id.syncButton).setOnClickListener {
            lifecycleScope.launch {
                val granted = healthConnectClient.permissionController.getGrantedPermissions()
                if (granted.containsAll(syncManager.permissions)) {
                    syncManager.syncAll { runOnUiThread { statusText.text = it } }
                } else {
                    requestPermissionLauncher.launch(syncManager.permissions)
                }
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
}

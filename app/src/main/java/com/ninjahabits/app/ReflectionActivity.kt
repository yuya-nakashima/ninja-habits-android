package com.ninjahabits.app

import android.content.Context
import android.os.Bundle
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class ReflectionActivity : AppCompatActivity() {

    private val healthConnectClient by lazy { HealthConnectClient.getOrCreate(this) }
    private val syncManager by lazy {
        HealthSyncManager(
            client = healthConnectClient,
            apiUrl = "${BuildConfig.API_BASE_URL}/ingest",
            apiKey = BuildConfig.API_KEY,
        )
    }
    private val prefs by lazy { getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reflection)

        val webView = findViewById<WebView>(R.id.webView)
        webView.settings.javaScriptEnabled = true
        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    view.loadData(
                        "<html><body><p>ページを読み込めませんでした (${error.errorCode})</p></body></html>",
                        "text/html", "utf-8"
                    )
                }
            }
        }
        webView.loadUrl("${BuildConfig.API_BASE_URL}/ui/reflections")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack()
                else { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
            }
        })

        // バックグラウンドで Health Connect 同期
        syncInBackground()
    }

    private fun syncInBackground() {
        val requestPermissionLauncher = registerForActivityResult(
            PermissionController.createRequestPermissionResultContract()
        ) { granted ->
            if (granted.containsAll(syncManager.permissions)) {
                lifecycleScope.launch { runSync() }
            }
        }

        lifecycleScope.launch {
            val granted = healthConnectClient.permissionController.getGrantedPermissions()
            if (granted.containsAll(syncManager.permissions)) {
                runSync()
            } else {
                requestPermissionLauncher.launch(syncManager.permissions)
            }
        }
    }

    private suspend fun runSync() {
        syncManager.syncAll {}
        val now = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Tokyo"))
            .format(java.time.format.DateTimeFormatter.ofPattern("MM/dd HH:mm"))
        prefs.edit().putString("last_sync", now).apply()
    }
}

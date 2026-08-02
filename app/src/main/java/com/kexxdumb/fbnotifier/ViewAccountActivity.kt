package com.kexxdumb.fbnotifier

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

const val EXTRA_PROFILE_ID = "profile_id"
private const val FB_URL = "https://m.facebook.com"

class ViewAccountActivity : AppCompatActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_account)

        val profileId = intent.getStringExtra(EXTRA_PROFILE_ID)
        val profile = ProfileStore.list(this).find { it.id == profileId }
        if (profile == null) {
            Toast.makeText(this, "No se encontró la cuenta.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val cookieManager = CookieManager.getInstance()
        val webView = findViewById<WebView>(R.id.webView)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                view.evaluateJavascript(CleanupScript.JS, null)
            }
        }

        // Sin esto, tocar "Guardar imagen"/descargar un archivo dentro del
        // WebView no hace nada — hay que decirle explícitamente al sistema
        // qué hacer con esa descarga.
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            try {
                val request = DownloadManager.Request(Uri.parse(url))
                val cookie = cookieManager.getCookie(url)
                if (!cookie.isNullOrBlank()) {
                    request.addRequestHeader("Cookie", cookie)
                }
                request.addRequestHeader("User-Agent", userAgent)
                request.setMimeType(mimeType)
                val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                manager.enqueue(request)
                Toast.makeText(this, "Descargando…", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                // Mensaje con el error real, temporalmente, para saber qué
                // está fallando en vez de adivinar.
                Toast.makeText(this, "Error al descargar: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        cookieManager.removeAllCookies(null)
        profile.cookieString.split(";").forEach { pair ->
            val trimmed = pair.trim()
            if (trimmed.isNotEmpty()) {
                cookieManager.setCookie(FB_URL, trimmed)
            }
        }
        cookieManager.flush()
        webView.loadUrl(FB_URL)
    }

    override fun onBackPressed() {
        val webView = findViewById<WebView>(R.id.webView)
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}

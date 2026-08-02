package com.kexxdumb.fbnotifier

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
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

        // Limpia cualquier sesión anterior (de otra cuenta que hayas visto
        // antes) de forma síncrona antes de cargar, e inyecta las cookies
        // guardadas de esta cuenta específica.
        cookieManager.removeAllCookies { _ ->
            profile.cookieString.split(";").forEach { pair ->
                val trimmed = pair.trim()
                if (trimmed.isNotEmpty()) {
                    cookieManager.setCookie(FB_URL, trimmed)
                }
            }
            cookieManager.flush()
            webView.loadUrl(FB_URL)
        }
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

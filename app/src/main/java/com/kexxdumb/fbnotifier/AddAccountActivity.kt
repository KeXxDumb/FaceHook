package com.kexxdumb.fbnotifier

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val LOGIN_URL = "https://m.facebook.com/login"
private const val FB_URL = "https://m.facebook.com"

class AddAccountActivity : AppCompatActivity() {

    private var handledLogin = false
    private var pendingCookie: String? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_account)

        // Cookies limpias antes de empezar, para no mezclar con otra cuenta
        // agregada previamente en esta misma sesión de la app.
        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies(null)
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(findViewById(R.id.webView), true)

        val webView = findViewById<WebView>(R.id.webView)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                checkLoginCompleted()
            }
        }
        webView.loadUrl(LOGIN_URL)

        findViewById<TextView>(R.id.saveAccountButton).setOnClickListener {
            confirmAddAccount()
        }
    }

    // Solo acepta el login cuando la cookie real de sesión (c_user) está
    // presente. La URL sola no es confiable: Facebook pasa por varias
    // redirecciones intermedias antes de terminar el login.
    private fun checkLoginCompleted() {
        if (handledLogin) return
        val cookieString = CookieManager.getInstance().getCookie(FB_URL) ?: return
        if (!FacebookPoller.hasAuthCookie(cookieString)) return

        handledLogin = true
        pendingCookie = cookieString
        findViewById<View>(R.id.namePromptOverlay).visibility = View.VISIBLE
    }

    private fun confirmAddAccount() {
        val cookieString = pendingCookie ?: return
        val label = findViewById<EditText>(R.id.labelInput).text.toString()

        val profile = try {
            ProfileStore.add(this, label, cookieString)
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo guardar la cuenta, intenta de nuevo.", Toast.LENGTH_LONG).show()
            return
        }

        // Cerrar de inmediato: guardar es local y ya está hecho. El "seed"
        // es solo para no recibir notificaciones viejas de golpe, así que
        // si falla (red, bloqueo, etc.) no debe bloquear nada.
        finish()

        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val counts = FacebookPoller.fetchCounts(profile.cookieString)
                    ProfileStore.setCounts(applicationContext, profile.id, counts)
                } catch (_: Exception) {
                    // sin red o bloqueado: no pasa nada, el próximo poll periódico lo intentará de nuevo
                }
            }
        }
    }
}

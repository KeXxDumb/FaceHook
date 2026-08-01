package com.kexxdumb.fbnotifier

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
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

        val webView = findViewById<WebView>(R.id.webView)
        cookieManager.setAcceptThirdPartyCookies(webView, true)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                checkLoginCompleted()
            }
        }
        webView.loadUrl(LOGIN_URL)
    }

    // Solo acepta el login cuando la cookie real de sesión (c_user) está
    // presente. La URL sola no es confiable: Facebook pasa por varias
    // redirecciones intermedias antes de terminar el login.
    private fun checkLoginCompleted() {
        if (handledLogin) return
        val cookieString = CookieManager.getInstance().getCookie(FB_URL) ?: return
        if (!FacebookPoller.hasAuthCookie(cookieString)) return

        handledLogin = true
        findViewById<View>(R.id.savingOverlay).visibility = View.VISIBLE
        saveAccount(cookieString)
    }

    private fun saveAccount(cookieString: String) {
        scope.launch {
            val name = withContext(Dispatchers.IO) {
                try {
                    FacebookPoller.fetchDisplayName(cookieString)
                } catch (_: Exception) {
                    null
                }
            }

            val profile = try {
                ProfileStore.add(this@AddAccountActivity, name ?: "", cookieString)
            } catch (e: Exception) {
                Toast.makeText(
                    this@AddAccountActivity,
                    "No se pudo guardar la cuenta, intenta de nuevo.",
                    Toast.LENGTH_LONG,
                ).show()
                finish()
                return@launch
            }

            finish()

            withContext(Dispatchers.IO) {
                try {
                    val counts = FacebookPoller.fetchCounts(profile.cookieString)
                    ProfileStore.setCounts(applicationContext, profile.id, counts)
                } catch (_: Exception) {
                    // sin red o bloqueado: el próximo poll periódico lo intentará de nuevo
                }
            }
        }
    }
}

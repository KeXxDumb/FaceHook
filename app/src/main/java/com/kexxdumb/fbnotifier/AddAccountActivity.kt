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
private const val ME_URL = "https://m.facebook.com/me"
private const val FB_URL = "https://m.facebook.com"

private enum class Phase { LOGGING_IN, LOADING_PROFILE, DONE }

class AddAccountActivity : AppCompatActivity() {

    private var phase = Phase.LOGGING_IN
    private val scope = CoroutineScope(Dispatchers.Main)
    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_account)

        webView = findViewById(R.id.webView)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                when (phase) {
                    Phase.LOGGING_IN -> checkLoginCompleted()
                    Phase.LOADING_PROFILE -> readDisplayName()
                    Phase.DONE -> {}
                }
            }
        }

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)
        webView.clearCache(true)
        webView.clearHistory()
        webView.clearFormData()
        cookieManager.removeAllCookies(null)
        webView.loadUrl(LOGIN_URL)
    }

    // Solo acepta el login cuando la cookie real de sesión (c_user) está
    // presente. La URL sola no es confiable: Facebook pasa por varias
    // redirecciones intermedias antes de terminar el login.
    private fun checkLoginCompleted() {
        val cookieString = CookieManager.getInstance().getCookie(FB_URL) ?: return
        if (!FacebookPoller.hasAuthCookie(cookieString)) return

        phase = Phase.LOADING_PROFILE
        // Navega el MISMO WebView (misma sesión ya autenticada) a la
        // página de perfil, en vez de hacer una petición HTTP aparte —
        // así se evita que falte algún cookie/token que Facebook exige
        // y que antes causaba que no detectara el nombre real.
        webView.loadUrl(ME_URL)
    }

    private fun readDisplayName() {
        phase = Phase.DONE
        findViewById<View>(R.id.savingOverlay).visibility = View.VISIBLE

        webView.evaluateJavascript("(function(){return document.title;})();") { rawTitle ->
            val title = rawTitle?.trim('"')
                ?.replace("\\\"", "\"")
                ?.removeSuffix(" | Facebook")
                ?.removeSuffix(" - Facebook")
                ?.trim()
            saveAccount(title?.takeIf { it.isNotBlank() && it != "null" })
        }
    }

    private fun saveAccount(name: String?) {
        val cookieString = CookieManager.getInstance().getCookie(FB_URL) ?: ""

        val profile = try {
            ProfileStore.add(this, name ?: "", cookieString)
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo guardar la cuenta, intenta de nuevo.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        finish()

        scope.launch {
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

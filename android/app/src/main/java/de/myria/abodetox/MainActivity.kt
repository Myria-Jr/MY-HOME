package de.myria.abodetox

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.WebViewAssetLoader
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import org.json.JSONObject
import java.io.File

/**
 * Thin native shell around the Abo Detox web app (bundled in assets):
 *  - Google sign-in via Play Services AuthorizationClient (Google blocks OAuth inside WebViews)
 *  - Material You dynamic colours + system-bar insets handed to the page
 *  - Android back button routed through the page first
 */
class MainActivity : ComponentActivity() {

    private lateinit var web: WebView
    private var insetTop = 0f
    private var insetBottom = 0f

    private val authLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        try {
            val auth = Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(result.data)
            sendToken(auth.accessToken, null)
        } catch (e: Exception) {
            sendToken(null, describe(e))
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web = WebView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = true
            addJavascriptInterface(Bridge(), "AndroidBridge")
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                    loader.shouldInterceptRequest(request.url)

                // Anything that isn't the bundled app (setup links etc.) opens in the real browser.
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (request.url.host == "appassets.androidplatform.net") return false
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                    return true
                }

                override fun onPageFinished(view: WebView, url: String) = pushInsets()
            }
        }
        setContentView(web)

        ViewCompat.setOnApplyWindowInsetsListener(web) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val d = resources.displayMetrics.density
            insetTop = bars.top / d
            insetBottom = bars.bottom / d
            pushInsets()
            WindowInsetsCompat.CONSUMED
        }

        onBackPressedDispatcher.addCallback(this) {
            web.evaluateJavascript("window.__onBack ? window.__onBack() : false") { handled ->
                if (handled != "true") {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        }

        if (savedInstanceState == null) web.loadUrl("https://appassets.androidplatform.net/assets/index.html")
        else web.restoreState(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    private fun pushInsets() {
        web.evaluateJavascript("window.__setInsets && window.__setInsets($insetTop, $insetBottom)", null)
    }

    private fun authorize(interactive: Boolean) {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(YT_SCOPE)))
            .build()
        Identity.getAuthorizationClient(this).authorize(request)
            .addOnSuccessListener { result ->
                val pending = result.pendingIntent
                if (result.hasResolution() && pending != null) {
                    // Silent auto sign-in at app start must never pop up a consent screen
                    if (!interactive) { sendToken(null, "NEEDS_UI"); return@addOnSuccessListener }
                    authLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                } else {
                    sendToken(result.accessToken, null)
                }
            }
            .addOnFailureListener { e -> sendToken(null, describe(e)) }
    }

    private fun describe(e: Exception): String {
        if (e is ApiException) {
            return when (e.statusCode) {
                10 -> "Google kennt diese App noch nicht. Leg in der Cloud Console einen Android-OAuth-Client an (Paketname + SHA-1 aus der Anleitung)."
                16, 12501 -> "Anmeldung abgebrochen."
                7 -> "Keine Internetverbindung."
                else -> "Anmeldung fehlgeschlagen (Code ${e.statusCode})."
            }
        }
        return e.message ?: "Anmeldung abgebrochen."
    }

    private fun sendToken(token: String?, error: String?) {
        val t = if (token != null) JSONObject.quote(token) else "null"
        val err = if (error != null) JSONObject.quote(error) else "null"
        runOnUiThread { web.evaluateJavascript("window.__nativeToken && window.__nativeToken($t, $err)", null) }
    }

    /** Android 12+ system palette (Material You) as {a1:{0:'#..',10:..}, ...}; null below 12. */
    private fun palette(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val tones = intArrayOf(0, 10, 50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000)
        val groups = mapOf(
            "a1" to "system_accent1_", "a2" to "system_accent2_", "a3" to "system_accent3_",
            "n1" to "system_neutral1_", "n2" to "system_neutral2_",
        )
        val out = JSONObject()
        for ((key, prefix) in groups) {
            val g = JSONObject()
            for (t in tones) {
                @SuppressLint("DiscouragedApi")
                val id = resources.getIdentifier(prefix + t, "color", "android")
                if (id != 0) g.put(t.toString(), String.format("#%06X", 0xFFFFFF and getColor(id)))
            }
            out.put(key, g)
        }
        return out.toString()
    }

    inner class Bridge {
        @JavascriptInterface fun requestToken(interactive: Boolean) = runOnUiThread { authorize(interactive) }
        @JavascriptInterface fun openUrl(url: String) = runOnUiThread {
            val uri = Uri.parse(url)
            if (uri.scheme == "https" && uri.host?.endsWith("youtube.com") == true) {
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) } // YouTube app picks these up
            }
        }
        @JavascriptInterface fun share(text: String, fileName: String) = runOnUiThread {
            runCatching {
                val dir = File(cacheDir, "exports").apply { mkdirs() }
                val safe = fileName.replace(Regex("[^A-Za-z0-9äöüÄÖÜß._-]"), "-")
                val file = File(dir, safe).apply { writeText(text) }
                val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.files", file)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, safe)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(send, "Liste exportieren"))
            }
        }
        @JavascriptInterface fun haptic() = runOnUiThread { web.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
        @JavascriptInterface fun setDark(dark: Boolean) = runOnUiThread {
            val c = WindowCompat.getInsetsController(window, window.decorView)
            c.isAppearanceLightStatusBars = !dark
            c.isAppearanceLightNavigationBars = !dark
            window.decorView.setBackgroundColor(if (dark) Color.parseColor("#141218") else Color.parseColor("#FEF7FF"))
        }
        @JavascriptInterface fun palette(): String? = this@MainActivity.palette()
        @JavascriptInterface fun insets(): String = """{"top":$insetTop,"bottom":$insetBottom}"""
        @JavascriptInterface fun signOut() { /* token lives only in the page; nothing stored natively */ }
    }

    companion object {
        private const val YT_SCOPE = "https://www.googleapis.com/auth/youtube"
    }
}

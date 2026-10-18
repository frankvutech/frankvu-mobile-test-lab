package com.mobiletestplayground.app.scenarios.webview

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mobiletestplayground.app.BuildConfig
import com.mobiletestplayground.app.R
import com.mobiletestplayground.core.ui.TestTags
import com.mobiletestplayground.core.ui.components.PlaygroundScaffold
import com.mobiletestplayground.core.ui.components.ValueRow
import org.json.JSONObject

const val WEB_PLAYGROUND_URL = "file:///android_asset/playground.html"

/** Receives messages posted by the page. JavaScript calls it on a background thread. */
class WebBridge(private val onMessage: (String) -> Unit) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun postMessage(message: String) {
        main.post { onMessage(message) }
    }
}

/**
 * The one place the app hosts a classic Android View: Compose has no WebView, so it is embedded
 * with AndroidView. Everything around it is Compose. The page is a local asset with no network
 * access; JavaScript is enabled because the scenario exists to test JavaScript interaction.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(onBack: () -> Unit) {
    var lastMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val language = LocalConfiguration.current.locales[0].language
    val nativeGreeting = stringResource(R.string.webview_native_greeting)

    PlaygroundScaffold(stringResource(R.string.scenario_webview), TestTags.WEBVIEW_SCREEN, onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                ValueRow(
                    stringResource(R.string.webview_message),
                    lastMessage ?: stringResource(R.string.value_none),
                    TestTags.WEBVIEW_MESSAGE,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { webView?.evaluateJavascript("receiveFromNative(${JSONObject.quote(nativeGreeting)})", null) },
                        modifier = Modifier.testTag(TestTags.WEBVIEW_SEND_TO_WEB),
                    ) { Text(stringResource(R.string.webview_send)) }
                    OutlinedButton(onClick = { webView?.reload() }, modifier = Modifier.testTag(TestTags.WEBVIEW_RELOAD)) {
                        Text(stringResource(R.string.webview_reload))
                    }
                }
            }
            AndroidView(
                factory = { context ->
                    WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
                    WebView(context).apply {
                        id = R.id.webview
                        settings.javaScriptEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        addJavascriptInterface(WebBridge { lastMessage = it }, "AndroidBridge")
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String?) {
                                view.evaluateJavascript("setLanguage('$language')", null)
                            }
                        }
                        loadUrl(WEB_PLAYGROUND_URL)
                        webView = this
                    }
                },
                modifier = Modifier.fillMaxWidth().weight(1f).testTag(TestTags.WEBVIEW),
            )
        }
    }
}

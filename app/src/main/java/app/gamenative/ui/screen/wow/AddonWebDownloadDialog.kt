package app.gamenative.ui.screen.wow

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** What a page asked the in-app browser to download. */
data class BrowserDownload(val url: String, val userAgent: String, val cookies: String?, val fileName: String)

/**
 * Opens an addon's download page in an in-app browser, so sites that block plain app requests
 * (CurseForge) still serve the file. The first download the page starts is handed to [onDownload].
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AddonWebDownloadDialog(
    title: String,
    pageUrl: String,
    onDownload: (BrowserDownload) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("$title: the download starts by itself", Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
                AndroidView(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            webViewClient = WebViewClient()
                            var handled = false
                            setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                                if (handled) return@setDownloadListener
                                handled = true
                                onDownload(
                                    BrowserDownload(
                                        url = url,
                                        userAgent = userAgent,
                                        cookies = CookieManager.getInstance().getCookie(url),
                                        fileName = URLUtil.guessFileName(url, contentDisposition, mimeType),
                                    ),
                                )
                            }
                            loadUrl(pageUrl)
                        }
                    },
                    onRelease = { it.destroy() },
                )
            }
        }
    }
}

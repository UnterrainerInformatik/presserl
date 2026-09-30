package info.unterrainer.presserl.admin.auth

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * The issuer [page] in a web view (design D3/D4): JavaScript on for the issuer's pages, no file or content access, no
 * JavaScript interface. Navigations to the redirect URI go to [auth] and are never loaded; links to other hosts open
 * in the browser. On the issuer's password page [autofill] submits the slip's credentials once; [onRejected] follows a
 * refusal.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginWebView(
    page: IssuerPage,
    auth: AndroidAuthClient,
    autofill: SlipAutofill,
    onRejected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rejected by rememberUpdatedState(onRejected)
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.javaScriptCanOpenWindowsAutomatically = false
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val url = request.url.toString()
                        if (auth.interceptRedirect(url)) return true
                        if (request.url.host != auth.issuerHost) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            return true
                        }
                        return false
                    }

                    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                        // a redirect the override did not see (e.g. after a form post) is stopped here
                        if (auth.interceptRedirect(url)) view.stopLoading()
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        if (Uri.parse(url).host != auth.issuerHost) return
                        view.evaluateJavascript(SlipAutofill.PROBE_SCRIPT) { result ->
                            val probe = runCatching { Json.parseToJsonElement(Json.decodeFromString<String>(result)).jsonArray }
                                .getOrNull() ?: return@evaluateJavascript
                            if (!probe[0].jsonPrimitive.boolean) return@evaluateJavascript
                            when (val action = autofill.onPasswordPage(submittedFromThisPage = probe[1].jsonPrimitive.boolean)) {
                                is SlipAutofill.Action.Submit -> view.evaluateJavascript(action.script, null)
                                SlipAutofill.Action.Rejected -> rejected()
                                SlipAutofill.Action.None -> Unit
                            }
                        }
                    }

                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                        // without the issuer the logout still ends the app's session
                        if (request.isForMainFrame && auth.page.value?.logout == true) auth.finishLogout()
                    }
                }
                tag = page.url
                loadUrl(page.url)
            }
        },
        update = { view ->
            if (view.tag != page.url) {
                view.tag = page.url
                view.loadUrl(page.url)
            }
        },
    )
}

/** Forgets the issuer session in the web view: cookies and web storage. */
fun clearWebSession() {
    CookieManager.getInstance().removeAllCookies(null)
    CookieManager.getInstance().flush()
    WebStorage.getInstance().deleteAllData()
}

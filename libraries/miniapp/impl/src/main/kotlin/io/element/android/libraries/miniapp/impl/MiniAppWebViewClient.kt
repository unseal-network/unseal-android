/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.miniapp.impl

import android.graphics.Bitmap
import android.webkit.ConsoleMessage
import android.webkit.JsPromptResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import io.element.android.libraries.miniapp.api.MiniAppConfig
import org.json.JSONObject
import timber.log.Timber

/**
 * [WebViewClient] for mini-app WebView sessions.
 *
 * Responsibilities:
 * - Inject the startup JS script at page-start (platform, token, user, options, appId).
 * - Block navigation to custom schemes (e.g. `pinduoduo://`) and report them to the host.
 */
internal class MiniAppWebViewClient(
    private val config: MiniAppConfig,
    private val startupScript: String,
    private val onPageFinished: () -> Unit,
    private val onPageStarted: () -> Unit,
) : WebViewClient() {

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        Timber.d("MiniApp: onPageStarted url=$url")
        onPageStarted()
        // Inject startup globals (platform, token, user, device, shim) into the
        // new page's JS context before the page's own scripts run.
        // evaluateJavascript is the modern replacement for loadUrl("javascript:...").
        view.evaluateJavascript(startupScript, null)
    }

    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        Timber.d("MiniApp: page finished $url")
        onPageFinished()
        // Re-inject as a fallback — some pages execute their main bundle before
        // onPageStarted injection completes (e.g. pre-cached local bundles).
        view.evaluateJavascript(startupScript, null)
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val uri = request.url ?: return false
        return when (uri.scheme?.lowercase()) {
            "http", "https" -> false  // let WebView handle normal HTTP navigation
            "pinduoduo" -> {
                // Custom app-scheme link — ignore silently on Android.
                Timber.d("MiniApp: ignoring pinduoduo:// link $uri")
                true
            }
            else -> {
                Timber.d("MiniApp: unknown scheme ${uri.scheme}, ignoring")
                true
            }
        }
    }
}

/**
 * [WebChromeClient] for mini-app WebView sessions.
 *
 * Handles:
 * - JavaScript console messages → Timber log (makes bridge errors visible in Logcat).
 * - `window.prompt()` sync bridge calls — forwards to [MiniAppJsBridge] so that
 *   iOS-style `window.prompt(JSON.stringify({name:"storage.get",args:{key:"..."}}))` calls
 *   work on Android the same way as iOS `WKUIDelegate.webView(_:runJavaScriptTextInputPanel…)`.
 */
internal class MiniAppChromeClient(
    private val bridge: MiniAppJsBridge,
) : WebChromeClient() {

    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
        val level = consoleMessage.messageLevel()
        val msg = "[JS ${consoleMessage.sourceId()}:${consoleMessage.lineNumber()}] ${consoleMessage.message()}"
        when (level) {
            ConsoleMessage.MessageLevel.ERROR -> Timber.e("MiniApp $msg")
            ConsoleMessage.MessageLevel.WARNING -> Timber.w("MiniApp $msg")
            else -> Timber.d("MiniApp $msg")
        }
        return true
    }

    // Handle iOS-style sync bridge calls that arrive via window.prompt().
    // iOS uses WKUIDelegate.webView(_:runJavaScriptTextInputPanelWithPrompt:...) for sync
    // calls like storage.get, appInfo, safeArea, etc.  Android's equivalent hook is
    // WebChromeClient.onJsPrompt().
    override fun onJsPrompt(
        view: WebView,
        url: String,
        message: String,
        defaultValue: String?,
        result: JsPromptResult,
    ): Boolean {
        return try {
            val payload = JSONObject(message)
            val name = payload.optString("name").takeIf { it.isNotBlank() } ?: return false
            val args = payload.opt("args")
            val argsJson = when (args) {
                is JSONObject -> args.toString()
                is String -> args
                else -> "{}"
            }
            val response = bridge.handleSyncPrompt(name, argsJson) ?: return false
            result.confirm(response)
            true
        } catch (_: Exception) {
            false
        }
    }
}

// ── Startup script builder ────────────────────────────────────────────────────

/**
 * Build the JS startup script injected at [MiniAppWebViewClient.onPageStarted].
 *
 * Sets the following globals to match the iOS `initInfo()` injection:
 * - `window.___platform = 'android'`
 * - `window.___device = { ... }`
 * - `window.___appId = <appId>`
 * - `window.___token = <tokenJSON | null>`
 * - `window.___user  = <userJSON  | null>`
 * - `window.___options = <optionsJSON>`
 *
 * Also installs a `window.webkit.messageHandlers` compatibility shim so that
 * iOS-style `window.webkit.messageHandlers.X.postMessage(body)` calls are
 * transparently forwarded to the Android `window.webkit.X(JSON.stringify(body))`
 * bridge. This lets a single JS bundle run on both platforms without changes.
 */
internal fun buildStartupScript(config: MiniAppConfig): String {
    val tokenJson = config.token?.let { t ->
        """{"accessToken":${t.accessToken.jsonQuote()},"refreshToken":${t.refreshToken?.jsonQuote() ?: "null"},"platform":${t.platform?.jsonQuote() ?: "null"}}"""
    } ?: "null"

    val userJson = config.user?.let { u ->
        buildString {
            append("{")
            append(""""userId":${u.userId.jsonQuote()}""")
            u.displayName?.let { append(""","displayName":${it.jsonQuote()}""") }
            u.avatarUrl?.let { append(""","avatarUrl":${it.jsonQuote()}""") }
            u.powerLevel?.let { append(""","powerLevel":$it""") }
            u.avatarBgColor?.let { append(""","avatarBgColor":${it.jsonQuote()}""") }
            u.avatarTextColor?.let { append(""","avatarTextColor":${it.jsonQuote()}""") }
            u.isAgent?.let { append(""","isAgent":$it""") }
            u.isSelf?.let { append(""","isSelf":$it""") }
            u.agentId?.let { append(""","agentId":${it.jsonQuote()}""") }
            append("}")
        }
    } ?: "null"

    val optionsJson = if (config.options.isEmpty()) {
        "{}"
    } else {
        config.options.entries.joinToString(",", prefix = "{", postfix = "}") { (k, v) ->
            "${k.jsonQuote()}:${v.toJsonLiteral()}"
        }
    }

    val deviceJson = """{
        "platform":"android",
        "model":${android.os.Build.MODEL.jsonQuote()},
        "brand":${android.os.Build.BRAND.jsonQuote()},
        "systemVersion":${android.os.Build.VERSION.RELEASE.jsonQuote()},
        "systemName":"Android"
    }"""

    // Compatibility shim: maps iOS-style window.webkit.messageHandlers.X.postMessage(body)
    // → Android window.webkit.X(JSON.stringify(body)) so a single JS bundle runs on both.
    val shimScript = """
(function(){
    var w=window.webkit;
    if(!w||w.messageHandlers)return;
    try{w.messageHandlers=new Proxy({},{get:function(_,n){return{postMessage:function(b){var f=w[n];if(typeof f==='function')f(typeof b==='string'?b:JSON.stringify(b));}};}})}catch(e){}
})();
""".trimIndent()

    return buildString {
        append("window.___platform='android';")
        append("window.___device=$deviceJson;")
        append("window.___appId=${config.appId};")
        append("window.___token=$tokenJson;")
        append("window.___user=$userJson;")
        append("window.___options=$optionsJson;")
        append(shimScript)
    }
}

private fun String.jsonQuote(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")}\""

private fun Any.toJsonLiteral(): String = when (this) {
    is String -> jsonQuote()
    is Boolean -> toString()
    is Number -> toString()
    is List<*> -> joinToString(",", "[", "]") { it?.toJsonLiteral() ?: "null" }
    is Map<*, *> -> entries.joinToString(",", "{", "}") { (k, v) ->
        "${k.toString().jsonQuote()}:${v?.toJsonLiteral() ?: "null"}"
    }
    else -> "\"${toString().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")}\""
}

/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.miniapp.impl

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import io.element.android.libraries.miniapp.api.MiniAppConfig
import io.element.android.libraries.miniapp.api.MiniAppHostBridge
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient
import timber.log.Timber

// ── Bundle load state ─────────────────────────────────────────────────────────

/**
 * Internal sealed state for the ZIP bundle download lifecycle.
 *
 * Mirrors iOS `WebViewController`'s `hasLoad` / `hasLoadFinish` guards, but
 * expressed as an explicit state machine so the Composable can show appropriate
 * UI for each phase.
 */
private sealed interface BundleState {
    /** Direct URL mode (no ZIP), or cache hit — WebView can load immediately. */
    data class Ready(val loadUrl: String) : BundleState

    /** ZIP is being downloaded; [progress] is in `[0.0, 1.0]`. */
    data class Downloading(val progress: Float) : BundleState

    /** ZIP extracted, WebView loading the page — overlay stays until onPageFinished. */
    data class WebLoading(val loadUrl: String) : BundleState

    /** Download or extraction failed. */
    data class Error(val message: String) : BundleState
}

// ── Public composable ─────────────────────────────────────────────────────────

/**
 * Composable that renders a mini-app inside an Android [WebView].
 *
 * This is the primary entry point for the `libraries/miniapp` library.
 * It mirrors the role of iOS `EditorControllerWrapper` / `WebViewController`.
 *
 * ## Load modes
 *
 * ### Remote mode ([MiniAppConfig.zipUrl] == null)
 * The WebView loads [MiniAppConfig.url] directly as a remote URL.
 * Equivalent to iOS `init(url:cache:…)` path.
 *
 * ### Bundle mode ([MiniAppConfig.zipUrl] != null)
 * Mirrors iOS `checkLoad()` → `loadMiniApp()` → `download()` → `unzipLocalZip()` → `loadLocal()`:
 *
 * 1. [MiniAppBundleManager.prepareBundle] checks the local cache. If the bundle
 *    was already extracted on a previous launch it is used immediately (cache hit).
 * 2. If not cached: the ZIP is downloaded from [MiniAppConfig.zipUrl] with OkHttp
 *    (progress 0 → 0.95 reported while streaming).
 * 3. The ZIP is extracted with [java.util.zip.ZipInputStream] into
 *    `<filesDir>/miniapp/app_<appId>/`.
 * 4. `index.html` from the extracted directory is loaded via a `file://` URL.
 *
 * A full-screen loading overlay shows download progress while the ZIP is being
 * fetched. On extraction failure the overlay switches to an error message.
 *
 * ## JS bridge
 * [MiniAppJsBridge] is registered as `window.webkit`; an iOS-compatibility shim
 * at startup maps `window.webkit.messageHandlers.X.postMessage(body)` calls so the
 * same JS bundle runs on both platforms without changes.
 *
 * @param config        Mini-app session configuration.
 * @param hostBridge    Optional host callbacks (sendMessage, getToken, etc.).
 * @param okHttpClient  OkHttp client used for ZIP downloads and the `request` bridge.
 * @param modifier      Compose modifier applied to the outer container.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MiniAppView(
    config: MiniAppConfig,
    hostBridge: MiniAppHostBridge?,
    okHttpClient: OkHttpClient,
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ── Bundle state ─────────────────────────────────────────────────────────
    var bundleState by remember(config.appId, config.zipUrl) {
        mutableStateOf<BundleState>(
            when {
                !config.zipUrl.isNullOrBlank() -> BundleState.Downloading(0f)
                !config.url.isBlank() -> BundleState.Ready(config.url)
                // Both url and zipUrl are empty — bundle could not be resolved.
                // Show an error overlay instead of a silent blank WebView.
                else -> BundleState.Error("Bundle URL not available (appId=${config.appId})")
            }
        )
    }
    // WebView and bridge holders: set in AndroidView factory.
    val webViewHolder = remember { arrayOfNulls<WebView>(1) }
    val bridgeHolder = remember { arrayOfNulls<MiniAppJsBridge>(1) }
    // Mutable slot: filled by LaunchedEffect once the load URL is known.
    // Called by MiniAppWebViewClient.onPageFinished to switch bundleState → Ready.
    val pageFinishedAction = remember { arrayOfNulls<() -> Unit>(1) }

    // ── Startup script ────────────────────────────────────────────────────────
    val startupScript = remember(config) { buildStartupScript(config) }

    // For bundle mode: serve the extracted directory via https://appassets.androidplatform.net/
    // so the WebApp's fetch() calls (e.g. loading WASM) work correctly.
    // The Fetch API blocks file:// URLs; HTTPS served via WebViewAssetLoader is the fix,
    // mirroring how iOS uses GCDWebServer to serve the local bundle over http://localhost:PORT.
    val bundleDir = remember(config.appId) { java.io.File(context.filesDir, "miniapp/app_${config.appId}") }
    val assetLoader = remember(config.appId, config.zipUrl) {
        if (config.zipUrl.isNullOrBlank()) null
        else androidx.webkit.WebViewAssetLoader.Builder()
            .addPathHandler("/") { path ->
                val file = java.io.File(bundleDir, path)
                if (file.exists() && file.isFile) {
                    val mime = guessBundleMimeType(path)
                    android.webkit.WebResourceResponse(mime, null, file.inputStream())
                } else null
            }
            .build()
    }

    val webViewClient = remember(config, startupScript, assetLoader) {
        MiniAppWebViewClient(
            config = config,
            startupScript = startupScript,
            assetLoader = assetLoader,
            onPageStarted = { },
            onPageFinished = { pageFinishedAction[0]?.invoke() },
        )
    }

    // ── ZIP download ──────────────────────────────────────────────────────────
    LaunchedEffect(config.appId, config.zipUrl) {
        Timber.d("MiniApp: LaunchedEffect appId=${config.appId} zipUrl=${config.zipUrl} url=${config.url}")
        val zipUrl = config.zipUrl?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect

        val bundleManager = MiniAppBundleManager(context, okHttpClient)
        bundleManager.prepareBundle(
            appId = config.appId,
            zipUrl = zipUrl,
            serverVersion = config.bundleVersion ?: "",
            onProgress = { progress ->
                val pct = (progress * 100).toInt()
                Timber.d("MiniApp: downloading appId=${config.appId} $pct%%")
                bundleState = BundleState.Downloading(progress)
            },
        ).onSuccess { indexFile ->
            val loadUrl = if (assetLoader != null) {
                val relativePath = runCatching { indexFile.relativeTo(bundleDir).path }.getOrElse { "index.html" }
                "https://appassets.androidplatform.net/$relativePath"
            } else {
                indexFile.toUri().toString()
            }
            Timber.d("MiniApp: download done, loading WebView appId=${config.appId} url=$loadUrl")
            // Stay on overlay (WebLoading) until onPageFinished fires.
            pageFinishedAction[0] = {
                Timber.d("MiniApp: page loaded appId=${config.appId}")
                bundleState = BundleState.Ready(loadUrl)
                pageFinishedAction[0] = null
            }
            bundleState = BundleState.WebLoading(loadUrl)
            webViewHolder[0]?.loadUrl(loadUrl)
        }.onFailure { error ->
            Timber.e(error, "MiniApp: bundle preparation failed")
            bundleState = BundleState.Error(error.message ?: "Failed to load bundle")
        }
    }

    // ── WebView cleanup ───────────────────────────────────────────────────────
    DisposableEffect(config.appId) {
        onDispose {
            Timber.d("MiniApp: disposing WebView for appId=${config.appId}")
            pageFinishedAction[0] = null
            bridgeHolder[0]?.release()
            bridgeHolder[0] = null
            webViewHolder[0]?.let { wv ->
                wv.stopLoading()
                wv.destroy()
            }
            webViewHolder[0] = null
        }
    }

    // ── Layout ────────────────────────────────────────────────────────────────
    // Black background fills any empty space below the game HTML content so the
    // viewport remainder doesn't flash white on devices where the game page
    // doesn't stretch to 100vh.
    Box(modifier = modifier.background(androidx.compose.ui.graphics.Color.Black)) {

        // WebView — always present in the composition so it is created early.
        // In bundle mode it stays invisible under the overlay until Ready.
        MiniAppWebViewContainer(
            modifier = Modifier.fillMaxSize(),
            config = config,
            webViewClient = webViewClient,
            hostBridge = hostBridge,
            okHttpClient = okHttpClient,
            scope = scope,
            // In bundle mode don't load a URL in the factory — LaunchedEffect does it later.
            initialUrl = if (config.zipUrl.isNullOrBlank()) config.url else null,
            onWebViewCreated = { wv, bridge ->
                webViewHolder[0] = wv
                bridgeHolder[0] = bridge
            },
        )

        // Loading / error overlay — drawn on top of the WebView.
        when (val state = bundleState) {
            is BundleState.Downloading -> MiniAppLoadingOverlay(
                state = MiniAppLoadingState.Loading(progress = state.progress),
                onClose = onClose,
            )
            is BundleState.WebLoading -> MiniAppLoadingOverlay(
                state = MiniAppLoadingState.Loading(progress = null),
                onClose = onClose,
            )
            is BundleState.Error -> MiniAppLoadingOverlay(
                state = MiniAppLoadingState.Error(
                    message = state.message,
                    onRetry = {
                        bundleState = BundleState.Downloading(0f)
                    },
                ),
                onClose = onClose,
            )
            is BundleState.Ready -> { /* WebView visible — no overlay */ }
        }
    }
}

// ── WebView container ─────────────────────────────────────────────────────────

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MiniAppWebViewContainer(
    modifier: Modifier,
    config: MiniAppConfig,
    webViewClient: MiniAppWebViewClient,
    hostBridge: MiniAppHostBridge?,
    okHttpClient: OkHttpClient,
    scope: CoroutineScope,
    initialUrl: String?,
    onWebViewCreated: (WebView, MiniAppJsBridge) -> Unit,
) {
    androidx.compose.ui.viewinterop.AndroidView(
        modifier = modifier,
        factory = { context ->
            val (wv, bridge) = createWebView(
                context = context,
                config = config,
                client = webViewClient,
                hostBridge = hostBridge,
                okHttpClient = okHttpClient,
                scope = scope,
            )
            onWebViewCreated(wv, bridge)
            if (!initialUrl.isNullOrBlank()) {
                Timber.d("MiniApp: initialUrl $initialUrl")
                wv.loadUrl(initialUrl)
            }
            wv
        },
        update = { /* config is baked into client/bridge at factory time */ },
    )
}

// ── WebView factory ───────────────────────────────────────────────────────────

@SuppressLint("SetJavaScriptEnabled")
private fun createWebView(
    context: Context,
    config: MiniAppConfig,
    client: MiniAppWebViewClient,
    hostBridge: MiniAppHostBridge?,
    okHttpClient: OkHttpClient,
    scope: CoroutineScope,
): Pair<WebView, MiniAppJsBridge> {
    val storage = MiniAppStorage(context, config.appId)
    lateinit var webViewInstance: WebView

    val bridge = MiniAppJsBridge(
        context = context,
        config = config,
        storage = storage,
        hostBridge = hostBridge,
        okHttpClient = okHttpClient,
        webViewRef = { webViewInstance },
        scope = scope,
    )

    webViewInstance = WebView(context).apply {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            @Suppress("DEPRECATION")
            databaseEnabled = true
            allowFileAccess = true
            setSupportZoom(false)
            cacheMode = WebSettings.LOAD_NO_CACHE
            mediaPlaybackRequiresUserGesture = false

            // Allow file:// pages to make cross-origin requests (local app bundles).
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = true
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = true
        }

        // Register bridge as `window.webkit`
        addJavascriptInterface(bridge, "webkit")
        WebView.setWebContentsDebuggingEnabled(true)
        webViewClient = client
        webChromeClient = MiniAppChromeClient(bridge)

        // Black background: prevents the white flash in the empty area below the
        // game HTML content on screens where the page doesn't fill 100% of the
        // viewport height (e.g. the game's error / loading screen).
        setBackgroundColor(android.graphics.Color.BLACK)
    }

    return Pair(webViewInstance, bridge)
}

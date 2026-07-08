/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.miniapp.impl

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.webkit.JavascriptInterface
import android.webkit.WebView
import io.element.android.libraries.miniapp.api.MiniAppConfig
import io.element.android.libraries.miniapp.api.MiniAppHostBridge
import io.element.android.libraries.miniapp.api.toMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.json.JSONObject
import timber.log.Timber
import java.io.File

/**
 * JS ↔ native bridge registered as `window.webkit` via
 * `WebView.addJavascriptInterface(bridge, "webkit")`.
 *
 * **Sync methods** — called from the JS thread; return a value immediately.
 * **Async methods** — called from the JS thread; return `void` and deliver the
 *   result via `window.__webkitNotification(json)` on the main thread.
 *
 * Covers the full bridge surface of iOS `WebViewController`:
 * - All `WebRespType.Base` async handlers (WebView+JavaScript.swift).
 * - All `WebPromptType` sync handlers.
 *
 * @param context       Application or activity context.
 * @param config        Mini-app configuration (appId, token, user, options, appBundleData).
 * @param storage       Per-app SharedPreferences wrapper.
 * @param hostBridge    Host callbacks (getAccessToken, sendMessage, …).
 * @param okHttpClient  OkHttp instance for the `request` bridge method and sound preloads.
 * @param webViewRef    Weak reference supplier that returns the live WebView.
 *                      Called on the JS thread — must be thread-safe.
 * @param scope         Coroutine scope for async bridge work.
 */
@Suppress("TooManyFunctions")
internal class MiniAppJsBridge(
    private val context: Context,
    private val config: MiniAppConfig,
    private val storage: MiniAppStorage,
    private val hostBridge: MiniAppHostBridge?,
    private val okHttpClient: OkHttpClient,
    private val webViewRef: () -> WebView?,
    private val scope: CoroutineScope,
) {
    private val soundManager = MiniAppSoundManager(context, okHttpClient)
    private val sqliteHelper = MiniAppSqlite(context)

    // ── Sync bridge methods ───────────────────────────────────────────────────

    /**
     * Return static app metadata.
     * JS: `window.webkit.appInfo(params)`
     */
    @JavascriptInterface
    fun appInfo(params: String): String {
        val bundleDir = java.io.File(context.filesDir, "miniapp/app_${config.appId}")
        val indexFile = java.io.File(bundleDir, "index.html")
        val localExist = indexFile.exists()

        val obj = JSONObject()
        obj.put("app_id", config.appId)
        // Mirror iOS: include url (local file:// or remote), path, local_exist
        obj.put("url", if (localExist) indexFile.toURI().toString() else config.url)
        obj.put("path", bundleDir.absolutePath)
        obj.put("local_exist", localExist)
        // Always use the live token so JS gets the current Matrix session token,
        // not the snapshot baked into config at bridge-creation time. The bridge
        // may outlive a token refresh if the composable re-renders with a new config
        // while the AndroidView factory (and thus the bridge) is not re-created.
        val liveToken = runCatching {
            kotlinx.coroutines.runBlocking { hostBridge?.getAccessToken() }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: config.token?.accessToken
        liveToken?.let { at ->
            obj.put("token", JSONObject().apply {
                put("accessToken", at)
                config.token?.refreshToken?.let { put("refreshToken", it) }
                config.token?.platform?.let { put("platform", it) }
            })
        }
        config.user?.let { user ->
            obj.put("user", user.toMap().toJsonObject())
        }
        obj.put("options", liveOptions().toJsonObject())
        config.homeserver?.let { obj.put("homeserver", it) }
        return obj.toString()
    }

    /**
     * Returns a live view of [config.options] with doc_id re-read from SharedPreferences.
     * config.options is immutable (built at bridge-creation time), so if createSuccess() saves
     * a doc_id after the initial load the next appInfo() call (e.g. after WebView reload)
     * would miss it. Re-reading SP here mirrors the live-token pattern above.
     */
    private fun liveOptions(): Map<String, Any> {
        if (config.options.containsKey("doc_id")) return config.options
        val streamId = config.options["stream_id"]?.toString()?.takeIf { it.isNotBlank() }
            ?: run { Timber.d("MiniApp: liveOptions — no stream_id in options, skipping SP lookup"); return config.options }
        val spKey = "${config.appId}_$streamId"
        val docId = context
            .getSharedPreferences("miniapp_doc_ids", Context.MODE_PRIVATE)
            .getString(spKey, null)
            ?.takeIf { it.isNotBlank() }
            ?: run { Timber.d("MiniApp: liveOptions — SP key=%s not found", spKey); return config.options }
        Timber.d("MiniApp: liveOptions injecting docId appId=%d streamId=%s", config.appId, streamId)
        return config.options + ("doc_id" to docId)
    }

    /**
     * SharedPreferences read.
     * JS: `window.webkit.storageGet(JSON.stringify({ key }))`
     */
    @JavascriptInterface
    fun storageGet(params: String): String = storage.get(params)

    /**
     * SharedPreferences write.
     * JS: `window.webkit.storageSet(JSON.stringify({ key, value }))`
     */
    @JavascriptInterface
    fun storageSet(params: String) = storage.set(params)

    /**
     * SharedPreferences delete.
     * JS: `window.webkit.storageRemove(JSON.stringify({ key }))`
     */
    @JavascriptInterface
    fun storageRemove(params: String) = storage.remove(params)

    /**
     * Status-bar colour query / set — no-op on Android.
     * JS: `window.webkit.statusBar(styleString)`
     */
    @JavascriptInterface
    fun statusBar(params: String): String {
        Timber.d("MiniApp: statusBar(%s) — no-op on Android", params)
        return ""
    }

    /**
     * Return display safe-area insets (dp).
     * JS: `window.webkit.safeArea(params)`
     */
    @JavascriptInterface
    fun safeArea(params: String): String {
        val density = context.resources.displayMetrics.density
        val statusBarPx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager
            wm?.currentWindowMetrics?.windowInsets
                ?.getInsets(android.view.WindowInsets.Type.statusBars())?.top ?: 0
        } else {
            @Suppress("DEPRECATION")
            val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
            if (id > 0) context.resources.getDimensionPixelSize(id) else 0
        }
        val dm = context.resources.displayMetrics
        return JSONObject().apply {
            put("top", (statusBarPx / density).toInt())
            put("bottom", 0)
            put("left", 0)
            put("right", 0)
            put("height", (dm.heightPixels / density).toInt())
            put("width", (dm.widthPixels / density).toInt())
        }.toString()
    }

    /**
     * Called by the mini-app once its JS bundle has finished executing.
     * JS: `window.webkit.loaded(params)`
     */
    @JavascriptInterface
    fun loaded(params: String) {
        Timber.d("MiniApp: loaded")
    }

    /** Signal successful file creation. Persists [doc_id] keyed by stream_id for future sessions. */
    @JavascriptInterface
    fun createSuccess(params: String) {
        Timber.d("MiniApp: createSuccess params=%s options=%s", params, config.options)
        val streamId = config.options["stream_id"]?.toString()?.takeIf { it.isNotBlank() }
            ?: run { Timber.w("MiniApp: createSuccess aborted — stream_id missing from options"); return }
        val docId = runCatching {
            val json = JSONObject(params)
            // Mini-apps may send "doc_id" or "id"; accept either.
            json.optString("doc_id").takeIf { it.isNotBlank() }
                ?: json.optString("id").takeIf { it.isNotBlank() }
        }.getOrNull()
            ?: run { Timber.w("MiniApp: createSuccess aborted — doc_id/id missing/blank in params=%s", params); return }
        context.getSharedPreferences("miniapp_doc_ids", Context.MODE_PRIVATE)
            .edit()
            .putString("${config.appId}_$streamId", docId)
            .apply()
        Timber.d("MiniApp: docId saved appId=%d streamId=%s", config.appId, streamId)
        val escaped = docId.replace("\\", "\\\\").replace("\"", "\\\"")
        // Patch the live page so the current JS session can read doc_id immediately:
        // - window.___options.doc_id: startup-script global read directly by some mini-apps
        // - window._co._app.options.doc_id: games-SDK co singleton (window._co is the global ref)
        // - window.___co_reset: signals the webapp co.ts singleton to invalidate its _app cache
        //   so the next co.app access re-fetches via liveOptions() and picks up the new doc_id
        val js = """(function(){""" +
            """try{if(window.___options)window.___options.doc_id="$escaped";}catch(e){}""" +
            """try{var c=window._co;if(c&&c._app&&c._app.options)c._app.options.doc_id="$escaped";}catch(e){}""" +
            """try{window.___co_reset=true;}catch(e){}""" +
            """})()"""
        webViewRef()?.post { webViewRef()?.evaluateJavascript(js, null) }
    }

    /** Signal file-creation progress. */
    @JavascriptInterface
    fun createProgress(params: String) {
        Timber.d("MiniApp: createProgress %s", params)
    }

    /** Signal failed file creation. */
    @JavascriptInterface
    fun createError(params: String) {
        Timber.d("MiniApp: createError %s", params)
    }

    /**
     * Get the local cached version for the current app bundle.
     * JS: `window.webkit.version(params)`
     * Returns: `{"version":"<ver>"}` or `{"version":""}`.
     */
    @JavascriptInterface
    fun version(params: String): String {
        val prefs = context.getSharedPreferences("miniapp_meta_${config.appId}", Context.MODE_PRIVATE)
        val ver = prefs.getString("version", "") ?: ""
        return JSONObject().apply { put("version", ver) }.toString()
    }

    /**
     * Get the local cached version for any appId (iOS `localVersion` handler).
     * JS: `window.webkit.localVersion(JSON.stringify({ appId }))`
     */
    @JavascriptInterface
    fun localVersion(params: String): String {
        val appId = runCatching { JSONObject(params).optLong("appId", config.appId) }
            .getOrDefault(config.appId)
        val prefs = context.getSharedPreferences("miniapp_meta_$appId", Context.MODE_PRIVATE)
        val ver = prefs.getString("version", "") ?: ""
        return JSONObject().apply { put("version", ver) }.toString()
    }

    /**
     * Check whether the current app's local bundle exists on disk.
     * JS: `window.webkit.checkAppExist(params)`
     * Returns: `{"exist":true}` or `{"exist":false}`.
     */
    @JavascriptInterface
    fun checkAppExist(params: String): String {
        val bundleDir = File(context.filesDir, "miniapp/app_${config.appId}")
        val exists = File(bundleDir, "index.html").exists()
        return JSONObject().apply { put("exist", exists) }.toString()
    }

    /**
     * Adjust WebView scroll behaviour when the soft keyboard is shown.
     * No-op on Android — the system handles keyboard-inset layout automatically.
     */
    @JavascriptInterface
    fun keyboardFollow(params: String): String = ""

    /**
     * Configure custom toolbar items above the soft keyboard.
     * No-op on Android.
     */
    @JavascriptInterface
    fun keyboardToolbars(params: String): String = ""

    // ── Sound sync bridge methods ─────────────────────────────────────────────

    /**
     * Start audio playback.
     * JS: `window.webkit.soundPlay(JSON.stringify({ soundId, loop }))`
     */
    @JavascriptInterface
    fun soundPlay(params: String): String {
        val obj = runCatching { JSONObject(params) }.getOrDefault(JSONObject())
        val soundId = obj.optString("soundId", "default")
        val loop = obj.optBoolean("loop", false)
        soundManager.play(soundId, loop)
        return ""
    }

    /**
     * Stop audio playback and reset to the beginning.
     * JS: `window.webkit.soundStop(JSON.stringify({ soundId }))`
     */
    @JavascriptInterface
    fun soundStop(params: String): String {
        val soundId = runCatching { JSONObject(params).optString("soundId", "default") }
            .getOrDefault("default")
        soundManager.stop(soundId)
        return ""
    }

    /**
     * Pause audio playback.
     * JS: `window.webkit.soundPause(JSON.stringify({ soundId }))`
     */
    @JavascriptInterface
    fun soundPause(params: String): String {
        val soundId = runCatching { JSONObject(params).optString("soundId", "default") }
            .getOrDefault("default")
        soundManager.pause(soundId)
        return ""
    }

    /**
     * Release all audio resources.
     * JS: `window.webkit.soundClear(params)`
     */
    @JavascriptInterface
    fun soundClear(params: String): String {
        soundManager.clear()
        return ""
    }

    /**
     * Set the volume for a sound.
     * JS: `window.webkit.soundVolume(JSON.stringify({ soundId, volume }))`
     * [volume] is a float in `[0, 1]`.
     */
    @JavascriptInterface
    fun soundVolume(params: String): String {
        val obj = runCatching { JSONObject(params) }.getOrDefault(JSONObject())
        val soundId = obj.optString("soundId", "default")
        val volume = obj.optDouble("volume", 1.0).toFloat()
        soundManager.setVolume(soundId, volume)
        return ""
    }

    // ── Async bridge methods ──────────────────────────────────────────────────

    /**
     * HTTP proxy: forward a network request from the mini-app.
     * JS: `window.webkit.request(JSON.stringify({ handle, data: { url, method, headers, data } }))`
     */
    @JavascriptInterface
    fun request(params: String) {
        val from = FromJsData(params)
        Timber.d("MiniApp: request handleId=%s url=%s", from.handleId, from.data.optString("url"))
        val webView = webViewRef() ?: run {
            Timber.w("MiniApp: request called but webView is null, dropping handleId=%s", from.handleId)
            return
        }
        scope.launch(Dispatchers.IO) {
            MiniAppRequest.execute(from, okHttpClient, webView)
        }
    }

    /**
     * Trigger a bundle update check / re-download.
     * iOS `update` handler — on Android this is a no-op that immediately returns success
     * (the actual update logic runs at open time via [MiniAppBundleManager]).
     */
    @JavascriptInterface
    fun update(params: String) {
        val from = FromJsData(params)
        val resp = ToJsData(handle = from.handleId)
        resp.data = mapOf("success" to true)
        notify(resp)
    }

    /**
     * Open another mini-app by appId (iOS `open` handler).
     * JS: `window.webkit.open(JSON.stringify({ handle, data: { appId, ... } }))`
     */
    @JavascriptInterface
    fun open(params: String) {
        val from = FromJsData(params)
        val appId = from.data.optInt("appId", 0)
        if (appId > 0) {
            hostBridge?.openMiniApp(appId, from.data.toMap())
        }
        val resp = ToJsData(handle = from.handleId)
        resp.data = mapOf("success" to true)
        notify(resp)
    }

    /**
     * Download an app bundle by appId (iOS `downloadApp` handler).
     * On Android the bundle is managed by [MiniAppBundleManager]; this method
     * returns success immediately without triggering a new download.
     */
    @JavascriptInterface
    fun downloadApp(params: String) {
        val from = FromJsData(params)
        Timber.d("MiniApp: downloadApp — delegating to MiniAppBundleManager at open time")
        val resp = ToJsData(handle = from.handleId)
        resp.data = mapOf("success" to true)
        notify(resp)
    }

    /**
     * Close the mini-app (iOS `back` handler → `delegate.closeApp()`).
     * JS: `window.webkit.back(params)`
     */
    @JavascriptInterface
    fun back(params: String) {
        hostBridge?.closeApp()
    }

    /**
     * Open an external URL inside a new mini-app WebView.
     * JS: `window.webkit.openWeb(JSON.stringify({ handle, data: { url, ... } }))`
     */
    @JavascriptInterface
    fun openWeb(params: String) {
        val from = FromJsData(params)
        val url = from.data.optString("url")
        if (url.isBlank()) return
        hostBridge?.openWeb(url, from.data.toMap())
    }

    /**
     * Check whether a URL scheme can be handled by the system.
     * iOS: `check.app` handler → `UIApplication.canOpenURL`.
     * JS: `window.webkit.checkApp(JSON.stringify({ handle, data: { url } }))`
     */
    @JavascriptInterface
    fun checkApp(params: String) {
        val from = FromJsData(params)
        val url = from.data.optString("url")
        val canOpen = if (url.isNotBlank()) {
            runCatching {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                @Suppress("DEPRECATION")
                context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null
            }.getOrDefault(false)
        } else {
            false
        }
        val resp = ToJsData(handle = from.handleId)
        resp.data = mapOf("exist" to canOpen)
        notify(resp)
    }

    /**
     * Open an external app or URL (iOS `open.app` handler).
     * JS: `window.webkit.openApp(JSON.stringify({ handle, data: { url } }))`
     */
    @JavascriptInterface
    fun openApp(params: String) {
        val from = FromJsData(params)
        val url = from.data.optString("url")
        if (url.isNotBlank()) {
            hostBridge?.openApp(url)
        }
        val resp = ToJsData(handle = from.handleId)
        resp.data = mapOf("success" to true)
        notify(resp)
    }

    /**
     * Preload an audio file for later playback (iOS `sound.preload` handler).
     * JS: `window.webkit.soundPreload(JSON.stringify({ handle, data: { soundId, url } }))`
     */
    @JavascriptInterface
    fun soundPreload(params: String) {
        val from = FromJsData(params)
        val soundId = from.data.optString("soundId", "default")
        val url = from.data.optString("url")
        scope.launch {
            runCatching { soundManager.preload(soundId, url) }
                .onFailure { Timber.e(it, "MiniApp: soundPreload failed") }
            val resp = ToJsData(handle = from.handleId)
            resp.data = mapOf("success" to true, "soundId" to soundId)
            notify(resp)
        }
    }

    /**
     * Open the device camera (stub — requires Activity result API).
     * Returns a `403` error so the JS bundle can fall back gracefully.
     */
    @JavascriptInterface
    fun openCamera(params: String) {
        val from = FromJsData(params)
        Timber.d("MiniApp: openCamera — not yet supported on Android")
        val resp = ToJsData(handle = from.handleId)
        resp.code = 403
        resp.msg = "Camera not supported"
        notify(resp)
    }

    /**
     * Open the photo picker (stub — requires Activity result API).
     * Returns a `403` error so the JS bundle can fall back gracefully.
     */
    @JavascriptInterface
    fun openPhotos(params: String) {
        val from = FromJsData(params)
        Timber.d("MiniApp: openPhotos — not yet supported on Android")
        val resp = ToJsData(handle = from.handleId)
        resp.code = 403
        resp.msg = "Photos not supported"
        notify(resp)
    }

    /**
     * Check whether an app update is available on the store.
     * No-op stub — returns "no update" so JS proceeds without blocking.
     */
    @JavascriptInterface
    fun checkAppStore(params: String) {
        val from = FromJsData(params)
        val resp = ToJsData(handle = from.handleId)
        resp.data = mapOf("hasNewVersion" to false, "storeVersion" to "")
        notify(resp)
    }

    /**
     * SQLite3 database operations (iOS `sqlite3` handler → `WebView+SQLite.swift`).
     * JS: `window.webkit.sqlite3(JSON.stringify({ handle, data: { operation, dbName, ... } }))`
     *
     * Supported operations: `execute`, `query`, `insert`, `update`, `delete`,
     * `saveAppModel`, `getAppModel`, `deleteAppModel`.
     */
    @JavascriptInterface
    fun sqlite3(params: String) {
        val from = FromJsData(params)
        scope.launch(Dispatchers.IO) {
            val result = sqliteHelper.dispatch(from.data)
            val resp = ToJsData(handle = from.handleId)
            result.onSuccess { resp.data = it }
                .onFailure {
                    resp.code = 500
                    resp.msg = it.message ?: "SQLite error"
                    resp.data = mapOf("success" to false)
                }
            notify(resp)
        }
    }

    /**
     * Fetch app bundle metadata (iOS `app.data` handler → `requestAppModelFromServer`).
     * Returns [MiniAppConfig.appBundleData] if available (pre-fetched by [MiniAppNode]),
     * otherwise falls back to [MiniAppHostBridge.fetchAppData].
     * JS: `window.webkit.appData(JSON.stringify({ handle, data: { appId? } }))`
     */
    @JavascriptInterface
    fun appData(params: String) {
        val from = FromJsData(params)
        scope.launch {
            val requestedAppId = from.data.optInt("appId", config.appId.toInt())
            val data: Map<String, Any>? = when {
                requestedAppId.toLong() == config.appId && config.appBundleData != null ->
                    config.appBundleData
                else ->
                    hostBridge?.fetchAppData(requestedAppId)
                        ?: config.appBundleData
            }
            val resp = ToJsData(handle = from.handleId)
            if (data != null) {
                resp.data = data
            } else {
                resp.code = 404
                resp.msg = "App data not available"
            }
            notify(resp)
        }
    }

    /**
     * Save a base64-encoded file to local storage (iOS `file.save` handler).
     *
     * JS payload:
     * ```json
     * { "handle": "...", "data": { "file": "<base64>", "filename": "foo.png", "to_target": "photo|video|document|" } }
     * ```
     *
     * - `to_target == "photo"`  → saved to [MediaStore.Images] (Android 10+) or
     *                             `<External Pictures>/MiniApp/`.
     * - `to_target == "video"`  → saved to [MediaStore.Video].
     * - otherwise               → saved to `<filesDir>/miniapp/app_<appId>/documents/`.
     */
    @JavascriptInterface
    fun saveFile(params: String) {
        val from = FromJsData(params)
        scope.launch(Dispatchers.IO) {
            val resp = ToJsData(handle = from.handleId)
            runCatching {
                val base64Str = from.data.optString("file")
                if (base64Str.isBlank()) error("Missing file data")
                val filename = from.data.optString("filename", "file").ifBlank { "file" }
                val target = from.data.optString("to_target", "")
                val bytes = android.util.Base64.decode(base64Str, android.util.Base64.DEFAULT)
                val savedPath = saveBytes(bytes, filename, target)
                resp.data = mapOf("success" to true, "path" to savedPath)
            }.onFailure {
                Timber.e(it, "MiniApp: saveFile failed")
                resp.code = 500
                resp.msg = it.message ?: "File save failed"
            }
            notify(resp)
        }
    }

    /**
     * Read a local file and return its content as a base64 string (iOS `file.get` handler).
     *
     * JS payload:
     * ```json
     * { "handle": "...", "data": { "url": "file:///..." } }
     * ```
     */
    @JavascriptInterface
    fun getFile(params: String) {
        val from = FromJsData(params)
        scope.launch(Dispatchers.IO) {
            val resp = ToJsData(handle = from.handleId)
            runCatching {
                val rawUrl = from.data.optString("url").ifBlank { error("Missing url") }
                val path = when {
                    rawUrl.startsWith("file://") -> rawUrl.removePrefix("file://")
                    rawUrl.startsWith("/") -> rawUrl
                    else -> File(context.filesDir, "miniapp/app_${config.appId}/$rawUrl").absolutePath
                }
                val file = File(path)
                if (!file.exists()) error("File not found: $path")
                val base64 = android.util.Base64.encodeToString(file.readBytes(), android.util.Base64.NO_WRAP)
                resp.data = mapOf("success" to true, "file" to base64)
            }.onFailure {
                Timber.e(it, "MiniApp: getFile failed")
                resp.code = 500
                resp.msg = it.message ?: "File read failed"
            }
            notify(resp)
        }
    }

    /**
     * Get the current Matrix access token.
     * JS: `window.webkit.getToken(JSON.stringify({ handle, data: {} }))`
     */
    @JavascriptInterface
    fun getToken(params: String) {
        val from = FromJsData(params)
        scope.launch {
            val token = hostBridge?.getAccessToken() ?: ""
            val resp = ToJsData(handle = from.handleId)
            resp.data = token
            notify(resp)
        }
    }

    /**
     * Get a single room member by userId.
     * JS: `window.webkit.getMember(JSON.stringify({ handle, data: { userId } }))`
     */
    @JavascriptInterface
    fun getMember(params: String) {
        val from = FromJsData(params)
        val userId = from.data.optString("userId")
        val user = hostBridge?.getMember(userId)
        val resp = ToJsData(handle = from.handleId)
        resp.data = user?.toMap() ?: ""
        notify(resp)
    }

    /**
     * Get all room members.
     * JS: `window.webkit.getMembers(JSON.stringify({ handle, data: {} }))`
     */
    @JavascriptInterface
    fun getMembers(params: String) {
        val from = FromJsData(params)
        scope.launch {
            val users = hostBridge?.getMembers()
            val resp = ToJsData(handle = from.handleId)
            resp.data = users?.map { it.toMap() } ?: ""
            notify(resp)
        }
    }

    /**
     * Forward a Matrix message from the mini-app to the room.
     * JS: `window.webkit.sendMessage(JSON.stringify({ handle, data: { ... } }))`
     */
    @JavascriptInterface
    fun sendMessage(params: String) {
        val from = FromJsData(params)
        scope.launch {
            hostBridge?.sendMessage(from.data.toMap())
            val resp = ToJsData(handle = from.handleId)
            notify(resp)
        }
    }

    /**
     * Get game-session metadata.
     * JS: `window.webkit.getGameInfo(JSON.stringify({ handle, data: {} }))`
     */
    @JavascriptInterface
    fun getGameInfo(params: String) {
        val from = FromJsData(params)
        val info = hostBridge?.getGameInfo() ?: emptyMap()
        val resp = ToJsData(handle = from.handleId)
        resp.data = info
        notify(resp)
    }

    // ── dot-to-camelCase aliases ──────────────────────────────────────────────
    // JS converts "game.info.get" → "gameInfoGet" before calling window.webkit[name].
    // These aliases forward to the canonical method so both naming styles work.

    @JavascriptInterface fun gameInfoGet(params: String) = getGameInfo(params)
    @JavascriptInterface fun tokenGet(params: String) = getToken(params)
    @JavascriptInterface fun memberGet(params: String) = getMember(params)
    @JavascriptInterface fun membersGet(params: String) = getMembers(params)
    @JavascriptInterface fun messageSend(params: String) = sendMessage(params)
    @JavascriptInterface fun fileSave(params: String) = saveFile(params)
    @JavascriptInterface fun fileGet(params: String) = getFile(params)
    @JavascriptInterface fun checkStore(params: String) = checkAppStore(params)

    // ── Sync-prompt dispatcher ────────────────────────────────────────────────

    /**
     * Dispatch an iOS-style `window.prompt(JSON.stringify({name, args}))` sync call.
     * Called by [MiniAppChromeClient.onJsPrompt] so game bundles that were built
     * for iOS (using `WKUIDelegate` sync bridge) work on Android without changes.
     *
     * Returns the response string to confirm via [JsPromptResult.confirm],
     * or null if [name] is not a known sync bridge method (the caller should
     * return `false` from `onJsPrompt` and let the system handle it).
     */
    fun handleSyncPrompt(name: String, argsJson: String): String? = when (name) {
        "storage.get", "storageGet" -> storageGet(argsJson)
        "storage.set", "storageSet" -> { storageSet(argsJson); "" }
        "storage.remove", "storageRemove" -> { storageRemove(argsJson); "" }
        "appInfo" -> appInfo(argsJson)
        "safeArea" -> safeArea(argsJson)
        "version" -> version(argsJson)
        "checkAppExist" -> checkAppExist(argsJson)
        "localVersion" -> localVersion(argsJson)
        "sound.play", "soundPlay" -> soundPlay(argsJson)
        "sound.stop", "soundStop" -> soundStop(argsJson)
        "sound.pause", "soundPause" -> soundPause(argsJson)
        "sound.clear", "soundClear" -> soundClear(argsJson)
        "sound.volume", "soundVolume" -> soundVolume(argsJson)
        "keyboard.follow", "keyboardFollow" -> keyboardFollow(argsJson)
        "keyboard.toolbars", "keyboardToolbars" -> keyboardToolbars(argsJson)
        "loaded" -> { loaded(argsJson); "" }
        "statusBar" -> statusBar(argsJson)
        "create.success", "createSuccess" -> { createSuccess(argsJson); "" }
        "create.progress", "createProgress" -> { createProgress(argsJson); "" }
        "create.error", "createError" -> { createError(argsJson); "" }
        else -> null
    }

    // ── File save helper ──────────────────────────────────────────────────────

    private fun saveBytes(bytes: ByteArray, filename: String, target: String): String {
        return when (target.lowercase()) {
            "photo" -> saveToMediaStore(bytes, filename, isVideo = false)
            "video" -> saveToMediaStore(bytes, filename, isVideo = true)
            else -> saveToDocuments(bytes, filename)
        }
    }

    private fun saveToDocuments(bytes: ByteArray, filename: String): String {
        val dir = File(context.filesDir, "miniapp/app_${config.appId}/documents")
        dir.mkdirs()
        val file = File(dir, filename.replace("[^a-zA-Z0-9._-]".toRegex(), "_"))
        file.writeBytes(bytes)
        return file.absolutePath
    }

    private fun saveToMediaStore(bytes: ByteArray, filename: String, isVideo: Boolean): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val collection = if (isVideo) {
                android.provider.MediaStore.Video.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                android.provider.MediaStore.Images.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                    if (isVideo) android.os.Environment.DIRECTORY_MOVIES
                    else android.os.Environment.DIRECTORY_PICTURES)
                put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, values)
                ?: error("MediaStore insert returned null")
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
            values.clear()
            values.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri.toString()
        } else {
            // Fallback: save to documents for API < 29
            saveToDocuments(bytes, filename)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Release audio resources when the WebView session ends. */
    fun release() {
        soundManager.release()
    }

    private fun notify(resp: ToJsData) {
        val js = "window.__webkitNotification(${resp.toJsonString()})"
        webViewRef()?.post {
            webViewRef()?.evaluateJavascript(js, null)
        }
    }
}

// Extension: convert JSONObject to Map<String, Any>
private fun JSONObject.toMap(): Map<String, Any> = buildMap {
    keys().forEach { key -> opt(key)?.let { put(key, it) } }
}

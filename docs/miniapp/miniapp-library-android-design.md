# MiniApp Library — Android Design

**Status**: Implemented  
**Date**: 2026-06-12  
**Scope**: `libraries/miniapp/api` + `libraries/miniapp/impl`

---

## 1. Overview

This library provides a reusable MiniApp WebView component that is the Android equivalent of the iOS `EditorControllerWrapper` / `WebViewController` pair from `unseal-mini-app`. It wraps Android `WebView` in a Compose-friendly API, injects a JS bridge for host↔web communication, and wires Unseal auth (token, APP-U header) transparently.

**iOS reference → Android target**

| iOS | Android |
|---|---|
| `WebViewController` (`UIViewController`) | `MiniAppWebViewState` + `MiniAppWebView` (Composable + `AndroidView`) |
| `EditorControllerWrapper` (`UIViewControllerRepresentable`) | `MiniAppView` (public Composable entry point) |
| `WebViewControllerwDelegate` protocol | `MiniAppHostBridge` interface |
| `WKScriptMessageHandler` — async JS → native | `MiniAppJsBridge` (`@JavascriptInterface`, async methods) |
| `webView.runJavaScriptTextInputPanelWithPrompt` — sync JS → native | Same `@JavascriptInterface` (sync return value) |
| `webview.evaluateJavaScript("window.__webkitNotification(…)")` | `webView.evaluateJavascript("window.__webkitNotification(…)", null)` |
| `UserDefaults` per-app KV store | `SharedPreferences` per-app KV store |
| `WKURLSchemeHandler` for `pinduoduo://` | `shouldOverrideUrlLoading` in `WebViewClient` |
| GCDWebServer CORS proxy | `WebView` `setAllowUniversalAccessFromFileURLs(true)` + OkHttp for `request()` |

---

## 2. Module structure

```
libraries/miniapp/
  api/
    build.gradle.kts
    src/main/kotlin/…/miniapp/api/
      MiniAppConfig.kt          # input config data class
      MiniAppHostBridge.kt      # interface the host (room screen) must implement
      MiniAppUser.kt            # member data class (≈ PUser on iOS)
      MiniAppResult.kt          # result / callback sealed class
  impl/
    build.gradle.kts
    src/main/kotlin/…/miniapp/impl/
      MiniAppView.kt            # public Composable entry point
      MiniAppWebViewState.kt    # rememberMiniAppWebViewState()
      MiniAppJsBridge.kt        # @JavascriptInterface registered as "webkit"
      MiniAppWebViewClient.kt   # WebViewClient (URL override, page load events)
      MiniAppJsModel.kt         # ToJsData / FromJsData Kotlin equivalents
      MiniAppStorage.kt         # per-app SharedPreferences helper
      MiniAppRequest.kt         # OkHttp-based `request` handler (background thread)
      internal/
        JsCallback.kt           # helper: build & dispatch __webkitNotification
        PlatformScript.kt       # injects window.___platform = 'android'
```

---

## 3. Public API (`api` module)

### `MiniAppConfig`

```kotlin
/**
 * All inputs needed to open a MiniApp WebView.
 *
 * Equivalent to the parameters of iOS EditorControllerWrapper.init()
 * and WebViewController.init(appId:…) / init(url:…).
 */
@Immutable
data class MiniAppConfig(
    /** Numeric app ID from the server (maps to iOS AppID enum values). */
    val appId: Long,
    /** If provided, load this remote URL instead of the locally cached bundle. */
    val remoteUrl: String? = null,
    /**
     * Arbitrary key/value options injected into the web app at startup
     * via window.__webkitOptions.  Maps to iOS init _options parameter.
     * Example: mapOf("doc_id" to docId, "create_type" to "new")
     */
    val options: Map<String, Any> = emptyMap(),
    /** Unseal auth token — injected as window.__token and via APP-U header. */
    val token: String? = null,
    /** Current user info injected into the JS context. */
    val user: MiniAppUser? = null,
)
```

### `MiniAppUser`

```kotlin
/** Mirrors iOS PUser.  Injected into the JS context as window.__user. */
@Immutable
data class MiniAppUser(
    val userId: String,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val powerLevel: Int? = null,
    val avatarBgColor: String? = null,
    val avatarTextColor: String? = null,
    val isAgent: Boolean? = null,
    val isSelf: Boolean? = null,
    val agentId: String? = null,
)
```

### `MiniAppHostBridge`

Equivalent to iOS `WebViewControllerwDelegate`.

```kotlin
/**
 * Callbacks the host screen must implement so the MiniApp can communicate
 * back to the Element X / Unseal layer.
 *
 * All methods are called on the main thread.
 */
interface MiniAppHostBridge {
    /** JS called message.send — post a Matrix event to the current room. */
    suspend fun sendMessage(data: Map<String, Any>)

    /** JS called token.get — return the current Unseal access token. */
    suspend fun getAccessToken(): String

    /** JS called members.get — return all visible room members. */
    suspend fun getMembers(): List<MiniAppUser>

    /** JS called member.get — return a single member by Matrix userId. */
    fun getMember(userId: String): MiniAppUser?

    /** JS called game.info.get — return current game metadata (gameId, roomId…). */
    fun getGameInfo(): Map<String, Any>

    /** JS called back / close — host should pop or dismiss the MiniApp screen. */
    fun closeApp()
}
```

### `MiniAppResult`

```kotlin
sealed interface MiniAppResult {
    data object Success : MiniAppResult
    data class Error(val message: String) : MiniAppResult
}
```

---

## 4. Composable entry point (`impl` module)

### `MiniAppView`

```kotlin
/**
 * Full-screen WebView that hosts a MiniApp.
 *
 * Usage (inside an Appyx Node or Composable screen):
 *
 *   MiniAppView(
 *       config = MiniAppConfig(appId = 42L, token = token, user = currentUser),
 *       bridge = myHostBridge,
 *       modifier = Modifier.fillMaxSize(),
 *   )
 */
@Composable
fun MiniAppView(
    config: MiniAppConfig,
    bridge: MiniAppHostBridge,
    modifier: Modifier = Modifier,
    onResult: (MiniAppResult) -> Unit = {},
)
```

Internally:

```kotlin
@Composable
fun MiniAppView(…) {
    val state = rememberMiniAppWebViewState(config, bridge)

    AndroidView(
        factory = { ctx ->
            WebView(ctx).also { wv ->
                state.attachTo(wv)           // configure settings, inject bridge, load URL
            }
        },
        update = { wv ->
            state.update(wv)                 // re-inject config if it changes
        },
        modifier = modifier,
    )

    // Back press: delegate to JS (same as iOS viewWillDisappear flow)
    BackHandler { state.onBackPressed() }
}
```

### `rememberMiniAppWebViewState`

```kotlin
@Composable
fun rememberMiniAppWebViewState(
    config: MiniAppConfig,
    bridge: MiniAppHostBridge,
): MiniAppWebViewState = remember(config.appId) {
    MiniAppWebViewState(config, bridge)
}
```

---

## 5. JS bridge method mapping

The bridge is registered as `webkit` to match iOS (`window.webkit.messageHandlers.*`).  
On iOS, async methods use `WKScriptMessageHandler`; sync methods use the `prompt` intercept. On Android, both are `@JavascriptInterface` — sync methods return a `String`, async fire `jsCallback()` on the main thread after background work.

### `MiniAppJsBridge` — method table

| iOS handler name | Android `@JavascriptInterface` method | Sync/Async | Description |
|---|---|---|---|
| `appInfo` (prompt) | `fun appInfo(params: String): String` | sync | Return `{app_id, name, brief, icon}` |
| `storage.get` (prompt) | `fun storageGet(params: String): String` | sync | Read SharedPreferences KV |
| `storage.set` (prompt) | `fun storageSet(params: String): String` | sync | Write SharedPreferences KV |
| `storage.remove` (prompt) | `fun storageRemove(params: String): String` | sync | Delete SharedPreferences KV |
| `statusBar` (prompt) | `fun statusBar(params: String): String` | sync | Get/set status bar style |
| `safeArea` (prompt) | `fun safeArea(params: String): String` | sync | Return window insets |
| `version` (prompt) | `fun version(params: String): String` | sync | Return app version |
| `appInfo` (prompt) | `fun appInfo(params: String): String` | sync | App metadata |
| `loaded` (prompt) | `fun loaded(params: String): String` | sync | Page signals it has loaded |
| `back` | `fun back(params: String)` | async | Close the WebView |
| `request` | `fun request(params: String)` | async | HTTP request proxy (OkHttp) |
| `open.web` | `fun openWeb(params: String)` | async | Open nested WebViewActivity |
| `open.camera` | `fun openCamera(params: String)` | async | Camera picker |
| `open.photos` | `fun openPhotos(params: String)` | async | Photo gallery picker |
| `token.get` | `fun getToken(params: String)` | async | Delegate to bridge.getAccessToken() |
| `member.get` | `fun getMember(params: String)` | async | Delegate to bridge.getMember() |
| `members.get` | `fun getMembers(params: String)` | async | Delegate to bridge.getMembers() |
| `message.send` | `fun sendMessage(params: String)` | async | Delegate to bridge.sendMessage() |
| `game.info.get` | `fun getGameInfo(params: String)` | async | Delegate to bridge.getGameInfo() |

### Message protocol

Identical to iOS:

**JS → Native (FromJsData)**
```json
{ "handleId": "abc123", "data": { … } }
```

**Native → JS (ToJsData / window.__webkitNotification)**
```json
{ "handle": "abc123", "code": 200, "msg": "", "data": "…" }
```

Called via:
```kotlin
webView.evaluateJavascript(
    "window.__webkitNotification(${Uri.encode(json)})",
    null
)
```

### Events pushed by native to JS (no handle)

Equivalent to iOS `sendToWebEvent`:

| iOS `WebEventName` | Android equivalent | Trigger |
|---|---|---|
| `enter_background` | Activity `onPause` | App backgrounded |
| `enter_foreground` | Activity `onResume` | App foregrounded |
| `keyboard_will_show` | `ViewTreeObserver.OnGlobalLayoutListener` | Soft keyboard appears |
| `keyboard_will_hide` | `ViewTreeObserver.OnGlobalLayoutListener` | Soft keyboard hides |

---

## 6. WebView configuration

Mirrors `BaseWebViewActivity.initWebView()` + iOS `WKWebViewConfiguration`:

```kotlin
fun WebView.applyMiniAppSettings(appId: Long) {
    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        databaseEnabled = true
        allowFileAccess = true
        setSupportZoom(false)
        cacheMode = WebSettings.LOAD_NO_CACHE
        // Allow cross-origin access from file:// (iOS: allowUniversalAccessFromFileURLs)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            allowUniversalAccessFromFileURLs = true
            allowFileAccessFromFileURLs = true
        }
        // HTML5 media inline playback (iOS: allowsInlineMediaPlayback = true)
        mediaPlaybackRequiresUserGesture = false
        // Allow mixed content (iOS: upgradeKnownHostsToHTTPS = false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
    }
    WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
}
```

---

## 7. Startup script injection

Equivalent to iOS `initInfo()` + `appInitScript`:

Injected at `onPageStarted`:

```kotlin
val startScript = """
    window.___platform = 'android';
    window.___device = ${deviceInfoJson()};
    window.___token = '${config.token ?: ""}';
    window.___user = ${config.user?.toJson() ?: "null"};
    window.___appId = ${config.appId};
    window.___options = ${config.options.toJson()};
""".trimIndent()
webView.loadUrl("javascript:$startScript")
```

This matches the iOS `window.___platform = 'ios'` + `window.___device` injection.

---

## 8. `request()` bridge implementation

The reference `WebRequest.connectServer()` uses raw `HttpURLConnection`. In this library we use **OkHttp** (already in the project) on a background coroutine:

```kotlin
@JavascriptInterface
fun request(params: String) {
    val from = FromJsData(params) ?: return
    scope.launch(Dispatchers.IO) {
        val result = runCatching { miniAppRequest.execute(from.data, config.token) }
        val to = ToJsData(from.handleId).apply {
            data = result.getOrNull()?.data ?: ""
            code = if (result.isSuccess) 200 else 400
            msg = result.exceptionOrNull()?.message ?: ""
        }
        withContext(Dispatchers.Main) {
            jsCallback(to.toJson())
        }
    }
}
```

`MiniAppRequest.execute()` reads `method`, `url`, `headers`, `data` from the JSON, adds `APP-U: s=<homeserverHost>` if the request targets the homeserver, and delegates to OkHttp.

---

## 9. Per-app storage

Equivalent to iOS `UserDefaults` with per-app prefix:

```kotlin
class MiniAppStorage(context: Context, appId: Long) {
    private val prefs = context.getSharedPreferences("miniapp_${appId}", Context.MODE_PRIVATE)

    fun get(key: String): String? {
        // Special key "__auth" is session-scoped (no appId prefix) — same as iOS
        val prefKey = if (key == "__auth") "miniapp_auth_$key" else "miniapp_${appId}_$key"
        return prefs.getString(prefKey, null)
    }

    fun set(key: String, value: String) { … }
    fun remove(key: String) { … }
}
```

---

## 10. Navigation integration

### Option A — Appyx Node (preferred for full-screen game/MiniApp)

```
features/miniapp/
  api/  → MiniAppNode.kt interface + navigation target
  impl/ → MiniAppNode.kt (ContributesNode), MiniAppPresenter, MiniAppView
```

`MiniAppNode` receives `MiniAppConfig` as assisted-inject argument, implements `MiniAppHostBridge` by delegating to the room's `JoinedRoom` and `MatrixClient`.

### Option B — Composable-only (lightweight embed)

`MiniAppView` is used directly inside an existing Node (e.g., from a game card tap). The parent Node implements `MiniAppHostBridge`.

Phase 1 implements **Option B** (no new Node needed). Option A is the Phase 2 full-screen path.

---

## 11. File list (impl)

| File | Purpose |
|---|---|
| `MiniAppView.kt` | Public Composable; `AndroidView(::WebView)` + `BackHandler` |
| `MiniAppWebViewState.kt` | Holds WebView ref, config, coroutine scope; `attachTo(wv)`, `update(wv)`, `onBackPressed()` |
| `MiniAppJsBridge.kt` | `@JavascriptInterface` class; all `webkit.*` methods |
| `MiniAppWebViewClient.kt` | `WebViewClient`: `onPageStarted` → inject script; `onPageFinished`; `shouldOverrideUrlLoading` (`pinduoduo://` scheme) |
| `MiniAppJsModel.kt` | `FromJsData`, `ToJsData`, `AppJsEvent` (bridge protocol data classes) |
| `MiniAppStorage.kt` | SharedPreferences KV helper |
| `MiniAppRequest.kt` | OkHttp HTTP proxy for `request()` bridge call |
| `internal/JsCallback.kt` | `fun WebView.jsCallback(json: String)` — `evaluateJavascript(window.__webkitNotification(…))` |
| `internal/PlatformScript.kt` | Builds the startup JS injection string |
| `internal/DeviceInfo.kt` | Builds `window.___device` JSON |

---

## 12. Gradle module wiring

```kotlin
// libraries/miniapp/impl/build.gradle.kts
dependencies {
    api(project(":libraries:miniapp:api"))
    implementation(libs.androidx.webkit)        // WebView
    implementation(libs.okhttp)                 // HTTP bridge
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.compose.ui)             // AndroidView
    implementation(libs.timber)
}
```

The consuming feature (e.g., `features/messages/impl`) adds:
```kotlin
implementation(project(":libraries:miniapp:impl"))
```

---

## 13. Open questions

| # | Question | Default assumption |
|---|---|---|
| 1 | Does the game WebView load from a **local bundle** (zip cached on disk) or always **remote URL**? | Phase 1 = remote URL only (`remoteUrl` in `MiniAppConfig`). Local bundle cache is Phase 2. |
| 2 | How does the JS bridge `request()` know which requests need `APP-U` headers? | Any URL whose host matches `config.homeserverHost` gets the header; others are passed through as-is. |
| 3 | Should `MiniAppView` be full-screen Activity or an in-place Composable? | Phase 1: in-place Composable inside existing MessagesNode. Phase 2: dedicated `MiniAppNode`. |
| 4 | Audio / microphone permission for game audio (WebRTC)? | Request at WebView level via `WebChromeClient.onPermissionRequest`. Not in scope for Phase 1. |
| 5 | Nested `open.web` (opens another WebView from within the MiniApp)? | Phase 1: no-op (log + `Timber.d`). Phase 2: push a second `MiniAppView` on the back stack. |

---

## 14. Acceptance criteria

- [ ] `MiniAppView(config, bridge)` renders a WebView that loads `config.remoteUrl`
- [ ] `window.___platform === 'android'` is set before the page executes any JS
- [ ] `window.___token`, `window.___user`, `window.___options` are injected at document start
- [ ] `webkit.appInfo()`, `webkit.storageGet/Set/Remove()` return correct sync responses
- [ ] `webkit.request()` proxies an HTTP call via OkHttp and calls `window.__webkitNotification`
- [ ] `webkit.getToken()`, `webkit.getMembers()`, `webkit.getMember()`, `webkit.sendMessage()`, `webkit.getGameInfo()` delegate correctly to `MiniAppHostBridge`
- [ ] `webkit.back()` / `webkit.closeApp()` calls `bridge.closeApp()` and pops the screen
- [ ] Back-press on Android is forwarded to JS first (`window.__webkitNotification({ event: "back" })`); if no JS handler responds within 300 ms, the system back action proceeds
- [ ] Per-app SharedPreferences is namespaced by `appId` and survives WebView destruction
- [ ] `WebView.setWebContentsDebuggingEnabled(true)` in debug builds only
- [ ] `./gradlew :libraries:miniapp:impl:testDebugUnitTest` passes

---

## 15. Implementation order

1. `api` module: `MiniAppConfig`, `MiniAppUser`, `MiniAppHostBridge`, `MiniAppResult`
2. `impl` module: `MiniAppJsModel`, `MiniAppStorage`, `JsCallback`, `PlatformScript`
3. `impl` module: `MiniAppJsBridge` (sync methods first, then async stubs)
4. `impl` module: `MiniAppWebViewClient`, `MiniAppWebViewState`
5. `impl` module: `MiniAppView` Composable
6. `impl` module: `MiniAppRequest` (OkHttp bridge)
7. Wire `bridge.sendMessage / getAccessToken / getMembers / getMember / getGameInfo / closeApp`
8. Integrate into `TimelineEvent.OpenGame` handler in `TimelinePresenter` (Phase 2 of game card)

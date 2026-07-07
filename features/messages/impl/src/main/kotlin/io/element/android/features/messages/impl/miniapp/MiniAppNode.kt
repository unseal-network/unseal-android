/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.miniapp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.libraries.architecture.NodeInputs
import io.element.android.libraries.architecture.inputs
import io.element.android.libraries.chatbot.api.ChatbotBaseUrlResolver
import io.element.android.libraries.di.RoomScope
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.gameapi.api.AppBundleInfo
import io.element.android.libraries.gameapi.impl.DefaultGameApiService
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.room.RoomMember
import io.element.android.libraries.matrix.api.room.activeRoomMembers
import io.element.android.libraries.miniapp.api.MiniAppConfig
import io.element.android.libraries.miniapp.api.MiniAppHostBridge
import io.element.android.libraries.miniapp.api.MiniAppToken
import io.element.android.libraries.miniapp.api.MiniAppUser
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import io.element.android.libraries.miniapp.impl.MiniAppLoadingOverlay
import io.element.android.libraries.miniapp.impl.MiniAppLoadingState
import io.element.android.libraries.miniapp.impl.MiniAppView
import okhttp3.OkHttpClient
import timber.log.Timber

/**
 * Full-screen Appyx Node that hosts a mini-app WebView.
 *
 * ## Load flow (mirrors iOS `WebViewController.checkLoad()`)
 *
 * ```
 * MiniAppNode.View()
 *   │
 *   ├─ appId == 0 ──────────────────────────────────────────────────────────────┐
 *   │                                                                           │
 *   └─ appId > 0: call pkg.app.check.update (DefaultGameApiService)             │
 *         │                                                                     │
 *         ├─ loadMode = Remote → MiniAppConfig(url = remoteUrl, zipUrl = null) ─┤
 *         │                                                                     │
 *         └─ loadMode = Local  → MiniAppConfig(url = fallback, zipUrl = zipUrl) ┘
 *                                         │
 *                               MiniAppView
 *                                 ├─ zipUrl == null → load url directly
 *                                 └─ zipUrl != null → download ZIP →
 *                                                     extract → load index.html
 * ```
 *
 * Phase 1 (bundle info fetch): the node shows a full-screen spinner.
 * Phase 2 (ZIP download + extraction): [MiniAppView] shows its own progress overlay.
 * Phase 3 (page loaded): WebView is visible; JS bridge is active.
 *
 * ## JS host bridge
 * [MiniAppHostBridge] is implemented inline with real Matrix APIs:
 * - [MiniAppHostBridge.getAccessToken]  → [MatrixClient.currentAccessToken]
 * - [MiniAppHostBridge.getMembers]      → [JoinedRoom.membersStateFlow] or [JoinedRoom.getMembers]
 * - [MiniAppHostBridge.getMember]       → cached member lookup
 * - [MiniAppHostBridge.getGameInfo]     → static metadata (appId, roomId, meetId)
 * - [MiniAppHostBridge.sendMessage]     → [JoinedRoom.liveTimeline.sendMessage]
 * - [MiniAppHostBridge.closeApp]        → [navigateUp]
 * - [MiniAppHostBridge.openWeb]         → no-op (future: push nested MiniAppNode)
 */
@ContributesNode(RoomScope::class)
class MiniAppNode @AssistedInject constructor(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    @ApplicationContext private val context: Context,
    private val matrixClient: MatrixClient,
    private val room: JoinedRoom,
    private val baseUrlResolver: ChatbotBaseUrlResolver,
    private val okHttpClient: () -> OkHttpClient,
) : Node(buildContext, plugins = plugins) {

    data class Inputs(
        val appId: Long,
        val remoteUrl: String,
        val meetId: String,
    ) : NodeInputs

    private val inputs = inputs<Inputs>()

    /** Cached from the `pkg.app.check.update` response; used by `app.data` JS bridge. */
    @Volatile
    private var lastBundleInfo: AppBundleInfo? = null

    /** Cached homeserver base URL resolved in [resolveBundleConfig]; used by [getGameInfo]. */
    @Volatile
    private var cachedHomeserverUrl: String = ""

    // ── Host bridge ───────────────────────────────────────────────────────────

    private val hostBridge = object : MiniAppHostBridge {

        override suspend fun getAccessToken(): String =
            matrixClient.currentAccessToken()
                .onFailure { Timber.e(it, "MiniApp: failed to get access token") }
                .getOrNull() ?: ""

        override suspend fun getMembers(): List<MiniAppUser>? {
            val cached = room.membersStateFlow.value.activeRoomMembers()
            if (cached.isNotEmpty()) return cached.map { it.toMiniAppUser() }
            return room.getMembers(limit = 500)
                .onFailure { Timber.e(it, "MiniApp: failed to fetch members") }
                .getOrNull()
                ?.map { it.toMiniAppUser() }
        }

        override fun getMember(userId: String): MiniAppUser? =
            room.membersStateFlow.value
                .activeRoomMembers()
                .find { it.userId.value == userId }
                ?.toMiniAppUser()

        override fun getGameInfo(): Map<String, Any> {
            val self = room.membersStateFlow.value
                .activeRoomMembers()
                .find { matrixClient.isMe(it.userId) }
            return buildMap {
                put("roomId", room.roomId.value)
                put("userId", matrixClient.sessionId.value)
                put("displayName", self?.displayName ?: "")
                put("powerLevel", self?.powerLevel?.toInt() ?: 0)
                put("gameRoomId", inputs.meetId)
                put("config", mapOf("streamURL" to cachedHomeserverUrl))
            }
        }

        override suspend fun sendMessage(data: Map<String, Any>) {
            val body = (data["text"] as? String)
                ?: (data["body"] as? String)
                ?: data.entries.joinToString(", ") { (k, v) -> "$k=$v" }
            room.liveTimeline.sendMessage(
                body = body,
                htmlBody = null,
                intentionalMentions = emptyList(),
            ).onFailure { Timber.e(it, "MiniApp: failed to send message") }
        }

        override fun closeApp() = navigateUp()

        override fun openWeb(url: String, params: Map<String, Any>) {
            Timber.d("MiniApp: openWeb $url — not yet supported")
        }

        override fun openApp(url: String) {
            Timber.d("MiniApp: openApp %s", url)
            runCatching {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }.onFailure { Timber.e(it, "MiniApp: openApp failed for url=%s", url) }
        }

        override fun openMiniApp(appId: Int, params: Map<String, Any>) {
            Timber.d("MiniApp: openMiniApp appId=%d — not yet implemented", appId)
        }

        override suspend fun fetchAppData(appId: Int): Map<String, Any>? {
            // Return the cached bundle info if it matches the requested appId.
            lastBundleInfo?.takeIf { it.appId == appId }?.let { info ->
                return info.toBundleDataMap()
            }

            // Otherwise attempt a fresh API call.
            val homeserverUrl = runCatching {
                baseUrlResolver.resolveHomeserverBaseUrl(matrixClient.userIdServerName())
            }.getOrNull() ?: return null

            val service = DefaultGameApiService(
                homeserverUrl = homeserverUrl,
                matrixClient = matrixClient,
                okHttpClient = okHttpClient(),
                context = context,
            )
            return service.fetchAppBundle(appId)
                .onSuccess { lastBundleInfo = it }
                .map { it.toBundleDataMap() }
                .getOrNull()
        }
    }

    // ── View ──────────────────────────────────────────────────────────────────

    @Composable
    override fun View(modifier: Modifier) {
        // Hide system bars for an immersive full-screen game experience.
        // Restored when this node leaves the composition (e.g. on back navigation).
        val view = LocalView.current
        DisposableEffect(Unit) {
            val activity = view.context as? Activity
            val window = activity?.window
            val insetsController = window?.let { WindowCompat.getInsetsController(it, view) }
            insetsController?.apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            onDispose {
                insetsController?.show(WindowInsetsCompat.Type.systemBars())
            }
        }

        // LoggedInView applies systemBarsPadding() globally, so the modifier received here
        // starts below the status bar. Escape that constraint by measuring with the extra
        // status-bar height and placing the content above its layout origin so the loading
        // overlay and WebView fill the full screen (edge-to-edge is already enabled by the
        // Activity via enableEdgeToEdge()).
        val density = LocalDensity.current
        val statusBarTop = WindowInsets.statusBars.getTop(density)
        val fullScreenModifier = Modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(
                    constraints.copy(maxHeight = constraints.maxHeight + statusBarTop)
                )
                layout(placeable.width, placeable.height) {
                    placeable.place(0, -statusBarTop)
                }
            }
            .then(modifier)

        // Phase 1: resolve bundle info from the server.
        // null = API call in progress (show spinner).
        // non-null = config ready; hand off to MiniAppView.
        val config by produceState<MiniAppConfig?>(initialValue = null) {
            value = resolveBundleConfig()
        }

        if (config == null) {
            // Phase 1 — covers the full screen while pkg.app.check.update is in-flight.
            MiniAppLoadingOverlay(
                state = MiniAppLoadingState.Loading(),
                onClose = { navigateUp() },
                modifier = fullScreenModifier,
            )
            return
        }

        // Phase 2 + 3: MiniAppView handles ZIP download (if zipUrl set) and page load.
        MiniAppView(
            config = config!!,
            hostBridge = hostBridge,
            okHttpClient = okHttpClient(),
            onClose = { navigateUp() },
            modifier = fullScreenModifier.fillMaxSize(),
        )
    }

    // ── Bundle config resolution ──────────────────────────────────────────────

    /**
     * Calls `pkg.app.check.update` and converts the result into a [MiniAppConfig].
     * Always returns a non-null config (falls back to direct remoteUrl on any error).
     *
     * Mirrors iOS `checkLoad()` decision logic:
     * - loadMode=remote  → MiniAppConfig with direct url, no zipUrl
     * - loadMode=local   → MiniAppConfig with zipUrl set for bundle download
     */
    private suspend fun resolveBundleConfig(): MiniAppConfig {
        // Fetch token and current user once; included in every config path below.
        val accessToken = matrixClient.currentAccessToken()
            .onFailure { Timber.e(it, "MiniApp: failed to get access token") }
            .getOrNull() ?: ""
        val token = if (accessToken.isNotEmpty()) MiniAppToken(accessToken = accessToken) else null
        val selfUser = room.membersStateFlow.value.activeRoomMembers()
            .find { matrixClient.isMe(it.userId) }
            ?.toMiniAppUser()

        // Resolve homeserver for all paths so JS always has window.___homeserver available.
        val homeserverUrl = runCatching {
            baseUrlResolver.resolveHomeserverBaseUrl(matrixClient.userIdServerName())
        }.getOrNull()

        // No appId → load remoteUrl directly.
        if (inputs.appId <= 0L) {
            return MiniAppConfig(
                appId = inputs.appId,
                url = inputs.remoteUrl,
                zipUrl = null,
                token = token,
                user = selfUser,
                homeserver = homeserverUrl,
            )
        }

        if (homeserverUrl.isNullOrBlank()) {
            Timber.w("MiniApp: homeserver URL unavailable — loading remoteUrl directly")
            return MiniAppConfig(appId = inputs.appId, url = inputs.remoteUrl, zipUrl = null, token = token, user = selfUser)
        }
        cachedHomeserverUrl = homeserverUrl

        val service = DefaultGameApiService(
            homeserverUrl = homeserverUrl,
            matrixClient = matrixClient,
            okHttpClient = okHttpClient(),
            context = context,
        )

        return service.fetchAppBundle(inputs.appId.toInt())
            .map { info ->
                lastBundleInfo = info
                Timber.d(
                    "MiniApp: bundle info appId=%d version=%s mode=%s zipUrl=%s",
                    info.appId, info.version, info.loadMode, info.zipUrl
                )
                when (info.loadMode) {
                    AppBundleInfo.LoadMode.Remote -> MiniAppConfig(
                        appId = inputs.appId,
                        url = info.remoteUrl ?: inputs.remoteUrl,
                        zipUrl = null,
                        token = token,
                        user = selfUser,
                        appBundleData = info.toBundleDataMap(),
                        homeserver = homeserverUrl,
                    )
                    AppBundleInfo.LoadMode.Local -> MiniAppConfig(
                        appId = inputs.appId,
                        url = info.remoteUrl ?: inputs.remoteUrl,  // fallback if ZIP fails
                        zipUrl = info.zipUrl,
                        token = token,
                        user = selfUser,
                        appBundleData = info.toBundleDataMap(),
                        bundleVersion = info.version,
                        homeserver = homeserverUrl,
                    )
                }
            }
            .getOrElse { error ->
                Timber.e(error, "MiniApp: fetchAppBundle failed — falling back to remoteUrl")
                MiniAppConfig(appId = inputs.appId, url = inputs.remoteUrl, zipUrl = null, token = token, user = selfUser, homeserver = homeserverUrl)
            }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun RoomMember.toMiniAppUser() = MiniAppUser(
        userId = userId.value,
        displayName = displayName,
        avatarUrl = avatarUrl,
        powerLevel = powerLevel.toInt(),
        isAgent = isServiceMember.takeIf { isServiceMember },
        isSelf = matrixClient.isMe(userId),
    )
}

/**
 * Convert an [AppBundleInfo] to the flat map returned by the `app.data` JS bridge method.
 * Mirrors the `AppModel` dict built in iOS `WebView+SQLite.swift saveAppModelToDatabase`.
 */
private fun AppBundleInfo.toBundleDataMap(): Map<String, Any> = buildMap {
    put("id", appId)
    put("version", version)
    put("load_mode", loadMode.name.lowercase())
    remoteUrl?.let { put("remote_url", it) }
    zipUrl?.let { put("zip_url", it) }
}

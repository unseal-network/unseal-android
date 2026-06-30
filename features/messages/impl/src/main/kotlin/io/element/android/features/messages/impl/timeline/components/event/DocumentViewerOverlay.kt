/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.element.android.libraries.miniapp.api.MiniAppConfig
import io.element.android.libraries.miniapp.api.MiniAppHostBridge
import io.element.android.libraries.miniapp.api.MiniAppUser
import io.element.android.libraries.miniapp.impl.MiniAppLoadingOverlay
import io.element.android.libraries.miniapp.impl.MiniAppLoadingState
import io.element.android.libraries.miniapp.impl.MiniAppView

/**
 * Fullscreen document viewer backed by the MiniApp WebView stack.
 *
 * Resolves the bundle config for [appId] via [launcher] (calls `pkg.app.check.update`),
 * shows a loading overlay while in-flight, then hands off to [MiniAppView].
 *
 * Usage:
 * ```
 * // PPT slides
 * DocumentViewerOverlay(
 *     appId = MiniAppIds.PPT,
 *     options = mapOf("htmls" to slides, "initialIndex" to index),
 *     launcher = launcher,
 *     onDismiss = { showViewer = false },
 * )
 * // Word document (future)
 * DocumentViewerOverlay(
 *     appId = MiniAppIds.DOCX,
 *     options = mapOf("doc_id" to docId),
 *     launcher = launcher,
 *     onDismiss = { showViewer = false },
 * )
 * ```
 */
@Composable
fun DocumentViewerOverlay(
    appId: Long,
    options: Map<String, Any>,
    launcher: MiniAppDocumentLauncher,
    onDismiss: () -> Unit,
) {
    val config by produceState<MiniAppConfig?>(initialValue = null) {
        value = launcher.buildConfig(appId, options)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .systemBarsPadding(),
        ) {
            BackHandler(onBack = onDismiss)

            val resolvedConfig = config
            if (resolvedConfig == null) {
                MiniAppLoadingOverlay(
                    state = MiniAppLoadingState.Loading(),
                    onClose = onDismiss,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                MiniAppView(
                    config = resolvedConfig,
                    hostBridge = DocumentViewerHostBridge(
                        accessToken = resolvedConfig.token?.accessToken ?: "",
                        onClose = onDismiss,
                    ),
                    okHttpClient = launcher.okHttpClient(),
                    onClose = onDismiss,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** Minimal [MiniAppHostBridge] for read-only document viewing. */
private class DocumentViewerHostBridge(
    private val accessToken: String,
    private val onClose: () -> Unit,
) : MiniAppHostBridge {
    override suspend fun getAccessToken(): String = accessToken
    override suspend fun getMembers(): List<MiniAppUser>? = null
    override fun getMember(userId: String): MiniAppUser? = null
    override fun getGameInfo(): Map<String, Any> = emptyMap()
    override suspend fun sendMessage(data: Map<String, Any>) = Unit
    override fun closeApp() = onClose()
    override fun openWeb(url: String, params: Map<String, Any>) = Unit
}

/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
            dismissOnBackPress = false,
        ),
    ) {
        val view = LocalView.current
        // Operate ONLY on the Dialog's own window — never touch the Activity window.
        // Hiding system bars on the Activity window causes a focus change that makes
        // Android call onDismissRequest, closing the overlay immediately.
        DisposableEffect(Unit) {
            val dialogWindow = (view.parent as? DialogWindowProvider)?.window ?: return@DisposableEffect onDispose {}

            dialogWindow.attributes = dialogWindow.attributes.also { lp ->
                lp.width = ViewGroup.LayoutParams.MATCH_PARENT
                lp.height = ViewGroup.LayoutParams.MATCH_PARENT
                lp.gravity = Gravity.TOP or Gravity.START
                lp.x = 0
                lp.y = 0
            }
            dialogWindow.addFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            )
            WindowCompat.setDecorFitsSystemWindows(dialogWindow, false)
            WindowCompat.getInsetsController(dialogWindow, dialogWindow.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }

            onDispose {
                dialogWindow.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
                WindowCompat.getInsetsController(dialogWindow, dialogWindow.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
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

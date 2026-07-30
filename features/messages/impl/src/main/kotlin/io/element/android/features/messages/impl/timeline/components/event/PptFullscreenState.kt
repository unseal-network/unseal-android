/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Pending request to show the PPT fullscreen overlay.
 *
 * Carries the launcher so the render site (MessagesNode, above the LazyColumn)
 * does not need its own DI access to [MiniAppDocumentLauncher].
 */
data class PptFullscreenRequest(
    val slides: List<String>,
    val initialIndex: Int,
    val streamId: String?,
    val taskId: String?,
    val launcher: MiniAppDocumentLauncher,
)

/**
 * Stable state holder shared between the LazyColumn item ([PptSlidesView]) that
 * triggers fullscreen and the screen-level composable ([MessagesNode]) that renders it.
 *
 * [PptSlidesView] writes [request] (inside the LazyColumn).
 * [MessagesNode] reads [request] and renders [DocumentViewerOverlay] outside the LazyColumn,
 * so the Dialog is never part of a recyclable LazyColumn item and cannot be closed by
 * the item leaving the viewport.
 */
class PptFullscreenState {
    var request: PptFullscreenRequest? by mutableStateOf(null)

    fun open(
        slides: List<String>,
        initialIndex: Int,
        streamId: String?,
        taskId: String?,
        launcher: MiniAppDocumentLauncher,
    ) {
        request = PptFullscreenRequest(
            slides = slides,
            initialIndex = initialIndex,
            streamId = streamId,
            taskId = taskId,
            launcher = launcher,
        )
    }

    fun close() {
        request = null
    }
}

/** Provided by [MessagesNode], consumed by [PptSlidesView] and the screen-level render site. */
val LocalPptFullscreenState = compositionLocalOf { PptFullscreenState() }

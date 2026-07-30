/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.element.android.libraries.matrix.api.media.MediaSource

/**
 * Pending request to show the file editor overlay (docx / xlsx / pdf).
 *
 * [eventId] is used as the `stream_id` key for docId persistence via the same
 * `pkg.doc.bind` / `pkg.doc.query` mechanism as AI-stream PPT slides.
 */
data class FileEditorRequest(
    val appId: Long,
    val eventId: String,
    val filename: String,
    val mimeType: String,
    val mediaSource: MediaSource,
    val launcher: MiniAppDocumentLauncher,
    /** Pre-resolved homeserver URL from the call site, mirrors iOS `clientProxy.homeserver`. */
    val homeserverUrl: String,
)

/**
 * Shared mutable state between [MessagesFlowNode] (which writes on file click)
 * and [MessagesNode] (which reads and renders [DocumentViewerOverlay]).
 *
 * Created once per room session inside [MessagesFlowNode] and passed to
 * [MessagesNode] via [MessagesNode.Inputs], so both sides reference the
 * same object without any DI scope changes.
 */
class FileEditorOverlayState {
    var request: FileEditorRequest? by mutableStateOf(null)

    fun open(
        appId: Long,
        eventId: String,
        filename: String,
        mimeType: String,
        mediaSource: MediaSource,
        launcher: MiniAppDocumentLauncher,
        homeserverUrl: String,
    ) {
        request = FileEditorRequest(
            appId = appId,
            eventId = eventId,
            filename = filename,
            mimeType = mimeType,
            mediaSource = mediaSource,
            launcher = launcher,
            homeserverUrl = homeserverUrl,
        )
    }

    fun close() {
        request = null
    }
}

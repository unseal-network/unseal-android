/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbot.api

import io.element.android.libraries.matrix.api.MatrixClient
import java.io.Closeable

/**
 * Opens a WebSocket connection to the workflow progress endpoint for a given task.
 *
 * WebSocket URL pattern: {wsBaseUrl}/chat/ws/workflow/{taskId}?authorization=Bearer {token}
 * where wsBaseUrl is the homeserver base URL with https replaced by wss.
 *
 * Mirrors iOS WorkflowWebSocketV2.
 */
interface WorkflowWebSocketFactory {
    /**
     * Opens a WebSocket connection for the given [taskId].
     *
     * @param taskId the workflow task ID (e.g. "ppt_plan_20260603_001")
     * @param explicitWsUrl if provided, use this URL directly instead of deriving from the homeserver base URL
     * @param matrixClient the current Matrix session, used to obtain the access token and base URL
     * @param onActivity called with each workflow_activity or history message received
     * @param onComplete called when the workflow is complete (workflow_complete message received)
     * @param onError called when a connection error or workflow failure occurs
     * @return a [Closeable] that disconnects the WebSocket when closed
     */
    suspend fun open(
        taskId: String,
        explicitWsUrl: String?,
        matrixClient: MatrixClient,
        onActivity: (WorkflowWebSocketMessage) -> Unit,
        onComplete: () -> Unit,
        onError: (Throwable) -> Unit,
    ): Closeable
}

data class WorkflowWebSocketMessage(
    val type: String,
    /** Workflow stage name, e.g. "starting", "planning", "writing", "complete" */
    val stage: String?,
    val message: String?,
    val isDone: Boolean?,
    val totalSlides: Int?,
    val totalSections: Int?,
    val slideData: WorkflowSlideData?,
    val sectionData: WorkflowSectionData?,
)

data class WorkflowSlideData(
    val slideNumber: Int,
    val slideTitle: String,
    val htmlContent: String,
)

data class WorkflowSectionData(
    val sectionIndex: Int,
    val sectionTitle: String,
    val content: String,
    val totalSections: Int,
)

/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.chatbot.api.WorkflowWebSocketFactory
import io.element.android.libraries.chatbot.api.WorkflowWebSocketMessage
import io.element.android.libraries.di.RoomScope
import io.element.android.libraries.matrix.api.MatrixClient
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.Closeable

/**
 * Manages active WebSocket connections for workflow tasks.
 *
 * When a timeline item contains a tool output with task_type "ppt_generation", "writing_generation",
 * "deep_research", or "professor_review", the presenter calls [ensureTracking] with the task_id.
 * This opens a WebSocket to /chat/ws/workflow/{taskId} and pushes progress updates via [progressFlow].
 *
 * Mirrors iOS WorkflowWebSocketV2 + the callback dispatch in SSEClient+Parse.swift handleToolCallOutput.
 */
@SingleIn(RoomScope::class)
@Inject
class WorkflowProgressManager(
    private val matrixClient: MatrixClient,
    private val workflowWebSocketFactory: WorkflowWebSocketFactory,
) {
    private val connections = mutableMapOf<String, Closeable>()
    private val progressFlows = mutableMapOf<String, MutableStateFlow<WorkflowTaskProgress>>()
    private val lock = Any()

    fun progressFlow(taskId: String): StateFlow<WorkflowTaskProgress> {
        return synchronized(lock) {
            progressFlows.getOrPut(taskId) {
                MutableStateFlow(WorkflowTaskProgress(taskId = taskId))
            }
        }.asStateFlow()
    }

    /**
     * Ensures a WebSocket is open for [taskId]. No-op if already connected.
     * Must be called from a coroutine (needed to fetch the access token).
     */
    suspend fun ensureTracking(
        taskId: String,
        wsUrl: String?,
        scope: CoroutineScope,
    ) {
        val alreadyTracking = synchronized(lock) { connections.containsKey(taskId) }
        if (alreadyTracking) return

        Timber.tag("WorkflowWS").d("ensureTracking task=%s", taskId)

        val stateFlow = synchronized(lock) {
            progressFlows.getOrPut(taskId) { MutableStateFlow(WorkflowTaskProgress(taskId = taskId)) }
        }

        val connection = runCatching {
            workflowWebSocketFactory.open(
                taskId = taskId,
                explicitWsUrl = wsUrl,
                matrixClient = matrixClient,
                onActivity = { msg -> scope.launch { handleActivity(taskId, stateFlow, msg) } },
                onComplete = { scope.launch { handleComplete(taskId, stateFlow) } },
                onError = { err -> scope.launch { handleError(taskId, stateFlow, err) } },
            )
        }.getOrElse { err ->
            Timber.tag("WorkflowWS").w(err, "Failed to open WebSocket task=%s", taskId)
            return
        }

        synchronized(lock) {
            connections[taskId] = connection
        }
    }

    fun stopTracking(taskId: String) {
        val conn = synchronized(lock) { connections.remove(taskId) }
        conn?.close()
    }

    fun stopAll() {
        val all = synchronized(lock) {
            val copy = connections.values.toList()
            connections.clear()
            copy
        }
        all.forEach { it.close() }
    }

    private fun handleActivity(
        taskId: String,
        flow: MutableStateFlow<WorkflowTaskProgress>,
        msg: WorkflowWebSocketMessage,
    ) {
        val current = flow.value
        val newActivities = buildList {
            addAll(current.activities)
            msg.stage?.let { stage ->
                val label = buildString {
                    append(stage.replaceFirstChar { it.uppercaseChar() })
                    msg.message?.let { append(": $it") }
                }
                if (!contains(label)) add(label)
            } ?: msg.message?.let { if (!contains(it)) add(it) }
        }
        flow.value = current.copy(
            activities = newActivities.toImmutableList(),
            latestStage = msg.stage ?: current.latestStage,
            latestMessage = msg.message ?: current.latestMessage,
            completedSlides = current.completedSlides + (if (msg.slideData != null) 1 else 0),
            completedSections = current.completedSections + (if (msg.sectionData != null) 1 else 0),
            totalSlides = msg.totalSlides ?: current.totalSlides,
            totalSections = msg.totalSections ?: current.totalSections,
        )
    }

    private fun handleComplete(taskId: String, flow: MutableStateFlow<WorkflowTaskProgress>) {
        flow.value = flow.value.copy(isComplete = true)
        synchronized(lock) { connections.remove(taskId) }
    }

    private fun handleError(taskId: String, flow: MutableStateFlow<WorkflowTaskProgress>, err: Throwable) {
        Timber.tag("WorkflowWS").w(err, "Workflow error task=%s", taskId)
        flow.value = flow.value.copy(hasError = true)
        synchronized(lock) { connections.remove(taskId) }
    }
}

data class WorkflowTaskProgress(
    val taskId: String,
    val isComplete: Boolean = false,
    val hasError: Boolean = false,
    val latestStage: String? = null,
    val latestMessage: String? = null,
    val activities: ImmutableList<String> = persistentListOf(),
    val completedSlides: Int = 0,
    val totalSlides: Int? = null,
    val completedSections: Int = 0,
    val totalSections: Int? = null,
) {
    val isTracking: Boolean get() = !isComplete && !hasError
    val progressText: String?
        get() = when {
            isComplete -> "Complete"
            hasError -> "Failed"
            totalSlides != null && totalSlides > 0 -> "$completedSlides / $totalSlides slides"
            totalSections != null && totalSections > 0 -> "$completedSections / $totalSections sections"
            latestStage != null -> latestStage.replaceFirstChar { it.uppercaseChar() }
            else -> null
        }
}

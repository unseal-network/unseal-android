/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import dev.zacsweers.metro.Inject
import io.element.android.libraries.agentstream.UnsealWorkflowWebSocket
import io.element.android.libraries.chatbot.api.ChatbotBaseUrlResolver
import io.element.android.libraries.matrix.api.MatrixClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import org.json.JSONObject

/** Parsed message from a workflow WebSocket `pollMessage()` call. */
sealed interface WorkflowMessage {
    data object Empty : WorkflowMessage
    data class Progress(
        val stage: String,
        val message: String,
        val isDone: Boolean,
        val totalSlides: Int?,
        val totalSections: Int?,
    ) : WorkflowMessage
    data class Completed(val workflowId: String?) : WorkflowMessage
    data class Error(val reason: String) : WorkflowMessage
}

/** Creates [UnsealWorkflowWebSocket] instances — swapped for a fake in tests. */
interface WorkflowWebSocketFactory {
    fun create(taskId: String, wsBaseUrl: String, accessToken: String): UnsealWorkflowWebSocket
}

class DefaultWorkflowWebSocketFactory @Inject constructor() : WorkflowWebSocketFactory {
    override fun create(taskId: String, wsBaseUrl: String, accessToken: String): UnsealWorkflowWebSocket {
        return UnsealWorkflowWebSocket(taskId, wsBaseUrl, accessToken)
    }
}

/** Exposes per-task workflow WebSocket progress as a [Flow]. */
interface WorkflowProgressProvider {
    fun progressFlow(taskId: String): Flow<WorkflowMessage>
}

/**
 * Connects to the Unseal workflow WebSocket and exposes a [Flow] of [WorkflowMessage]s.
 *
 * The `wsBaseUrl` is derived by converting the Unseal API HTTPS base URL to `wss://`.
 * The access token comes from [MatrixClient.currentAccessToken].
 */
@Inject
class WorkflowProgressManager(
    private val matrixClient: MatrixClient,
    private val baseUrlResolver: ChatbotBaseUrlResolver,
    private val webSocketFactory: WorkflowWebSocketFactory,
) : WorkflowProgressProvider {
    override fun progressFlow(taskId: String): Flow<WorkflowMessage> = flow {
        val serverName = matrixClient.userIdServerName()
        val httpBaseUrl = baseUrlResolver.resolveUnsealApiBaseUrl(serverName)
        val wsBaseUrl = httpBaseUrl
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://")
        val accessToken = matrixClient.currentAccessToken().getOrNull().orEmpty()
        if (accessToken.isEmpty()) return@flow

        val socket = webSocketFactory.create(taskId, wsBaseUrl, accessToken)
        socket.use {
            socket.connect()
            while (currentCoroutineContext().isActive) {
                val raw = socket.pollMessage()
                if (raw.isNotEmpty()) {
                    val msg = parseMessage(raw)
                    emit(msg)
                    if (msg is WorkflowMessage.Completed || msg is WorkflowMessage.Error) break
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun parseMessage(raw: String): WorkflowMessage {
        return try {
            val json = JSONObject(raw)
            when (json.optString("type")) {
                "workflow_activity" -> WorkflowMessage.Progress(
                    stage = json.optString("stage"),
                    message = json.optString("message"),
                    isDone = json.optBoolean("is_done", false),
                    totalSlides = json.optInt("total_slides").takeIf { it > 0 },
                    totalSections = json.optInt("total_sections").takeIf { it > 0 },
                )
                "workflow_complete" -> WorkflowMessage.Completed(
                    workflowId = json.optString("workflow_id").takeIf { it.isNotEmpty() },
                )
                "workflow_error", "_error" -> WorkflowMessage.Error(
                    reason = json.optString("error").ifBlank { "Workflow error" },
                )
                else -> WorkflowMessage.Empty
            }
        } catch (_: Exception) {
            WorkflowMessage.Empty
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 50L
    }
}

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
import timber.log.Timber
import kotlin.coroutines.cancellation.CancellationException

/** Parsed message from a workflow WebSocket `pollMessage()` call. */
sealed interface WorkflowMessage {
    data object Empty : WorkflowMessage
    data class Progress(
        val stage: String,
        val message: String,
        val isDone: Boolean,
        val totalSlides: Int?,
        val totalSections: Int?,
        val slideHtml: String? = null,
    ) : WorkflowMessage
    data class Completed(
        val workflowId: String?,
        val slides: List<String> = emptyList(),
    ) : WorkflowMessage
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
interface WorkflowProgressProvider { fun progressFlow(taskId: String, wsBaseUrl: String? = null): Flow<WorkflowMessage>
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
    override fun progressFlow(taskId: String, wsBaseUrl: String?): Flow<WorkflowMessage> = flow {
        Timber.tag("WsDbg").d("progressFlow START taskId=%s wsBaseUrl=%s", taskId, wsBaseUrl)
        try {
            val resolvedWsBaseUrl = if (!wsBaseUrl.isNullOrBlank()) {
                wsBaseUrl
            } else {
                val serverName = matrixClient.userIdServerName()
                val httpBaseUrl = baseUrlResolver.resolveHomeserverBaseUrl(serverName)
                httpBaseUrl
                    .replaceFirst("https://", "wss://")
                    .replaceFirst("http://", "ws://")
            }
            Timber.tag("WsDbg").d("progressFlow resolvedUrl=%s taskId=%s", resolvedWsBaseUrl, taskId)
            val accessToken = matrixClient.currentAccessToken().getOrNull().orEmpty()
            if (accessToken.isEmpty()) {
                Timber.tag("WsDbg").w("progressFlow NO TOKEN taskId=%s", taskId)
                emit(WorkflowMessage.Error("No access token available"))
                return@flow
            }
            Timber.tag("WsDbg").d("progressFlow token=***%s taskId=%s", accessToken.takeLast(6), taskId)

            val socket = webSocketFactory.create(taskId, resolvedWsBaseUrl, accessToken)
            socket.use {
                Timber.tag("WsDbg").d("progressFlow CONNECTING taskId=%s", taskId)
                socket.connect()
                Timber.tag("WsDbg").d("progressFlow CONNECTED taskId=%s (polling...)", taskId)
                var pollCount = 0
                while (currentCoroutineContext().isActive) {
                    val raw = socket.pollMessage()
                    if (raw.isNotEmpty()) {
                        Timber.tag("WsDbg").d("progressFlow RAW taskId=%s msg=%s", taskId, raw.take(200))
                        val msg = parseMessage(raw)
                        Timber.tag("WsDbg").d("progressFlow PARSED taskId=%s type=%s", taskId, msg::class.simpleName)
                        emit(msg)
                        if (msg is WorkflowMessage.Completed || msg is WorkflowMessage.Error) break
                    } else {
                        pollCount++
                        if (pollCount % 200 == 0) {
                            Timber.tag("WsDbg").d("progressFlow POLLING taskId=%s polls=%d (no msg yet)", taskId, pollCount)
                        }
                    }
                    delay(POLL_INTERVAL_MS)
                }
                Timber.tag("WsDbg").d("progressFlow LOOP EXIT taskId=%s polls=%d", taskId, pollCount)
            }
        } catch (e: CancellationException) {
            // Composition left — let coroutine cancellation propagate normally.
            throw e
        } catch (e: Exception) {
            Timber.tag("WsDbg").e(e, "progressFlow ERROR taskId=%s", taskId)
            emit(WorkflowMessage.Error(e.message ?: "WebSocket connection failed"))
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
                    // iOS uses html_content; some servers may use html — try both
                    slideHtml = json.optJSONObject("slide_data")?.let { sd ->
                        sd.optString("html_content").ifEmpty { sd.optString("html") }.takeIf { it.isNotEmpty() }
                    },
                )
                "workflow_complete" -> WorkflowMessage.Completed(
                    workflowId = json.optString("workflow_id").takeIf { it.isNotEmpty() },
                    slides = parseSlidesFromJson(json),
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

    /** Extract slide HTML list from a workflow_complete JSON object.
     *  Supports: slides[].html_content  (iOS format) and slides[].html (alternative). */
    private fun parseSlidesFromJson(json: JSONObject): List<String> {
        val slidesArray = json.optJSONArray("slides") ?: return emptyList()
        val result = mutableListOf<String>()
        for (i in 0 until slidesArray.length()) {
            val slide = slidesArray.optJSONObject(i) ?: continue
            val html = slide.optString("html_content").ifEmpty { slide.optString("html") }
            if (html.isNotEmpty()) result.add(html)
        }
        return result
    }

    private companion object {
        const val POLL_INTERVAL_MS = 50L
    }
}

/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbot.impl

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import io.element.android.libraries.chatbot.api.ChatbotBaseUrlResolver
import io.element.android.libraries.chatbot.api.WorkflowSlideData
import io.element.android.libraries.chatbot.api.WorkflowSectionData
import io.element.android.libraries.chatbot.api.WorkflowWebSocketFactory
import io.element.android.libraries.chatbot.api.WorkflowWebSocketMessage
import io.element.android.libraries.matrix.api.MatrixClient
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import timber.log.Timber
import java.io.Closeable
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicBoolean

@ContributesBinding(AppScope::class, binding = binding<WorkflowWebSocketFactory>())
@Inject
class DefaultWorkflowWebSocketFactory(
    private val okHttpClientProvider: () -> OkHttpClient,
    private val tokenProvider: ChatbotAccessTokenProvider,
    private val baseUrlResolver: ChatbotBaseUrlResolver,
) : WorkflowWebSocketFactory {
    override suspend fun open(
        taskId: String,
        explicitWsUrl: String?,
        matrixClient: MatrixClient,
        onActivity: (WorkflowWebSocketMessage) -> Unit,
        onComplete: () -> Unit,
        onError: (Throwable) -> Unit,
    ): Closeable {
        val token = tokenProvider.accessToken(matrixClient)?.takeIf { it.isNotBlank() }
            ?: run {
                onError(IllegalStateException("No access token for workflow WebSocket"))
                return Closeable { }
            }

        val wsUrl = explicitWsUrl?.takeIf { it.isNotBlank() }
            ?: buildWsUrl(
                httpBase = baseUrlResolver.resolveHomeserverBaseUrl(matrixClient.userIdServerName()),
                taskId = taskId,
                token = token,
            )

        Timber.tag("WorkflowWS").d("Connecting task=%s url=%s", taskId, wsUrl)

        val client = okHttpClientProvider()
        val request = Request.Builder().url(wsUrl).build()
        val closed = AtomicBoolean(false)
        val listener = WorkflowWebSocketListener(taskId, onActivity, onComplete, onError)
        val ws = client.newWebSocket(request, listener)

        return Closeable {
            if (closed.compareAndSet(false, true)) {
                Timber.tag("WorkflowWS").d("Closing task=%s", taskId)
                ws.close(1000, "Client closed")
            }
        }
    }

    private fun buildWsUrl(httpBase: String, taskId: String, token: String): String {
        val wsBase = httpBase
            .trimEnd('/')
            .replace("https://", "wss://")
            .replace("http://", "ws://")
        val encodedToken = URLEncoder.encode(token, "UTF-8")
        return "$wsBase/chat/ws/workflow/$taskId?authorization=Bearer%20$encodedToken"
    }
}

private class WorkflowWebSocketListener(
    private val taskId: String,
    private val onActivity: (WorkflowWebSocketMessage) -> Unit,
    private val onComplete: () -> Unit,
    private val onError: (Throwable) -> Unit,
) : WebSocketListener() {
    private var completed = false

    override fun onOpen(webSocket: WebSocket, response: Response) {
        Timber.tag("WorkflowWS").d("Connected task=%s", taskId)
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        try {
            val json = JSONObject(text)
            val type = json.optString("type")
            Timber.tag("WorkflowWS").d("Message type=%s task=%s", type, taskId)
            when (type) {
                "workflow_connection_established" -> {
                    // Connection ready — no action needed
                }
                "history" -> {
                    val activities = json.optJSONArray("activities") ?: return
                    for (i in 0 until activities.length()) {
                        val item = activities.getJSONObject(i)
                        parseActivity(item)?.let { onActivity(it) }
                    }
                }
                "workflow_activity" -> {
                    parseActivity(json)?.let { onActivity(it) }
                }
                "workflow_complete", "workflow_completion", "completed", "result" -> {
                    if (!completed) {
                        completed = true
                        onComplete()
                    }
                }
                "workflow_error", "failed", "error" -> {
                    completed = true
                    val msg = json.optString("error").ifBlank { json.optString("message").ifBlank { "Workflow failed" } }
                    onError(RuntimeException(msg))
                }
                "ping" -> {
                    webSocket.send("""{"type":"pong"}""")
                }
                "pong" -> { /* heartbeat response, ignore */ }
                else -> {
                    // Fall back: treat as activity if it has known activity fields
                    if (json.has("stage") || json.has("slide_data") || json.has("section_data")) {
                        parseActivity(json)?.let { onActivity(it) }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.tag("WorkflowWS").w(e, "Failed to parse message task=%s", taskId)
        }
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        Timber.tag("WorkflowWS").d("Closing code=%d reason=%s task=%s", code, reason, taskId)
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        Timber.tag("WorkflowWS").d("Closed code=%d task=%s", code, taskId)
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        Timber.tag("WorkflowWS").w(t, "Failure task=%s", taskId)
        if (!completed) {
            onError(t)
        }
    }

    private fun parseActivity(json: JSONObject): WorkflowWebSocketMessage? {
        return runCatching {
            val slideJson = json.optJSONObject("slide_data")
            val slide = slideJson?.let {
                WorkflowSlideData(
                    slideNumber = it.optInt("slide_number", 0),
                    slideTitle = it.optString("slide_title"),
                    htmlContent = it.optString("html_content"),
                )
            }
            val sectionJson = json.optJSONObject("section_data")
            val section = sectionJson?.let {
                WorkflowSectionData(
                    sectionIndex = it.optInt("section_index", 0),
                    sectionTitle = it.optString("section_title"),
                    content = it.optString("content"),
                    totalSections = it.optInt("total_sections", 0),
                )
            }
            WorkflowWebSocketMessage(
                type = json.optString("type", "workflow_activity"),
                stage = json.optString("stage").takeIf { it.isNotBlank() },
                message = json.optString("message").takeIf { it.isNotBlank() },
                isDone = if (json.has("is_done")) json.getBoolean("is_done") else null,
                totalSlides = if (json.has("total_slides")) json.getInt("total_slides") else null,
                totalSections = if (json.has("total_sections")) json.getInt("total_sections") else null,
                slideData = slide,
                sectionData = section,
            )
        }.getOrNull()
    }
}

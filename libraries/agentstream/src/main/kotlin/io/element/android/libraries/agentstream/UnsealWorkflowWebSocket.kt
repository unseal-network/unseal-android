package io.element.android.libraries.agentstream

import io.element.android.libraries.agentstream.jni.UnsealWorkflowWebSocketNative
import java.io.Closeable

/**
 * WebSocket client for receiving real-time workflow progress from the Unseal backend.
 *
 * The native layer manages the TCP/TLS connection, reconnection (up to 5 attempts with
 * exponential backoff), heartbeat pings, and JSON parsing. Android just polls for messages.
 *
 * ## Usage
 * ```kotlin
 * val ws = UnsealWorkflowWebSocket(taskId, wsBaseUrl, accessToken)
 * ws.connect()
 *
 * // In a coroutine:
 * while (isActive) {
 *     val msg = ws.pollMessage()
 *     if (msg.isNotEmpty()) {
 *         val json = JSONObject(msg)
 *         when (json.getString("type")) {
 *             "workflow_connection_established" -> { /* connected */ }
 *             "workflow_activity" -> {
 *                 val stage = json.optString("stage")
 *                 val slideData = json.optJSONObject("slide_data")
 *                 val sectionData = json.optJSONObject("section_data")
 *                 // handle progress…
 *             }
 *             "workflow_complete" -> { ws.close(); break }
 *             "workflow_error", "_error" -> {
 *                 val error = json.optString("error")
 *                 ws.close(); break
 *             }
 *         }
 *     }
 *     delay(50)
 * }
 * ```
 *
 * ## Message types
 * | `type`                              | Extra fields                                      |
 * |-------------------------------------|---------------------------------------------------|
 * | `workflow_connection_established`   | `workflow_id?`                                    |
 * | `workflow_activity`                 | `stage?`, `message?`, `is_done?`, `total_slides?`,|
 * |                                     | `total_sections?`, `slide_data?`, `section_data?` |
 * | `workflow_complete`                 | `workflow_id?`                                    |
 * | `workflow_error`                    | `error`                                           |
 * | `_error`                            | `error` (SDK-level: connection failure, max retry)|
 *
 * @param taskId       Task ID returned in the SSE `tool_call_output`
 * @param wsBaseUrl    Base WebSocket URL, e.g. `wss://api.example.com`
 * @param accessToken  Bearer token (will be URL-encoded automatically)
 */
class UnsealWorkflowWebSocket(
    taskId: String,
    wsBaseUrl: String,
    accessToken: String,
) : Closeable {

    private val ptr: Long = UnsealWorkflowWebSocketNative.nativeCreateWorkflowSession(
        taskId, wsBaseUrl, accessToken
    )

    /** Start the WebSocket connection on a background thread. Returns immediately. */
    fun connect() = UnsealWorkflowWebSocketNative.nativeConnectWorkflow(ptr)

    /** Signal the background thread to stop. Returns immediately (non-blocking). */
    fun disconnect() = UnsealWorkflowWebSocketNative.nativeDisconnectWorkflow(ptr)

    /**
     * Pop the next parsed message from the queue.
     * Returns an empty string when the queue is empty — never null.
     * Call this in a tight polling loop (e.g. every 50 ms) from a background coroutine.
     */
    fun pollMessage(): String = UnsealWorkflowWebSocketNative.nativePollWorkflowMessage(ptr)

    /** Disconnect and free native memory. Safe to call multiple times. */
    override fun close() {
        disconnect()
        UnsealWorkflowWebSocketNative.nativeDestroyWorkflowSession(ptr)
    }
}

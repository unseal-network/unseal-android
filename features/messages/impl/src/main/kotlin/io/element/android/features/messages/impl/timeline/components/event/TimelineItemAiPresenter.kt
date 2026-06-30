/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import io.element.android.features.messages.impl.timeline.di.TimelineItemEventContentKey
import io.element.android.features.messages.impl.timeline.di.TimelineItemPresenterFactory
import io.element.android.features.messages.impl.timeline.factories.event.AiStreamContentCache
import io.element.android.features.messages.impl.timeline.factories.event.AiStreamHandleStore
import io.element.android.features.messages.impl.timeline.factories.event.AiSdkStreamReducer
import io.element.android.features.messages.impl.timeline.model.event.AiDataStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiPptWorkflowStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiToolStreamPart
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemAiContent
import io.element.android.libraries.agentstream.api.StreamRequest
import io.element.android.libraries.agentstream.api.StreamSnapshot
import io.element.android.libraries.agentstream.api.StreamSnapshotUpdateDecision
import io.element.android.libraries.agentstream.api.StreamSnapshotUpdatePolicy
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.di.RoomScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import timber.log.Timber

@BindingContainer
@ContributesTo(RoomScope::class)
interface TimelineItemAiPresenterModule {
    @Binds
    @IntoMap
    @TimelineItemEventContentKey(TimelineItemAiContent::class)
    fun bindTimelineItemAiPresenterFactory(factory: TimelineItemAiPresenter.Factory): TimelineItemPresenterFactory<*, *>

    @Binds
    fun bindWorkflowWebSocketFactory(impl: DefaultWorkflowWebSocketFactory): WorkflowWebSocketFactory

    @Binds
    fun bindWorkflowProgressProvider(impl: WorkflowProgressManager): WorkflowProgressProvider

    @Binds
    fun bindWorkflowTaskStore(impl: DefaultWorkflowTaskStore): WorkflowTaskStore
}

data class TimelineItemAiState(
    val content: TimelineItemAiContent,
    val workflowMessages: Map<String, WorkflowMessage> = emptyMap(),
    val workflowSlides: Map<String, List<String>> = emptyMap(),
    val miniAppDocumentLauncher: MiniAppDocumentLauncher? = null,
)

@AssistedInject
class TimelineItemAiPresenter(
    @Assisted private val content: TimelineItemAiContent,
    private val aiStreamHandleStore: AiStreamHandleStore,
    private val aiStreamContentCache: AiStreamContentCache,
    private val aiSdkStreamReducer: AiSdkStreamReducer,
    private val dispatchers: CoroutineDispatchers,
    private val workflowProgressManager: WorkflowProgressProvider,
    private val workflowTaskStore: WorkflowTaskStore,
    private val miniAppDocumentLauncher: MiniAppDocumentLauncher,
) : Presenter<TimelineItemAiState> {
    @AssistedFactory
    fun interface Factory : TimelineItemPresenterFactory<TimelineItemAiContent, TimelineItemAiState> {
        override fun create(content: TimelineItemAiContent): TimelineItemAiPresenter
    }

    @Composable
    override fun present(): TimelineItemAiState {
        val initialContent = content
        val streamId = initialContent.streamId
        val contentIdentity = streamId ?: initialContent.parts
        val cachedContent = remember(contentIdentity) {
            streamId?.let(aiStreamContentCache::get)?.withFallbackMetadata(initialContent)
        }
        var currentContent by remember(contentIdentity) {
            mutableStateOf(
                cachedContent ?: initialContent
            )
        }
        var workflowMessages by remember { mutableStateOf<Map<String, WorkflowMessage>>(emptyMap()) }
        var workflowSlides by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
        var persistedSlidesLoaded by remember { mutableStateOf(false) }

        LaunchedEffect(contentIdentity) {
            if (streamId == null) {
                currentContent = initialContent
                return@LaunchedEffect
            }
            val baseContent = cachedContent ?: initialContent
            if (baseContent.isTerminalRenderableStream(streamId)) {
                return@LaunchedEffect
            }
            loadCompletedCachedContent(
                streamId = streamId,
                fallbackContent = initialContent,
            )?.let { completedContent ->
                currentContent = completedContent
                return@LaunchedEffect
            }

            collectStreamContent(
                streamId = streamId,
                fallbackContent = initialContent,
                updateContent = { updated -> currentContent = updated },
            )
        }

        // Map of taskId → Pair(wsBaseUrl, taskType) for WebSocket tracking.
        // wsBaseUrl null falls back to resolveHomeserverBaseUrl in WorkflowProgressManager.
        val workflowTasks = remember(currentContent.visibleParts) {
            buildMap<String, Pair<String?, String>> {
                Timber.tag("WsDbg").d("workflowTasks BUILD visibleParts=%d streamId=%s", currentContent.visibleParts.size, currentContent.streamId)
                currentContent.visibleParts.forEach { part ->
                    Timber.tag("WsDbg").d("workflowTasks PART type=%s", part::class.simpleName)
                    when {
                        part is AiDataStreamPart && part.type == "data" && part.payload.isPptPlanningPayload() ->
                            extractPptTaskId(part.payload)?.let {
                                Timber.tag("WsDbg").d("workflowTasks ADD ppt_planning taskId=%s", it)
                                put(it, null to TASK_TYPE_PPT_PLANNING)
                            }
                        part is AiToolStreamPart && part.toolName == "generate_ppt_html_presentation" ->
                            part.output?.let { output ->
                                extractPptTaskId(output)?.let { taskId ->
                                    Timber.tag("WsDbg").d("workflowTasks ADD ppt_gen (tool) taskId=%s", taskId)
                                    put(taskId, extractWsBaseUrl(output) to TASK_TYPE_PPT_GENERATION)
                                }
                            }
                        part is AiPptWorkflowStreamPart -> {
                            Timber.tag("WsDbg").d("workflowTasks ADD ppt_gen (AiPpt) taskId=%s wsUrl=%s", part.taskId, part.websocketUrl)
                            put(part.taskId, part.websocketUrl?.let { extractWsBaseUrlFromString(it) } to TASK_TYPE_PPT_GENERATION)
                        }
                        else -> Unit
                    }
                }
            }.also { Timber.tag("WsDbg").d("workflowTasks RESULT size=%d keys=%s", it.size, it.keys) }
        }

        // Single effect keyed on task IDs (stable Set<String>).
        // Loads DB first (sequential), then connects WS only for tasks with no local data.
        // Keying on .keys instead of the full Map prevents reconnects when wsBaseUrl reference
        // changes on recomposition while task IDs stay the same.
        LaunchedEffect(workflowTasks.keys) {
            if (workflowTasks.isEmpty()) return@LaunchedEffect

            // Step 1: load persisted slides from DB before deciding on WS connection.
            if (!persistedSlidesLoaded) {
                val loaded = mutableMapOf<String, List<String>>()
                workflowTasks.keys.forEach { taskId ->
                    val record = workflowTaskStore.load(taskId)
                    if (record != null && record.slides.isNotEmpty()) {
                        // Only truncate when totalSlides > 1 AND slides exceed it — guards against
                        // the old WS-reconnect accumulation bug (e.g. 84 stored vs 12 expected).
                        // When totalSlides <= 1 it was likely stored wrong (new bug), so trust
                        // the actual slides count instead of truncating.
                        val clean = if (record.totalSlides > 1 && record.slides.size > record.totalSlides) {
                            record.slides.take(record.totalSlides)
                        } else {
                            record.slides
                        }
                        loaded[taskId] = clean
                        if (clean.size < record.slides.size) {
                            workflowTaskStore.saveSlides(taskId, record.taskType, clean, record.status, knownTotal = record.totalSlides)
                        }
                    }
                }
                if (loaded.isNotEmpty()) workflowSlides = workflowSlides + loaded
                persistedSlidesLoaded = true
            }

            // Step 2: connect WS only for tasks that have no local slides yet.
            workflowTasks.forEach { (taskId, wsBaseUrlAndType) ->
                val (wsBaseUrl, taskType) = wsBaseUrlAndType
                // Skip if DB already has slides — avoids re-accumulating on WS reconnect.
                if ((workflowSlides[taskId]?.size ?: 0) > 0) {
                    Timber.tag("WsDbg").d("skip WS taskId=%s (DB has %d slides)", taskId, workflowSlides[taskId]?.size)
                    return@forEach
                }
                launch {
                    Timber.tag("WsDbg").d("launch collect taskId=%s type=%s", taskId, taskType)
                    // Track WS slide count separately so re-delivery doesn't append to DB data.
                    var wsSlideCount = 0
                    workflowProgressManager.progressFlow(taskId, wsBaseUrl).collect { msg ->
                        if (msg !is WorkflowMessage.Empty) {
                            workflowMessages = workflowMessages + (taskId to msg)
                            when (msg) {
                                is WorkflowMessage.Progress -> {
                                    msg.slideHtml?.let { html ->
                                        // Use WS slide index as position to prevent duplicates
                                        // if the effect somehow restarts mid-stream.
                                        val current = workflowSlides[taskId] ?: emptyList()
                                        if (wsSlideCount < current.size) {
                                            // Already have this slide (from a prior partial run)
                                        } else {
                                            val updated = current + html
                                            workflowSlides = workflowSlides + (taskId to updated)
                                            // Pass totalSlides from WS so it's correctly persisted
                                            // from the very first partial save.
                                            workflowTaskStore.saveSlides(taskId, taskType, updated, "running", knownTotal = msg.totalSlides)
                                        }
                                        wsSlideCount++
                                        Timber.tag("WsDbg").d("slide PROGRESS taskId=%s wsIdx=%d total=%d wsTotal=%s", taskId, wsSlideCount, workflowSlides[taskId]?.size, msg.totalSlides)
                                    }
                                }
                                is WorkflowMessage.Completed -> {
                                    val finalSlides = msg.slides.ifEmpty { workflowSlides[taskId] ?: emptyList() }
                                    if (finalSlides.isNotEmpty()) {
                                        workflowSlides = workflowSlides + (taskId to finalSlides)
                                    }
                                    workflowTaskStore.saveSlides(taskId, taskType, finalSlides, "completed", knownTotal = finalSlides.size)
                                    Timber.tag("WsDbg").d("COMPLETED taskId=%s slides=%d", taskId, finalSlides.size)
                                }
                                is WorkflowMessage.Error -> {
                                    val current = workflowSlides[taskId] ?: emptyList()
                                    workflowTaskStore.saveSlides(taskId, taskType, current, "error")
                                    Timber.tag("WsDbg").d("ERROR taskId=%s reason=%s", taskId, msg.reason)
                                }
                                else -> Unit
                            }
                        }
                    }
                    Timber.tag("WsDbg").d("collect DONE taskId=%s", taskId)
                }
            }
        }

        return TimelineItemAiState(currentContent, workflowMessages, workflowSlides, miniAppDocumentLauncher)
    }

    private suspend fun loadCompletedCachedContent(
        streamId: String,
        fallbackContent: TimelineItemAiContent,
    ): TimelineItemAiContent? {
        return withContext(dispatchers.io) {
            aiStreamHandleStore.cachedCompletedSnapshot(streamId)?.let { snapshot ->
                aiSdkStreamReducer.mapSnapshot(
                    snapshot = snapshot,
                    isEdited = fallbackContent.isEdited,
                    sender = fallbackContent.sender,
                ).also(aiStreamContentCache::put)
            }
        }
    }

    private suspend fun collectStreamContent(
        streamId: String,
        fallbackContent: TimelineItemAiContent,
        updateContent: suspend (TimelineItemAiContent) -> Unit,
    ) {
        // Map snapshots (JSON → parts) off the main thread; only the state write hops to main.
        withContext(dispatchers.io) {
            val snapshots = Channel<StreamSnapshot>(Channel.UNLIMITED)
            val updatePolicy = StreamSnapshotUpdatePolicy(
                // iOS writes every text-delta into the observed message model, which gives the
                // visible type-on effect. Keep Android bounded for markdown parse cost, but flush
                // streaming text often enough that it does not appear in large 500ms batches.
                patchCoalesceMs = STREAMING_TEXT_PATCH_COALESCE_MS,
            )

            suspend fun emit(snapshot: StreamSnapshot) {
                val updated = aiSdkStreamReducer.mapSnapshot(
                    snapshot = snapshot,
                    isEdited = fallbackContent.isEdited,
                    sender = fallbackContent.sender,
                )
                aiStreamContentCache.put(updated)
                withContext(dispatchers.main) {
                    updateContent(updated)
                }
            }

            suspend fun flushPendingPatch() {
                updatePolicy.flushPending(System.currentTimeMillis())?.let { snapshot ->
                    emit(snapshot)
                }
            }

            val binding = aiStreamHandleStore.bind(
                request = StreamRequest(
                    streamId = streamId,
                    sender = fallbackContent.sender.orEmpty(),
                    roomId = fallbackContent.roomId.orEmpty(),
                    eventId = fallbackContent.eventId.orEmpty(),
                    includeRawEvents = false,
                ),
            ) { snapshot ->
                snapshots.trySend(snapshot)
            }
            try {
                while (true) {
                    val timeoutMs = updatePolicy.nextFlushDelayMs(System.currentTimeMillis())
                    val snapshot = if (timeoutMs == null) {
                        snapshots.receiveCatching().getOrNull()
                    } else {
                        withTimeoutOrNull(timeoutMs) {
                            snapshots.receiveCatching().getOrNull()
                        }
                    }
                    if (snapshot == null) {
                        flushPendingPatch()
                        continue
                    }
                    when (val decision = updatePolicy.accept(snapshot, System.currentTimeMillis())) {
                        is StreamSnapshotUpdateDecision.Emit -> {
                            emit(decision.snapshot)
                            if (decision.snapshot.isTerminal) {
                                break
                            }
                        }
                        StreamSnapshotUpdateDecision.Pending,
                        StreamSnapshotUpdateDecision.Skip -> Unit
                    }
                }
            } finally {
                binding.close()
                snapshots.close()
            }
        }
    }

    private companion object {
        const val STREAMING_TEXT_PATCH_COALESCE_MS = 120L
        const val TASK_TYPE_PPT_GENERATION = "ppt_generation"
        const val TASK_TYPE_PPT_PLANNING = "ppt_planning"
    }
}

private fun String.isPptPlanningPayload(): Boolean {
    return try {
        JSONObject(this).optString("content_type") == "ppt_planning"
    } catch (_: Exception) {
        false
    }
}

private fun extractPptTaskId(payload: String): String? {
    return try {
        JSONObject(payload).optString("task_id").takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }
}

/** Extracts scheme+host from a full websocket_url, e.g. "wss://keepsecret.io/path" → "wss://keepsecret.io". */
private fun extractWsBaseUrl(payload: String): String? {
    return try {
        val url = JSONObject(payload).optString("websocket_url").takeIf { it.isNotEmpty() } ?: return null
        extractWsBaseUrlFromString(url)
    } catch (_: Exception) {
        null
    }
}

private fun extractWsBaseUrlFromString(url: String): String? {
    val schemeEnd = url.indexOf("://")
    if (schemeEnd < 0) return null
    val hostStart = schemeEnd + 3
    val hostEnd = url.indexOf('/', hostStart).let { if (it < 0) url.length else it }
    return url.substring(0, hostEnd)
}

private fun TimelineItemAiContent.isTerminalRenderableStream(streamId: String): Boolean {
    // Only skip re-loading when the full snapshot has been mapped into parts.
    // hasRichParts also fires on toolCalls from the Matrix event JSON, which does NOT include
    // visibleParts — checking parts.isNotEmpty() ensures the snapshot was actually hydrated.
    return this.streamId == streamId &&
        isTerminal &&
        parts.isNotEmpty()
}

private fun TimelineItemAiContent.withFallbackMetadata(fallback: TimelineItemAiContent): TimelineItemAiContent {
    return copy(
        isEdited = fallback.isEdited,
        sender = sender ?: fallback.sender,
        roomId = roomId ?: fallback.roomId,
        eventId = eventId ?: fallback.eventId,
    )
}

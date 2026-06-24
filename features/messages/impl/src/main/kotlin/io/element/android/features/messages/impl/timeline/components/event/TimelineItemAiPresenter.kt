/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import io.element.android.features.messages.impl.timeline.model.event.AiToolStreamPart
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemAiContent
import io.element.android.libraries.agentstream.api.StreamRequest
import io.element.android.libraries.agentstream.api.StreamSnapshot
import io.element.android.libraries.agentstream.api.StreamSnapshotUpdateDecision
import io.element.android.libraries.agentstream.api.StreamSnapshotUpdatePolicy
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.di.RoomScope
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONException
import org.json.JSONObject

@BindingContainer
@ContributesTo(RoomScope::class)
interface TimelineItemAiPresenterModule {
    @Binds
    @IntoMap
    @TimelineItemEventContentKey(TimelineItemAiContent::class)
    fun bindTimelineItemAiPresenterFactory(factory: TimelineItemAiPresenter.Factory): TimelineItemPresenterFactory<*, *>
}

data class TimelineItemAiState(
    val content: TimelineItemAiContent,
    /** Progress updates from WebSocket connections, keyed by task_id. */
    val workflowProgress: ImmutableMap<String, WorkflowTaskProgress> = persistentMapOf(),
)

@AssistedInject
class TimelineItemAiPresenter(
    @Assisted private val content: TimelineItemAiContent,
    private val aiStreamHandleStore: AiStreamHandleStore,
    private val aiStreamContentCache: AiStreamContentCache,
    private val aiSdkStreamReducer: AiSdkStreamReducer,
    private val workflowProgressManager: WorkflowProgressManager,
    private val dispatchers: CoroutineDispatchers,
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

        // Start WebSocket tracking for any workflow task_ids found in parts.
        val workflowTaskIds = remember(currentContent.parts) {
            currentContent.parts.extractWorkflowTaskIds()
        }
        LaunchedEffect(workflowTaskIds) {
            for ((taskId, wsUrl) in workflowTaskIds) {
                workflowProgressManager.ensureTracking(
                    taskId = taskId,
                    wsUrl = wsUrl,
                    scope = this,
                )
            }
        }

        // Collect live progress for all known task_ids.
        val workflowProgress = workflowTaskIds
            .map { (taskId, _) -> workflowProgressManager.progressFlow(taskId).collectAsState() }
            .associate { state -> state.value.taskId to state.value }
            .toImmutableMap()

        return TimelineItemAiState(
            content = currentContent,
            workflowProgress = workflowProgress,
        )
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
    }
}

/** Content types that trigger a WebSocket workflow progress connection when task_id is present. */
private val WORKFLOW_DATA_CONTENT_TYPES = setOf(
    "ppt_planning",
    "ppt_generation_workflow_activity",
    "writing_planning",
    "writing_generation_workflow_activity",
    "deep_research",
    "research",
    "professor_review",
)

/** Tool output task_types that indicate a workflow WebSocket should be opened. */
private val WORKFLOW_TOOL_TASK_TYPES = setOf(
    "ppt_generation",
    "writing_generation",
    "deep_research",
    "professor_review",
)

/**
 * Scans all parts for workflow task IDs that need WebSocket progress tracking.
 * Returns a list of Pair(taskId, optionalExplicitWsUrl).
 */
private fun Iterable<io.element.android.features.messages.impl.timeline.model.event.AiStreamPart>.extractWorkflowTaskIds(): List<Pair<String, String?>> {
    val seen = mutableSetOf<String>()
    return buildList {
        for (part in this@extractWorkflowTaskIds) {
            when (part) {
                is AiDataStreamPart -> {
                    if (part.type != "data-json-block") continue
                    val json = tryParseJson(part.payload) ?: continue
                    val contentType = json.optString("content_type").takeIf { it.isNotBlank() } ?: continue
                    if (contentType !in WORKFLOW_DATA_CONTENT_TYPES) continue
                    val taskId = json.optString("task_id").takeIf { it.isNotBlank() } ?: continue
                    if (seen.add(taskId)) add(taskId to null)
                }
                is AiToolStreamPart -> {
                    val output = part.output?.takeIf { it.isNotBlank() } ?: continue
                    val json = tryParseJson(output) ?: continue
                    val taskType = json.optString("task_type").takeIf { it.isNotBlank() } ?: continue
                    if (taskType !in WORKFLOW_TOOL_TASK_TYPES) continue
                    val taskId = json.optString("task_id").takeIf { it.isNotBlank() } ?: continue
                    val wsUrl = json.optString("websocket_url").takeIf { it.isNotBlank() }
                    if (seen.add(taskId)) add(taskId to wsUrl)
                }
                else -> continue
            }
        }
    }
}

private fun tryParseJson(s: String): JSONObject? {
    return try {
        JSONObject(s)
    } catch (_: JSONException) {
        null
    }
}

private fun TimelineItemAiContent.isTerminalRenderableStream(streamId: String): Boolean {
    return this.streamId == streamId &&
        isTerminal &&
        (hasRichParts || body.isNotBlank())
}

private fun TimelineItemAiContent.withFallbackMetadata(fallback: TimelineItemAiContent): TimelineItemAiContent {
    return copy(
        isEdited = fallback.isEdited,
        sender = sender ?: fallback.sender,
        roomId = roomId ?: fallback.roomId,
        eventId = eventId ?: fallback.eventId,
    )
}

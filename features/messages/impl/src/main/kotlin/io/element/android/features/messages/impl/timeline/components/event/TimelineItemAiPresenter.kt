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
import io.element.android.features.messages.impl.timeline.factories.event.AiSdkStreamReducer
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemAiContent
import io.element.android.libraries.agentstream.api.AgentStreamClient
import io.element.android.libraries.agentstream.api.StreamPart
import io.element.android.libraries.agentstream.api.StreamRequest
import io.element.android.libraries.agentstream.api.StreamSnapshot
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.di.RoomScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.max

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
)

@AssistedInject
class TimelineItemAiPresenter(
    @Assisted private val content: TimelineItemAiContent,
    private val agentStreamClient: AgentStreamClient,
    private val aiSdkStreamReducer: AiSdkStreamReducer,
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
        var currentContent by remember(streamId, initialContent.parts) {
            mutableStateOf(initialContent)
        }

        LaunchedEffect(streamId, initialContent.parts) {
            if (streamId == null) {
                currentContent = initialContent
                return@LaunchedEffect
            }

            if (initialContent.parts.isNotEmpty()) {
                currentContent = initialContent
                return@LaunchedEffect
            }

            collectStreamContent(
                streamId = streamId,
                fallbackContent = initialContent,
                updateContent = { updated -> currentContent = updated },
            )
        }

        return TimelineItemAiState(currentContent)
    }

    private suspend fun collectStreamContent(
        streamId: String,
        fallbackContent: TimelineItemAiContent,
        updateContent: suspend (TimelineItemAiContent) -> Unit,
    ) {
        val handle = agentStreamClient.getStream(
            StreamRequest(
                streamId = streamId,
                sender = fallbackContent.sender.orEmpty(),
                roomId = "",
                eventId = "",
                includeRawEvents = false,
            )
        )
        val snapshots = Channel<StreamSnapshot>(Channel.UNLIMITED)
        var lastEmittedSnapshot: StreamSnapshot? = null
        var pendingPatchSnapshot: StreamSnapshot? = null
        var lastPatchEmittedAtMs = 0L

        suspend fun emit(snapshot: StreamSnapshot) {
            lastEmittedSnapshot = snapshot
            lastPatchEmittedAtMs = System.currentTimeMillis()
            val updated = aiSdkStreamReducer.mapSnapshot(
                snapshot = snapshot,
                isEdited = fallbackContent.isEdited,
                sender = fallbackContent.sender,
            )
            withContext(dispatchers.main) {
                updateContent(updated)
            }
        }

        suspend fun flushPendingPatch() {
            val snapshot = pendingPatchSnapshot ?: return
            pendingPatchSnapshot = null
            if (!snapshot.hasSameContentAs(lastEmittedSnapshot)) {
                emit(snapshot)
            }
        }

        suspend fun handleSnapshot(snapshot: StreamSnapshot): Boolean {
            if (snapshot.parts.isEmpty() && !snapshot.isTerminal && snapshot.error == null) {
                return false
            }
            if (snapshot.hasSameContentAs(lastEmittedSnapshot)) {
                return false
            }
            val hasStateChange = !snapshot.hasSameStateAs(lastEmittedSnapshot)
            if (snapshot.isTerminal || hasStateChange) {
                pendingPatchSnapshot = null
                emit(snapshot)
                return snapshot.isTerminal
            }
            pendingPatchSnapshot = snapshot
            return false
        }

        val subscription = handle.subscribe { snapshot ->
            snapshots.trySend(snapshot)
        }
        try {
            while (true) {
                val timeoutMs = pendingPatchSnapshot?.let {
                    max(0L, STREAM_PATCH_UPDATE_COALESCE_MS - (System.currentTimeMillis() - lastPatchEmittedAtMs))
                }
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
                if (handleSnapshot(snapshot)) {
                    break
                }
            }
        } finally {
            subscription.cancel()
            snapshots.close()
        }
    }

    private companion object {
        const val STREAM_PATCH_UPDATE_COALESCE_MS = 500L
    }
}

private fun StreamSnapshot.hasSameStateAs(other: StreamSnapshot?): Boolean {
    return stateSignature() == other?.stateSignature()
}

private fun StreamSnapshot.hasSameContentAs(other: StreamSnapshot?): Boolean {
    return contentSignature() == other?.contentSignature()
}

private fun StreamSnapshot.stateSignature(): SnapshotStateSignature {
    return SnapshotStateSignature(
        status = status.name,
        parts = parts.map { part ->
            PartStateSignature(
                id = part.id,
                type = part.type,
                state = part.state,
                toolName = (part as? StreamPart.Tool)?.toolName,
                toolCallId = (part as? StreamPart.Tool)?.toolCallId,
            )
        },
        error = error?.message,
    )
}

private fun StreamSnapshot.contentSignature(): SnapshotContentSignature {
    return SnapshotContentSignature(
        status = status.name,
        parts = parts.map { part ->
            PartContentSignature(
                id = part.id,
                type = part.type,
                state = part.state,
                content = when (part) {
                    is StreamPart.Text -> part.text
                    is StreamPart.Reasoning -> part.text
                    is StreamPart.Tool -> listOf(part.input, part.rawInput, part.output, part.error).joinToString()
                    is StreamPart.Data -> part.data.toString()
                    is StreamPart.Source -> listOf(part.sourceType, part.title, part.url, part.payload).joinToString()
                    is StreamPart.File -> listOf(part.mediaType, part.filename, part.url, part.data).joinToString()
                    is StreamPart.Step -> listOf(part.title, part.payload).joinToString()
                    is StreamPart.Error -> part.error.toString()
                    is StreamPart.Custom -> part.payload.toString()
                },
            )
        },
        error = error?.message,
    )
}

private data class SnapshotStateSignature(
    val status: String,
    val parts: List<PartStateSignature>,
    val error: String?,
)

private data class PartStateSignature(
    val id: String,
    val type: String,
    val state: String?,
    val toolName: String?,
    val toolCallId: String?,
)

private data class SnapshotContentSignature(
    val status: String,
    val parts: List<PartContentSignature>,
    val error: String?,
)

private data class PartContentSignature(
    val id: String,
    val type: String,
    val state: String?,
    val content: String,
)

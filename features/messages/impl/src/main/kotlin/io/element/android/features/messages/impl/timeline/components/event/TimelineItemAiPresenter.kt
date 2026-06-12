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
import io.element.android.libraries.agentstream.api.StreamRequest
import io.element.android.libraries.agentstream.api.StreamSnapshot
import io.element.android.libraries.agentstream.api.StreamSnapshotUpdateDecision
import io.element.android.libraries.agentstream.api.StreamSnapshotUpdatePolicy
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.di.RoomScope
import kotlinx.coroutines.channels.Channel
import timber.log.Timber
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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
        // Map snapshots (JSON → parts) off the main thread; only the state write hops to main.
        withContext(dispatchers.io) {
        val handle = agentStreamClient.getStream(
            StreamRequest(
                streamId = streamId,
                sender = fallbackContent.sender.orEmpty(),
                roomId = "",
                eventId = "",
                includeRawEvents = false,
            )
        )
        Timber.tag(DBG).d("getStream stream=%s initialStatus=%s initialParts=%d", streamId, handle.snapshot().status, handle.snapshot().parts.size)
        val snapshots = Channel<StreamSnapshot>(Channel.UNLIMITED)
        val updatePolicy = StreamSnapshotUpdatePolicy()

        suspend fun emit(snapshot: StreamSnapshot) {
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
            updatePolicy.flushPending(System.currentTimeMillis())?.let { snapshot ->
                emit(snapshot)
            }
        }

        val subscription = handle.subscribe { snapshot ->
            Timber.tag(DBG).d("recv stream=%s status=%s parts=%d", streamId, snapshot.status, snapshot.parts.size)
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
                        Timber.tag(DBG).d("EMIT stream=%s status=%s parts=%d terminal=%s", streamId, decision.snapshot.status, decision.snapshot.parts.size, decision.snapshot.isTerminal)
                        emit(decision.snapshot)
                        if (decision.snapshot.isTerminal) {
                            Timber.tag(DBG).d("TERMINAL break stream=%s status=%s", streamId, decision.snapshot.status)
                            break
                        }
                    }
                    StreamSnapshotUpdateDecision.Pending,
                    StreamSnapshotUpdateDecision.Skip -> Timber.tag(DBG).d("%s stream=%s status=%s", decision::class.simpleName, streamId, snapshot.status)
                }
            }
        } finally {
            Timber.tag(DBG).d("collect END stream=%s", streamId)
            subscription.cancel()
            snapshots.close()
        }
        }
    }

    private companion object {
        const val DBG = "AiStreamDbg"
    }
}

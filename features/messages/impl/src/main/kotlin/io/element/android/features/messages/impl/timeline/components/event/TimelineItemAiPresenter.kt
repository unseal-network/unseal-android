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
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.di.RoomScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext

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
        val snapshots = Channel<StreamSnapshot>(Channel.CONFLATED)
        var lastEmittedAtMs = 0L

        suspend fun emit(snapshot: StreamSnapshot, force: Boolean = false) {
            val now = System.currentTimeMillis()
            if (!force && now - lastEmittedAtMs < STREAM_UPDATE_THROTTLE_MS) {
                return
            }
            lastEmittedAtMs = now
            val updated = aiSdkStreamReducer.mapSnapshot(
                snapshot = snapshot,
                isEdited = fallbackContent.isEdited,
                sender = fallbackContent.sender,
            )
            withContext(dispatchers.main) {
                updateContent(updated)
            }
        }

        val subscription = handle.subscribe { snapshot ->
            snapshots.trySend(snapshot)
        }
        try {
            for (snapshot in snapshots) {
                emit(snapshot, force = snapshot.isTerminal)
                if (snapshot.isTerminal) {
                    break
                }
            }
        } finally {
            subscription.cancel()
            snapshots.close()
        }
    }

    private companion object {
        const val STREAM_UPDATE_THROTTLE_MS = 80L
    }
}

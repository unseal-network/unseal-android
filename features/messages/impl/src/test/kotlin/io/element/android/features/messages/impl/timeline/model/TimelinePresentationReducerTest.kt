/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.model

import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.roomkey.RoomKeyRecoveryDisplayStage
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemAiContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemEncryptedContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemGameContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemRoomKeyRecovery
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemRoomKeyRecoveryState
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemRtcNotificationContent
import io.element.android.features.messages.impl.timeline.model.event.aStaticLocationMode
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemAudioContent
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemFileContent
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemLocationContent
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemStateEventContent
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemTextContent
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemVoiceContent
import io.element.android.features.messages.impl.timeline.model.event.RtcNotificationState
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.encryption.roomkey.RoomKeyRecoveryRequest
import io.element.android.libraries.matrix.api.notification.CallIntent
import io.element.android.libraries.matrix.api.timeline.item.event.UnableToDecryptContent
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test

class TimelinePresentationReducerTest {
    @Test
    fun `reduce renders AI stream as standalone content with sender in direct room`() {
        val model = TimelinePresentationReducer.reduce(
            content = aTimelineItemAiContent(),
            groupPosition = TimelineItemGroupPosition.None,
        )

        assertThat(model.bubblePolicy).isEqualTo(TimelineBubblePolicy.Standalone)
        assertThat(model.contentKind).isEqualTo(TimelineContentKind.AiStream)
        assertThat(model.contentPaddingPolicy).isEqualTo(TimelineContentPaddingPolicy.Media)
        assertThat(model.editedPolicy).isEqualTo(TimelineEditedPolicy.Hide)
        assertThat(model.showSenderInformation).isTrue()
    }

    @Test
    fun `reduce renders regular direct-room text as standalone content with sender row`() {
        val model = TimelinePresentationReducer.reduce(
            content = aTimelineItemTextContent(),
            groupPosition = TimelineItemGroupPosition.None,
        )

        assertThat(model.bubblePolicy).isEqualTo(TimelineBubblePolicy.Standalone)
        assertThat(model.contentKind).isEqualTo(TimelineContentKind.PlainText)
        assertThat(model.contentPaddingPolicy).isEqualTo(TimelineContentPaddingPolicy.Textual)
        assertThat(model.editedPolicy).isEqualTo(TimelineEditedPolicy.ShowWhenEdited)
        assertThat(model.showSenderInformation).isTrue()
    }

    @Test
    fun `reduce renders grouped plain-text message without a repeated sender row`() {
        val model = TimelinePresentationReducer.reduce(
            content = aTimelineItemTextContent(),
            groupPosition = TimelineItemGroupPosition.Middle,
        )

        assertThat(model.bubblePolicy).isEqualTo(TimelineBubblePolicy.Standalone)
        assertThat(model.contentKind).isEqualTo(TimelineContentKind.PlainText)
        assertThat(model.contentPaddingPolicy).isEqualTo(TimelineContentPaddingPolicy.Textual)
        assertThat(model.editedPolicy).isEqualTo(TimelineEditedPolicy.ShowWhenEdited)
        assertThat(model.showSenderInformation).isFalse()
    }

    @Test
    fun `reduce renders room key recovery as standalone content`() {
        val model = TimelinePresentationReducer.reduce(
            content = aTimelineItemEncryptedRecoveryContent(),
            groupPosition = TimelineItemGroupPosition.None,
        )

        assertThat(model.bubblePolicy).isEqualTo(TimelineBubblePolicy.Standalone)
        assertThat(model.contentKind).isEqualTo(TimelineContentKind.RoomKeyRecovery)
        assertThat(model.contentPaddingPolicy).isEqualTo(TimelineContentPaddingPolicy.Media)
        assertThat(model.editedPolicy).isEqualTo(TimelineEditedPolicy.ShowWhenEdited)
        assertThat(model.showSenderInformation).isTrue()
    }

    @Test
    fun `reduce keeps ordinary encrypted events in the standard bubble`() {
        val model = TimelinePresentationReducer.reduce(
            content = TimelineItemEncryptedContent(data = UnableToDecryptContent.Data.Unknown),
            groupPosition = TimelineItemGroupPosition.None,
        )

        assertThat(model.bubblePolicy).isEqualTo(TimelineBubblePolicy.StandardBubble)
        assertThat(model.contentKind).isEqualTo(TimelineContentKind.RichEvent)
        assertThat(model.contentPaddingPolicy).isEqualTo(TimelineContentPaddingPolicy.Textual)
    }

    @Test
    fun `reduce renders game invites as standalone card content`() {
        val model = TimelinePresentationReducer.reduce(
            content = aTimelineItemGameContent(),
            groupPosition = TimelineItemGroupPosition.None,
        )

        assertThat(model.bubblePolicy).isEqualTo(TimelineBubblePolicy.Standalone)
        assertThat(model.contentKind).isEqualTo(TimelineContentKind.Media)
        assertThat(model.contentPaddingPolicy).isEqualTo(TimelineContentPaddingPolicy.Media)
    }

    @Test
    fun `reduce renders attachment cards as standalone content`() {
        listOf(
            aTimelineItemFileContent(),
            aTimelineItemAudioContent(),
            aTimelineItemLocationContent(mode = aStaticLocationMode()),
        ).forEach { content ->
            val model = TimelinePresentationReducer.reduce(
                content = content,
                groupPosition = TimelineItemGroupPosition.None,
            )

            assertThat(model.bubblePolicy).isEqualTo(TimelineBubblePolicy.Standalone)
            assertThat(model.contentKind).isEqualTo(TimelineContentKind.Media)
            assertThat(model.contentPaddingPolicy).isEqualTo(TimelineContentPaddingPolicy.Media)
        }
    }

    @Test
    fun `reduce renders voice messages as standalone media content`() {
        val model = TimelinePresentationReducer.reduce(
            content = aTimelineItemVoiceContent(),
            groupPosition = TimelineItemGroupPosition.None,
        )

        assertThat(model.bubblePolicy).isEqualTo(TimelineBubblePolicy.Standalone)
        assertThat(model.contentKind).isEqualTo(TimelineContentKind.Media)
        assertThat(model.contentPaddingPolicy).isEqualTo(TimelineContentPaddingPolicy.Media)
    }

    @Test
    fun `reduce keeps room key recovery standalone`() {
        val model = TimelinePresentationReducer.reduce(
            content = aTimelineItemEncryptedRecoveryContent(),
            groupPosition = TimelineItemGroupPosition.None,
        )

        assertThat(model.bubblePolicy).isEqualTo(TimelineBubblePolicy.Standalone)
        assertThat(model.contentKind).isEqualTo(TimelineContentKind.RoomKeyRecovery)
        assertThat(model.contentPaddingPolicy).isEqualTo(TimelineContentPaddingPolicy.Media)
    }

    @Test
    fun `reduce renders state events through the plain text timeline layout`() {
        val model = TimelinePresentationReducer.reduce(
            content = aTimelineItemStateEventContent(),
            groupPosition = TimelineItemGroupPosition.None,
        )

        assertThat(model.bubblePolicy).isEqualTo(TimelineBubblePolicy.Standalone)
        assertThat(model.contentKind).isEqualTo(TimelineContentKind.PlainText)
        assertThat(model.contentPaddingPolicy).isEqualTo(TimelineContentPaddingPolicy.Textual)
    }

    @Test
    fun `reduce renders call notifications as standalone media content`() {
        val model = TimelinePresentationReducer.reduce(
            content = TimelineItemRtcNotificationContent(
                callIntent = CallIntent.VIDEO,
                state = RtcNotificationState.Started,
            ),
            groupPosition = TimelineItemGroupPosition.None,
        )

        assertThat(model.bubblePolicy).isEqualTo(TimelineBubblePolicy.Standalone)
        assertThat(model.contentKind).isEqualTo(TimelineContentKind.Media)
        assertThat(model.contentPaddingPolicy).isEqualTo(TimelineContentPaddingPolicy.Media)
    }

    @Test
    fun `edited policy still shows edited for inline AI content without stream parts`() {
        val model = TimelinePresentationReducer.reduce(
            content = aTimelineItemAiContent(streamId = null),
            groupPosition = TimelineItemGroupPosition.None,
        )

        assertThat(model.editedPolicy).isEqualTo(TimelineEditedPolicy.ShowWhenEdited)
    }

    private fun aTimelineItemAiContent(
        streamId: String? = "stream-id",
    ): TimelineItemAiContent {
        return TimelineItemAiContent(
            body = "",
            isEdited = false,
            isStreaming = false,
            isTerminal = true,
            streamId = streamId,
            thinkingSteps = persistentListOf(),
            toolCalls = persistentListOf(),
            sources = persistentListOf(),
            quickActions = persistentListOf(),
        )
    }

    private fun aTimelineItemEncryptedRecoveryContent(): TimelineItemEncryptedContent {
        return TimelineItemEncryptedContent(
            data = UnableToDecryptContent.Data.Unknown,
            recovery = TimelineItemRoomKeyRecovery(
                request = RoomKeyRecoveryRequest(
                    roomId = RoomId("!room:example.org"),
                    senderUserId = UserId("@alice:example.org"),
                    senderDeviceId = "ALICEDEVICE",
                    senderKey = "senderKey",
                    sessionId = "sessionId",
                    ciphertext = "ciphertext",
                ),
                eventCount = 1,
                state = TimelineItemRoomKeyRecoveryState.Active,
                planStages = listOf(RoomKeyRecoveryDisplayStage.Backup, RoomKeyRecoveryDisplayStage.Sender),
                currentStage = RoomKeyRecoveryDisplayStage.Backup,
            )
        )
    }

    private fun aTimelineItemGameContent(): TimelineItemGameContent {
        return TimelineItemGameContent(
            gameName = "Wolf-New",
            gameBrief = "Wolf-NewWolf-NewWolf-New",
            resolvedIconUrl = null,
            homeserverHost = null,
            gameRoomId = "game-room",
            gameId = 1,
            remoteUrl = null,
            creatorUserId = "@alice:example.com",
            fallbackBody = "Start game",
        )
    }
}

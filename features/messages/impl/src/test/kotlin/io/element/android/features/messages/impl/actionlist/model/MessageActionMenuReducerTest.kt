/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.actionlist.model

import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.actionlist.ActionListState
import io.element.android.features.messages.impl.crypto.sendfailure.VerifiedUserSendFailure
import io.element.android.features.messages.impl.timeline.aTimelineItemEvent
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test

class MessageActionMenuReducerTest {
    @Test
    fun `reduce groups actions in stable iOS-style sections`() {
        val target = ActionListState.Target.Success(
            event = aTimelineItemEvent(),
            sentTimeFull = "June 14, 2026",
            displayEmojiReactions = true,
            recentEmojis = persistentListOf("👍", "❤️"),
            verifiedUserSendFailure = VerifiedUserSendFailure.None,
            actions = persistentListOf(
                TimelineItemAction.CopyText,
                TimelineItemAction.SelectText,
                TimelineItemAction.Redact,
                TimelineItemAction.Reply,
                TimelineItemAction.Edit,
                TimelineItemAction.Pin,
                TimelineItemAction.ViewSource,
            ),
        )

        val model = MessageActionMenuReducer.reduce(target)

        assertThat(model.sections.map { it.kind }).containsExactly(
            MessageActionMenuSection.Primary,
            MessageActionMenuSection.Edit,
            MessageActionMenuSection.Copy,
            MessageActionMenuSection.Pin,
            MessageActionMenuSection.Debug,
            MessageActionMenuSection.Danger,
        ).inOrder()
        assertThat(model.sections.flatMap { it.entries }.map { it.action }).containsExactly(
            TimelineItemAction.Reply,
            TimelineItemAction.Edit,
            TimelineItemAction.SelectText,
            TimelineItemAction.CopyText,
            TimelineItemAction.Pin,
            TimelineItemAction.ViewSource,
            TimelineItemAction.Redact,
        ).inOrder()
    }

    @Test
    fun `reduce preserves menu metadata`() {
        val target = ActionListState.Target.Success(
            event = aTimelineItemEvent(),
            sentTimeFull = "June 14, 2026",
            displayEmojiReactions = true,
            recentEmojis = persistentListOf("👍", "❤️"),
            verifiedUserSendFailure = VerifiedUserSendFailure.UnsignedDevice.FromYou,
            actions = persistentListOf(TimelineItemAction.ReportContent),
        )

        val model = MessageActionMenuReducer.reduce(target)

        assertThat(model.sentTimeFull).isEqualTo("June 14, 2026")
        assertThat(model.displayEmojiReactions).isTrue()
        assertThat(model.recentEmojis).containsExactly("👍", "❤️").inOrder()
        assertThat(model.verifiedUserSendFailure).isEqualTo(VerifiedUserSendFailure.UnsignedDevice.FromYou)
        assertThat(model.sections.single().entries.single().destructive).isTrue()
    }

    @Test
    fun `reduce exposes iOS actions that are intentionally unavailable on Android`() {
        val target = ActionListState.Target.Success(
            event = aTimelineItemEvent(),
            sentTimeFull = "June 14, 2026",
            displayEmojiReactions = false,
            recentEmojis = persistentListOf(),
            verifiedUserSendFailure = VerifiedUserSendFailure.None,
            actions = persistentListOf(TimelineItemAction.CopyText),
        )

        val model = MessageActionMenuReducer.reduce(target)

        assertThat(model.unavailableIosActions.map { it.action }).containsExactly(
            IosTimelineAction.SaveMessage,
            IosTimelineAction.UnsaveMessage,
            IosTimelineAction.Translate,
            IosTimelineAction.ShareMedia,
            IosTimelineAction.SaveMedia,
        ).inOrder()
        assertThat(model.unavailableIosActions.map { it.reason }.toSet())
            .containsExactly(MessageActionUnavailableReason.RequiresBottomLayer)
    }
}

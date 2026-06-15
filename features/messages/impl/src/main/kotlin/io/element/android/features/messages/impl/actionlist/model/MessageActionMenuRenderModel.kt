/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.actionlist.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import io.element.android.features.messages.impl.actionlist.ActionListState
import io.element.android.features.messages.impl.crypto.sendfailure.VerifiedUserSendFailure
import io.element.android.features.messages.impl.timeline.model.TimelineItem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@Immutable
data class MessageActionMenuRenderModel(
    val event: TimelineItem.Event,
    val sentTimeFull: String,
    val displayEmojiReactions: Boolean,
    val recentEmojis: ImmutableList<String>,
    val verifiedUserSendFailure: VerifiedUserSendFailure,
    val sections: ImmutableList<MessageActionMenuSectionModel>,
)

@Immutable
data class MessageActionMenuSectionModel(
    val kind: MessageActionMenuSection,
    val entries: ImmutableList<MessageActionMenuEntry>,
)

@Immutable
data class MessageActionMenuEntry(
    val action: TimelineItemAction,
    @StringRes val titleRes: Int,
    @DrawableRes val icon: Int,
    val destructive: Boolean,
)

enum class MessageActionMenuSection {
    Primary,
    Edit,
    Copy,
    Pin,
    Debug,
    Danger,
}

object MessageActionMenuReducer {
    fun reduce(target: ActionListState.Target.Success): MessageActionMenuRenderModel {
        return MessageActionMenuRenderModel(
            event = target.event,
            sentTimeFull = target.sentTimeFull,
            displayEmojiReactions = target.displayEmojiReactions,
            recentEmojis = target.recentEmojis,
            verifiedUserSendFailure = target.verifiedUserSendFailure,
            sections = target.actions
                .map { action ->
                    action.section() to MessageActionMenuEntry(
                        action = action,
                        titleRes = action.titleRes,
                        icon = action.icon,
                        destructive = action.destructive,
                    )
                }
                .groupBy({ it.first }, { it.second })
                .map { (section, entries) ->
                    MessageActionMenuSectionModel(
                        kind = section,
                        entries = entries.toImmutableList(),
                    )
                }
                .sortedBy { it.kind.ordinal }
                .toImmutableList(),
        )
    }

    private fun TimelineItemAction.section(): MessageActionMenuSection {
        return when (this) {
            TimelineItemAction.Reply,
            TimelineItemAction.ReplyInThread,
            TimelineItemAction.Forward,
            TimelineItemAction.ViewInTimeline -> MessageActionMenuSection.Primary
            TimelineItemAction.Edit,
            TimelineItemAction.EditPoll,
            TimelineItemAction.AddCaption,
            TimelineItemAction.EditCaption -> MessageActionMenuSection.Edit
            TimelineItemAction.CopyText,
            TimelineItemAction.CopyCaption,
            TimelineItemAction.CopyLink -> MessageActionMenuSection.Copy
            TimelineItemAction.Pin,
            TimelineItemAction.Unpin -> MessageActionMenuSection.Pin
            TimelineItemAction.ViewSource -> MessageActionMenuSection.Debug
            TimelineItemAction.EndPoll,
            TimelineItemAction.RemoveCaption,
            TimelineItemAction.ReportContent,
            TimelineItemAction.Redact -> MessageActionMenuSection.Danger
        }
    }
}

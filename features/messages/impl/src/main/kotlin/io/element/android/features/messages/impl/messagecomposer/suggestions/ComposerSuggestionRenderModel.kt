/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer.suggestions

import io.element.android.features.messages.impl.roomdata.RoomUnsealContext
import io.element.android.libraries.core.data.filterUpTo
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.textcomposer.mentions.ResolvedSuggestion

data class ComposerSuggestionRenderModel(
    val id: String,
    val displayName: String?,
    val subtitle: String?,
    val avatarUrl: String?,
    val kind: ComposerSuggestionKind,
    val isAgent: Boolean,
    val insertPayload: ComposerSuggestionInsertPayload,
)

enum class ComposerSuggestionKind {
    User,
    Agent,
    AllUsers,
    Room,
    Command,
}

sealed interface ComposerSuggestionInsertPayload {
    data class UserMention(val userId: UserId) : ComposerSuggestionInsertPayload
    data object RoomMention : ComposerSuggestionInsertPayload
    data class RoomAlias(val roomId: String, val roomAlias: String) : ComposerSuggestionInsertPayload
    data class Command(val command: String) : ComposerSuggestionInsertPayload
}

object ComposerSuggestionReducer {
    private const val MAX_MEMBER_ITEMS = 100

    fun memberSuggestions(
        query: String,
        context: RoomUnsealContext,
        currentUserId: UserId,
        canMentionAllUsers: Boolean,
        isDirectOneToOne: Boolean,
    ): List<ComposerSuggestionRenderModel> {
        val memberSuggestions = context.members
            .filterUpTo(MAX_MEMBER_ITEMS) { member ->
                member.isActive &&
                    member.userId != currentUserId &&
                    memberMatchesQuery(member.userId.value, member.displayName, query)
            }
            .map { member ->
                ComposerSuggestionRenderModel(
                    id = member.userId.value,
                    displayName = member.displayName,
                    subtitle = member.userId.value,
                    avatarUrl = member.avatarUrl,
                    kind = if (member.isAgent) ComposerSuggestionKind.Agent else ComposerSuggestionKind.User,
                    isAgent = member.isAgent,
                    insertPayload = ComposerSuggestionInsertPayload.UserMention(member.userId),
                )
            }

        return if (canMentionAllUsers && !isDirectOneToOne && memberMatchesQuery("@room", "Everyone", query)) {
            listOf(
                ComposerSuggestionRenderModel(
                    id = "@room",
                    displayName = "Everyone",
                    subtitle = "@room",
                    avatarUrl = null,
                    kind = ComposerSuggestionKind.AllUsers,
                    isAgent = false,
                    insertPayload = ComposerSuggestionInsertPayload.RoomMention,
                )
            ) + memberSuggestions
        } else {
            memberSuggestions
        }
    }

    fun fromResolvedSuggestions(
        suggestions: List<ResolvedSuggestion>,
        context: RoomUnsealContext?,
    ): List<ComposerSuggestionRenderModel> {
        return suggestions.map { suggestion ->
            when (suggestion) {
                ResolvedSuggestion.AtRoom -> ComposerSuggestionRenderModel(
                    id = "@room",
                    displayName = "Everyone",
                    subtitle = "@room",
                    avatarUrl = null,
                    kind = ComposerSuggestionKind.AllUsers,
                    isAgent = false,
                    insertPayload = ComposerSuggestionInsertPayload.RoomMention,
                )
                is ResolvedSuggestion.Member -> {
                    val member = suggestion.roomMember
                    val enrichedMember = context?.members?.firstOrNull { it.userId == member.userId }
                    val isAgent = enrichedMember?.isAgent == true
                    ComposerSuggestionRenderModel(
                        id = member.userId.value,
                        displayName = member.displayName,
                        subtitle = member.userId.value,
                        avatarUrl = member.avatarUrl,
                        kind = if (isAgent) ComposerSuggestionKind.Agent else ComposerSuggestionKind.User,
                        isAgent = isAgent,
                        insertPayload = ComposerSuggestionInsertPayload.UserMention(member.userId),
                    )
                }
                is ResolvedSuggestion.Alias -> ComposerSuggestionRenderModel(
                    id = suggestion.roomId.value,
                    displayName = suggestion.roomName,
                    subtitle = suggestion.roomAlias.value,
                    avatarUrl = suggestion.roomAvatarUrl,
                    kind = ComposerSuggestionKind.Room,
                    isAgent = false,
                    insertPayload = ComposerSuggestionInsertPayload.RoomAlias(suggestion.roomId.value, suggestion.roomAlias.value),
                )
                is ResolvedSuggestion.Command -> ComposerSuggestionRenderModel(
                    id = suggestion.command.command,
                    displayName = suggestion.command.command,
                    subtitle = suggestion.command.description,
                    avatarUrl = null,
                    kind = ComposerSuggestionKind.Command,
                    isAgent = false,
                    insertPayload = ComposerSuggestionInsertPayload.Command(suggestion.command.command),
                )
            }
        }
    }

    private fun memberMatchesQuery(userId: String, displayName: String?, query: String): Boolean {
        return query.isBlank() ||
            userId.contains(query, ignoreCase = true) ||
            displayName?.contains(query, ignoreCase = true) == true
    }
}

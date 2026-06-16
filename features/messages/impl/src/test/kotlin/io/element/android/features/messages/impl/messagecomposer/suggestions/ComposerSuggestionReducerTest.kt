/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer.suggestions

import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.roomdata.RoomAgentDescriptor
import io.element.android.features.messages.impl.roomdata.RoomUnsealContext
import io.element.android.features.messages.impl.roomdata.RoomUnsealDataSnapshot
import io.element.android.features.messages.impl.roomdata.RoomUnsealResource
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.room.RoomMembershipState
import io.element.android.libraries.matrix.test.room.aRoomMember
import io.element.android.libraries.textcomposer.mentions.ResolvedSuggestion
import org.junit.Test

class ComposerSuggestionReducerTest {
    @Test
    fun `memberSuggestions marks enriched agent members`() {
        val context = RoomUnsealContext.from(
            roomId = ROOM_ID,
            members = listOf(
                aRoomMember(userId = CURRENT_USER_ID, displayName = "Me", membership = RoomMembershipState.JOIN),
                aRoomMember(userId = AGENT_USER_ID, displayName = "Mail Agent", membership = RoomMembershipState.JOIN),
                aRoomMember(userId = HUMAN_USER_ID, displayName = "Alice", membership = RoomMembershipState.JOIN),
            ),
            snapshot = RoomUnsealDataSnapshot(
                roomAgents = RoomUnsealResource.success(
                    listOf(RoomAgentDescriptor(AGENT_USER_ID.value, "Mail Agent", null, null, "join"))
                )
            ),
        )

        val suggestions = ComposerSuggestionReducer.memberSuggestions(
            query = "",
            context = context,
            currentUserId = CURRENT_USER_ID,
            canMentionAllUsers = false,
            isDirectOneToOne = false,
        )

        assertThat(suggestions.map { it.id }).containsExactly(AGENT_USER_ID.value, HUMAN_USER_ID.value).inOrder()
        assertThat(suggestions.first().kind).isEqualTo(ComposerSuggestionKind.Agent)
        assertThat(suggestions.first().isAgent).isTrue()
        assertThat(suggestions.first().insertPayload).isEqualTo(ComposerSuggestionInsertPayload.UserMention(AGENT_USER_ID))
        assertThat(suggestions.last().kind).isEqualTo(ComposerSuggestionKind.User)
    }

    @Test
    fun `memberSuggestions inserts room mention first when allowed`() {
        val context = RoomUnsealContext.from(
            roomId = ROOM_ID,
            members = listOf(aRoomMember(userId = HUMAN_USER_ID, displayName = "Alice", membership = RoomMembershipState.JOIN)),
            snapshot = RoomUnsealDataSnapshot(),
        )

        val suggestions = ComposerSuggestionReducer.memberSuggestions(
            query = "room",
            context = context,
            currentUserId = CURRENT_USER_ID,
            canMentionAllUsers = true,
            isDirectOneToOne = false,
        )

        assertThat(suggestions.first().kind).isEqualTo(ComposerSuggestionKind.AllUsers)
        assertThat(suggestions.first().insertPayload).isEqualTo(ComposerSuggestionInsertPayload.RoomMention)
    }

    @Test
    fun `memberSuggestions hides room mention in direct one to one rooms`() {
        val context = RoomUnsealContext.from(
            roomId = ROOM_ID,
            members = listOf(aRoomMember(userId = HUMAN_USER_ID, displayName = "Alice", membership = RoomMembershipState.JOIN)),
            snapshot = RoomUnsealDataSnapshot(),
        )

        val suggestions = ComposerSuggestionReducer.memberSuggestions(
            query = "room",
            context = context,
            currentUserId = CURRENT_USER_ID,
            canMentionAllUsers = true,
            isDirectOneToOne = true,
        )

        assertThat(suggestions.none { it.kind == ComposerSuggestionKind.AllUsers }).isTrue()
    }

    @Test
    fun `fromResolvedSuggestions marks resolved members with room context agent data`() {
        val context = RoomUnsealContext.from(
            roomId = ROOM_ID,
            members = listOf(
                aRoomMember(
                    userId = AGENT_USER_ID,
                    displayName = "Matrix fallback",
                    avatarUrl = "mxc://matrix/fallback",
                    membership = RoomMembershipState.JOIN,
                )
            ),
            snapshot = RoomUnsealDataSnapshot(
                roomAgents = RoomUnsealResource.success(
                    listOf(RoomAgentDescriptor(AGENT_USER_ID.value, "Mail Agent", "mxc://agent/avatar", null, "join"))
                )
            ),
        )

        val renderModels = ComposerSuggestionReducer.fromResolvedSuggestions(
            suggestions = listOf(ResolvedSuggestion.Member(aRoomMember(userId = AGENT_USER_ID, displayName = "Mail Agent"))),
            context = context,
        )

        assertThat(renderModels.single().kind).isEqualTo(ComposerSuggestionKind.Agent)
        assertThat(renderModels.single().isAgent).isTrue()
        assertThat(renderModels.single().displayName).isEqualTo("Mail Agent")
        assertThat(renderModels.single().avatarUrl).isEqualTo("mxc://agent/avatar")
        assertThat(renderModels.single().insertPayload).isEqualTo(ComposerSuggestionInsertPayload.UserMention(AGENT_USER_ID))
    }

    private companion object {
        val ROOM_ID = RoomId("!room:example.org")
        val CURRENT_USER_ID = UserId("@me:example.org")
        val AGENT_USER_ID = UserId("@mail:example.org")
        val HUMAN_USER_ID = UserId("@alice:example.org")
    }
}

/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer.skills

import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.roomdata.AgentAccountDescriptor
import io.element.android.features.messages.impl.roomdata.RoomAgentDescriptor
import io.element.android.features.messages.impl.roomdata.RoomAgentSkillCatalogDescriptor
import io.element.android.features.messages.impl.roomdata.RoomAgentSkillDescriptor
import io.element.android.features.messages.impl.roomdata.RoomAgentSkillRelationDescriptor
import io.element.android.features.messages.impl.roomdata.RoomUnsealContext
import io.element.android.features.messages.impl.roomdata.RoomUnsealDataSnapshot
import io.element.android.features.messages.impl.roomdata.RoomUnsealResource
import io.element.android.libraries.chatbot.api.model.skills.ChatbotRoomAgentSkillRelationKind
import io.element.android.libraries.chatbot.api.model.skills.ChatbotRoomAgentSkillSource
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.room.RoomMembershipState
import io.element.android.libraries.matrix.test.room.aRoomMember
import org.junit.Test

class ComposerAgentSkillReducerTest {
    @Test
    fun `stateFromContext exposes direct room agent target from room context`() {
        val context = contextWithAgents(
            agentUserIds = listOf(AGENT_USER_ID),
            allAgents = listOf(agentAccount(AGENT_USER_ID.value, "Gemini")),
        )

        val state = ComposerAgentSkillReducer.stateFromContext(
            context = context,
            currentUserId = CURRENT_USER_ID.value,
            isDirectRoom = true,
        )

        assertThat(state.knownAgentMxids).containsExactly(AGENT_USER_ID.value)
        assertThat(state.targets.map { it.mxid }).containsExactly(AGENT_USER_ID.value)
        assertThat(state.targets.single().label).isEqualTo("Gemini")
        assertThat(state.activeAgentMxid).isEqualTo(AGENT_USER_ID.value)
        assertThat(state.isPresented).isFalse()
    }

    @Test
    fun `stateFromContext exposes mentioned agent targets outside direct rooms`() {
        val context = contextWithAgents(
            agentUserIds = listOf(SECOND_AGENT_USER_ID, AGENT_USER_ID),
            allAgents = listOf(
                agentAccount(SECOND_AGENT_USER_ID.value, "Beta"),
                agentAccount(AGENT_USER_ID.value, "Alpha"),
            ),
        )

        val state = ComposerAgentSkillReducer.stateFromContext(
            context = context,
            currentUserId = CURRENT_USER_ID.value,
            isDirectRoom = false,
            mentionedUserIds = setOf(SECOND_AGENT_USER_ID.value),
        )

        assertThat(state.targets.map { it.mxid }).containsExactly(SECOND_AGENT_USER_ID.value)
        assertThat(state.targets.single().label).isEqualTo("Beta")
        assertThat(state.activeAgentMxid).isEqualTo(SECOND_AGENT_USER_ID.value)
    }

    @Test
    fun `stateFromContext merges direct and mentioned agent targets without duplicates`() {
        val context = contextWithAgents(
            agentUserIds = listOf(AGENT_USER_ID, SECOND_AGENT_USER_ID),
            allAgents = listOf(
                agentAccount(AGENT_USER_ID.value, "Alpha"),
                agentAccount(SECOND_AGENT_USER_ID.value, "Beta"),
            ),
        )

        val state = ComposerAgentSkillReducer.stateFromContext(
            context = context,
            currentUserId = CURRENT_USER_ID.value,
            isDirectRoom = true,
            mentionedUserIds = setOf(AGENT_USER_ID.value, SECOND_AGENT_USER_ID.value),
        )

        assertThat(state.targets.map { it.mxid }).containsExactly(AGENT_USER_ID.value, SECOND_AGENT_USER_ID.value).inOrder()
        assertThat(state.activeAgentMxid).isNull()
    }

    @Test
    fun `mentionedAgentDescriptors uses known agents and sorts by label`() {
        val context = contextWithAgents(
            agentUserIds = listOf(SECOND_AGENT_USER_ID, AGENT_USER_ID),
            allAgents = listOf(
                agentAccount(SECOND_AGENT_USER_ID.value, "Beta"),
                agentAccount(AGENT_USER_ID.value, "Alpha"),
            ),
        )
        val knownAgents = ComposerAgentSkillReducer.agentDescriptors(context)

        val descriptors = ComposerAgentSkillReducer.mentionedAgentDescriptors(
            mentionedUserIds = setOf(SECOND_AGENT_USER_ID.value, AGENT_USER_ID.value),
            knownAgents = knownAgents,
            members = context.members,
        )

        assertThat(descriptors.map { it.label }).containsExactly("Alpha", "Beta").inOrder()
    }

    @Test
    fun `visibleSkillCandidates filters by active agent and selected skill`() {
        val alpha = ComposerAgentDescriptor(agentId = AGENT_USER_ID.value, mxid = AGENT_USER_ID.value, label = "Alpha")
        val beta = ComposerAgentDescriptor(agentId = SECOND_AGENT_USER_ID.value, mxid = SECOND_AGENT_USER_ID.value, label = "Beta")
        val candidates = listOf(
            candidate(alpha, "mail"),
            candidate(alpha, "weather"),
            candidate(beta, "mail"),
        )
        val selectedSkills = listOf(
            ComposerSelectedAgentSkill(
                agentId = AGENT_USER_ID.value,
                agentMxid = AGENT_USER_ID.value,
                agentLabel = "Alpha",
                skillName = "mail",
                path = null,
                directoryName = null,
            )
        )

        val visible = ComposerAgentSkillReducer.visibleSkillCandidates(
            candidates = candidates,
            selectedSkills = selectedSkills,
            activeAgentMxid = AGENT_USER_ID.value,
        )

        assertThat(visible.map { it.skillName }).containsExactly("weather")
    }

    @Test
    fun `deduplicateSkillCandidates keeps first candidate per agent and skill`() {
        val agent = ComposerAgentDescriptor(agentId = AGENT_USER_ID.value, mxid = AGENT_USER_ID.value, label = "Gemini")
        val first = candidate(agent, "mail", source = ComposerAgentSkillSource.Db)
        val duplicate = candidate(agent, "mail", source = ComposerAgentSkillSource.S3)
        val other = candidate(agent, "weather", source = ComposerAgentSkillSource.Db)

        val deduplicated = ComposerAgentSkillReducer.deduplicateSkillCandidates(listOf(first, duplicate, other))

        assertThat(deduplicated).containsExactly(first, other).inOrder()
    }

    @Test
    fun `roomSkillCandidates maps relation catalog source and runtime visibility`() {
        val agent = ComposerAgentDescriptor(agentId = AGENT_USER_ID.value, mxid = AGENT_USER_ID.value, label = "Gemini")

        val candidates = ComposerAgentSkillReducer.roomSkillCandidates(
            target = agent,
            agentId = AGENT_USER_ID.value,
            catalog = RoomAgentSkillCatalogDescriptor(
                skills = mapOf(
                    "skill-1" to RoomAgentSkillDescriptor(
                        id = "skill-1",
                        name = "weather",
                        description = null,
                        sources = listOf(ChatbotRoomAgentSkillSource.Db),
                        persisted = true,
                        runtimeVisible = false,
                    )
                ),
                relations = mapOf(
                    AGENT_USER_ID.value to mapOf(
                        "skill-1" to RoomAgentSkillRelationDescriptor(
                            relation = ChatbotRoomAgentSkillRelationKind.Available,
                            source = ChatbotRoomAgentSkillSource.Db,
                            path = null,
                            directoryName = null,
                            runtimeVisible = false,
                            persisted = true,
                            stale = null,
                        )
                    )
                ),
            ),
        )

        assertThat(candidates.single().skillKey).isEqualTo("skill-1")
        assertThat(candidates.single().source).isEqualTo(ComposerAgentSkillSource.Db)
        assertThat(candidates.single().relation).isEqualTo(ComposerAgentSkillRelation.Available)
        assertThat(candidates.single().runtimeVisible).isFalse()
    }

    private fun contextWithAgents(
        agentUserIds: List<UserId>,
        allAgents: List<AgentAccountDescriptor>,
    ): RoomUnsealContext {
        return RoomUnsealContext.from(
            roomId = ROOM_ID,
            members = listOf(aRoomMember(userId = CURRENT_USER_ID, membership = RoomMembershipState.JOIN)) +
                agentUserIds.map { agentUserId -> aRoomMember(userId = agentUserId, membership = RoomMembershipState.JOIN) },
            snapshot = RoomUnsealDataSnapshot(
                roomAgents = RoomUnsealResource.success(
                    agentUserIds.map { agentUserId ->
                        RoomAgentDescriptor(
                            userId = agentUserId.value,
                            displayName = null,
                            avatarUrl = null,
                            userType = "agent",
                            membership = "join",
                        )
                    }
                ),
                allAgents = RoomUnsealResource.success(allAgents),
            ),
        )
    }

    private fun agentAccount(mxid: String, label: String): AgentAccountDescriptor {
        return AgentAccountDescriptor(
            botName = label,
            localpart = mxid.substringAfter("@").substringBefore(":"),
            serverName = mxid.substringAfter(":"),
            matrixUserId = mxid,
            displayName = label,
            avatarUrl = null,
            isDeviceAgent = false,
            boundDeviceId = null,
        )
    }

    private fun candidate(
        agent: ComposerAgentDescriptor,
        skillName: String,
        source: ComposerAgentSkillSource = ComposerAgentSkillSource.Db,
    ): ComposerAgentSkillCandidate {
        return ComposerAgentSkillCandidate(
            agent = agent,
            skillKey = skillName,
            skillName = skillName,
            source = source,
            relation = ComposerAgentSkillRelation.Runtime,
            path = null,
            directoryName = null,
            runtimeVisible = true,
        )
    }

    private companion object {
        val ROOM_ID = RoomId("!room:example.org")
        val CURRENT_USER_ID = UserId("@me:example.org")
        val AGENT_USER_ID = UserId("@gemini:example.org")
        val SECOND_AGENT_USER_ID = UserId("@beta:example.org")
    }
}

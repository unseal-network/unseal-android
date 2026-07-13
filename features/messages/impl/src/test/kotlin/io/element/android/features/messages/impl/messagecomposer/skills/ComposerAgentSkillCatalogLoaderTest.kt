/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer.skills

import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.roomdata.AgentAccountDescriptor
import io.element.android.features.messages.impl.roomdata.FakeRoomUnsealDataClient
import io.element.android.features.messages.impl.roomdata.RoomAgentDescriptor
import io.element.android.features.messages.impl.roomdata.RoomAgentSkillAgentDescriptor
import io.element.android.features.messages.impl.roomdata.RoomAgentSkillCatalogDescriptor
import io.element.android.features.messages.impl.roomdata.RoomAgentSkillDescriptor
import io.element.android.features.messages.impl.roomdata.RoomAgentSkillRelationDescriptor
import io.element.android.features.messages.impl.roomdata.RoomUnsealContext
import io.element.android.features.messages.impl.roomdata.RoomUnsealDataSnapshot
import io.element.android.features.messages.impl.roomdata.RoomUnsealResource
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.chatbot.api.model.skills.ChatbotRoomAgentSkillRelationKind
import io.element.android.libraries.chatbot.api.model.skills.ChatbotRoomAgentSkillSource
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.room.RoomMembershipState
import io.element.android.libraries.matrix.test.room.FakeBaseRoom
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.libraries.matrix.test.room.aRoomMember
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ComposerAgentSkillCatalogLoaderTest {
    @Test
    fun `load returns runtime visible room agent skill candidates`() = runTest {
        val dataClient = FakeRoomUnsealDataClient().apply {
            roomAgentSkillsResult = { _, _, runtimeOwnerUserId ->
                assertThat(runtimeOwnerUserId).isEqualTo(CURRENT_USER_ID.value)
                Result.success(catalog(skillName = "weather"))
            }
        }
        val loader = loader(dataClient)

        val result = loader.load(
            context = contextWithAgent(),
            targets = listOf(agentTarget()),
            currentUserId = CURRENT_USER_ID.value,
            isDirectRoom = true,
        )

        assertThat(result.error).isNull()
        assertThat(result.candidates.map { it.skillName }).containsExactly("weather")
        assertThat(result.candidates.single().source).isEqualTo(ComposerAgentSkillSource.Db)
        assertThat(result.catalogs).hasSize(1)
        assertThat(result.refreshCatalogs).isEmpty()
        assertThat(dataClient.roomAgentSkillRefreshRequests).isEmpty()
    }

    @Test
    fun `load only requests skill catalog for explicit targets`() = runTest {
        val otherAgentUserId = UserId("@other:example.org")
        val dataClient = FakeRoomUnsealDataClient().apply {
            roomAgentSkillsResult = { _, agentId, _ ->
                Result.success(catalog(agentId = agentId, skillKey = "skill-$agentId", skillName = if (agentId == AGENT_USER_ID.value) "weather" else "other"))
            }
        }

        val result = loader(dataClient).load(
            context = contextWithAgents(listOf(AGENT_USER_ID, otherAgentUserId)),
            targets = listOf(agentTarget()),
            currentUserId = CURRENT_USER_ID.value,
            isDirectRoom = true,
        )

        assertThat(result.error).isNull()
        assertThat(result.candidates.map { it.agent.mxid }).containsExactly(AGENT_USER_ID.value)
        assertThat(result.candidates.map { it.skillName }).containsExactly("weather")
        assertThat(dataClient.roomAgentSkillRequests.map { it.agentId }).containsExactly(AGENT_USER_ID.value)
    }

    @Test
    fun `load returns cached partial catalog and refreshWorkspace replaces it`() = runTest {
        val dataClient = FakeRoomUnsealDataClient().apply {
            roomAgentSkillsResult = { _, _, _ ->
                Result.success(
                    catalog(
                        status = "partial",
                        cacheKey = "cache-key",
                        skillName = "hidden",
                        relation = ChatbotRoomAgentSkillRelationKind.Available,
                        runtimeVisible = false,
                    )
                )
            }
            refreshRoomAgentSkillsResult = { _, agentId, cacheKey, runtimeOwnerUserId ->
                assertThat(agentId).isEqualTo(AGENT_USER_ID.value)
                assertThat(cacheKey).isEqualTo("cache-key")
                assertThat(runtimeOwnerUserId).isEqualTo(CURRENT_USER_ID.value)
                Result.success(
                    catalog(
                        status = "complete",
                        cacheKey = "cache-key",
                        skillName = "mail",
                        source = ChatbotRoomAgentSkillSource.Workspace,
                        path = "/workspace/mail/SKILL.md",
                        directoryName = "mail",
                    )
                )
            }
        }
        val catalogLoader = loader(dataClient)

        val result = catalogLoader.load(
            context = contextWithAgent(),
            targets = listOf(agentTarget()),
            currentUserId = CURRENT_USER_ID.value,
            isDirectRoom = true,
        )

        assertThat(result.error).isNull()
        assertThat(result.candidates.map { it.skillName }).containsExactly("hidden")
        assertThat(result.candidates.single().runtimeVisible).isFalse()
        assertThat(result.catalogs).hasSize(1)
        assertThat(result.refreshCatalogs).hasSize(1)

        val refreshed = catalogLoader.refreshWorkspace(result.refreshCatalogs, CURRENT_USER_ID.value)

        assertThat(refreshed.error).isNull()
        assertThat(refreshed.candidates.map { it.skillName }).containsExactly("mail")
        assertThat(refreshed.candidates.single().source).isEqualTo(ComposerAgentSkillSource.Workspace)
        assertThat(dataClient.roomAgentSkillRefreshRequests.map { it.cacheKey }).containsExactly("cache-key")
    }

    @Test
    fun `refreshWorkspace force reloads a complete cached catalog`() = runTest {
        val dataClient = FakeRoomUnsealDataClient().apply {
            roomAgentSkillsResult = { _, _, _ ->
                Result.success(catalog(status = "complete", cacheKey = "cache-key", skillName = "old-skill"))
            }
            refreshRoomAgentSkillsResult = { _, _, _, _ ->
                Result.success(catalog(status = "complete", cacheKey = "cache-key", skillName = "new-skill"))
            }
        }
        val catalogLoader = loader(dataClient)

        val result = catalogLoader.load(
            context = contextWithAgent(),
            targets = listOf(agentTarget()),
            currentUserId = CURRENT_USER_ID.value,
            isDirectRoom = true,
        )
        val refreshed = catalogLoader.refreshWorkspace(
            catalogs = result.catalogs,
            currentUserId = CURRENT_USER_ID.value,
            force = true,
        )

        assertThat(refreshed.candidates.map { it.skillName }).containsExactly("new-skill")
        assertThat(dataClient.roomAgentSkillRefreshRequests.map { it.cacheKey }).containsExactly("cache-key")
    }

    @Test
    fun `load reports room catalog error without legacy fallback`() = runTest {
        val dataClient = FakeRoomUnsealDataClient().apply {
            roomAgentSkillsResult = { _, _, _ -> Result.failure(IllegalStateException("catalog failed")) }
        }

        val result = loader(dataClient).load(
            context = contextWithAgent(),
            targets = listOf(agentTarget()),
            currentUserId = CURRENT_USER_ID.value,
            isDirectRoom = true,
        )

        assertThat(result.candidates).isEmpty()
        assertThat(result.error).isEqualTo("catalog failed")
        assertThat(dataClient.roomAgentSkillRefreshRequests).isEmpty()
    }

    private fun loader(dataClient: FakeRoomUnsealDataClient): ComposerAgentSkillCatalogLoader {
        val dispatcher = UnconfinedTestDispatcher()
        return ComposerAgentSkillCatalogLoader(
            room = FakeJoinedRoom(baseRoom = FakeBaseRoom(roomId = ROOM_ID)),
            roomUnsealDataClient = dataClient,
            dispatchers = CoroutineDispatchers(dispatcher, dispatcher, dispatcher),
        )
    }

    private fun contextWithAgent(): RoomUnsealContext {
        return contextWithAgents(listOf(AGENT_USER_ID))
    }

    private fun contextWithAgents(agentUserIds: List<UserId>): RoomUnsealContext {
        return RoomUnsealContext.from(
            roomId = ROOM_ID,
            members = listOf(aRoomMember(userId = CURRENT_USER_ID, membership = RoomMembershipState.JOIN)) +
                agentUserIds.map { userId -> aRoomMember(userId = userId, membership = RoomMembershipState.JOIN) },
            snapshot = RoomUnsealDataSnapshot(
                roomAgents = RoomUnsealResource.success(
                    agentUserIds.map { userId ->
                        RoomAgentDescriptor(userId = userId.value, displayName = null, avatarUrl = null, userType = "agent", membership = "join")
                    }
                ),
                allAgents = RoomUnsealResource.success(
                    agentUserIds.map { userId ->
                        val localpart = userId.value.removePrefix("@").substringBefore(":")
                        AgentAccountDescriptor(
                            botName = localpart,
                            localpart = localpart,
                            serverName = "example.org",
                            matrixUserId = userId.value,
                            displayName = localpart,
                            avatarUrl = null,
                            isDeviceAgent = false,
                            boundDeviceId = null,
                        )
                    }
                ),
            ),
        )
    }

    private fun agentTarget(): ComposerAgentDescriptor {
        return ComposerAgentDescriptor(
            agentId = AGENT_USER_ID.value,
            mxid = AGENT_USER_ID.value,
            label = "Gemini",
        )
    }

    private fun catalog(
        agentId: String = AGENT_USER_ID.value,
        status: String = "complete",
        cacheKey: String = "",
        skillKey: String = "skill-1",
        skillName: String = "weather",
        source: ChatbotRoomAgentSkillSource = ChatbotRoomAgentSkillSource.Db,
        relation: ChatbotRoomAgentSkillRelationKind = ChatbotRoomAgentSkillRelationKind.Runtime,
        path: String? = null,
        directoryName: String? = null,
        runtimeVisible: Boolean = true,
    ): RoomAgentSkillCatalogDescriptor {
        return RoomAgentSkillCatalogDescriptor(
            status = status,
            cacheKey = cacheKey,
            agents = mapOf(agentId to RoomAgentSkillAgentDescriptor(agentId = agentId, displayName = "Gemini")),
            skills = mapOf(
                skillKey to RoomAgentSkillDescriptor(
                    id = skillKey,
                    name = skillName,
                    description = null,
                    sources = listOf(source),
                    persisted = source != ChatbotRoomAgentSkillSource.Workspace,
                    runtimeVisible = runtimeVisible,
                )
            ),
            relations = mapOf(
                agentId to mapOf(
                    skillKey to RoomAgentSkillRelationDescriptor(
                        relation = relation,
                        source = source,
                        path = path,
                        directoryName = directoryName,
                        runtimeVisible = runtimeVisible,
                        persisted = source != ChatbotRoomAgentSkillSource.Workspace,
                        stale = null,
                    )
                )
            ),
        )
    }

    private companion object {
        val ROOM_ID = RoomId("!room:example.org")
        val CURRENT_USER_ID = UserId("@me:example.org")
        val AGENT_USER_ID = UserId("@gemini:example.org")
    }
}

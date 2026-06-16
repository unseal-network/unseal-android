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
import io.element.android.features.messages.impl.roomdata.RoomAgentSkillDescriptor
import io.element.android.features.messages.impl.roomdata.RoomLegacyAgentSkillDescriptor
import io.element.android.features.messages.impl.roomdata.RoomUnsealContext
import io.element.android.features.messages.impl.roomdata.RoomUnsealDataSnapshot
import io.element.android.features.messages.impl.roomdata.RoomUnsealResource
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
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
                Result.success(listOf(RoomAgentSkillDescriptor(id = "skill-1", name = "weather", description = null, runtimeVisible = true)))
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
        assertThat(dataClient.legacyAgentSkillRequests).isEmpty()
    }

    @Test
    fun `load falls back to legacy installed skills when runtime candidates are not visible`() = runTest {
        val dataClient = FakeRoomUnsealDataClient().apply {
            roomAgentSkillsResult = { _, _, _ ->
                Result.success(listOf(RoomAgentSkillDescriptor(id = "skill-1", name = "hidden", description = null, runtimeVisible = false)))
            }
            legacyAgentSkillsResult = { lookupId ->
                if (lookupId == "gemini") {
                    Result.success(listOf(RoomLegacyAgentSkillDescriptor(id = "legacy-1", name = "mail", description = null)))
                } else {
                    Result.success(emptyList())
                }
            }
        }

        val result = loader(dataClient).load(
            context = contextWithAgent(),
            targets = listOf(agentTarget()),
            currentUserId = CURRENT_USER_ID.value,
            isDirectRoom = true,
        )

        assertThat(result.error).isNull()
        assertThat(result.candidates.map { it.skillName }).containsExactly("mail")
        assertThat(result.candidates.single().source).isEqualTo(ComposerAgentSkillSource.S3)
        assertThat(dataClient.legacyAgentSkillRequests).containsExactly(AGENT_USER_ID.value, "gemini").inOrder()
    }

    @Test
    fun `load falls back to legacy installed skills when room catalog fails`() = runTest {
        val dataClient = FakeRoomUnsealDataClient().apply {
            roomAgentSkillsResult = { _, _, _ -> Result.failure(IllegalStateException("catalog failed")) }
            legacyAgentSkillsResult = { lookupId ->
                if (lookupId == AGENT_USER_ID.value) {
                    Result.success(listOf(RoomLegacyAgentSkillDescriptor(id = "legacy-1", name = "mail", description = null)))
                } else {
                    Result.success(emptyList())
                }
            }
        }

        val result = loader(dataClient).load(
            context = contextWithAgent(),
            targets = listOf(agentTarget()),
            currentUserId = CURRENT_USER_ID.value,
            isDirectRoom = true,
        )

        assertThat(result.error).isNull()
        assertThat(result.candidates.map { it.skillName }).containsExactly("mail")
    }

    @Test
    fun `load reports room catalog error when fallback is empty`() = runTest {
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
        return RoomUnsealContext.from(
            roomId = ROOM_ID,
            members = listOf(
                aRoomMember(userId = CURRENT_USER_ID, membership = RoomMembershipState.JOIN),
                aRoomMember(userId = AGENT_USER_ID, membership = RoomMembershipState.JOIN),
            ),
            snapshot = RoomUnsealDataSnapshot(
                roomAgents = RoomUnsealResource.success(
                    listOf(RoomAgentDescriptor(userId = AGENT_USER_ID.value, displayName = null, avatarUrl = null, userType = "agent", membership = "join"))
                ),
                allAgents = RoomUnsealResource.success(
                    listOf(
                        AgentAccountDescriptor(
                            botName = "Gemini",
                            localpart = "gemini",
                            serverName = "example.org",
                            matrixUserId = AGENT_USER_ID.value,
                            displayName = "Gemini",
                            avatarUrl = null,
                            isDeviceAgent = false,
                            boundDeviceId = null,
                        )
                    )
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

    private companion object {
        val ROOM_ID = RoomId("!room:example.org")
        val CURRENT_USER_ID = UserId("@me:example.org")
        val AGENT_USER_ID = UserId("@gemini:example.org")
    }
}

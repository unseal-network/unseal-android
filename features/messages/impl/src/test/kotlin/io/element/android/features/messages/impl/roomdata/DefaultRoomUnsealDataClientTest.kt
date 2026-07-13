/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.roomdata

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgent
import io.element.android.libraries.chatbot.api.model.rooms.ChatbotGetRoomAgentsResponse
import io.element.android.libraries.chatbot.api.model.rooms.ChatbotRoomAgent
import io.element.android.libraries.chatbot.api.model.schedules.ChatbotSchedule
import io.element.android.libraries.chatbot.api.model.skills.ChatbotListRoomAgentSkillsResponse
import io.element.android.libraries.chatbot.api.model.skills.ChatbotRoomAgentSkill
import io.element.android.libraries.chatbot.api.model.skills.ChatbotRoomAgentSkillAgent
import io.element.android.libraries.chatbot.api.model.skills.ChatbotRoomAgentSkillRelation
import io.element.android.libraries.chatbot.api.model.skills.ChatbotRoomAgentSkillRelationKind
import io.element.android.libraries.chatbot.api.model.skills.ChatbotRoomAgentSkillSource
import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotWebhookTrigger
import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotWebhookTriggerStatus
import io.element.android.libraries.chatbot.test.FakeChatbotApiService
import io.element.android.libraries.chatbot.test.FakeChatbotApiServiceFactory
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.test.FakeMatrixClient
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

class DefaultRoomUnsealDataClientTest {
    @Test
    fun `getRoomAgents maps room agents using mxid when present`() = runTest {
        val service = FakeChatbotApiService().apply {
            getRoomAgentsResult = {
                Result.success(
                    ChatbotGetRoomAgentsResponse(
                        agents = listOf(
                            ChatbotRoomAgent(
                                agentId = "@fallback:example.org",
                                mxid = "@agent:example.org",
                                displayName = "Mail Agent",
                                avatarUrl = "mxc://avatar",
                                userType = "agent",
                                membership = "join",
                            )
                        )
                    )
                )
            }
        }
        val client = createClient(service)

        val agents = client.getRoomAgents(A_ROOM_ID).getOrThrow()

        assertThat(agents).containsExactly(
            RoomAgentDescriptor(
                userId = "@agent:example.org",
                displayName = "Mail Agent",
                avatarUrl = "mxc://avatar",
                userType = "agent",
                membership = "join",
            )
        )
    }

    @Test
    fun `listAgents maps matrix id and device metadata`() = runTest {
        val service = FakeChatbotApiService().apply {
            listAgentsResult = {
                Result.success(
                    listOf(
                        ChatbotAgent(
                            botName = "device",
                            localpart = "device-agent",
                            serverName = "example.org",
                            displayName = "Device Agent",
                            avatarUrl = "mxc://device",
                            metadata = mapOf(
                                "agent_kind" to JsonPrimitive("device"),
                                "bound_device_id" to JsonPrimitive("DEVICEID"),
                            ),
                        )
                    )
                )
            }
        }
        val client = createClient(service)

        val agents = client.listAgents().getOrThrow()

        assertThat(agents).containsExactly(
            AgentAccountDescriptor(
                botName = "device",
                localpart = "device-agent",
                serverName = "example.org",
                matrixUserId = "@device-agent:example.org",
                displayName = "Device Agent",
                avatarUrl = "mxc://device",
                isDeviceAgent = true,
                boundDeviceId = "DEVICEID",
            )
        )
    }

    @Test
    fun `listSchedules maps enabled status with status taking precedence`() = runTest {
        val service = FakeChatbotApiService().apply {
            listSchedulesResult = {
                Result.success(
                    listOf(
                        ChatbotSchedule(
                            scheduleId = "schedule-1",
                            name = "Morning",
                            cron = "0 9 * * *",
                            action = "hello",
                            agentId = "@agent:example.org",
                            roomId = A_ROOM_ID.value,
                            timezone = "Asia/Shanghai",
                            status = "enabled",
                            enabled = false,
                        )
                    )
                )
            }
        }
        val client = createClient(service)

        val schedules = client.listSchedules(A_ROOM_ID).getOrThrow()

        assertThat(schedules.single().isEnabled).isTrue()
        assertThat(schedules.single().timezone).isEqualTo("Asia/Shanghai")
    }

    @Test
    fun `skills APIs map catalog and refresh workspace`() = runTest {
        val service = FakeChatbotApiService().apply {
            listRoomAgentSkillsResult = { roomId, agentId, runtimeOwner ->
                assertThat(roomId).isEqualTo(A_ROOM_ID.value)
                assertThat(agentId).isEqualTo("@agent:example.org")
                assertThat(runtimeOwner).isEqualTo("@me:example.org")
                Result.success(
                    ChatbotListRoomAgentSkillsResponse(
                        status = "partial",
                        cacheKey = "cache-key",
                        agents = mapOf("@agent:example.org" to ChatbotRoomAgentSkillAgent(agentId = "@agent:example.org", displayName = "Agent")),
                        skills = mapOf(
                            "runtime-1" to ChatbotRoomAgentSkill(
                                id = "runtime-1",
                                name = "Runtime Skill",
                                sources = listOf(ChatbotRoomAgentSkillSource.Db),
                                persisted = true,
                                runtimeVisible = false,
                            )
                        ),
                        relations = mapOf(
                            "@agent:example.org" to mapOf(
                                "runtime-1" to ChatbotRoomAgentSkillRelation(
                                    relation = ChatbotRoomAgentSkillRelationKind.Available,
                                    source = ChatbotRoomAgentSkillSource.Db,
                                    runtimeVisible = false,
                                    persisted = true,
                                )
                            )
                        ),
                    )
                )
            }
            refreshRoomAgentSkillsResult = { roomId, agentId, cacheKey, runtimeOwner ->
                assertThat(roomId).isEqualTo(A_ROOM_ID.value)
                assertThat(agentId).isEqualTo("@agent:example.org")
                assertThat(cacheKey).isEqualTo("cache-key")
                assertThat(runtimeOwner).isEqualTo("@me:example.org")
                Result.success(ChatbotListRoomAgentSkillsResponse(status = "complete", cacheKey = "cache-key"))
            }
        }
        val client = createClient(service)

        val catalog = client.listRoomAgentSkills(A_ROOM_ID, "@agent:example.org", "@me:example.org").getOrThrow()
        val refreshed = client.refreshRoomAgentSkills(A_ROOM_ID, "@agent:example.org", "cache-key", "@me:example.org").getOrThrow()

        assertThat(catalog.status).isEqualTo("partial")
        assertThat(catalog.cacheKey).isEqualTo("cache-key")
        assertThat(catalog.skills["runtime-1"]?.name).isEqualTo("Runtime Skill")
        assertThat(catalog.relations["@agent:example.org"]?.get("runtime-1")?.relation).isEqualTo(ChatbotRoomAgentSkillRelationKind.Available)
        assertThat(refreshed.status).isEqualTo("complete")
    }

    @Test
    fun `webhook and working memory APIs are room scoped`() = runTest {
        val service = FakeChatbotApiService().apply {
            listWebhookTriggersResult = { agentId, source, roomId, status ->
                assertThat(agentId).isNull()
                assertThat(source).isNull()
                assertThat(roomId).isEqualTo(A_ROOM_ID.value)
                assertThat(status).isNull()
                Result.success(
                    listOf(
                        ChatbotWebhookTrigger(
                            triggerId = "trigger-1",
                            agentId = "@agent:example.org",
                            name = "GitHub",
                            source = "github",
                            eventTypes = listOf("push"),
                            actionPrompt = "summarize",
                            roomId = A_ROOM_ID.value,
                            status = ChatbotWebhookTriggerStatus.Enabled,
                        )
                    )
                )
            }
            getRoomWorkingMemoryResult = { roomId ->
                assertThat(roomId).isEqualTo(A_ROOM_ID.value)
                Result.success("memory")
            }
        }
        val client = createClient(service)

        val triggers = client.listWebhookTriggers(A_ROOM_ID).getOrThrow()
        val workingMemory = client.getRoomWorkingMemory(A_ROOM_ID).getOrThrow()

        assertThat(triggers.single().source).isEqualTo("github")
        assertThat(workingMemory).isEqualTo("memory")
    }

    @Test
    fun `loadRoomData keeps successful resources when another request fails`() = runTest {
        val failure = IllegalStateException("agents failed")
        val service = FakeChatbotApiService().apply {
            getRoomAgentsResult = { Result.failure(failure) }
            listSchedulesResult = {
                Result.success(
                    listOf(
                        ChatbotSchedule(
                            scheduleId = "schedule-1",
                            name = "Morning",
                            cron = "0 9 * * *",
                            action = "hello",
                            agentId = "@agent:example.org",
                            roomId = A_ROOM_ID.value,
                            enabled = true,
                        )
                    )
                )
            }
            getRoomWorkingMemoryResult = { Result.success("memory") }
        }
        val client = createClient(service)

        val snapshot = client.loadRoomData(A_ROOM_ID)

        assertThat(snapshot.roomAgents.value).isEmpty()
        assertThat(snapshot.roomAgents.error).isSameInstanceAs(failure)
        assertThat(snapshot.schedules.value.single().id).isEqualTo("schedule-1")
        assertThat(snapshot.schedules.isSuccess).isTrue()
        assertThat(snapshot.workingMemory.value).isEqualTo("memory")
    }

    @Test
    fun `loadRoomIdentityData only requests agent identity resources`() = runTest {
        var requestedSchedules = false
        var requestedWebhooks = false
        var requestedWorkingMemory = false
        val service = FakeChatbotApiService().apply {
            getRoomAgentsResult = {
                Result.success(
                    ChatbotGetRoomAgentsResponse(
                        agents = listOf(
                            ChatbotRoomAgent(
                                agentId = "@agent:example.org",
                                mxid = "@agent:example.org",
                                displayName = "Agent",
                                userType = "agent",
                                membership = "join",
                            )
                        )
                    )
                )
            }
            listAgentsResult = {
                Result.success(
                    listOf(
                        ChatbotAgent(
                            botName = "agent",
                            localpart = "agent",
                            serverName = "example.org",
                            displayName = "Agent",
                        )
                    )
                )
            }
            listSchedulesResult = {
                requestedSchedules = true
                Result.failure(IllegalStateException("schedules should not load"))
            }
            listWebhookTriggersResult = { _, _, _, _ ->
                requestedWebhooks = true
                Result.failure(IllegalStateException("webhooks should not load"))
            }
            getRoomWorkingMemoryResult = {
                requestedWorkingMemory = true
                Result.failure(IllegalStateException("memory should not load"))
            }
        }
        val client = createClient(service)

        val snapshot = client.loadRoomIdentityData(A_ROOM_ID)

        assertThat(snapshot.roomAgents.value.single().userId).isEqualTo("@agent:example.org")
        assertThat(snapshot.allAgents.value.single().matrixUserId).isEqualTo("@agent:example.org")
        assertThat(snapshot.schedules.value).isEmpty()
        assertThat(snapshot.webhookTriggers.value).isEmpty()
        assertThat(snapshot.workingMemory.value).isEmpty()
        assertThat(requestedSchedules).isFalse()
        assertThat(requestedWebhooks).isFalse()
        assertThat(requestedWorkingMemory).isFalse()
    }

    @Test
    fun `loadRoomData reports identity snapshot before room detail resources`() = runTest {
        val service = FakeChatbotApiService().apply {
            getRoomAgentsResult = {
                Result.success(
                    ChatbotGetRoomAgentsResponse(
                        agents = listOf(
                            ChatbotRoomAgent(
                                agentId = "@agent:example.org",
                                mxid = "@agent:example.org",
                                displayName = "Agent",
                                userType = "agent",
                                membership = "join",
                            )
                        )
                    )
                )
            }
            listAgentsResult = {
                Result.success(
                    listOf(
                        ChatbotAgent(
                            botName = "agent",
                            localpart = "agent",
                            serverName = "example.org",
                            displayName = "Agent",
                        )
                    )
                )
            }
            listSchedulesResult = {
                Result.success(
                    listOf(
                        ChatbotSchedule(
                            scheduleId = "schedule-1",
                            name = "Morning",
                            cron = "0 9 * * *",
                            action = "hello",
                            agentId = "@agent:example.org",
                            roomId = A_ROOM_ID.value,
                            enabled = true,
                        )
                    )
                )
            }
            listWebhookTriggersResult = { _, _, _, _ ->
                Result.success(
                    listOf(
                        ChatbotWebhookTrigger(
                            triggerId = "trigger-1",
                            agentId = "@agent:example.org",
                            name = "GitHub",
                            actionPrompt = "summarize",
                            roomId = A_ROOM_ID.value,
                            status = ChatbotWebhookTriggerStatus.Enabled,
                        )
                    )
                )
            }
            getRoomWorkingMemoryResult = { Result.success("memory") }
        }
        val client = createClient(service)
        var identitySnapshot: RoomUnsealDataSnapshot? = null

        val snapshot = client.loadRoomData(A_ROOM_ID) { identitySnapshot = it }

        assertThat(identitySnapshot?.roomAgents?.value?.single()?.userId).isEqualTo("@agent:example.org")
        assertThat(identitySnapshot?.allAgents?.value?.single()?.matrixUserId).isEqualTo("@agent:example.org")
        assertThat(identitySnapshot?.schedules?.value).isEmpty()
        assertThat(identitySnapshot?.webhookTriggers?.value).isEmpty()
        assertThat(identitySnapshot?.workingMemory?.value).isEmpty()
        assertThat(snapshot.schedules.value.single().id).isEqualTo("schedule-1")
        assertThat(snapshot.webhookTriggers.value.single().id).isEqualTo("trigger-1")
        assertThat(snapshot.workingMemory.value).isEqualTo("memory")
    }

    private fun createClient(service: FakeChatbotApiService): DefaultRoomUnsealDataClient {
        return DefaultRoomUnsealDataClient(
            matrixClient = FakeMatrixClient(),
            chatbotApiServiceFactory = FakeChatbotApiServiceFactory(service),
        )
    }

    private companion object {
        val A_ROOM_ID = RoomId("!room:example.org")
    }
}

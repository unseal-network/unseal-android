/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.agentmanagement.impl.detail

import app.cash.turbine.TurbineTestContext
import com.google.common.truth.Truth.assertThat
import io.element.android.features.agentmanagement.impl.shared.AgentDirectChatService
import io.element.android.libraries.androidutils.clipboard.FakeClipboardHelper
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgent
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgentRoom
import io.element.android.libraries.chatbot.test.FakeChatbotApiService
import io.element.android.libraries.chatbot.test.FakeChatbotApiServiceFactory
import io.element.android.libraries.chatbot.test.aChatbotAgent
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.RoomIdOrAlias
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class AgentDetailPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `present - loads agent and rooms once`() = runTest {
        var getAgentCalls = 0
        var listRoomsCalls = 0
        val service = FakeChatbotApiService().apply {
            getAgentResult = {
                getAgentCalls++
                Result.success(aChatbotAgent(botName = it, displayName = "Planner"))
            }
            listAgentRoomsResult = {
                listRoomsCalls++
                Result.success(listOf(ChatbotAgentRoom(roomId = "!room:example.org", roomName = "Ops")))
            }
        }
        val presenter = createAgentDetailPresenter(service = service)

        presenter.test {
            val initialState = awaitItem()
            initialState.eventSink(AgentDetailEvents.OnAppear)

            val loadedState = awaitStateWhere { it.agent?.displayName == "Planner" && it.rooms.size == 1 && !it.isLoading }
            assertThat(loadedState.rooms.single().displayName()).isEqualTo("Ops")

            loadedState.eventSink(AgentDetailEvents.OnAppear)
            assertThat(getAgentCalls).isEqualTo(1)
            assertThat(listRoomsCalls).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - refresh keeps previous agent when loading fails`() = runTest {
        var fail = false
        val service = FakeChatbotApiService().apply {
            getAgentResult = {
                if (fail) {
                    Result.failure(IllegalStateException("network down"))
                } else {
                    Result.success(aChatbotAgent(botName = it, displayName = "Stable"))
                }
            }
        }
        val presenter = createAgentDetailPresenter(service = service)

        presenter.test {
            awaitItem().eventSink(AgentDetailEvents.OnAppear)
            val loadedState = awaitStateWhere { it.agent?.displayName == "Stable" && !it.isLoading }

            fail = true
            loadedState.eventSink(AgentDetailEvents.Refresh)
            val refreshedState = awaitStateWhere { it.agent?.displayName == "Stable" && !it.isLoading }
            assertThat(refreshedState.error).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - copy agent id uses mxid and falls back to bot name`() = runTest {
        val clipboardHelper = FakeClipboardHelper()
        val service = FakeChatbotApiService().apply {
            getAgentResult = {
                Result.success(
                    ChatbotAgent(
                        botName = it,
                        localpart = "planner",
                        serverName = "example.org",
                    )
                )
            }
        }
        val presenter = createAgentDetailPresenter(service = service, clipboardHelper = clipboardHelper)

        presenter.test {
            awaitItem().eventSink(AgentDetailEvents.OnAppear)
            val loadedState = awaitStateWhere { it.agent != null && !it.isLoading }

            loadedState.eventSink(AgentDetailEvents.CopyAgentId)
            val copiedState = awaitStateWhere { it.copiedAgentId == "@planner:example.org" }
            assertThat(clipboardHelper.clipboardContents).isEqualTo("@planner:example.org")

            copiedState.eventSink(AgentDetailEvents.ClearError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - start chat opens existing dm`() = runTest {
        val navigator = FakeAgentDetailNavigator()
        val directChatService = FakeAgentDirectChatService().apply {
            existingRoomResult = Result.success(RoomId("!existing:example.org"))
        }
        val service = FakeChatbotApiService().apply {
            getAgentResult = {
                Result.success(
                    ChatbotAgent(
                        botName = it,
                        localpart = "planner",
                        serverName = "example.org",
                    )
                )
            }
        }
        val presenter = createAgentDetailPresenter(
            service = service,
            navigator = navigator,
            directChatService = directChatService,
        )

        presenter.test {
            awaitItem().eventSink(AgentDetailEvents.OnAppear)
            val loadedState = awaitStateWhere { it.canStartChat && !it.isLoading }

            loadedState.eventSink(AgentDetailEvents.StartChat)
            awaitStateWhere { !it.isStartingChat }
            assertThat(navigator.openedRooms.single().identifier).isEqualTo("!existing:example.org")
            assertThat(directChatService.createCalls).isEqualTo(0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - start chat creates dm when no existing room`() = runTest {
        val navigator = FakeAgentDetailNavigator()
        val directChatService = FakeAgentDirectChatService().apply {
            existingRoomResult = Result.success(null)
            createdRoomResult = Result.success(RoomId("!created:example.org"))
        }
        val service = FakeChatbotApiService().apply {
            getAgentResult = {
                Result.success(
                    ChatbotAgent(
                        botName = it,
                        providerAgentId = "@agent:example.org",
                    )
                )
            }
        }
        val presenter = createAgentDetailPresenter(
            service = service,
            navigator = navigator,
            directChatService = directChatService,
        )

        presenter.test {
            awaitItem().eventSink(AgentDetailEvents.OnAppear)
            val loadedState = awaitStateWhere { it.canStartChat && !it.isLoading }

            loadedState.eventSink(AgentDetailEvents.StartChat)
            awaitStateWhere { !it.isStartingChat }
            assertThat(navigator.openedRooms.single().identifier).isEqualTo("!created:example.org")
            assertThat(directChatService.createdUserIds).containsExactly("@agent:example.org")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - leave room calls api and refreshes rooms`() = runTest {
        var listRoomsCalls = 0
        val service = FakeChatbotApiService().apply {
            getAgentResult = { Result.success(aChatbotAgent(botName = it)) }
            listAgentRoomsResult = {
                listRoomsCalls++
                Result.success(
                    if (listRoomsCalls == 1) {
                        listOf(ChatbotAgentRoom(roomId = "!room:example.org", roomName = "Ops"))
                    } else {
                        emptyList()
                    }
                )
            }
        }
        val presenter = createAgentDetailPresenter(service = service)

        presenter.test {
            awaitItem().eventSink(AgentDetailEvents.OnAppear)
            val loadedState = awaitStateWhere { it.rooms.size == 1 && !it.isLoading }

            loadedState.eventSink(AgentDetailEvents.LeaveRoom("!room:example.org"))
            awaitStateWhere { it.rooms.isEmpty() && !it.isLoading }
            assertThat(listRoomsCalls).isAtLeast(2)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createAgentDetailPresenter(
        service: FakeChatbotApiService = FakeChatbotApiService(),
        navigator: AgentDetailNavigator = FakeAgentDetailNavigator(),
        directChatService: AgentDirectChatService = FakeAgentDirectChatService(),
        clipboardHelper: FakeClipboardHelper = FakeClipboardHelper(),
    ): AgentDetailPresenter {
        return AgentDetailPresenter(
            botName = "planner",
            navigator = navigator,
            matrixClient = FakeMatrixClient(),
            chatbotApiServiceFactory = FakeChatbotApiServiceFactory(service),
            directChatService = directChatService,
            clipboardHelper = clipboardHelper,
        )
    }
}

private class FakeAgentDetailNavigator : AgentDetailNavigator {
    val openedRooms = mutableListOf<RoomIdOrAlias>()
    val editedBotNames = mutableListOf<String>()
    val openedSkillsBotNames = mutableListOf<String>()

    override fun onEdit(botName: String) {
        editedBotNames += botName
    }

    override fun onOpenRoom(roomIdOrAlias: RoomIdOrAlias) {
        openedRooms += roomIdOrAlias
    }

    override fun onOpenSkills(botName: String) {
        openedSkillsBotNames += botName
    }
}

private class FakeAgentDirectChatService : AgentDirectChatService {
    var existingRoomResult: Result<RoomId?> = Result.success(null)
    var createdRoomResult: Result<RoomId> = Result.success(RoomId("!created:example.org"))
    var createCalls = 0
    val createdUserIds = mutableListOf<String>()

    override suspend fun findExistingDirectRoom(userId: String): Result<RoomId?> {
        return existingRoomResult
    }

    override suspend fun createDirectRoom(userId: String): Result<RoomId> {
        createCalls++
        createdUserIds += userId
        return createdRoomResult
    }
}

private suspend fun TurbineTestContext<AgentDetailState>.awaitStateWhere(
    predicate: (AgentDetailState) -> Boolean,
): AgentDetailState {
    while (true) {
        val state = awaitItem()
        if (predicate(state)) {
            return state
        }
    }
}

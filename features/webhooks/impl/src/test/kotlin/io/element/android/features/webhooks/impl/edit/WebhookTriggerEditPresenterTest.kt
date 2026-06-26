/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.webhooks.impl.edit

import app.cash.turbine.TurbineTestContext
import com.google.common.truth.Truth.assertThat
import io.element.android.features.webhooks.api.WebhookTriggerEditMode
import io.element.android.libraries.chatbot.api.model.connectors.ChatbotConnectedAccount
import io.element.android.libraries.chatbot.api.model.connectors.ChatbotListConnectedAccountsResponse
import io.element.android.libraries.chatbot.api.model.rooms.ChatbotGetRoomAgentsResponse
import io.element.android.libraries.chatbot.api.model.rooms.ChatbotRoomAgent
import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotCreateWebhookTriggerRequest
import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotUpdateWebhookTriggerRequest
import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotWebhookEventCatalogResponse
import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotWebhookEventConnection
import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotWebhookEventSource
import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotWebhookEventType
import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotWebhookProviderTrigger
import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotWebhookTrigger
import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotWebhookTriggerDraftResponse
import io.element.android.libraries.chatbot.test.FakeChatbotApiService
import io.element.android.libraries.chatbot.test.FakeChatbotApiServiceFactory
import io.element.android.libraries.chatbot.test.aChatbotWebhookTrigger
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.room.RoomMembersState
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.A_ROOM_ID_2
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.room.FakeBaseRoom
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.libraries.matrix.test.room.aRoomMember
import io.element.android.libraries.matrix.test.room.aRoomSummary
import io.element.android.libraries.matrix.test.roomlist.FakeDynamicRoomList
import io.element.android.libraries.matrix.test.roomlist.FakeRoomListService
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.test
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class WebhookTriggerEditPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `present - create mode loads catalog and rooms and preselects room`() = runTest {
        val service = serviceWithCatalog().apply {
            getRoomAgentsResult = { Result.success(ChatbotGetRoomAgentsResponse(listOf(ChatbotRoomAgent(agentId = "agent-a")))) }
        }
        val presenter = createPresenter(
            service = service,
            mode = WebhookTriggerEditMode.Create(prefilledRoomId = A_ROOM_ID),
            matrixClient = matrixClientWithRooms("Room A"),
        )

        presenter.test {
            awaitItem().eventSink(WebhookTriggerEditEvents.OnAppear)
            val loaded = awaitStateWhere { !it.isLoading && it.eventSources.size == 2 && it.availableRooms.size == 1 && it.availableAgents.size == 1 }
            assertThat(loaded.selectedRoomId).isEqualTo(A_ROOM_ID.value)
            assertThat(loaded.availableRooms.single().info.name).isEqualTo("Room A")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - edit mode seeds fields source event types account room and agent`() = runTest {
        val trigger = existingTrigger()
        val service = serviceWithCatalog().apply {
            listConnectedAccountsResult = { _, _, _ -> Result.success(ChatbotListConnectedAccountsResponse(items = listOf(account("account-1"), account("account-2")))) }
            getRoomAgentsResult = { Result.success(ChatbotGetRoomAgentsResponse(listOf(ChatbotRoomAgent(agentId = "agent-edit")))) }
        }
        val presenter = createPresenter(service = service, mode = WebhookTriggerEditMode.Edit(trigger))

        presenter.test {
            awaitItem().eventSink(WebhookTriggerEditEvents.OnAppear)
            val loaded = awaitStateWhere { !it.isLoading && it.selectedAccount?.id == "account-2" && it.selectedAgentId == "agent-edit" }
            assertThat(loaded.name).isEqualTo("Existing")
            assertThat(loaded.description).isEqualTo("Existing description")
            assertThat(loaded.actionPrompt).isEqualTo("Existing action")
            assertThat(loaded.selectedRoomId).isEqualTo(A_ROOM_ID.value)
            assertThat(loaded.selectedSource?.source).isEqualTo("gmail")
            assertThat(loaded.selectedEventTypes).containsExactly("gmail.new_email")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - prefilled room agents are filtered through Matrix joined room members like iOS`() = runTest {
        val service = serviceWithCatalog().apply {
            getRoomAgentsResult = {
                Result.success(
                    ChatbotGetRoomAgentsResponse(
                        listOf(
                            ChatbotRoomAgent(agentId = "@agent-a:server.org", displayName = "Server A"),
                            ChatbotRoomAgent(agentId = "@agent-b:server.org", displayName = "Server B"),
                        )
                    )
                )
            }
        }
        val presenter = createPresenter(
            service = service,
            mode = WebhookTriggerEditMode.Create(prefilledRoomId = A_ROOM_ID),
            matrixClient = matrixClientWithJoinedMembers(
                aRoomMember(UserId("@agent-b:server.org"), displayName = "Matrix B", avatarUrl = "mxc://matrix-b"),
                aRoomMember(UserId("@human:server.org"), displayName = "Human"),
            ),
        )

        presenter.test {
            awaitItem().eventSink(WebhookTriggerEditEvents.OnAppear)
            val loaded = awaitStateWhere { !it.isLoading && it.availableAgents.size == 1 }
            assertThat(loaded.availableAgents.single().agentId).isEqualTo("@agent-b:server.org")
            assertThat(loaded.availableAgents.single().displayName).isEqualTo("Matrix B")
            assertThat(loaded.availableAgents.single().avatarUrl).isEqualTo("mxc://matrix-b")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - select source clears event types account and loads connected accounts`() = runTest {
        val service = serviceWithCatalog().apply {
            listConnectedAccountsResult = { toolkit, _, _ -> Result.success(ChatbotListConnectedAccountsResponse(items = listOf(account("$toolkit-account")))) }
        }
        val presenter = createPresenter(service = service)

        presenter.test {
            awaitItem().eventSink(WebhookTriggerEditEvents.OnAppear)
            val loaded = awaitStateWhere { !it.isLoading && it.eventSources.isNotEmpty() }
            loaded.eventSink(WebhookTriggerEditEvents.ToggleEventType("gmail.new_email"))
            val withEvent = awaitStateWhere { it.selectedEventTypes.contains("gmail.new_email") }
            withEvent.eventSink(WebhookTriggerEditEvents.SelectSource(withEvent.eventSources.first { it.source == "slack" }))
            val selected = awaitStateWhere { it.selectedSource?.source == "slack" && it.selectedAccount?.id == "slack-account" }
            assertThat(selected.selectedEventTypes).isEmpty()
            assertThat(selected.connectedAccounts.map { it.id }).containsExactly("slack-account")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - select room clears agent and loads room agents`() = runTest {
        val service = serviceWithCatalog().apply {
            getRoomAgentsResult = { roomId -> Result.success(ChatbotGetRoomAgentsResponse(listOf(ChatbotRoomAgent(agentId = "agent-$roomId")))) }
        }
        val presenter = createPresenter(service = service)

        presenter.test {
            awaitItem().eventSink(WebhookTriggerEditEvents.OnAppear)
            val loaded = awaitStateWhere { !it.isLoading }
            loaded.eventSink(WebhookTriggerEditEvents.SelectAgent("old-agent"))
            val withAgent = awaitStateWhere { it.selectedAgentId == "old-agent" }
            withAgent.eventSink(WebhookTriggerEditEvents.SelectRoom(A_ROOM_ID_2.value))
            val selected = awaitStateWhere { it.selectedRoomId == A_ROOM_ID_2.value && it.availableAgents.singleOrNull()?.agentId == "agent-${A_ROOM_ID_2.value}" }
            assertThat(selected.selectedAgentId).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - generate draft fills source events name and action`() = runTest {
        val service = serviceWithCatalog().apply {
            draftWebhookTriggerResult = {
                Result.success(ChatbotWebhookTriggerDraftResponse(source = "gmail", eventTypes = listOf("gmail.new_email"), name = "Drafted", actionPrompt = "Do draft"))
            }
        }
        val presenter = createPresenter(service = service)

        presenter.test {
            awaitItem().eventSink(WebhookTriggerEditEvents.OnAppear)
            val loaded = awaitStateWhere { !it.isLoading && it.eventSources.isNotEmpty() }
            loaded.eventSink(WebhookTriggerEditEvents.DraftPromptChanged(" build webhook "))
            val prompted = awaitStateWhere { it.draftPrompt == " build webhook " }
            prompted.eventSink(WebhookTriggerEditEvents.GenerateDraft)
            val drafted = awaitStateWhere { !it.isDrafting && it.name == "Drafted" && it.selectedEventTypes.contains("gmail.new_email") }
            assertThat(drafted.selectedSource?.source).isEqualTo("gmail")
            assertThat(drafted.actionPrompt).isEqualTo("Do draft")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `state - can save requires name event type prompt room and create agent`() = runTest {
        val presenter = createPresenter(service = serviceWithCatalog())

        presenter.test {
            awaitItem().eventSink(WebhookTriggerEditEvents.OnAppear)
            val loaded = awaitStateWhere { !it.isLoading && it.eventSources.isNotEmpty() }
            assertThat(loaded.canSave).isFalse()
            loaded.eventSink(WebhookTriggerEditEvents.NameChanged("Name"))
            awaitStateWhere { it.name == "Name" }.eventSink(WebhookTriggerEditEvents.ActionPromptChanged("Action"))
            awaitStateWhere { it.actionPrompt == "Action" }.eventSink(WebhookTriggerEditEvents.SelectRoom(A_ROOM_ID.value))
            awaitStateWhere { it.selectedRoomId == A_ROOM_ID.value }.eventSink(WebhookTriggerEditEvents.SelectAgent("agent"))
            awaitStateWhere { it.selectedAgentId == "agent" }.eventSink(WebhookTriggerEditEvents.ToggleEventType("gmail.new_email"))
            val savable = awaitStateWhere { it.selectedEventTypes.contains("gmail.new_email") }
            assertThat(savable.canSave).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - create sends iOS request body and emits saved`() = runTest {
        var request: ChatbotCreateWebhookTriggerRequest? = null
        val service = serviceWithCatalog().apply {
            createWebhookTriggerResult = {
                request = it
                Result.success(aChatbotWebhookTrigger(triggerId = "created"))
            }
            listConnectedAccountsResult = { _, _, _ -> Result.success(ChatbotListConnectedAccountsResponse(items = listOf(account("account-1")))) }
        }
        val navigator = FakeWebhookTriggerEditNavigator()
        val presenter = createPresenter(service = service, navigator = navigator)

        presenter.test {
            awaitItem().eventSink(WebhookTriggerEditEvents.OnAppear)
            val loaded = awaitStateWhere { !it.isLoading && it.eventSources.isNotEmpty() }
            loaded.eventSink(WebhookTriggerEditEvents.SelectSource(loaded.eventSources.first { it.source == "gmail" }))
            awaitStateWhere { it.selectedAccount?.id == "account-1" }.eventSink(WebhookTriggerEditEvents.NameChanged("  Name  "))
            awaitStateWhere { it.name == "  Name  " }.eventSink(WebhookTriggerEditEvents.ActionPromptChanged("  Action  "))
            awaitStateWhere { it.actionPrompt == "  Action  " }.eventSink(WebhookTriggerEditEvents.SelectRoom(A_ROOM_ID.value))
            awaitStateWhere { it.selectedRoomId == A_ROOM_ID.value }.eventSink(WebhookTriggerEditEvents.SelectAgent("agent-a"))
            awaitStateWhere { it.selectedAgentId == "agent-a" }.eventSink(WebhookTriggerEditEvents.ToggleEventType("gmail.new_email"))
            awaitStateWhere { it.canSave }.eventSink(WebhookTriggerEditEvents.Save)
            awaitStateWhere { !it.isSaving && navigator.saved.isNotEmpty() }
            assertThat(request).isEqualTo(
                ChatbotCreateWebhookTriggerRequest(
                    agentId = "agent-a",
                    name = "Name",
                    description = null,
                    connectionId = "account-1",
                    eventTypes = listOf("gmail.new_email"),
                    actionPrompt = "Action",
                    roomId = A_ROOM_ID.value,
                )
            )
            assertThat(navigator.saved.single().triggerId).isEqualTo("created")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - update sends metadata request and emits saved`() = runTest {
        var request: ChatbotUpdateWebhookTriggerRequest? = null
        val service = serviceWithCatalog().apply {
            updateWebhookTriggerResult = { _, body ->
                request = body
                Result.success(aChatbotWebhookTrigger(triggerId = "updated"))
            }
        }
        val navigator = FakeWebhookTriggerEditNavigator()
        val presenter = createPresenter(service = service, mode = WebhookTriggerEditMode.Edit(existingTrigger()), navigator = navigator)

        presenter.test {
            awaitItem().eventSink(WebhookTriggerEditEvents.OnAppear)
            val loaded = awaitStateWhere { !it.isLoading && it.canSave }
            loaded.eventSink(WebhookTriggerEditEvents.NameChanged("  Updated  "))
            awaitStateWhere { it.name == "  Updated  " }.eventSink(WebhookTriggerEditEvents.DescriptionChanged("Updated description"))
            awaitStateWhere { it.description == "Updated description" }.eventSink(WebhookTriggerEditEvents.DescriptionChanged(""))
            awaitStateWhere { it.description == "" }.eventSink(WebhookTriggerEditEvents.ActionPromptChanged("  New action  "))
            awaitStateWhere { it.actionPrompt == "  New action  " }.eventSink(WebhookTriggerEditEvents.Save)
            awaitStateWhere { !it.isSaving && navigator.saved.isNotEmpty() }
            assertThat(request).isEqualTo(
                ChatbotUpdateWebhookTriggerRequest(
                    name = "Updated",
                    description = null,
                    actionPrompt = "New action",
                    roomId = A_ROOM_ID.value,
                )
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - connect source opens connect url through navigator`() = runTest {
        var initiateArgs: Pair<String, String>? = null
        val service = serviceWithCatalog().apply {
            initiateConnectionResult = { toolkit, redirectUrl ->
                initiateArgs = toolkit to redirectUrl
                Result.success(io.element.android.libraries.chatbot.api.model.connectors.ChatbotInitiateConnectionResponse("https://connect.example"))
            }
        }
        val navigator = FakeWebhookTriggerEditNavigator()
        val presenter = createPresenter(service = service, navigator = navigator)

        presenter.test {
            awaitItem().eventSink(WebhookTriggerEditEvents.OnAppear)
            val loaded = awaitStateWhere { !it.isLoading && it.eventSources.isNotEmpty() }
            loaded.eventSink(WebhookTriggerEditEvents.SelectSource(loaded.eventSources.first { it.source == "gmail" }))
            awaitStateWhere { it.selectedSource?.source == "gmail" }.eventSink(WebhookTriggerEditEvents.ConnectSource)
            testScheduler.advanceUntilIdle()
            assertThat(initiateArgs).isEqualTo("gmail" to "network.unseal.android://composio-callback?toolkit=gmail")
            assertThat(navigator.openedUrls).containsExactly("https://connect.example")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - failure preserves editable state and exposes error`() = runTest {
        val service = serviceWithCatalog().apply {
            createWebhookTriggerResult = { Result.failure(RuntimeException("network")) }
        }
        val presenter = createPresenter(service = service)

        presenter.test {
            awaitItem().eventSink(WebhookTriggerEditEvents.OnAppear)
            val loaded = awaitStateWhere { !it.isLoading && it.eventSources.isNotEmpty() }
            loaded.eventSink(WebhookTriggerEditEvents.NameChanged("Name"))
            awaitStateWhere { it.name == "Name" }.eventSink(WebhookTriggerEditEvents.ActionPromptChanged("Action"))
            awaitStateWhere { it.actionPrompt == "Action" }.eventSink(WebhookTriggerEditEvents.SelectRoom(A_ROOM_ID.value))
            awaitStateWhere { it.selectedRoomId == A_ROOM_ID.value }.eventSink(WebhookTriggerEditEvents.SelectAgent("agent"))
            awaitStateWhere { it.selectedAgentId == "agent" }.eventSink(WebhookTriggerEditEvents.ToggleEventType("gmail.new_email"))
            awaitStateWhere { it.canSave }.eventSink(WebhookTriggerEditEvents.Save)
            val failed = awaitStateWhere { it.error?.contains("network") == true }
            assertThat(failed.name).isEqualTo("Name")
            assertThat(failed.actionPrompt).isEqualTo("Action")
            assertThat(failed.selectedRoomId).isEqualTo(A_ROOM_ID.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createPresenter(
        service: FakeChatbotApiService = FakeChatbotApiService(),
        mode: WebhookTriggerEditMode = WebhookTriggerEditMode.Create(),
        navigator: FakeWebhookTriggerEditNavigator = FakeWebhookTriggerEditNavigator(),
        matrixClient: FakeMatrixClient = matrixClientWithRooms("Room"),
    ): WebhookTriggerEditPresenter {
        return WebhookTriggerEditPresenter(
            mode = mode,
            navigator = navigator,
            matrixClient = matrixClient,
            chatbotApiServiceFactory = FakeChatbotApiServiceFactory(service),
        )
    }
}

private fun serviceWithCatalog() = FakeChatbotApiService().apply {
    listWebhookEventTypesResult = { Result.success(catalog()) }
}

private fun catalog() = ChatbotWebhookEventCatalogResponse(
    sources = listOf(
        ChatbotWebhookEventSource(
            source = "gmail",
            name = "Gmail",
            connections = listOf(ChatbotWebhookEventConnection(connectionId = "connection")),
            eventTypes = listOf(ChatbotWebhookEventType(eventType = "gmail.new_email", name = "New email")),
        ),
        ChatbotWebhookEventSource(
            source = "slack",
            name = "Slack",
            eventTypes = listOf(ChatbotWebhookEventType(eventType = "slack.message", name = "Message")),
        ),
    )
)

private fun existingTrigger(): ChatbotWebhookTrigger = aChatbotWebhookTrigger(triggerId = "existing").copy(
    name = "Existing",
    description = "Existing description",
    actionPrompt = "Existing action",
    eventTypes = listOf("gmail.new_email"),
    roomId = A_ROOM_ID.value,
    agentId = "agent-edit",
    providerTriggers = listOf(ChatbotWebhookProviderTrigger(connectionId = "account-2")),
)

private fun account(id: String) = ChatbotConnectedAccount(
    id = id,
    toolkit = "gmail",
    status = "active",
    createdAt = "2026-06-09T00:00:00Z",
    updatedAt = "2026-06-09T00:00:00Z",
)

private fun matrixClientWithRooms(vararg names: String): FakeMatrixClient {
    val roomList = FakeDynamicRoomList(
        summaries = MutableStateFlow(names.mapIndexed { index, name ->
            aRoomSummary(roomId = if (index == 0) A_ROOM_ID else A_ROOM_ID_2, name = name)
        }),
    )
    return FakeMatrixClient(roomListService = FakeRoomListService(allRooms = roomList))
}

private fun matrixClientWithJoinedMembers(
    vararg members: io.element.android.libraries.matrix.api.room.RoomMember,
): FakeMatrixClient {
    val joinedRoom = FakeJoinedRoom(
        baseRoom = FakeBaseRoom(
            updateMembersResult = {},
        ).apply {
            givenRoomMembersState(RoomMembersState.Ready(persistentListOf(*members)))
        }
    )
    return matrixClientWithRooms("Room").apply {
        givenGetRoomResult(A_ROOM_ID, joinedRoom)
    }
}

private class FakeWebhookTriggerEditNavigator : WebhookTriggerEditNavigator {
    val saved = mutableListOf<ChatbotWebhookTrigger>()
    val openedUrls = mutableListOf<String>()
    var cancelledCalls = 0

    override fun onSaved(trigger: ChatbotWebhookTrigger) {
        saved += trigger
    }

    override fun onCancelled() {
        cancelledCalls++
    }

    override fun onOpenConnectUrl(url: String) {
        openedUrls += url
    }
}

private suspend fun TurbineTestContext<WebhookTriggerEditState>.awaitStateWhere(
    predicate: (WebhookTriggerEditState) -> Boolean,
): WebhookTriggerEditState {
    while (true) {
        val state = awaitItem()
        if (predicate(state)) {
            return state
        }
    }
}

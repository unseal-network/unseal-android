/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.webhooks.impl.list

import app.cash.turbine.TurbineTestContext
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.chatbot.api.model.rooms.ChatbotGetRoomAgentsResponse
import io.element.android.libraries.chatbot.api.model.rooms.ChatbotRoomAgent
import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotWebhookEventCatalogResponse
import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotWebhookEventSource
import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotWebhookEventType
import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotWebhookTrigger
import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotWebhookTriggerStatus
import io.element.android.libraries.chatbot.test.FakeChatbotApiService
import io.element.android.libraries.chatbot.test.FakeChatbotApiServiceFactory
import io.element.android.libraries.chatbot.test.aChatbotWebhookTrigger
import io.element.android.libraries.matrix.api.core.RoomId
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

class WebhookTriggerListPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `present - global loads event catalog rooms and triggers once`() = runTest {
        var catalogCalls = 0
        var triggerCalls = 0
        val service = FakeChatbotApiService().apply {
            listWebhookEventTypesResult = {
                catalogCalls++
                Result.success(catalog())
            }
            listWebhookTriggersResult = { _, _, _, _ ->
                triggerCalls++
                Result.success(listOf(trigger("one")))
            }
        }
        val presenter = createPresenter(
            service = service,
            matrixClient = matrixClientWithRooms(room("Room One")),
        )

        presenter.test {
            awaitItem().eventSink(WebhookTriggerListEvents.OnAppear)
            val loaded = awaitStateWhere { !it.isLoading && it.triggers.size == 1 && it.availableRooms.size == 1 && it.eventSources.size == 1 }
            assertThat(loaded.triggers.single().triggerId).isEqualTo("one")
            assertThat(loaded.availableRooms.single().info.name).isEqualTo("Room One")
            assertThat(catalogCalls).isEqualTo(1)
            assertThat(triggerCalls).isEqualTo(1)
            loaded.eventSink(WebhookTriggerListEvents.OnAppear)
            assertThat(catalogCalls).isEqualTo(1)
            assertThat(triggerCalls).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - room mode loads fixed room triggers and room agents`() = runTest {
        val queriedRooms = mutableListOf<String?>()
        val service = FakeChatbotApiService().apply {
            getRoomAgentsResult = { roomId ->
                Result.success(ChatbotGetRoomAgentsResponse(agents = listOf(ChatbotRoomAgent(agentId = "agent-a", displayName = "Agent A"))))
            }
            listWebhookTriggersResult = { _, _, roomId, _ ->
                queriedRooms += roomId
                Result.success(listOf(trigger("one", roomId = roomId ?: "missing")))
            }
        }
        val presenter = createPresenter(service = service, mode = WebhookTriggerListMode.Room(A_ROOM_ID, "Room"))

        presenter.test {
            awaitItem().eventSink(WebhookTriggerListEvents.OnAppear)
            val loaded = awaitStateWhere { !it.isLoading && it.triggers.size == 1 && it.availableAgents.size == 1 }
            assertThat(queriedRooms).containsExactly(A_ROOM_ID.value)
            assertThat(loaded.availableAgents.single().agentId).isEqualTo("agent-a")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - room mode filters agents through Matrix joined room members like iOS`() = runTest {
        val service = FakeChatbotApiService().apply {
            getRoomAgentsResult = {
                Result.success(
                    ChatbotGetRoomAgentsResponse(
                        agents = listOf(
                            ChatbotRoomAgent(agentId = "@agent-a:server.org", displayName = "Server A"),
                            ChatbotRoomAgent(agentId = "@agent-b:server.org", displayName = "Server B"),
                        )
                    )
                )
            }
            listWebhookTriggersResult = { _, _, roomId, _ ->
                Result.success(listOf(trigger("one", roomId = roomId ?: "missing")))
            }
        }
        val presenter = createPresenter(
            service = service,
            mode = WebhookTriggerListMode.Room(A_ROOM_ID, "Room"),
            matrixClient = matrixClientWithJoinedMembers(
                aRoomMember(UserId("@agent-b:server.org"), displayName = "Matrix B", avatarUrl = "mxc://matrix-b"),
                aRoomMember(UserId("@human:server.org"), displayName = "Human"),
            ),
        )

        presenter.test {
            awaitItem().eventSink(WebhookTriggerListEvents.OnAppear)
            val loaded = awaitStateWhere { !it.isLoading && it.availableAgents.size == 1 }
            assertThat(loaded.availableAgents.single().agentId).isEqualTo("@agent-b:server.org")
            assertThat(loaded.availableAgents.single().displayName).isEqualTo("Matrix B")
            assertThat(loaded.availableAgents.single().avatarUrl).isEqualTo("mxc://matrix-b")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - search trims and matches iOS fields`() = runTest {
        val triggers = listOf(
            trigger("name", name = "Build Shipped"),
            trigger("source", source = "github"),
            trigger("event", eventTypes = listOf("gmail.new_email")),
            trigger("agent", agentId = "release-agent"),
            trigger("prompt", actionPrompt = "Summarize invoice"),
            trigger("room", roomId = "!search-room:example"),
        )
        val service = FakeChatbotApiService().apply {
            listWebhookTriggersResult = { _, _, _, _ -> Result.success(triggers) }
        }
        val presenter = createPresenter(service = service)

        presenter.test {
            awaitItem().eventSink(WebhookTriggerListEvents.OnAppear)
            val loaded = awaitStateWhere { !it.isLoading && it.triggers.size == triggers.size }
            loaded.eventSink(WebhookTriggerListEvents.SearchChanged("  EMAIL  "))
            val filtered = awaitStateWhere { it.searchQuery == "  EMAIL  " }
            assertThat(filtered.filteredTriggers.map { it.triggerId }).containsExactly("event")
            filtered.eventSink(WebhookTriggerListEvents.SearchChanged("invoice"))
            val prompt = awaitStateWhere { it.searchQuery == "invoice" }
            assertThat(prompt.filteredTriggers.map { it.triggerId }).containsExactly("prompt")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - room filter reloads global triggers with selected room`() = runTest {
        val queriedRooms = mutableListOf<String?>()
        val service = FakeChatbotApiService().apply {
            listWebhookTriggersResult = { _, _, roomId, _ ->
                queriedRooms += roomId
                Result.success(listOf(trigger("for-room", roomId = roomId ?: "all")))
            }
        }
        val presenter = createPresenter(service = service)

        presenter.test {
            awaitItem().eventSink(WebhookTriggerListEvents.OnAppear)
            val loaded = awaitStateWhere { !it.isLoading && it.triggers.isNotEmpty() }
            loaded.eventSink(WebhookTriggerListEvents.SelectRoomFilter(A_ROOM_ID_2.value))
            val filtered = awaitStateWhere { !it.isLoading && it.selectedRoomId == A_ROOM_ID_2.value && it.triggers.single().roomId == A_ROOM_ID_2.value }
            assertThat(queriedRooms).containsExactly(null, A_ROOM_ID_2.value).inOrder()
            assertThat(filtered.selectedRoomId).isEqualTo(A_ROOM_ID_2.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - agent filter filters room triggers locally`() = runTest {
        var triggerCalls = 0
        val service = FakeChatbotApiService().apply {
            listWebhookTriggersResult = { _, _, _, _ ->
                triggerCalls++
                Result.success(listOf(trigger("one", agentId = "agent-a"), trigger("two", agentId = "agent-b")))
            }
        }
        val presenter = createPresenter(service = service, mode = WebhookTriggerListMode.Room(A_ROOM_ID, "Room"))

        presenter.test {
            awaitItem().eventSink(WebhookTriggerListEvents.OnAppear)
            val loaded = awaitStateWhere { !it.isLoading && it.triggers.size == 2 }
            loaded.eventSink(WebhookTriggerListEvents.SelectAgentFilter("agent-b"))
            val filtered = awaitStateWhere { it.selectedAgentId == "agent-b" }
            assertThat(filtered.filteredTriggers.map { it.triggerId }).containsExactly("two")
            assertThat(triggerCalls).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - toggle status calls api with inverse enabled value and reloads`() = runTest {
        val statusRequests = mutableListOf<Pair<String, Boolean>>()
        var triggerCalls = 0
        val service = FakeChatbotApiService().apply {
            listWebhookTriggersResult = { _, _, _, _ ->
                triggerCalls++
                Result.success(listOf(trigger("toggle", status = ChatbotWebhookTriggerStatus.Enabled)))
            }
            updateWebhookTriggerStatusResult = { id, enabled ->
                statusRequests += id to enabled
                Result.success(io.element.android.libraries.chatbot.api.model.webhooks.ChatbotWebhookTriggerStatusResponse(id, "disabled"))
            }
        }
        val navigator = FakeWebhookTriggerListNavigator()
        val presenter = createPresenter(service = service, navigator = navigator)

        presenter.test {
            awaitItem().eventSink(WebhookTriggerListEvents.OnAppear)
            val loaded = awaitStateWhere { !it.isLoading && it.triggers.singleOrNull()?.triggerId == "toggle" }
            loaded.eventSink(WebhookTriggerListEvents.ToggleStatus(loaded.triggers.single()))
            awaitStateWhere { !it.isLoading && it.togglingTriggerId == null && triggerCalls == 2 }
            assertThat(statusRequests).containsExactly("toggle" to false)
            assertThat(navigator.changedCalls).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - delete requires confirmation then deletes and reloads`() = runTest {
        val deleted = mutableListOf<String>()
        var triggerCalls = 0
        val service = FakeChatbotApiService().apply {
            listWebhookTriggersResult = { _, _, _, _ ->
                triggerCalls++
                Result.success(if (deleted.isEmpty()) listOf(trigger("delete")) else emptyList())
            }
            deleteWebhookTriggerResult = {
                deleted += it
                Result.success(io.element.android.libraries.chatbot.api.model.webhooks.ChatbotWebhookTriggerDeleteResponse(it, deleted = true))
            }
        }
        val presenter = createPresenter(service = service)

        presenter.test {
            awaitItem().eventSink(WebhookTriggerListEvents.OnAppear)
            val loaded = awaitStateWhere { !it.isLoading && it.triggers.size == 1 }
            loaded.eventSink(WebhookTriggerListEvents.RequestDelete(loaded.triggers.single()))
            val confirming = awaitStateWhere { it.deleteConfirmationTriggerId == "delete" }
            assertThat(deleted).isEmpty()
            confirming.eventSink(WebhookTriggerListEvents.ConfirmDelete)
            val deletedState = awaitStateWhere { !it.isLoading && it.triggers.isEmpty() && it.deleteConfirmationTriggerId == null }
            assertThat(deleted).containsExactly("delete")
            assertThat(triggerCalls).isEqualTo(2)
            assertThat(deletedState.filteredTriggers).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - load failure preserves previous data and exposes error`() = runTest {
        var fail = false
        val service = FakeChatbotApiService().apply {
            listWebhookTriggersResult = { _, _, _, _ ->
                if (fail) {
                    Result.failure(RuntimeException("network"))
                } else {
                    Result.success(listOf(trigger("kept")))
                }
            }
        }
        val presenter = createPresenter(service = service)

        presenter.test {
            awaitItem().eventSink(WebhookTriggerListEvents.OnAppear)
            val loaded = awaitStateWhere { !it.isLoading && it.triggers.singleOrNull()?.triggerId == "kept" }
            fail = true
            loaded.eventSink(WebhookTriggerListEvents.Refresh)
            val failed = awaitStateWhere { !it.isLoading && it.error?.contains("network") == true }
            assertThat(failed.triggers.map { it.triggerId }).containsExactly("kept")
            assertThat(failed.filteredTriggers.map { it.triggerId }).containsExactly("kept")
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createPresenter(
        service: FakeChatbotApiService = FakeChatbotApiService(),
        mode: WebhookTriggerListMode = WebhookTriggerListMode.Global,
        navigator: FakeWebhookTriggerListNavigator = FakeWebhookTriggerListNavigator(),
        matrixClient: FakeMatrixClient = FakeMatrixClient(),
    ): WebhookTriggerListPresenter {
        return WebhookTriggerListPresenter(
            mode = mode,
            navigator = navigator,
            matrixClient = matrixClient,
            chatbotApiServiceFactory = FakeChatbotApiServiceFactory(service),
        )
    }
}

private fun trigger(
    id: String,
    name: String = "Trigger $id",
    source: String? = null,
    eventTypes: List<String> = listOf("event.$id"),
    agentId: String = "agent",
    actionPrompt: String = "ping $id",
    roomId: String = A_ROOM_ID.value,
    status: ChatbotWebhookTriggerStatus = ChatbotWebhookTriggerStatus.Enabled,
): ChatbotWebhookTrigger {
    return aChatbotWebhookTrigger(triggerId = id).copy(
        name = name,
        source = source,
        eventTypes = eventTypes,
        agentId = agentId,
        actionPrompt = actionPrompt,
        roomId = roomId,
        status = status,
    )
}

private fun catalog() = ChatbotWebhookEventCatalogResponse(
    sources = listOf(
        ChatbotWebhookEventSource(
            source = "gmail",
            name = "Gmail",
            eventTypes = listOf(ChatbotWebhookEventType(eventType = "gmail.new_email", name = "New email")),
        )
    )
)

private fun room(name: String) = aRoomSummary(roomId = A_ROOM_ID, name = name)

private fun matrixClientWithRooms(vararg rooms: io.element.android.libraries.matrix.api.roomlist.RoomSummary): FakeMatrixClient {
    val roomList = FakeDynamicRoomList(
        summaries = MutableStateFlow(rooms.toList()),
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
    return matrixClientWithRooms(room("Room")).apply {
        givenGetRoomResult(A_ROOM_ID, joinedRoom)
    }
}

private class FakeWebhookTriggerListNavigator : WebhookTriggerListNavigator {
    val createRoomIds = mutableListOf<RoomId?>()
    val edited = mutableListOf<ChatbotWebhookTrigger>()
    var doneCalls = 0
    var changedCalls = 0

    override fun onCreateTrigger(prefilledRoomId: RoomId?) {
        createRoomIds += prefilledRoomId
    }

    override fun onEditTrigger(trigger: ChatbotWebhookTrigger) {
        edited += trigger
    }

    override fun onDone() {
        doneCalls++
    }

    override fun onTriggersChanged() {
        changedCalls++
    }
}

private suspend fun TurbineTestContext<WebhookTriggerListState>.awaitStateWhere(
    predicate: (WebhookTriggerListState) -> Boolean,
): WebhookTriggerListState {
    while (true) {
        val state = awaitItem()
        if (predicate(state)) {
            return state
        }
    }
}

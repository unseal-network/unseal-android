/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.agentmanagement.impl.edit

import app.cash.turbine.TurbineTestContext
import com.google.common.truth.Truth.assertThat
import io.element.android.features.agentmanagement.impl.shared.AgentDirectChatService
import io.element.android.libraries.chatbot.api.ChatbotApiError
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgent
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgentConfig
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgentProvider
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgentProviderInfo
import io.element.android.libraries.chatbot.api.model.agent.ChatbotCreateAgentRequest
import io.element.android.libraries.chatbot.api.model.agent.ChatbotProviderModel
import io.element.android.libraries.chatbot.api.model.agent.ChatbotUpdateAgentRequest
import io.element.android.libraries.chatbot.test.FakeChatbotApiService
import io.element.android.libraries.chatbot.test.FakeChatbotApiServiceFactory
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AgentEditPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `present - create mode defaults private auto join and orders providers like iOS`() = runTest {
        val service = FakeChatbotApiService().apply {
            getProvidersResult = {
                Result.success(
                    listOf(
                        aProvider("openai", models = listOf(aModel("gpt-4o"))),
                        aProvider("unseal", models = listOf(aModel("agent-default"))),
                    )
                )
            }
        }
        val presenter = createAgentEditPresenter(service = service)

        presenter.test {
            val initialState = awaitItem()
            assertThat(initialState.form.isPublic).isFalse()
            assertThat(initialState.form.autoJoin).isTrue()
            initialState.eventSink(AgentEditEvents.OnAppear)

            val loadedState = awaitStateWhere { it.providers.size == 2 && !it.isLoading }
            assertThat(loadedState.providers.map { it.id }).containsExactly("unseal", "openai").inOrder()
            assertThat(loadedState.form.providerId).isEqualTo("unseal")
            assertThat(loadedState.form.model).isEqualTo("agent-default")
            assertThat(loadedState.needsApiKey).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - provider change auto-selects first available model and exposes base url support`() = runTest {
        val service = FakeChatbotApiService().apply {
            getProvidersResult = {
                Result.success(
                    listOf(
                        aProvider("unseal", models = listOf(aModel("agent-default"))),
                        aProvider(
                            id = "openai",
                            supportsBaseUrl = true,
                            models = listOf(aModel("gpt-4o"), aModel("gpt-4.1")),
                        ),
                    )
                )
            }
        }
        val presenter = createAgentEditPresenter(service = service)

        presenter.test {
            awaitItem().eventSink(AgentEditEvents.OnAppear)
            val loadedState = awaitStateWhere { it.providers.size == 2 && !it.isLoading }

            loadedState.eventSink(AgentEditEvents.ProviderChanged("openai"))
            val openAiState = awaitStateWhere { it.form.providerId == "openai" }
            assertThat(openAiState.form.model).isEqualTo("gpt-4o")
            assertThat(openAiState.needsApiKey).isTrue()
            assertThat(openAiState.supportsBaseUrl).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - name availability debounces and maps 404 to available`() = runTest {
        val service = FakeChatbotApiService().apply {
            getAgentResult = { Result.failure(ChatbotApiError.HttpError(404, null)) }
        }
        val presenter = createAgentEditPresenter(service = service)

        presenter.test {
            val initialState = awaitItem()
            initialState.eventSink(AgentEditEvents.BotNameChanged(" planner "))
            awaitStateWhere { it.nameAvailability == AgentNameAvailability.Checking }

            advanceTimeBy(449)
            expectNoEvents()
            advanceTimeBy(1)
            assertThat(awaitStateWhere { it.nameAvailability == AgentNameAvailability.Available }.form.botName).isEqualTo(" planner ")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - create submit trims optional fields maps blanks to null creates dm and joins room`() = runTest {
        var capturedRequest: ChatbotCreateAgentRequest? = null
        val service = FakeChatbotApiService().apply {
            getProvidersResult = { Result.success(listOf(aProvider("unseal", models = listOf(aModel("agent-default"))))) }
            getAgentResult = { Result.failure(ChatbotApiError.HttpError(404, null)) }
            createAgentResult = { request ->
                capturedRequest = request
                Result.success(
                    ChatbotAgent(
                        botName = request.botName,
                        localpart = request.botName,
                        serverName = "example.org",
                        displayName = request.displayName,
                        provider = request.provider,
                        model = request.model,
                        isPublic = request.isPublic,
                    )
                )
            }
        }
        val directChatService = FakeAgentDirectChatService().apply {
            createdRoomResult = Result.success(RoomId("!dm:example.org"))
        }
        val presenter = createAgentEditPresenter(
            service = service,
            directChatService = directChatService,
        )

        presenter.test {
            awaitItem().eventSink(AgentEditEvents.OnAppear)
            val loadedState = awaitStateWhere { it.providers.isNotEmpty() && !it.isLoading }
            loadedState.eventSink(AgentEditEvents.BotNameChanged(" planner "))
            awaitItem().eventSink(AgentEditEvents.DisplayNameChanged(" Planner "))
            awaitItem().eventSink(AgentEditEvents.DescriptionChanged("   "))
            awaitItem().eventSink(AgentEditEvents.ApiKeyChanged("   "))
            awaitItem().eventSink(AgentEditEvents.BaseUrlChanged(" https://api.example "))
            awaitItem().eventSink(AgentEditEvents.SoulChanged(" helpful "))
            awaitItem().eventSink(AgentEditEvents.Submit)

            val successState = awaitStateWhere { it.phase is AgentEditPhase.Success }
            val summary = (successState.phase as AgentEditPhase.Success).summary
            assertThat(summary.botName).isEqualTo("planner")
            assertThat(summary.directRoomId?.value).isEqualTo("!dm:example.org")
            assertThat(capturedRequest?.botName).isEqualTo("planner")
            assertThat(capturedRequest?.displayName).isEqualTo("Planner")
            assertThat(capturedRequest?.description).isNull()
            assertThat(capturedRequest?.apiKey).isNull()
            assertThat(capturedRequest?.baseUrl).isEqualTo("https://api.example")
            assertThat(capturedRequest?.soul).isEqualTo("helpful")
            assertThat(capturedRequest?.settings?.autoJoin).isTrue()
            assertThat(directChatService.createdUserIds).containsExactly("@planner:example.org")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - create submit reuses existing dm when available`() = runTest {
        val service = FakeChatbotApiService().apply {
            getAgentResult = { Result.failure(ChatbotApiError.HttpError(404, null)) }
            createAgentResult = { request ->
                Result.success(
                    ChatbotAgent(
                        botName = request.botName,
                        localpart = request.botName,
                        serverName = "example.org",
                    )
                )
            }
        }
        val directChatService = FakeAgentDirectChatService().apply {
            existingRoomResult = Result.success(RoomId("!existing:example.org"))
        }
        val presenter = createAgentEditPresenter(
            service = service,
            directChatService = directChatService,
        )

        presenter.test {
            awaitItem().eventSink(AgentEditEvents.BotNameChanged("planner"))
            awaitItem().eventSink(AgentEditEvents.Submit)

            val successState = awaitStateWhere { it.phase is AgentEditPhase.Success }
            val summary = (successState.phase as AgentEditPhase.Success).summary
            assertThat(summary.directRoomId?.value).isEqualTo("!existing:example.org")
            assertThat(directChatService.createdUserIds).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - create submit maps backend already-exists 500 to name conflict`() = runTest {
        val service = FakeChatbotApiService().apply {
            getAgentResult = { Result.failure(ChatbotApiError.HttpError(404, null)) }
            createAgentResult = { Result.failure(ChatbotApiError.HttpError(500, "agent already exists")) }
        }
        val presenter = createAgentEditPresenter(service = service)

        presenter.test {
            awaitItem().eventSink(AgentEditEvents.BotNameChanged("planner"))
            awaitItem().eventSink(AgentEditEvents.Submit)

            val conflictState = awaitStateWhere { it.nameAvailability == AgentNameAvailability.Taken && it.error != null }
            assertThat(conflictState.phase).isEqualTo(AgentEditPhase.Editing)
            assertThat(conflictState.error).contains("already exists")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - edit mode preloads agent and update emits updated action`() = runTest {
        var capturedRequest: ChatbotUpdateAgentRequest? = null
        val navigator = FakeAgentEditNavigator()
        val service = FakeChatbotApiService().apply {
            getProvidersResult = { Result.success(listOf(aProvider("openai", models = listOf(aModel("gpt-4o"))))) }
            getAgentResult = {
                Result.success(
                    ChatbotAgent(
                        botName = "planner",
                        displayName = "Planner",
                        description = "Plans",
                        avatarUrl = "mxc://avatar",
                        isPublic = true,
                        provider = "openai",
                        model = "gpt-4o",
                        apiKey = "stored",
                        baseUrl = "https://old.example",
                        soul = "be useful",
                        config = ChatbotAgentConfig(autoJoin = false),
                    )
                )
            }
            updateAgentResult = { _, request ->
                capturedRequest = request
                Result.success(ChatbotAgent(botName = "planner"))
            }
        }
        val presenter = createAgentEditPresenter(
            mode = AgentEditMode.Edit("planner"),
            service = service,
            navigator = navigator,
        )

        presenter.test {
            awaitItem().eventSink(AgentEditEvents.OnAppear)
            val loadedState = awaitStateWhere { it.form.displayName == "Planner" && !it.isLoading }
            assertThat(loadedState.form.autoJoin).isFalse()
            assertThat(loadedState.form.providerId).isEqualTo("openai")

            loadedState.eventSink(AgentEditEvents.DescriptionChanged(" Updated "))
            awaitItem().eventSink(AgentEditEvents.AutoJoinChanged(true))
            awaitItem().eventSink(AgentEditEvents.Submit)
            awaitStateWhere { navigator.updatedBotNames.contains("planner") }

            assertThat(capturedRequest?.description).isEqualTo("Updated")
            assertThat(capturedRequest?.settings?.autoJoin).isTrue()
            assertThat(navigator.updatedBotNames).containsExactly("planner")
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createAgentEditPresenter(
        mode: AgentEditMode = AgentEditMode.Create,
        service: FakeChatbotApiService = FakeChatbotApiService(),
        navigator: AgentEditNavigator = FakeAgentEditNavigator(),
        directChatService: AgentDirectChatService = FakeAgentDirectChatService(),
    ): AgentEditPresenter {
        return AgentEditPresenter(
            mode = mode,
            navigator = navigator,
            matrixClient = FakeMatrixClient(),
            chatbotApiServiceFactory = FakeChatbotApiServiceFactory(service),
            directChatService = directChatService,
        )
    }
}

private class FakeAgentEditNavigator : AgentEditNavigator {
    val created = mutableListOf<Pair<String, RoomId?>>()
    val updatedBotNames = mutableListOf<String>()

    override fun onCreated(botName: String, directRoomId: RoomId?) {
        created += botName to directRoomId
    }

    override fun onUpdated(botName: String) {
        updatedBotNames += botName
    }
}

private class FakeAgentDirectChatService : AgentDirectChatService {
    var existingRoomResult: Result<RoomId?> = Result.success(null)
    var createdRoomResult: Result<RoomId> = Result.success(RoomId("!created:example.org"))
    val createdUserIds = mutableListOf<String>()

    override suspend fun findExistingDirectRoom(userId: String): Result<RoomId?> {
        return existingRoomResult
    }

    override suspend fun createDirectRoom(userId: String): Result<RoomId> {
        createdUserIds += userId
        return createdRoomResult
    }
}

private fun aProvider(
    id: String,
    supportsBaseUrl: Boolean = false,
    models: List<ChatbotProviderModel> = emptyList(),
): ChatbotAgentProvider {
    return ChatbotAgentProvider(
        id = id,
        displayName = id,
        info = ChatbotAgentProviderInfo(
            displayName = id,
            supportsBaseUrl = supportsBaseUrl,
            models = models,
        ),
    )
}

private fun aModel(id: String): ChatbotProviderModel {
    return ChatbotProviderModel(
        id = id,
        displayName = id,
    )
}

private suspend fun TurbineTestContext<AgentEditState>.awaitStateWhere(
    predicate: (AgentEditState) -> Boolean,
): AgentEditState {
    while (true) {
        val state = awaitItem()
        if (predicate(state)) {
            return state
        }
    }
}

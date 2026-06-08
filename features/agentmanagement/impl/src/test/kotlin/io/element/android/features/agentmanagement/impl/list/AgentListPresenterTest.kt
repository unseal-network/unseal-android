/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.agentmanagement.impl.list

import app.cash.turbine.TurbineTestContext
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgent
import io.element.android.libraries.chatbot.test.FakeChatbotApiService
import io.element.android.libraries.chatbot.test.FakeChatbotApiServiceFactory
import io.element.android.libraries.chatbot.test.aChatbotAgent
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class AgentListPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `present - loads agents once and sorts by display title`() = runTest {
        var calls = 0
        val service = FakeChatbotApiService().apply {
            listAgentsResult = {
                calls++
                Result.success(
                    listOf(
                        aChatbotAgent(botName = "zeta", displayName = null),
                        aChatbotAgent(botName = "beta", displayName = "Beta"),
                        aChatbotAgent(botName = "alpha", displayName = "Alpha"),
                    )
                )
            }
        }
        val presenter = createAgentListPresenter(service = service)

        presenter.test {
            val initialState = awaitItem()
            initialState.eventSink(AgentListEvents.OnAppear)

            val loadedState = awaitStateWhere { it.agents.size == 3 && !it.isLoading }
            assertThat(loadedState.agents.map { it.botName }).containsExactly("alpha", "beta", "zeta").inOrder()

            loadedState.eventSink(AgentListEvents.OnAppear)
            assertThat(calls).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - refresh reloads agents`() = runTest {
        var calls = 0
        val service = FakeChatbotApiService().apply {
            listAgentsResult = {
                calls++
                Result.success(listOf(aChatbotAgent(botName = "agent-$calls")))
            }
        }
        val presenter = createAgentListPresenter(service = service)

        presenter.test {
            awaitItem().eventSink(AgentListEvents.OnAppear)
            val loadedState = awaitStateWhere { it.agents.singleOrNull()?.botName == "agent-1" && !it.isLoading }

            loadedState.eventSink(AgentListEvents.Refresh)
            assertThat(awaitStateWhere { it.agents.singleOrNull()?.botName == "agent-2" && !it.isLoading }.agents.single().botName).isEqualTo("agent-2")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - search filters bot name display name and description case insensitively`() = runTest {
        val service = FakeChatbotApiService().apply {
            listAgentsResult = {
                Result.success(
                    listOf(
                        ChatbotAgent(botName = "planner", displayName = "Planner", description = "Plans work"),
                        ChatbotAgent(botName = "writer", displayName = "Writer", description = "Drafts docs"),
                        ChatbotAgent(botName = "reviewer", displayName = "Reviewer", description = "Checks PRs"),
                    )
                )
            }
        }
        val presenter = createAgentListPresenter(service = service)

        presenter.test {
            awaitItem().eventSink(AgentListEvents.OnAppear)
            val loadedState = awaitStateWhere { it.agents.size == 3 && !it.isLoading }

            loadedState.eventSink(AgentListEvents.SearchQueryChanged("DRAFTS"))
            val draftsState = awaitStateWhere { it.searchQuery == "DRAFTS" }
            assertThat(draftsState.filteredAgents.map { it.botName }).containsExactly("writer")

            draftsState.eventSink(AgentListEvents.SearchQueryChanged(" plan "))
            val planState = awaitStateWhere { it.searchQuery == " plan " }
            assertThat(planState.filteredAgents.map { it.botName }).containsExactly("planner")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - load failure keeps previous data and exposes error`() = runTest {
        var fail = false
        val service = FakeChatbotApiService().apply {
            listAgentsResult = {
                if (fail) {
                    Result.failure(IllegalStateException("network down"))
                } else {
                    Result.success(listOf(aChatbotAgent(botName = "stable")))
                }
            }
        }
        val presenter = createAgentListPresenter(service = service)

        presenter.test {
            awaitItem().eventSink(AgentListEvents.OnAppear)
            val loadedState = awaitStateWhere { it.agents.singleOrNull()?.botName == "stable" && !it.isLoading }
            assertThat(loadedState.agents.map { it.botName }).containsExactly("stable")

            fail = true
            loadedState.eventSink(AgentListEvents.Refresh)
            val failedState = awaitStateWhere { it.error?.contains("network down") == true && !it.isLoading }
            assertThat(failedState.agents.map { it.botName }).containsExactly("stable")
            assertThat(failedState.error).contains("network down")
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createAgentListPresenter(
        service: FakeChatbotApiService = FakeChatbotApiService(),
        navigator: AgentListNavigator = NoopAgentListNavigator,
    ): AgentListPresenter {
        return AgentListPresenter(
            navigator = navigator,
            matrixClient = FakeMatrixClient(),
            chatbotApiServiceFactory = FakeChatbotApiServiceFactory(service),
        )
    }
}

private object NoopAgentListNavigator : AgentListNavigator {
    override fun onCreateAgent() = Unit
    override fun onOpenAgent(botName: String) = Unit
    override fun onOpenSkills() = Unit
}

private suspend fun TurbineTestContext<AgentListState>.awaitStateWhere(
    predicate: (AgentListState) -> Boolean,
): AgentListState {
    while (true) {
        val state = awaitItem()
        if (predicate(state)) {
            return state
        }
    }
}

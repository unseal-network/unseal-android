/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.agentmanagement.impl.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.features.agentmanagement.impl.shared.displayTitle
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.chatbot.api.ChatbotApiServiceFactory
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgent
import io.element.android.libraries.matrix.api.MatrixClient
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@AssistedInject
class AgentListPresenter(
    @Assisted private val navigator: AgentListNavigator,
    private val matrixClient: MatrixClient,
    private val chatbotApiServiceFactory: ChatbotApiServiceFactory,
) : Presenter<AgentListState> {
    @AssistedFactory
    interface Factory {
        fun create(navigator: AgentListNavigator): AgentListPresenter
    }

    @Composable
    override fun present(): AgentListState {
        val coroutineScope = rememberCoroutineScope()
        var agents by remember { mutableStateOf(emptyList<ChatbotAgent>()) }
        var searchQuery by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        var hasLoadedOnce by remember { mutableStateOf(false) }

        fun loadAgents(isInitial: Boolean) {
            if (isInitial && hasLoadedOnce) return
            coroutineScope.launch {
                isLoading = true
                chatbotApiServiceFactory.createForHomeserver(matrixClient)
                    .listAgents()
                    .onSuccess { freshAgents ->
                        agents = freshAgents.sortedBy { it.displayTitle() }
                        error = null
                    }
                    .onFailure {
                        error = it.message ?: it::class.simpleName ?: "Failed to load agents"
                    }
                isLoading = false
                hasLoadedOnce = true
            }
        }

        fun handleEvent(event: AgentListEvents) {
            when (event) {
                AgentListEvents.OnAppear -> loadAgents(isInitial = true)
                AgentListEvents.Refresh -> loadAgents(isInitial = false)
                is AgentListEvents.SearchQueryChanged -> searchQuery = event.query
                AgentListEvents.CreateAgent -> navigator.onCreateAgent()
                is AgentListEvents.SelectAgent -> navigator.onOpenAgent(event.botName)
                AgentListEvents.OpenSkills -> navigator.onOpenSkills()
                AgentListEvents.ClearError -> error = null
            }
        }

        return AgentListState(
            agents = agents.toImmutableList(),
            filteredAgents = agents.filteredBy(searchQuery).toImmutableList(),
            searchQuery = searchQuery,
            isLoading = isLoading,
            error = error,
            eventSink = ::handleEvent,
        )
    }
}

private fun List<ChatbotAgent>.filteredBy(query: String): List<ChatbotAgent> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return this
    return filter { agent ->
        agent.botName.contains(trimmed, ignoreCase = true) ||
            agent.displayName.orEmpty().contains(trimmed, ignoreCase = true) ||
            agent.description.orEmpty().contains(trimmed, ignoreCase = true)
    }
}

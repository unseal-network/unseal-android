/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.skills.impl.marketplace

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.chatbot.api.ChatbotApiServiceFactory
import io.element.android.libraries.chatbot.api.model.skills.ChatbotUserSkill
import io.element.android.libraries.matrix.api.MatrixClient
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AssistedInject
class SkillMarketplacePresenter(
    @Assisted private val navigator: SkillMarketplaceNavigator,
    private val matrixClient: MatrixClient,
    private val chatbotApiServiceFactory: ChatbotApiServiceFactory,
) : Presenter<SkillMarketplaceState> {
    @AssistedFactory
    interface Factory {
        fun create(navigator: SkillMarketplaceNavigator): SkillMarketplacePresenter
    }

    @Composable
    override fun present(): SkillMarketplaceState {
        val coroutineScope = rememberCoroutineScope()
        var skills by remember { mutableStateOf(emptyList<ChatbotUserSkill>()) }
        var total by remember { mutableStateOf<Int?>(null) }
        var page by remember { mutableStateOf(1) }
        var isLoading by remember { mutableStateOf(false) }
        var isLoadingNextPage by remember { mutableStateOf(false) }
        var searchQuery by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        var searchJob by remember { mutableStateOf<Job?>(null) }

        suspend fun api() = chatbotApiServiceFactory.createForUnsealApi(matrixClient)

        fun hasMore(): Boolean = total?.let { skills.size < it } ?: (skills.size >= PAGE_SIZE)

        fun loadPage(requestedPage: Int, replacing: Boolean) {
            if (replacing) {
                isLoading = true
            } else {
                isLoadingNextPage = true
            }
            coroutineScope.launch {
                val query = searchQuery.trim().takeIf { it.isNotEmpty() }
                api().listPublicSkills(page = requestedPage, pageSize = PAGE_SIZE, search = query)
                    .onSuccess { response ->
                        total = response.total
                        page = response.page ?: requestedPage
                        skills = if (replacing) {
                            response.skills
                        } else {
                            skills + response.skills
                        }
                        error = null
                    }
                    .onFailure { error = it.message ?: it::class.simpleName ?: "Failed to load marketplace skills" }
                if (replacing) {
                    isLoading = false
                } else {
                    isLoadingNextPage = false
                }
            }
        }

        fun loadFirstPage() {
            page = 1
            loadPage(requestedPage = 1, replacing = true)
        }

        fun scheduleSearch() {
            searchJob?.cancel()
            searchJob = coroutineScope.launch {
                delay(SEARCH_DEBOUNCE_MS)
                loadFirstPage()
            }
        }

        fun handleEvent(event: SkillMarketplaceEvents) {
            when (event) {
                SkillMarketplaceEvents.OnAppear -> loadFirstPage()
                SkillMarketplaceEvents.Refresh -> loadFirstPage()
                is SkillMarketplaceEvents.SearchQueryChanged -> {
                    searchQuery = event.query
                    scheduleSearch()
                }
                SkillMarketplaceEvents.LoadNextPage -> {
                    if (!isLoadingNextPage && hasMore()) {
                        loadPage(requestedPage = page + 1, replacing = false)
                    }
                }
                is SkillMarketplaceEvents.SelectSkill -> navigator.onOpenSkill(event.id)
                SkillMarketplaceEvents.ClearError -> error = null
            }
        }

        return SkillMarketplaceState(
            skills = skills.toImmutableList(),
            total = total,
            page = page,
            pageSize = PAGE_SIZE,
            isLoading = isLoading,
            isLoadingNextPage = isLoadingNextPage,
            searchQuery = searchQuery,
            error = error,
            eventSink = ::handleEvent,
        )
    }

    private companion object {
        const val PAGE_SIZE = 20
        const val SEARCH_DEBOUNCE_MS = 450L
    }
}

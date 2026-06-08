/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.skills.impl.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.features.skills.impl.shared.sortedBySkillName
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.chatbot.api.ChatbotApiServiceFactory
import io.element.android.libraries.chatbot.api.model.skills.ChatbotUserSkill
import io.element.android.libraries.matrix.api.MatrixClient
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AssistedInject
class SkillsHomePresenter(
    @Assisted private val navigator: SkillsHomeNavigator,
    private val matrixClient: MatrixClient,
    private val chatbotApiServiceFactory: ChatbotApiServiceFactory,
) : Presenter<SkillsHomeState> {
    @AssistedFactory
    interface Factory {
        fun create(navigator: SkillsHomeNavigator): SkillsHomePresenter
    }

    @Composable
    override fun present(): SkillsHomeState {
        val coroutineScope = rememberCoroutineScope()
        var skills by remember { mutableStateOf(emptyList<ChatbotUserSkill>()) }
        var selectedTab by remember { mutableStateOf(SkillsHomeTab.Mine) }
        var marketplaceSkills by remember { mutableStateOf(emptyList<ChatbotUserSkill>()) }
        var marketplaceTotal by remember { mutableStateOf<Int?>(null) }
        var marketplacePage by remember { mutableStateOf(1) }
        var isLoading by remember { mutableStateOf(false) }
        var isLoadingMarketplace by remember { mutableStateOf(false) }
        var isLoadingMarketplaceNextPage by remember { mutableStateOf(false) }
        var searchQuery by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        var hasLoadedOnce by remember { mutableStateOf(false) }
        var marketplaceSearchJob by remember { mutableStateOf<Job?>(null) }

        suspend fun api() = chatbotApiServiceFactory.createForUnsealApi(matrixClient)

        fun loadSkills(isInitial: Boolean) {
            if (isInitial && hasLoadedOnce) return
            coroutineScope.launch {
                isLoading = true
                api().listUserSkills(visibility = null)
                    .onSuccess {
                        skills = it.sortedBySkillName()
                        error = null
                    }
                    .onFailure { error = it.message ?: it::class.simpleName ?: "Failed to load skills" }
                isLoading = false
                hasLoadedOnce = true
            }
        }

        fun loadMarketplacePage(page: Int, replacing: Boolean) {
            if (replacing) {
                isLoadingMarketplace = true
            } else {
                isLoadingMarketplaceNextPage = true
            }
            coroutineScope.launch {
                val query = searchQuery.trim().takeIf { it.isNotEmpty() }
                api().listPublicSkills(page = page, pageSize = MARKETPLACE_PAGE_SIZE, search = query)
                    .onSuccess { response ->
                        marketplaceTotal = response.total
                        marketplacePage = response.page ?: page
                        marketplaceSkills = if (replacing) {
                            response.skills
                        } else {
                            marketplaceSkills + response.skills
                        }
                        error = null
                    }
                    .onFailure { error = it.message ?: it::class.simpleName ?: "Failed to load marketplace skills" }
                if (replacing) {
                    isLoadingMarketplace = false
                } else {
                    isLoadingMarketplaceNextPage = false
                }
            }
        }

        fun loadMarketplaceFirstPage() {
            marketplacePage = 1
            loadMarketplacePage(page = 1, replacing = true)
        }

        fun marketplaceHasMore(): Boolean = marketplaceTotal?.let { marketplaceSkills.size < it } ?: (marketplaceSkills.size >= MARKETPLACE_PAGE_SIZE)

        fun scheduleMarketplaceSearch() {
            marketplaceSearchJob?.cancel()
            marketplaceSearchJob = coroutineScope.launch {
                delay(SEARCH_DEBOUNCE_MS)
                loadMarketplaceFirstPage()
            }
        }

        fun handleEvent(event: SkillsHomeEvents) {
            when (event) {
                SkillsHomeEvents.OnAppear -> loadSkills(isInitial = true)
                SkillsHomeEvents.Refresh -> {
                    loadSkills(isInitial = false)
                    if (selectedTab == SkillsHomeTab.Marketplace) {
                        loadMarketplaceFirstPage()
                    }
                }
                SkillsHomeEvents.CreateSkill -> navigator.onCreateSkill()
                is SkillsHomeEvents.SelectSkill -> navigator.onOpenSkill(event.id, isOwner = true)
                is SkillsHomeEvents.SelectMarketplaceSkill -> navigator.onOpenSkill(
                    id = event.id,
                    isOwner = skills.any { it.id == event.id },
                )
                is SkillsHomeEvents.SelectTab -> {
                    selectedTab = event.tab
                    searchQuery = ""
                    marketplaceSearchJob?.cancel()
                    if (event.tab == SkillsHomeTab.Marketplace && marketplaceSkills.isEmpty()) {
                        loadMarketplaceFirstPage()
                    }
                }
                is SkillsHomeEvents.SearchQueryChanged -> {
                    searchQuery = event.query
                    if (selectedTab == SkillsHomeTab.Marketplace) {
                        scheduleMarketplaceSearch()
                    }
                }
                SkillsHomeEvents.LoadNextMarketplacePage -> {
                    if (!isLoadingMarketplaceNextPage && marketplaceHasMore()) {
                        loadMarketplacePage(page = marketplacePage + 1, replacing = false)
                    }
                }
                SkillsHomeEvents.ClearError -> error = null
            }
        }

        return SkillsHomeState(
            skills = skills.toImmutableList(),
            selectedTab = selectedTab,
            marketplaceSkills = marketplaceSkills.toImmutableList(),
            marketplaceTotal = marketplaceTotal,
            marketplacePage = marketplacePage,
            marketplacePageSize = MARKETPLACE_PAGE_SIZE,
            isLoading = isLoading,
            isLoadingMarketplace = isLoadingMarketplace,
            isLoadingMarketplaceNextPage = isLoadingMarketplaceNextPage,
            searchQuery = searchQuery,
            error = error,
            eventSink = ::handleEvent,
        )
    }

    private companion object {
        const val MARKETPLACE_PAGE_SIZE = 20
        const val SEARCH_DEBOUNCE_MS = 450L
    }
}

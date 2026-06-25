/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.skills.impl.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.features.skills.impl.SkillMetadataFilterBridge
import io.element.android.features.skills.impl.SkillMetadataFilterOrigin
import io.element.android.features.skills.impl.shared.SkillFilterToken
import io.element.android.features.skills.impl.shared.sortedBySkillName
import io.element.android.features.skills.impl.shared.SkillFilterState
import io.element.android.features.skills.impl.shared.deriveSkillFacets
import io.element.android.features.skills.impl.shared.hasAnyFacet
import io.element.android.features.skills.impl.shared.toApiFilters
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.chatbot.api.ChatbotApiServiceFactory
import io.element.android.libraries.chatbot.api.model.skills.ChatbotSkillFacetsResponse
import io.element.android.libraries.chatbot.api.model.skills.ChatbotSkillVisibility
import io.element.android.libraries.chatbot.api.model.skills.ChatbotUserSkill
import io.element.android.libraries.matrix.api.MatrixClient
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AssistedInject
class SkillsHomePresenter(
    @Assisted private val navigator: SkillsHomeNavigator,
    @Assisted private val filterBridge: SkillMetadataFilterBridge?,
    private val matrixClient: MatrixClient,
    private val chatbotApiServiceFactory: ChatbotApiServiceFactory,
) : Presenter<SkillsHomeState> {
    @AssistedFactory
    interface Factory {
        fun create(navigator: SkillsHomeNavigator, filterBridge: SkillMetadataFilterBridge?): SkillsHomePresenter
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
        var filterState by remember { mutableStateOf(SkillFilterState()) }
        var facets by remember { mutableStateOf(ChatbotSkillFacetsResponse()) }
        var isFilterSheetVisible by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        var hasLoadedOnce by remember { mutableStateOf(false) }
        var marketplaceSearchJob by remember { mutableStateOf<Job?>(null) }
        var skillsRequestId by remember { mutableStateOf(0) }
        var marketplaceRequestId by remember { mutableStateOf(0) }
        var facetsRequestId by remember { mutableStateOf(0) }
        var handledFilterRequestId by remember { mutableStateOf(0L) }

        suspend fun api() = chatbotApiServiceFactory.createForHomeserver(matrixClient)

        fun loadFacets() {
            val requestId = ++facetsRequestId
            if (selectedTab == SkillsHomeTab.Mine) {
                facets = deriveSkillFacets(skills)
                isFilterSheetVisible = isFilterSheetVisible && facets.hasAnyFacet()
                return
            }
            coroutineScope.launch {
                api().listSkillFacets(ChatbotSkillVisibility.Public)
                    .onSuccess {
                        if (requestId == facetsRequestId && selectedTab == SkillsHomeTab.Marketplace) {
                            facets = it
                            isFilterSheetVisible = isFilterSheetVisible && it.hasAnyFacet()
                        }
                    }
                    .onFailure {
                        if (requestId == facetsRequestId && selectedTab == SkillsHomeTab.Marketplace) {
                            facets = ChatbotSkillFacetsResponse()
                            isFilterSheetVisible = false
                        }
                    }
            }
        }

        fun loadSkills(isInitial: Boolean) {
            if (isInitial && hasLoadedOnce) return
            val requestId = ++skillsRequestId
            coroutineScope.launch {
                isLoading = true
                api().listUserSkills(visibility = null)
                    .onSuccess {
                        if (requestId == skillsRequestId) {
                            skills = it.sortedBySkillName()
                            if (selectedTab == SkillsHomeTab.Mine) {
                                facets = deriveSkillFacets(skills)
                                isFilterSheetVisible = isFilterSheetVisible && facets.hasAnyFacet()
                            }
                            error = null
                        }
                    }
                    .onFailure {
                        if (requestId == skillsRequestId) {
                            error = it.message ?: it::class.simpleName ?: "Failed to load skills"
                        }
                    }
                if (requestId == skillsRequestId) {
                    isLoading = false
                    hasLoadedOnce = true
                }
            }
        }

        fun loadMarketplacePage(page: Int, replacing: Boolean) {
            val requestId = ++marketplaceRequestId
            if (replacing) {
                isLoadingMarketplace = true
            } else {
                isLoadingMarketplaceNextPage = true
            }
            coroutineScope.launch {
                api().listPublicSkills(page = page, pageSize = MARKETPLACE_PAGE_SIZE, filters = filterState.copy(searchQuery = searchQuery).toApiFilters())
                    .onSuccess { response ->
                        if (requestId == marketplaceRequestId && selectedTab == SkillsHomeTab.Marketplace) {
                            marketplaceTotal = response.total
                            marketplacePage = response.page ?: page
                            marketplaceSkills = if (replacing) {
                                response.skills
                            } else {
                                marketplaceSkills + response.skills
                            }
                            error = null
                        }
                    }
                    .onFailure {
                        if (requestId == marketplaceRequestId && selectedTab == SkillsHomeTab.Marketplace) {
                            error = it.message ?: it::class.simpleName ?: "Failed to load marketplace skills"
                        }
                    }
                if (requestId == marketplaceRequestId) {
                    if (replacing) {
                        isLoadingMarketplace = false
                    } else {
                        isLoadingMarketplaceNextPage = false
                    }
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

        fun applyFilterToken(token: SkillFilterToken) {
            filterState = filterState.apply(token)
            searchQuery = ""
            isFilterSheetVisible = false
            marketplaceSearchJob?.cancel()
            if (selectedTab == SkillsHomeTab.Marketplace) {
                loadMarketplaceFirstPage()
            }
        }

        LaunchedEffect(filterBridge) {
            filterBridge?.requests?.collect { request ->
                if (request != null && request.origin == SkillMetadataFilterOrigin.Home && request.id != handledFilterRequestId) {
                    handledFilterRequestId = request.id
                    applyFilterToken(request.token)
                    filterBridge.markHandled(request.id)
                }
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
                    val wasMarketplace = selectedTab == SkillsHomeTab.Marketplace
                    if (wasMarketplace && event.tab != SkillsHomeTab.Marketplace) {
                        marketplaceRequestId++
                        facetsRequestId++
                        marketplaceSearchJob?.cancel()
                        isLoadingMarketplace = false
                        isLoadingMarketplaceNextPage = false
                    }
                    selectedTab = event.tab
                    searchQuery = ""
                    filterState = SkillFilterState()
                    isFilterSheetVisible = false
                    marketplaceSearchJob?.cancel()
                    loadFacets()
                    if (event.tab == SkillsHomeTab.Marketplace) {
                        loadMarketplaceFirstPage()
                    }
                }
                is SkillsHomeEvents.SearchQueryChanged -> {
                    searchQuery = event.query
                    if (selectedTab == SkillsHomeTab.Marketplace) {
                        scheduleMarketplaceSearch()
                    }
                }
                SkillsHomeEvents.AddFilter -> if (facets.hasAnyFacet()) {
                    isFilterSheetVisible = true
                }
                SkillsHomeEvents.DismissFilterSheet -> isFilterSheetVisible = false
                is SkillsHomeEvents.ApplyFilterToken -> {
                    applyFilterToken(event.token)
                }
                is SkillsHomeEvents.RemoveFilterToken -> {
                    filterState = filterState.remove(event.token)
                    if (selectedTab == SkillsHomeTab.Marketplace) {
                        loadMarketplaceFirstPage()
                    }
                }
                SkillsHomeEvents.ClearFilters -> {
                    filterState = SkillFilterState()
                    searchQuery = ""
                    marketplaceSearchJob?.cancel()
                    if (selectedTab == SkillsHomeTab.Marketplace) {
                        loadMarketplaceFirstPage()
                    }
                }
                is SkillsHomeEvents.TagModeChanged -> {
                    filterState = filterState.copy(tagMode = event.tagMode)
                    if (selectedTab == SkillsHomeTab.Marketplace && filterState.tags.isNotEmpty()) {
                        loadMarketplaceFirstPage()
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
            filterState = filterState,
            facets = facets,
            isFilterSheetVisible = isFilterSheetVisible,
            error = error,
            eventSink = ::handleEvent,
        )
    }

    private companion object {
        const val MARKETPLACE_PAGE_SIZE = 20
        const val SEARCH_DEBOUNCE_MS = 450L
    }
}

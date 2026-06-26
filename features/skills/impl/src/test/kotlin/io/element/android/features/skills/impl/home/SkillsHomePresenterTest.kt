/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.skills.impl.home

import app.cash.turbine.TurbineTestContext
import com.google.common.truth.Truth.assertThat
import io.element.android.features.skills.impl.shared.SkillFilterToken
import io.element.android.features.skills.impl.SkillMetadataFilterBridge
import io.element.android.features.skills.impl.SkillMetadataFilterOrigin
import io.element.android.libraries.chatbot.api.model.skills.ChatbotListPublicSkillCategoriesResponse
import io.element.android.libraries.chatbot.api.model.skills.ChatbotListPublicSkillsResponse
import io.element.android.libraries.chatbot.api.model.skills.ChatbotSkillFacetsResponse
import io.element.android.libraries.chatbot.api.model.skills.ChatbotSkillListFilters
import io.element.android.libraries.chatbot.api.model.skills.ChatbotSkillNamedFacet
import io.element.android.libraries.chatbot.api.model.skills.ChatbotPublicSkillCategory
import io.element.android.libraries.chatbot.api.model.skills.ChatbotSkillSource
import io.element.android.libraries.chatbot.api.model.skills.ChatbotSkillVisibility
import io.element.android.libraries.chatbot.api.model.skills.ChatbotUserSkill
import io.element.android.libraries.chatbot.test.FakeChatbotApiService
import io.element.android.libraries.chatbot.test.FakeChatbotApiServiceFactory
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.awaitWithLatch
import io.element.android.tests.testutils.test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SkillsHomePresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `present - loads user skills once and sorts by name`() = runTest {
        var calls = 0
        val service = FakeChatbotApiService().apply {
            listUserSkillsResult = {
                calls++
                Result.success(
                    listOf(
                        aSkill(id = "z", name = "Zeta"),
                        aSkill(id = "a", name = "Alpha"),
                        aSkill(id = "b", name = "Beta"),
                    )
                )
            }
        }
        val presenter = createSkillsHomePresenter(service = service)

        presenter.test {
            val initialState = awaitItem()
            initialState.eventSink(SkillsHomeEvents.OnAppear)

            val loadedState = awaitStateWhere { it.skills.size == 3 && !it.isLoading }
            assertThat(loadedState.skills.map { it.id }).containsExactly("a", "b", "z").inOrder()

            loadedState.eventSink(SkillsHomeEvents.OnAppear)
            assertThat(calls).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - loads private user skills for mine tab`() = runTest {
        val requestedVisibilities = mutableListOf<ChatbotSkillVisibility?>()
        val service = FakeChatbotApiService().apply {
            listUserSkillsResult = { visibility ->
                requestedVisibilities += visibility
                Result.success(listOf(aSkill(id = "private", name = "Private")))
            }
        }
        val presenter = createSkillsHomePresenter(service = service)

        presenter.test {
            awaitItem().eventSink(SkillsHomeEvents.OnAppear)
            awaitStateWhere { it.skills.singleOrNull()?.id == "private" && !it.isLoading }

            assertThat(requestedVisibilities).containsExactly(ChatbotSkillVisibility.Private)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - refresh reloads user skills and marketplace only when selected`() = runTest {
        var userCalls = 0
        var marketplaceCalls = 0
        val service = FakeChatbotApiService().apply {
            listUserSkillsResult = {
                userCalls++
                Result.success(listOf(aSkill(id = "mine-$userCalls", name = "Mine $userCalls")))
            }
            listPublicSkillsResult = { _, _, _ ->
                marketplaceCalls++
                Result.success(ChatbotListPublicSkillsResponse(skills = listOf(aSkill(id = "public-$marketplaceCalls", name = "Public $marketplaceCalls")), total = 5))
            }
        }
        val presenter = createSkillsHomePresenter(service = service)

        presenter.test {
            awaitItem().eventSink(SkillsHomeEvents.OnAppear)
            val loadedState = awaitStateWhere { it.skills.singleOrNull()?.id == "mine-1" && !it.isLoading }

            loadedState.eventSink(SkillsHomeEvents.Refresh)
            val refreshedMineState = awaitStateWhere { it.skills.singleOrNull()?.id == "mine-2" && !it.isLoading }
            assertThat(marketplaceCalls).isEqualTo(0)

            refreshedMineState.eventSink(SkillsHomeEvents.SelectTab(SkillsHomeTab.Marketplace))
            val marketplaceState = awaitStateWhere { it.marketplaceSkills.singleOrNull()?.id == "public-1" && !it.isLoadingMarketplace }
            marketplaceState.eventSink(SkillsHomeEvents.Refresh)

            val refreshedMarketplaceState = awaitStateWhere {
                it.skills.singleOrNull()?.id == "mine-3" &&
                    it.marketplaceSkills.singleOrNull()?.id == "public-2" &&
                    !it.isLoading &&
                    !it.isLoadingMarketplace
            }
            assertThat(refreshedMarketplaceState.selectedTab).isEqualTo(SkillsHomeTab.Marketplace)
            assertThat(userCalls).isEqualTo(3)
            assertThat(marketplaceCalls).isEqualTo(2)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - local search trims and matches name or description case insensitively`() = runTest {
        val service = FakeChatbotApiService().apply {
            listUserSkillsResult = {
                Result.success(
                    listOf(
                        aSkill(id = "search", name = "Search", description = "Find things"),
                        aSkill(id = "calendar", name = "Calendar", description = "Schedules meetings"),
                        aSkill(id = "writer", name = "Writer", description = "Drafts docs"),
                    )
                )
            }
        }
        val presenter = createSkillsHomePresenter(service = service)

        presenter.test {
            awaitItem().eventSink(SkillsHomeEvents.OnAppear)
            val loadedState = awaitStateWhere { it.skills.size == 3 && !it.isLoading }

            loadedState.eventSink(SkillsHomeEvents.SearchQueryChanged(" SCHEDULES "))
            val schedulesState = awaitStateWhere { it.searchQuery == " SCHEDULES " }
            assertThat(schedulesState.filteredSkills.map { it.id }).containsExactly("calendar")

            schedulesState.eventSink(SkillsHomeEvents.SearchQueryChanged("search"))
            val searchState = awaitStateWhere { it.searchQuery == "search" }
            assertThat(searchState.filteredSkills.map { it.id }).containsExactly("search")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - selecting marketplace clears search and loads first page once`() = runTest {
        var marketplaceCalls = 0
        val service = FakeChatbotApiService().apply {
            listPublicSkillsResult = { page, pageSize, search ->
                marketplaceCalls++
                Result.success(ChatbotListPublicSkillsResponse(skills = listOf(aSkill(id = "public", name = "$page-$pageSize-$search")), total = 1, page = page))
            }
        }
        val presenter = createSkillsHomePresenter(service = service)

        presenter.test {
            val initialState = awaitItem()
            initialState.eventSink(SkillsHomeEvents.SearchQueryChanged("mine"))
            val searchedState = awaitStateWhere { it.searchQuery == "mine" }

            searchedState.eventSink(SkillsHomeEvents.SelectTab(SkillsHomeTab.Marketplace))
            val marketplaceState = awaitStateWhere { it.marketplaceSkills.isNotEmpty() && !it.isLoadingMarketplace }
            assertThat(marketplaceState.searchQuery).isEmpty()
            assertThat(marketplaceCalls).isEqualTo(1)

            marketplaceState.eventSink(SkillsHomeEvents.SelectTab(SkillsHomeTab.Mine))
            val mineState = awaitStateWhere { it.selectedTab == SkillsHomeTab.Mine }
            mineState.eventSink(SkillsHomeEvents.SelectTab(SkillsHomeTab.Marketplace))
            awaitStateWhere { it.selectedTab == SkillsHomeTab.Marketplace }
            assertThat(marketplaceCalls).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - selecting marketplace after filtered visit keeps marketplace filters separate from mine`() = runTest {
        val captured = mutableListOf<ChatbotSkillListFilters>()
        val service = FakeChatbotApiService().apply {
            listPublicSkillsWithFiltersResult = { _, _, filters ->
                captured += filters
                val id = filters.categorySlug ?: "all"
                Result.success(ChatbotListPublicSkillsResponse(skills = listOf(aSkill(id = id, name = id)), total = 1))
            }
        }
        val presenter = createSkillsHomePresenter(service = service)

        presenter.test {
            awaitItem().eventSink(SkillsHomeEvents.SelectTab(SkillsHomeTab.Marketplace))
            val marketplaceState = awaitStateWhere { it.marketplaceSkills.singleOrNull()?.id == "all" && !it.isLoadingMarketplace }
            marketplaceState.eventSink(SkillsHomeEvents.ApplyFilterToken(SkillFilterToken.Category("Testing")))
            val filteredState = awaitStateWhere { it.marketplaceSkills.singleOrNull()?.id == "Testing" && !it.isLoadingMarketplace }

            filteredState.eventSink(SkillsHomeEvents.SelectTab(SkillsHomeTab.Mine))
            val mineState = awaitStateWhere { it.selectedTab == SkillsHomeTab.Mine }
            mineState.eventSink(SkillsHomeEvents.SelectTab(SkillsHomeTab.Marketplace))

            val restoredState = awaitStateWhere { it.selectedTab == SkillsHomeTab.Marketplace && it.marketplaceSkills.singleOrNull()?.id == "Testing" && !it.isLoadingMarketplace }
            assertThat(restoredState.filterState.category).isEqualTo("Testing")
            assertThat(captured.map { it.categorySlug }).containsExactly(null, "Testing").inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - selecting marketplace after filtered visit clears stale rows while reloading with marketplace filters`() = runTest {
        val captured = mutableListOf<ChatbotSkillListFilters>()
        val thirdRequestStarted = CompletableDeferred<Unit>()
        val thirdRequestResult = CompletableDeferred<Result<ChatbotListPublicSkillsResponse>>()
        val service = FakeChatbotApiService().apply {
            listPublicSkillsWithFiltersSuspendResult = { _, _, filters ->
                captured += filters
                when (captured.size) {
                    1 -> Result.success(ChatbotListPublicSkillsResponse(skills = listOf(aSkill(id = "all", name = "All")), total = 7))
                    2 -> Result.success(ChatbotListPublicSkillsResponse(skills = listOf(aSkill(id = "Testing", name = "Testing")), total = 1))
                    else -> {
                        thirdRequestStarted.complete(Unit)
                        thirdRequestResult.await()
                    }
                }
            }
        }
        val presenter = createSkillsHomePresenter(service = service)

        presenter.test {
            awaitItem().eventSink(SkillsHomeEvents.SelectTab(SkillsHomeTab.Marketplace))
            val marketplaceState = awaitStateWhere { it.marketplaceSkills.singleOrNull()?.id == "all" && !it.isLoadingMarketplace }
            marketplaceState.eventSink(SkillsHomeEvents.ApplyFilterToken(SkillFilterToken.Category("Testing")))
            val filteredState = awaitStateWhere { it.marketplaceSkills.singleOrNull()?.id == "Testing" && !it.isLoadingMarketplace }

            filteredState.eventSink(SkillsHomeEvents.SelectTab(SkillsHomeTab.Mine))
            val mineState = awaitStateWhere { it.selectedTab == SkillsHomeTab.Mine }
            mineState.eventSink(SkillsHomeEvents.SelectTab(SkillsHomeTab.Marketplace))
            runCurrent()
            thirdRequestStarted.await()

            val reloadingState = awaitStateWhere { it.selectedTab == SkillsHomeTab.Marketplace && it.isLoadingMarketplace }
            assertThat(reloadingState.filterState.category).isEqualTo("Testing")
            assertThat(reloadingState.marketplaceSkills).isEmpty()
            assertThat(reloadingState.marketplaceTotal).isNull()
            assertThat(captured.map { it.categorySlug }).containsExactly(null, "Testing", "Testing").inOrder()

            thirdRequestResult.complete(Result.success(ChatbotListPublicSkillsResponse(skills = listOf(aSkill(id = "testing-again", name = "Testing again")), total = 1)))
            val restoredState = awaitStateWhere { it.marketplaceSkills.singleOrNull()?.id == "testing-again" && !it.isLoadingMarketplace }
            assertThat(restoredState.marketplaceTotal).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - marketplace failure after leaving tab does not set mine error`() = runTest {
        val marketplaceStarted = CompletableDeferred<Unit>()
        val marketplaceResult = CompletableDeferred<Result<ChatbotListPublicSkillsResponse>>()
        val service = FakeChatbotApiService().apply {
            listPublicSkillsWithFiltersSuspendResult = { _, _, _ ->
                marketplaceStarted.complete(Unit)
                marketplaceResult.await()
            }
        }
        val presenter = createSkillsHomePresenter(service = service)

        presenter.test {
            val initialState = awaitItem()
            initialState.eventSink(SkillsHomeEvents.SelectTab(SkillsHomeTab.Marketplace))
            runCurrent()
            marketplaceStarted.await()

            initialState.eventSink(SkillsHomeEvents.SelectTab(SkillsHomeTab.Mine))
            val mineState = awaitStateWhere { it.selectedTab == SkillsHomeTab.Mine }
            assertThat(mineState.error).isNull()
            marketplaceResult.complete(Result.failure(IllegalStateException("stale marketplace failure")))
            runCurrent()

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - metadata filter bridge applies filter to originating home list`() = runTest {
        val bridge = SkillMetadataFilterBridge()
        val service = FakeChatbotApiService().apply {
            listUserSkillsResult = {
                Result.success(
                    listOf(
                        aSkill(id = "browser", name = "Browser", category = "Testing"),
                        aSkill(id = "docs", name = "Docs", category = "Writing"),
                    )
                )
            }
        }
        val presenter = createSkillsHomePresenter(service = service, filterBridge = bridge)

        presenter.test {
            awaitItem().eventSink(SkillsHomeEvents.OnAppear)
            val loadedState = awaitStateWhere { it.skills.size == 2 && !it.isLoading }

            bridge.applyFilter(SkillMetadataFilterOrigin.Home, SkillFilterToken.Category("Testing"))
            val filteredState = awaitStateWhere { it.filterState.category == "Testing" }
            assertThat(filteredState.filteredSkills.map { it.id }).containsExactly("browser")
            assertThat(loadedState.filteredSkills.map { it.id }).containsExactly("browser", "docs").inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - marketplace search debounces and sends trimmed query`() = runTest {
        val capturedSearches = mutableListOf<String?>()
        val service = FakeChatbotApiService().apply {
            listPublicSkillsResult = { _, _, search ->
                capturedSearches += search
                Result.success(ChatbotListPublicSkillsResponse(skills = listOf(aSkill(id = "public-${capturedSearches.size}", name = "Public")), total = 10))
            }
        }
        val presenter = createSkillsHomePresenter(service = service)

        presenter.test {
            awaitItem().eventSink(SkillsHomeEvents.SelectTab(SkillsHomeTab.Marketplace))
            val marketplaceState = awaitStateWhere { it.marketplaceSkills.isNotEmpty() && !it.isLoadingMarketplace }
            assertThat(capturedSearches).containsExactly(null)

            marketplaceState.eventSink(SkillsHomeEvents.SearchQueryChanged("  search  "))
            awaitStateWhere { it.searchQuery == "  search  " && it.selectedTab == SkillsHomeTab.Marketplace }
            advanceTimeBy(449)
            assertThat(capturedSearches).containsExactly(null)
            advanceTimeBy(1)
            runCurrent()
            awaitStateWhere { capturedSearches == listOf(null, "search") && !it.isLoadingMarketplace }
            assertThat(capturedSearches).containsExactly(null, "search").inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - marketplace blank search sends nullable query`() = runTest {
        val capturedSearches = mutableListOf<String?>()
        val service = FakeChatbotApiService().apply {
            listPublicSkillsResult = { _, _, search ->
                capturedSearches += search
                Result.success(ChatbotListPublicSkillsResponse(skills = listOf(aSkill(id = "public-${capturedSearches.size}", name = "Public")), total = 10))
            }
        }
        val presenter = createSkillsHomePresenter(service = service)

        presenter.test {
            awaitItem().eventSink(SkillsHomeEvents.SelectTab(SkillsHomeTab.Marketplace))
            val marketplaceState = awaitStateWhere { it.marketplaceSkills.isNotEmpty() && !it.isLoadingMarketplace }
            assertThat(capturedSearches).containsExactly(null)

            marketplaceState.eventSink(SkillsHomeEvents.SearchQueryChanged("   "))
            awaitStateWhere { it.searchQuery == "   " && it.selectedTab == SkillsHomeTab.Marketplace }
            advanceTimeBy(450)
            runCurrent()
            awaitStateWhere { capturedSearches == listOf(null, null) && !it.isLoadingMarketplace }
            assertThat(capturedSearches).containsExactly(null, null).inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - owned facets are derived locally`() = runTest {
        val service = FakeChatbotApiService().apply {
            listUserSkillsResult = {
                Result.success(
                    listOf(
                        aSkill(id = "browser", name = "Browser", category = "Testing", tags = listOf("browser"), source = aSource(1, "GitHub", "github")),
                        aSkill(id = "docs", name = "Docs", category = "Writing", tags = listOf("docs"), source = aSource(2, "Internal", "internal", repository = "internal/skills")),
                    )
                )
            }
        }
        val presenter = createSkillsHomePresenter(service = service)

        presenter.test {
            awaitItem().eventSink(SkillsHomeEvents.OnAppear)
            val loadedState = awaitStateWhere { it.skills.size == 2 && !it.isLoading }

            assertThat(loadedState.filtersAvailable).isTrue()
            assertThat(loadedState.facets.categories.map { it.value }).containsExactly("Testing", "Writing")
            assertThat(loadedState.facets.sources.map { it.value }).containsExactly("GitHub", "Internal")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `state - mine empty filtered result requests clear filters affordance`() = runTest {
        val service = FakeChatbotApiService().apply {
            listUserSkillsResult = { Result.success(listOf(aSkill(id = "browser", name = "Browser", category = "Testing"))) }
        }
        val presenter = createSkillsHomePresenter(service = service)

        presenter.test {
            awaitItem().eventSink(SkillsHomeEvents.OnAppear)
            val loadedState = awaitStateWhere { it.skills.isNotEmpty() && !it.isLoading }

            loadedState.eventSink(SkillsHomeEvents.ApplyFilterToken(SkillFilterToken.Category("Writing")))
            val emptyState = awaitStateWhere { it.filteredSkills.isEmpty() && it.filterState.category == "Writing" }
            assertThat(emptyState.showClearFiltersForEmptyMine).isTrue()

            emptyState.eventSink(SkillsHomeEvents.ClearFilters)
            val clearedState = awaitStateWhere { it.filteredSkills.isNotEmpty() && it.filterState.activeTokenCount == 0 && it.searchQuery.isEmpty() }
            assertThat(clearedState.showClearFiltersForEmptyMine).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - add filter does not open sheet when facets are empty`() = runTest {
        val presenter = createSkillsHomePresenter()

        presenter.test {
            val initialState = awaitItem()
            assertThat(initialState.filtersAvailable).isFalse()
            assertThat(initialState.isFilterSheetVisible).isFalse()
            initialState.eventSink(SkillsHomeEvents.AddFilter)

            awaitWithLatch { }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - mine and marketplace keep independent filters and facets`() = runTest {
        val service = FakeChatbotApiService().apply {
            listUserSkillsResult = {
                Result.success(listOf(aSkill(id = "owned", name = "Owned", category = "Private")))
            }
            listPublicSkillCategoriesResult = {
                Result.success(ChatbotListPublicSkillCategoriesResponse(categories = listOf(aCategory("Testing", "testing"))))
            }
            listPublicSkillsResult = { _, _, _ ->
                Result.success(ChatbotListPublicSkillsResponse(skills = listOf(aSkill(id = "public", name = "Public", category = "Testing")), total = 1))
            }
        }
        val presenter = createSkillsHomePresenter(service = service)

        presenter.test {
            awaitItem().eventSink(SkillsHomeEvents.OnAppear)
            val mineState = awaitStateWhere { it.skills.singleOrNull()?.id == "owned" && !it.isLoading }
            mineState.eventSink(SkillsHomeEvents.ApplyFilterToken(SkillFilterToken.Category("Private")))
            val filteredMineState = awaitStateWhere { it.filterState.category == "Private" }

            filteredMineState.eventSink(SkillsHomeEvents.SelectTab(SkillsHomeTab.Marketplace))
            val marketplaceState = awaitStateWhere {
                it.selectedTab == SkillsHomeTab.Marketplace &&
                    it.marketplaceSkills.singleOrNull()?.id == "public" &&
                    it.facets.categories.singleOrNull()?.value == "Testing" &&
                    !it.isLoadingMarketplace
            }
            assertThat(marketplaceState.filterState.category).isNull()

            marketplaceState.eventSink(SkillsHomeEvents.ApplyFilterToken(SkillFilterToken.Category("Testing")))
            val filteredMarketplaceState = awaitStateWhere { it.filterState.category == "Testing" && !it.isLoadingMarketplace }
            filteredMarketplaceState.eventSink(SkillsHomeEvents.SelectTab(SkillsHomeTab.Mine))
            val restoredMineState = awaitStateWhere { it.selectedTab == SkillsHomeTab.Mine }

            assertThat(restoredMineState.filterState.category).isEqualTo("Private")
            assertThat(restoredMineState.facets.categories.map { it.value }).containsExactly("Private")
            assertThat(restoredMineState.filteredSkills.map { it.id }).containsExactly("owned")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - marketplace filter state is sent to public skills API`() = runTest {
        val captured = mutableListOf<ChatbotSkillListFilters>()
        val service = FakeChatbotApiService().apply {
            listPublicSkillCategoriesResult = {
                Result.success(ChatbotListPublicSkillCategoriesResponse(categories = listOf(aCategory("Testing", "testing"))))
            }
            listPublicSkillsWithFiltersResult = { _, _, filters ->
                captured += filters
                Result.success(ChatbotListPublicSkillsResponse(skills = listOf(aSkill(id = "browser", name = "Browser QA")), total = 1))
            }
        }
        val presenter = createSkillsHomePresenter(service = service)

        presenter.test {
            awaitItem().eventSink(SkillsHomeEvents.SelectTab(SkillsHomeTab.Marketplace))
            val marketplaceState = awaitStateWhere { it.marketplaceSkills.isNotEmpty() && !it.isLoadingMarketplace }
            marketplaceState.eventSink(SkillsHomeEvents.ApplyFilterToken(SkillFilterToken.Category("Testing")))
            awaitStateWhere { captured.any { filters -> filters.categorySlug == "Testing" } && !it.isLoadingMarketplace }

            assertThat(captured.last().categorySlug).isEqualTo("Testing")
            assertThat(captured.last().tagMode.queryValue).isEqualTo("any")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - next marketplace page appends and stops when total reached`() = runTest {
        val requestedPages = mutableListOf<Int>()
        val service = FakeChatbotApiService().apply {
            listPublicSkillsResult = { page, _, _ ->
                requestedPages += page
                val skills = when (page) {
                    1 -> listOf(aSkill(id = "one", name = "One"), aSkill(id = "two", name = "Two"))
                    2 -> listOf(aSkill(id = "three", name = "Three"))
                    else -> emptyList()
                }
                Result.success(ChatbotListPublicSkillsResponse(skills = skills, total = 3, page = page))
            }
        }
        val presenter = createSkillsHomePresenter(service = service)

        presenter.test {
            awaitItem().eventSink(SkillsHomeEvents.SelectTab(SkillsHomeTab.Marketplace))
            val firstPageState = awaitStateWhere { it.marketplaceSkills.size == 2 && !it.isLoadingMarketplace }
            assertThat(firstPageState.marketplaceHasMore).isTrue()

            firstPageState.eventSink(SkillsHomeEvents.LoadNextMarketplacePage)
            val secondPageState = awaitStateWhere { it.marketplaceSkills.size == 3 && !it.isLoadingMarketplaceNextPage }
            assertThat(secondPageState.marketplaceSkills.map { it.id }).containsExactly("one", "two", "three").inOrder()
            assertThat(secondPageState.marketplaceHasMore).isFalse()

            secondPageState.eventSink(SkillsHomeEvents.LoadNextMarketplacePage)
            assertThat(requestedPages).containsExactly(1, 2).inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - selecting marketplace skill computes ownership from user skills`() = runTest {
        val navigator = FakeSkillsHomeNavigator()
        val service = FakeChatbotApiService().apply {
            listUserSkillsResult = { Result.success(listOf(aSkill(id = "owned", name = "Owned"))) }
        }
        val presenter = createSkillsHomePresenter(service = service, navigator = navigator)

        presenter.test {
            awaitItem().eventSink(SkillsHomeEvents.OnAppear)
            val loadedState = awaitStateWhere { it.skills.singleOrNull()?.id == "owned" && !it.isLoading }

            loadedState.eventSink(SkillsHomeEvents.SelectMarketplaceSkill("owned"))
            assertThat(navigator.openedSkills).containsExactly("owned" to true)
            loadedState.eventSink(SkillsHomeEvents.SelectMarketplaceSkill("public"))
            assertThat(navigator.openedSkills).containsExactly("owned" to true, "public" to false).inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createSkillsHomePresenter(
        service: FakeChatbotApiService = FakeChatbotApiService(),
        navigator: SkillsHomeNavigator = FakeSkillsHomeNavigator(),
        filterBridge: SkillMetadataFilterBridge? = null,
    ): SkillsHomePresenter {
        return SkillsHomePresenter(
            navigator = navigator,
            filterBridge = filterBridge,
            matrixClient = FakeMatrixClient(),
            chatbotApiServiceFactory = FakeChatbotApiServiceFactory(service),
        )
    }
}

private class FakeSkillsHomeNavigator : SkillsHomeNavigator {
    val openedSkills = mutableListOf<Pair<String, Boolean>>()
    var createCalls = 0

    override fun onCreateSkill() {
        createCalls++
    }

    override fun onOpenSkill(id: String, isOwner: Boolean) {
        openedSkills += id to isOwner
    }
}

private fun aSkill(
    id: String,
    name: String,
    description: String? = null,
    category: String? = null,
    tags: List<String> = emptyList(),
    source: ChatbotSkillSource? = null,
): ChatbotUserSkill {
    return ChatbotUserSkill(
        id = id,
        name = name,
        description = description,
            category = category?.let { aFacet(1, it) },
            tags = tags.mapIndexed { index, tag -> aFacet(index + 10, tag) },
            source = source,
    )
}

private fun aFacet(id: Int, name: String): ChatbotSkillNamedFacet =
    ChatbotSkillNamedFacet(id = id, name = name, slug = name.lowercase())

private fun aCategory(name: String, slug: String): ChatbotPublicSkillCategory =
    ChatbotPublicSkillCategory(id = 1, name = name, slug = slug, sortOrder = 1, categoryType = 1)

private fun aSource(id: Int, name: String, slug: String, repository: String? = null): ChatbotSkillSource =
    ChatbotSkillSource(id = id, name = name, slug = slug, sourceType = "marketplace", repository = repository)

private suspend fun TurbineTestContext<SkillsHomeState>.awaitStateWhere(
    predicate: (SkillsHomeState) -> Boolean,
): SkillsHomeState {
    while (true) {
        val state = awaitItem()
        if (predicate(state)) {
            return state
        }
    }
}

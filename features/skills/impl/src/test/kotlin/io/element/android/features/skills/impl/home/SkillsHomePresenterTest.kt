/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.skills.impl.home

import app.cash.turbine.TurbineTestContext
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.chatbot.api.model.skills.ChatbotListPublicSkillsResponse
import io.element.android.libraries.chatbot.api.model.skills.ChatbotUserSkill
import io.element.android.libraries.chatbot.test.FakeChatbotApiService
import io.element.android.libraries.chatbot.test.FakeChatbotApiServiceFactory
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.test
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
    ): SkillsHomePresenter {
        return SkillsHomePresenter(
            navigator = navigator,
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
): ChatbotUserSkill {
    return ChatbotUserSkill(
        id = id,
        name = name,
        description = description,
    )
}

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

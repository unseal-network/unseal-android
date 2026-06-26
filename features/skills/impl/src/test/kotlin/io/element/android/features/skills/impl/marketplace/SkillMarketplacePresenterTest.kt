/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.skills.impl.marketplace

import app.cash.turbine.TurbineTestContext
import com.google.common.truth.Truth.assertThat
import io.element.android.features.skills.impl.shared.SkillFilterToken
import io.element.android.libraries.chatbot.api.model.skills.ChatbotListPublicSkillCategoriesResponse
import io.element.android.libraries.chatbot.api.model.skills.ChatbotListPublicSkillsResponse
import io.element.android.libraries.chatbot.api.model.skills.ChatbotPublicSkillCategory
import io.element.android.libraries.chatbot.api.model.skills.ChatbotSkillListFilters
import io.element.android.libraries.chatbot.api.model.skills.ChatbotUserSkill
import io.element.android.libraries.chatbot.test.FakeChatbotApiService
import io.element.android.libraries.chatbot.test.FakeChatbotApiServiceFactory
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.awaitWithLatch
import io.element.android.tests.testutils.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SkillMarketplacePresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `present - loads first page on appear`() = runTest {
        val service = FakeChatbotApiService().apply {
            listPublicSkillsResult = { page, pageSize, search ->
                Result.success(
                    ChatbotListPublicSkillsResponse(
                        skills = listOf(aSkill(id = "public", name = "Public")),
                        page = page,
                        pageSize = pageSize,
                        total = 2,
                    )
                ).also {
                    assertThat(search).isNull()
                }
            }
        }
        val presenter = createSkillMarketplacePresenter(service = service)

        presenter.test {
            val initialState = awaitItem()
            initialState.eventSink(SkillMarketplaceEvents.OnAppear)

            val loadedState = awaitStateWhere { it.skills.singleOrNull()?.id == "public" && !it.isLoading }
            assertThat(loadedState.page).isEqualTo(1)
            assertThat(loadedState.pageSize).isEqualTo(20)
            assertThat(loadedState.total).isEqualTo(2)
            assertThat(loadedState.hasMore).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - refresh reloads first page`() = runTest {
        var calls = 0
        val service = FakeChatbotApiService().apply {
            listPublicSkillsResult = { _, _, _ ->
                calls++
                Result.success(ChatbotListPublicSkillsResponse(skills = listOf(aSkill(id = "public-$calls", name = "Public $calls")), total = 3))
            }
        }
        val presenter = createSkillMarketplacePresenter(service = service)

        presenter.test {
            awaitItem().eventSink(SkillMarketplaceEvents.OnAppear)
            val loadedState = awaitStateWhere { it.skills.singleOrNull()?.id == "public-1" && !it.isLoading }

            loadedState.eventSink(SkillMarketplaceEvents.Refresh)
            awaitStateWhere { it.skills.singleOrNull()?.id == "public-2" && !it.isLoading }
            assertThat(calls).isEqualTo(2)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - search debounces and sends trimmed nullable query`() = runTest {
        val capturedSearches = mutableListOf<String?>()
        val service = FakeChatbotApiService().apply {
            listPublicSkillsResult = { _, _, search ->
                capturedSearches += search
                Result.success(ChatbotListPublicSkillsResponse(skills = listOf(aSkill(id = "public-${capturedSearches.size}", name = "Public")), total = 10))
            }
        }
        val presenter = createSkillMarketplacePresenter(service = service)

        presenter.test {
            awaitItem().eventSink(SkillMarketplaceEvents.OnAppear)
            val loadedState = awaitStateWhere { it.skills.isNotEmpty() && !it.isLoading }
            assertThat(capturedSearches).containsExactly(null)

            loadedState.eventSink(SkillMarketplaceEvents.SearchQueryChanged("  search  "))
            awaitStateWhere { it.searchQuery == "  search  " }
            advanceTimeBy(449)
            assertThat(capturedSearches).containsExactly(null)
            advanceTimeBy(1)
            runCurrent()
            awaitStateWhere { capturedSearches == listOf(null, "search") && !it.isLoading }
            assertThat(capturedSearches).containsExactly(null, "search").inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - blank search sends nullable query`() = runTest {
        val capturedSearches = mutableListOf<String?>()
        val service = FakeChatbotApiService().apply {
            listPublicSkillsResult = { _, _, search ->
                capturedSearches += search
                Result.success(ChatbotListPublicSkillsResponse(skills = listOf(aSkill(id = "public-${capturedSearches.size}", name = "Public")), total = 10))
            }
        }
        val presenter = createSkillMarketplacePresenter(service = service)

        presenter.test {
            awaitItem().eventSink(SkillMarketplaceEvents.OnAppear)
            val loadedState = awaitStateWhere { it.skills.isNotEmpty() && !it.isLoading }
            assertThat(capturedSearches).containsExactly(null)

            loadedState.eventSink(SkillMarketplaceEvents.SearchQueryChanged("   "))
            awaitStateWhere { it.searchQuery == "   " }
            advanceTimeBy(450)
            runCurrent()
            awaitStateWhere { capturedSearches == listOf(null, null) && !it.isLoading }
            assertThat(capturedSearches).containsExactly(null, null).inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - filter state is sent to public skills API`() = runTest {
        val captured = mutableListOf<ChatbotSkillListFilters>()
        val service = FakeChatbotApiService().apply {
            listPublicSkillCategoriesResult = {
                Result.success(ChatbotListPublicSkillCategoriesResponse(categories = listOf(aCategory("Testing", "testing"))))
            }
            listPublicSkillsWithFiltersResult = { _, _, filters ->
                captured += filters
                Result.success(ChatbotListPublicSkillsResponse(skills = listOf(aSkill(id = "browser", name = "Browser")), total = 1))
            }
        }
        val presenter = createSkillMarketplacePresenter(service = service)

        presenter.test {
            awaitItem().eventSink(SkillMarketplaceEvents.OnAppear)
            val loadedState = awaitStateWhere { it.skills.isNotEmpty() && !it.isLoading }
            loadedState.eventSink(SkillMarketplaceEvents.ApplyFilterToken(SkillFilterToken.Category("Testing")))
            awaitStateWhere { captured.any { filters -> filters.categorySlug == "Testing" } && !it.isLoading }

            assertThat(captured.last().categorySlug).isEqualTo("Testing")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - add filter does not open sheet when facets are empty`() = runTest {
        val service = FakeChatbotApiService()
        val presenter = createSkillMarketplacePresenter(service = service)

        presenter.test {
            val initialState = awaitItem()
            assertThat(initialState.filtersAvailable).isFalse()
            assertThat(initialState.isFilterSheetVisible).isFalse()
            initialState.eventSink(SkillMarketplaceEvents.AddFilter)

            awaitWithLatch { }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - next page appends and stops when total reached`() = runTest {
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
        val presenter = createSkillMarketplacePresenter(service = service)

        presenter.test {
            awaitItem().eventSink(SkillMarketplaceEvents.OnAppear)
            val firstPageState = awaitStateWhere { it.skills.size == 2 && !it.isLoading }

            firstPageState.eventSink(SkillMarketplaceEvents.LoadNextPage)
            val secondPageState = awaitStateWhere { it.skills.size == 3 && !it.isLoadingNextPage }
            assertThat(secondPageState.skills.map { it.id }).containsExactly("one", "two", "three").inOrder()
            assertThat(secondPageState.hasMore).isFalse()

            secondPageState.eventSink(SkillMarketplaceEvents.LoadNextPage)
            assertThat(requestedPages).containsExactly(1, 2).inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - select skill notifies navigator`() = runTest {
        val navigator = FakeSkillMarketplaceNavigator()
        val presenter = createSkillMarketplacePresenter(navigator = navigator)

        presenter.test {
            awaitItem().eventSink(SkillMarketplaceEvents.SelectSkill("skill-id"))
            assertThat(navigator.openedSkills).containsExactly("skill-id")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - load failure preserves loaded data and clears error`() = runTest {
        var fail = false
        val service = FakeChatbotApiService().apply {
            listPublicSkillsResult = { _, _, _ ->
                if (fail) {
                    Result.failure(IllegalStateException("marketplace down"))
                } else {
                    Result.success(ChatbotListPublicSkillsResponse(skills = listOf(aSkill(id = "stable", name = "Stable")), total = 2))
                }
            }
        }
        val presenter = createSkillMarketplacePresenter(service = service)

        presenter.test {
            awaitItem().eventSink(SkillMarketplaceEvents.OnAppear)
            val loadedState = awaitStateWhere { it.skills.singleOrNull()?.id == "stable" && !it.isLoading }

            fail = true
            loadedState.eventSink(SkillMarketplaceEvents.Refresh)
            val failedState = awaitStateWhere { it.error == "marketplace down" && !it.isLoading }
            assertThat(failedState.skills.single().id).isEqualTo("stable")

            failedState.eventSink(SkillMarketplaceEvents.ClearError)
            assertThat(awaitStateWhere { it.error == null }.skills.single().id).isEqualTo("stable")
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createSkillMarketplacePresenter(
        service: FakeChatbotApiService = FakeChatbotApiService(),
        navigator: SkillMarketplaceNavigator = FakeSkillMarketplaceNavigator(),
    ): SkillMarketplacePresenter {
        return SkillMarketplacePresenter(
            navigator = navigator,
            filterBridge = null,
            matrixClient = FakeMatrixClient(),
            chatbotApiServiceFactory = FakeChatbotApiServiceFactory(service),
        )
    }
}

private fun aCategory(name: String, slug: String): ChatbotPublicSkillCategory =
    ChatbotPublicSkillCategory(id = 1, name = name, slug = slug, sortOrder = 1, categoryType = 1)

private class FakeSkillMarketplaceNavigator : SkillMarketplaceNavigator {
    val openedSkills = mutableListOf<String>()

    override fun onOpenSkill(id: String) {
        openedSkills += id
    }
}

private fun aSkill(
    id: String,
    name: String,
): ChatbotUserSkill {
    return ChatbotUserSkill(
        id = id,
        name = name,
    )
}

private suspend fun TurbineTestContext<SkillMarketplaceState>.awaitStateWhere(
    predicate: (SkillMarketplaceState) -> Boolean,
): SkillMarketplaceState {
    while (true) {
        val state = awaitItem()
        if (predicate(state)) {
            return state
        }
    }
}

/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.skills.impl.agentskills

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
class AgentSkillsPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `present - loads attached and user skills once`() = runTest {
        var attachedCalls = 0
        var userCalls = 0
        val service = FakeChatbotApiService().apply {
            listAgentSkillsResult = {
                attachedCalls++
                Result.success(listOf(aSkill(id = "attached", name = "Attached")))
            }
            listUserSkillsResult = {
                userCalls++
                Result.success(listOf(aSkill(id = "z", name = "Zeta"), aSkill(id = "a", name = "Alpha")))
            }
        }
        val presenter = createAgentSkillsPresenter(service = service)

        presenter.test {
            awaitItem().eventSink(AgentSkillsEvents.OnAppear)
            val loadedState = awaitStateWhere { it.attachedSkills.size == 1 && it.userSkills.size == 2 && !it.isLoading }

            assertThat(loadedState.attachedSkills.map { it.id }).containsExactly("attached")
            assertThat(loadedState.selectedSkillIds).containsExactly("attached")
            assertThat(loadedState.originalSkillIds).containsExactly("attached")
            assertThat(loadedState.userSkills.map { it.id }).containsExactly("a", "z").inOrder()

            loadedState.eventSink(AgentSkillsEvents.OnAppear)
            assertThat(attachedCalls).isEqualTo(1)
            assertThat(userCalls).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - mine search trims and matches name or description`() = runTest {
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
        val presenter = createAgentSkillsPresenter(service = service)

        presenter.test {
            awaitItem().eventSink(AgentSkillsEvents.OnAppear)
            val loadedState = awaitStateWhere { it.userSkills.size == 3 && !it.isLoading }

            loadedState.eventSink(AgentSkillsEvents.SearchQueryChanged(" SCHEDULES "))
            val schedulesState = awaitStateWhere { it.searchQuery == " SCHEDULES " }
            assertThat(schedulesState.filteredUserSkills.map { it.id }).containsExactly("calendar")

            schedulesState.eventSink(AgentSkillsEvents.SearchQueryChanged("search"))
            val searchState = awaitStateWhere { it.searchQuery == "search" }
            assertThat(searchState.filteredUserSkills.map { it.id }).containsExactly("search")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - selecting public tab loads first page once`() = runTest {
        var publicCalls = 0
        val service = FakeChatbotApiService().apply {
            listPublicSkillsResult = { page, pageSize, search ->
                publicCalls++
                Result.success(ChatbotListPublicSkillsResponse(skills = listOf(aSkill(id = "public", name = "$page-$pageSize-$search")), total = 1, page = page))
            }
        }
        val presenter = createAgentSkillsPresenter(service = service)

        presenter.test {
            val initialState = awaitItem()
            initialState.eventSink(AgentSkillsEvents.SearchQueryChanged("mine"))
            val searchedState = awaitStateWhere { it.searchQuery == "mine" }

            searchedState.eventSink(AgentSkillsEvents.SelectTab(AgentSkillsTab.Public))
            val publicState = awaitStateWhere { it.publicSkills.isNotEmpty() && !it.isLoadingPublic }
            assertThat(publicState.searchQuery).isEmpty()
            assertThat(publicCalls).isEqualTo(1)

            publicState.eventSink(AgentSkillsEvents.SelectTab(AgentSkillsTab.Mine))
            val mineState = awaitStateWhere { it.selectedTab == AgentSkillsTab.Mine }
            mineState.eventSink(AgentSkillsEvents.SelectTab(AgentSkillsTab.Public))
            awaitStateWhere { it.selectedTab == AgentSkillsTab.Public }
            assertThat(publicCalls).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - public search debounces and sends trimmed nullable query`() = runTest {
        val capturedSearches = mutableListOf<String?>()
        val service = FakeChatbotApiService().apply {
            listPublicSkillsResult = { _, _, search ->
                capturedSearches += search
                Result.success(ChatbotListPublicSkillsResponse(skills = listOf(aSkill(id = "public-${capturedSearches.size}", name = "Public")), total = 10))
            }
        }
        val presenter = createAgentSkillsPresenter(service = service)

        presenter.test {
            awaitItem().eventSink(AgentSkillsEvents.SelectTab(AgentSkillsTab.Public))
            val publicState = awaitStateWhere { it.publicSkills.isNotEmpty() && !it.isLoadingPublic }
            assertThat(capturedSearches).containsExactly(null)

            publicState.eventSink(AgentSkillsEvents.SearchQueryChanged("  search  "))
            awaitStateWhere { it.searchQuery == "  search  " && it.selectedTab == AgentSkillsTab.Public }
            advanceTimeBy(449)
            assertThat(capturedSearches).containsExactly(null)
            advanceTimeBy(1)
            runCurrent()
            val searchedState = awaitStateWhere {
                capturedSearches == listOf(null, "search") &&
                    it.publicSkills.singleOrNull()?.id == "public-2" &&
                    !it.isLoadingPublic
            }

            searchedState.eventSink(AgentSkillsEvents.SearchQueryChanged("   "))
            awaitStateWhere { it.searchQuery == "   " }
            advanceTimeBy(450)
            runCurrent()
            awaitStateWhere { capturedSearches == listOf(null, "search", null) && !it.isLoadingPublic }
            assertThat(capturedSearches).containsExactly(null, "search", null).inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - next public page appends and stops when total reached`() = runTest {
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
        val presenter = createAgentSkillsPresenter(service = service)

        presenter.test {
            awaitItem().eventSink(AgentSkillsEvents.SelectTab(AgentSkillsTab.Public))
            val firstPageState = awaitStateWhere { it.publicSkills.size == 2 && !it.isLoadingPublic }
            assertThat(firstPageState.publicHasMore).isTrue()

            firstPageState.eventSink(AgentSkillsEvents.LoadNextPublicPage)
            val secondPageState = awaitStateWhere { it.publicSkills.size == 3 && !it.isLoadingPublicNextPage }
            assertThat(secondPageState.publicSkills.map { it.id }).containsExactly("one", "two", "three").inOrder()
            assertThat(secondPageState.publicHasMore).isFalse()

            secondPageState.eventSink(AgentSkillsEvents.LoadNextPublicPage)
            assertThat(requestedPages).containsExactly(1, 2).inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - save adds only newly selected skills in sorted order`() = runTest {
        val added = mutableListOf<String>()
        val navigator = FakeAgentSkillsNavigator()
        val service = FakeChatbotApiService().apply {
            listAgentSkillsResult = { Result.success(listOf(aSkill(id = "existing", name = "Existing"))) }
            addAgentSkillResult = { _, skillId, _ ->
                added += skillId
                Result.success(Unit)
            }
        }
        val presenter = createAgentSkillsPresenter(service = service, navigator = navigator)

        presenter.test {
            awaitItem().eventSink(AgentSkillsEvents.OnAppear)
            val loadedState = awaitStateWhere { it.originalSkillIds.contains("existing") && !it.isLoading }

            loadedState.eventSink(AgentSkillsEvents.ToggleSkill(aSkill(id = "z", name = "Zeta")))
            val zState = awaitStateWhere { "z" in it.selectedSkillIds }
            zState.eventSink(AgentSkillsEvents.ToggleSkill(aSkill(id = "a", name = "Alpha")))
            val selectedState = awaitStateWhere { it.newSelectedSkillIds == setOf("a", "z") }
            selectedState.eventSink(AgentSkillsEvents.Save)

            awaitStateWhere { !it.isSaving && navigator.savedCalls == 1 }
            assertThat(added).containsExactly("a", "z").inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - save with no new skills completes without api call`() = runTest {
        var addCalls = 0
        val navigator = FakeAgentSkillsNavigator()
        val service = FakeChatbotApiService().apply {
            listAgentSkillsResult = { Result.success(listOf(aSkill(id = "existing", name = "Existing"))) }
            addAgentSkillResult = { _, _, _ ->
                addCalls++
                Result.success(Unit)
            }
        }
        val presenter = createAgentSkillsPresenter(service = service, navigator = navigator)

        presenter.test {
            awaitItem().eventSink(AgentSkillsEvents.OnAppear)
            val loadedState = awaitStateWhere { it.originalSkillIds.contains("existing") && !it.isLoading }
            loadedState.eventSink(AgentSkillsEvents.Save)

            assertThat(navigator.savedCalls).isEqualTo(1)
            assertThat(addCalls).isEqualTo(0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - partial save failure keeps failed skill selected and merges successful additions`() = runTest {
        val service = FakeChatbotApiService().apply {
            addAgentSkillResult = { _, skillId, _ ->
                if (skillId == "b") {
                    Result.failure(IllegalStateException("boom"))
                } else {
                    Result.success(Unit)
                }
            }
        }
        val navigator = FakeAgentSkillsNavigator()
        val presenter = createAgentSkillsPresenter(service = service, navigator = navigator)

        presenter.test {
            val initialState = awaitItem()
            initialState.eventSink(AgentSkillsEvents.ToggleSkill(aSkill(id = "a", name = "Alpha")))
            val aState = awaitStateWhere { "a" in it.selectedSkillIds }
            aState.eventSink(AgentSkillsEvents.ToggleSkill(aSkill(id = "b", name = "Beta")))
            val selectedState = awaitStateWhere { it.newSelectedSkillIds == setOf("a", "b") }
            selectedState.eventSink(AgentSkillsEvents.Save)

            val failedState = awaitStateWhere { it.saveFailures.singleOrNull()?.skillId == "b" && !it.isSaving }
            assertThat(failedState.originalSkillIds).contains("a")
            assertThat(failedState.originalSkillIds).doesNotContain("b")
            assertThat(failedState.selectedSkillIds).containsAtLeast("a", "b")
            assertThat(failedState.error).isEqualTo("Failed to add 1 skill")
            assertThat(navigator.savedCalls).isEqualTo(0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - load failure preserves previously loaded state`() = runTest {
        var calls = 0
        val service = FakeChatbotApiService().apply {
            listAgentSkillsResult = {
                calls++
                if (calls == 1) {
                    Result.success(listOf(aSkill(id = "stable", name = "Stable")))
                } else {
                    Result.failure(IllegalStateException("network"))
                }
            }
        }
        val presenter = createAgentSkillsPresenter(service = service)

        presenter.test {
            awaitItem().eventSink(AgentSkillsEvents.OnAppear)
            val loadedState = awaitStateWhere { it.attachedSkills.singleOrNull()?.id == "stable" && !it.isLoading }
            loadedState.eventSink(AgentSkillsEvents.Refresh)

            val failedState = awaitStateWhere { it.error == "network" && !it.isLoading }
            assertThat(failedState.attachedSkills.single().id).isEqualTo("stable")
            assertThat(failedState.selectedSkillIds).contains("stable")
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createAgentSkillsPresenter(
        botName: String = "helper",
        service: FakeChatbotApiService = FakeChatbotApiService(),
        navigator: AgentSkillsNavigator = FakeAgentSkillsNavigator(),
    ): AgentSkillsPresenter {
        return AgentSkillsPresenter(
            botName = botName,
            navigator = navigator,
            matrixClient = FakeMatrixClient(),
            chatbotApiServiceFactory = FakeChatbotApiServiceFactory(service),
        )
    }
}

private class FakeAgentSkillsNavigator : AgentSkillsNavigator {
    var savedCalls = 0

    override fun onSaved() {
        savedCalls++
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

private suspend fun TurbineTestContext<AgentSkillsState>.awaitStateWhere(
    predicate: (AgentSkillsState) -> Boolean,
): AgentSkillsState {
    while (true) {
        val state = awaitItem()
        if (predicate(state)) {
            return state
        }
    }
}

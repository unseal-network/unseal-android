/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.skills.impl.agentskills

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
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AssistedInject
class AgentSkillsPresenter(
    @Assisted private val botName: String,
    @Assisted private val navigator: AgentSkillsNavigator,
    private val matrixClient: MatrixClient,
    private val chatbotApiServiceFactory: ChatbotApiServiceFactory,
) : Presenter<AgentSkillsState> {
    @AssistedFactory
    interface Factory {
        fun create(botName: String, navigator: AgentSkillsNavigator): AgentSkillsPresenter
    }

    @Composable
    override fun present(): AgentSkillsState {
        val coroutineScope = rememberCoroutineScope()
        var attachedSkills by remember { mutableStateOf(emptyList<ChatbotUserSkill>()) }
        var userSkills by remember { mutableStateOf(emptyList<ChatbotUserSkill>()) }
        var selectedSkills by remember { mutableStateOf(emptyList<ChatbotUserSkill>()) }
        var originalSkillIds by remember { mutableStateOf(emptySet<String>()) }
        var selectedSkillIds by remember { mutableStateOf(emptySet<String>()) }
        var selectedTab by remember { mutableStateOf(AgentSkillsTab.Mine) }
        var searchQuery by remember { mutableStateOf("") }
        var publicSkills by remember { mutableStateOf(emptyList<ChatbotUserSkill>()) }
        var publicTotal by remember { mutableStateOf<Int?>(null) }
        var publicHasMoreOverride by remember { mutableStateOf<Boolean?>(null) }
        var publicPage by remember { mutableStateOf(1) }
        var isLoading by remember { mutableStateOf(false) }
        var isLoadingPublic by remember { mutableStateOf(false) }
        var isLoadingPublicNextPage by remember { mutableStateOf(false) }
        var isSaving by remember { mutableStateOf(false) }
        var saveFailures by remember { mutableStateOf(emptyList<AgentSkillSaveFailure>()) }
        var error by remember { mutableStateOf<String?>(null) }
        var hasLoadedOnce by remember { mutableStateOf(false) }
        var publicSearchJob by remember { mutableStateOf<Job?>(null) }

        suspend fun api() = chatbotApiServiceFactory.createForHomeserver(matrixClient)

        fun errorMessage(throwable: Throwable, fallback: String): String {
            return throwable.message ?: throwable::class.simpleName ?: fallback
        }

        fun publicHasMore(): Boolean {
            return publicTotal?.let { publicSkills.size < it }
                ?: publicHasMoreOverride
                ?: (publicSkills.size >= PUBLIC_PAGE_SIZE)
        }

        fun loadAgentAndUserSkills(isInitial: Boolean) {
            if (isInitial && hasLoadedOnce) return
            coroutineScope.launch {
                isLoading = true
                val service = api()
                var nextError: String? = null
                service.listAgentSkills(botName)
                    .onSuccess { skills ->
                        attachedSkills = skills.sortedBySkillName()
                        val attachedIds = skills.map { it.id }.toSet()
                        originalSkillIds = originalSkillIds + attachedIds
                        selectedSkillIds = selectedSkillIds + attachedIds
                        selectedSkills = (selectedSkills + skills).distinctBy { it.id }.sortedBySkillName()
                    }
                    .onFailure { nextError = errorMessage(it, "Failed to load agent skills") }

                service.listUserSkills(visibility = null)
                    .onSuccess { skills ->
                        userSkills = skills.sortedBySkillName()
                    }
                    .onFailure { nextError = errorMessage(it, "Failed to load skills") }
                error = nextError
                isLoading = false
                hasLoadedOnce = true
            }
        }

        fun loadPublicPage(page: Int, replacing: Boolean) {
            if (replacing) {
                isLoadingPublic = true
            } else {
                isLoadingPublicNextPage = true
            }
            coroutineScope.launch {
                val query = searchQuery.trim().takeIf { it.isNotEmpty() }
                api().listPublicSkills(page = page, pageSize = PUBLIC_PAGE_SIZE, search = query)
                    .onSuccess { response ->
                        publicTotal = response.total
                        publicHasMoreOverride = response.hasMore
                        publicPage = response.page ?: page
                        publicSkills = if (replacing) {
                            response.skills
                        } else {
                            publicSkills + response.skills
                        }
                        error = null
                    }
                    .onFailure { error = errorMessage(it, "Failed to load public skills") }
                if (replacing) {
                    isLoadingPublic = false
                } else {
                    isLoadingPublicNextPage = false
                }
            }
        }

        fun loadPublicFirstPage() {
            publicPage = 1
            publicSkills = emptyList()
            publicTotal = null
            publicHasMoreOverride = null
            loadPublicPage(page = 1, replacing = true)
        }

        fun schedulePublicSearch() {
            publicSearchJob?.cancel()
            publicSearchJob = coroutineScope.launch {
                delay(SEARCH_DEBOUNCE_MS)
                loadPublicFirstPage()
            }
        }

        fun toggleSkill(skill: ChatbotUserSkill) {
            if (skill.id in selectedSkillIds) {
                selectedSkillIds = selectedSkillIds - skill.id
                selectedSkills = selectedSkills.filterNot { it.id == skill.id }
            } else {
                selectedSkillIds = selectedSkillIds + skill.id
                selectedSkills = (selectedSkills + skill).distinctBy { it.id }.sortedBySkillName()
            }
            saveFailures = saveFailures.filterNot { it.skillId == skill.id }
        }

        fun save() {
            val idsToAdd = (selectedSkillIds - originalSkillIds).sorted()
            if (idsToAdd.isEmpty()) {
                navigator.onSaved()
                return
            }
            coroutineScope.launch {
                isSaving = true
                saveFailures = emptyList()
                val failures = mutableListOf<AgentSkillSaveFailure>()
                val service = api()
                for (skillId in idsToAdd) {
                    service.addAgentSkill(botName = botName, skillId = skillId, name = null)
                        .onSuccess {
                            originalSkillIds = originalSkillIds + skillId
                            val metadata = selectedSkills.firstOrNull { it.id == skillId }
                            if (metadata != null && attachedSkills.none { it.id == skillId }) {
                                attachedSkills = (attachedSkills + metadata).sortedBySkillName()
                            }
                        }
                        .onFailure {
                            failures += AgentSkillSaveFailure(
                                skillId = skillId,
                                message = errorMessage(it, "Failed to add skill"),
                            )
                        }
                }
                saveFailures = failures
                error = failures.takeIf { it.isNotEmpty() }?.let { "Failed to add ${it.size} skill${if (it.size == 1) "" else "s"}" }
                isSaving = false
                if (failures.isEmpty()) {
                    navigator.onSaved()
                }
            }
        }

        fun handleEvent(event: AgentSkillsEvents) {
            when (event) {
                AgentSkillsEvents.OnAppear -> loadAgentAndUserSkills(isInitial = true)
                AgentSkillsEvents.Refresh -> loadAgentAndUserSkills(isInitial = false)
                is AgentSkillsEvents.SelectTab -> {
                    selectedTab = event.tab
                    searchQuery = ""
                    publicSearchJob?.cancel()
                    if (event.tab == AgentSkillsTab.Public && publicSkills.isEmpty()) {
                        loadPublicFirstPage()
                    }
                }
                is AgentSkillsEvents.SearchQueryChanged -> {
                    searchQuery = event.query
                    if (selectedTab == AgentSkillsTab.Public) {
                        schedulePublicSearch()
                    }
                }
                is AgentSkillsEvents.ToggleSkill -> toggleSkill(event.skill)
                AgentSkillsEvents.LoadNextPublicPage -> {
                    if (!isLoadingPublicNextPage && !isLoadingPublic && publicHasMore()) {
                        loadPublicPage(page = publicPage + 1, replacing = false)
                    }
                }
                AgentSkillsEvents.Save -> save()
                AgentSkillsEvents.ClearError -> error = null
            }
        }

        return AgentSkillsState(
            botName = botName,
            attachedSkills = attachedSkills.toImmutableList(),
            userSkills = userSkills.toImmutableList(),
            selectedSkills = selectedSkills.toImmutableList(),
            originalSkillIds = originalSkillIds.toImmutableSet(),
            selectedSkillIds = selectedSkillIds.toImmutableSet(),
            selectedTab = selectedTab,
            searchQuery = searchQuery,
            publicSkills = publicSkills.toImmutableList(),
            publicTotal = publicTotal,
            publicPage = publicPage,
            publicPageSize = PUBLIC_PAGE_SIZE,
            publicHasMore = publicHasMore(),
            isLoading = isLoading,
            isLoadingPublic = isLoadingPublic,
            isLoadingPublicNextPage = isLoadingPublicNextPage,
            isSaving = isSaving,
            saveFailures = saveFailures.toImmutableList(),
            error = error,
            eventSink = ::handleEvent,
        )
    }

    private companion object {
        const val PUBLIC_PAGE_SIZE = 20
        const val SEARCH_DEBOUNCE_MS = 450L
    }
}

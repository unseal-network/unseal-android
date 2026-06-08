/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.skills.impl.detail

import app.cash.turbine.TurbineTestContext
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.chatbot.api.model.json.ChatbotJsonObject
import io.element.android.libraries.chatbot.api.model.skills.ChatbotGetUserSkillResponse
import io.element.android.libraries.chatbot.api.model.skills.ChatbotSkillVisibility
import io.element.android.libraries.chatbot.api.model.skills.ChatbotUpdateUserSkillResponse
import io.element.android.libraries.chatbot.api.model.skills.ChatbotUserSkill
import io.element.android.libraries.chatbot.test.FakeChatbotApiService
import io.element.android.libraries.chatbot.test.FakeChatbotApiServiceFactory
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Rule
import org.junit.Test

class SkillDetailPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `present - loads skill once`() = runTest {
        var calls = 0
        val service = FakeChatbotApiService().apply {
            getUserSkillResult = {
                calls++
                Result.success(ChatbotGetUserSkillResponse(skill = aSkill(id = it, name = "Loaded")))
            }
        }
        val presenter = createSkillDetailPresenter(service = service)

        presenter.test {
            val initialState = awaitItem()
            initialState.eventSink(SkillDetailEvents.OnAppear)

            val loadedState = awaitStateWhere { it.skill?.name == "Loaded" && !it.isLoading }
            assertThat(loadedState.title).isEqualTo("Loaded")
            assertThat(loadedState.canEdit).isTrue()

            loadedState.eventSink(SkillDetailEvents.OnAppear)
            assertThat(calls).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - refresh keeps loaded skill when loading fails`() = runTest {
        var fail = false
        val service = FakeChatbotApiService().apply {
            getUserSkillResult = {
                if (fail) {
                    Result.failure(IllegalStateException("network down"))
                } else {
                    Result.success(ChatbotGetUserSkillResponse(skill = aSkill(id = it, name = "Stable")))
                }
            }
        }
        val presenter = createSkillDetailPresenter(service = service)

        presenter.test {
            awaitItem().eventSink(SkillDetailEvents.OnAppear)
            val loadedState = awaitStateWhere { it.skill?.name == "Stable" && !it.isLoading }

            fail = true
            loadedState.eventSink(SkillDetailEvents.Refresh)
            val refreshedState = awaitStateWhere { it.skill?.name == "Stable" && !it.isLoading && it.error == "network down" }
            refreshedState.eventSink(SkillDetailEvents.ClearError)
            assertThat(awaitStateWhere { it.error == null }.skill?.name).isEqualTo("Stable")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - owner editing seeds fields and save sends metadata body`() = runTest {
        var capturedBody: ChatbotJsonObject? = null
        val service = FakeChatbotApiService().apply {
            getUserSkillResult = {
                Result.success(
                    ChatbotGetUserSkillResponse(
                        skill = aSkill(
                            id = it,
                            name = "Original",
                            description = "Original description",
                            visibility = ChatbotSkillVisibility.Public,
                        )
                    )
                )
            }
            updateUserSkillResult = { id, body ->
                capturedBody = body
                Result.success(
                    ChatbotUpdateUserSkillResponse(
                        skill = aSkill(
                            id = id,
                            name = body.stringValue("name").orEmpty(),
                            description = body.stringValue("description"),
                            visibility = ChatbotSkillVisibility.Shared,
                        )
                    )
                )
            }
        }
        val presenter = createSkillDetailPresenter(service = service)

        presenter.test {
            awaitItem().eventSink(SkillDetailEvents.OnAppear)
            val loadedState = awaitStateWhere { it.skill?.name == "Original" && !it.isLoading }

            loadedState.eventSink(SkillDetailEvents.StartEditing)
            val editingState = awaitStateWhere { it.isEditing }
            assertThat(editingState.editName).isEqualTo("Original")
            assertThat(editingState.editDescription).isEqualTo("Original description")
            assertThat(editingState.editVisibility).isEqualTo(ChatbotSkillVisibility.Public)

            editingState.eventSink(SkillDetailEvents.EditNameChanged("Updated"))
            awaitStateWhere { it.editName == "Updated" }.eventSink(SkillDetailEvents.EditDescriptionChanged("Updated description"))
            awaitStateWhere { it.editDescription == "Updated description" }.eventSink(SkillDetailEvents.EditVisibilityChanged(ChatbotSkillVisibility.Shared))
            awaitStateWhere { it.editVisibility == ChatbotSkillVisibility.Shared }.eventSink(SkillDetailEvents.SaveEditing)

            val savedState = awaitStateWhere { it.skill?.name == "Updated" && !it.isSaving && !it.isEditing }
            assertThat(savedState.skill?.description).isEqualTo("Updated description")
            assertThat(savedState.visibilityLabel).isEqualTo("Shared")
            assertThat(capturedBody?.stringValue("name")).isEqualTo("Updated")
            assertThat(capturedBody?.stringValue("description")).isEqualTo("Updated description")
            assertThat(capturedBody?.stringValue("visibility")).isEqualTo("shared")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - non owner ignores edit save and delete`() = runTest {
        var updateCalls = 0
        var deleteCalls = 0
        val navigator = FakeSkillDetailNavigator()
        val service = FakeChatbotApiService().apply {
            getUserSkillResult = {
                Result.success(ChatbotGetUserSkillResponse(skill = aSkill(id = it, name = "Public")))
            }
            updateUserSkillResult = { _, _ ->
                updateCalls++
                Result.success(ChatbotUpdateUserSkillResponse())
            }
            deleteUserSkillResult = {
                deleteCalls++
                Result.success(io.element.android.libraries.chatbot.api.model.skills.ChatbotDeleteUserSkillResponse(success = true))
            }
        }
        val presenter = createSkillDetailPresenter(service = service, isOwner = false, navigator = navigator)

        presenter.test {
            awaitItem().eventSink(SkillDetailEvents.OnAppear)
            val loadedState = awaitStateWhere { it.skill?.name == "Public" && !it.isLoading }
            assertThat(loadedState.canEdit).isFalse()

            loadedState.eventSink(SkillDetailEvents.StartEditing)
            loadedState.eventSink(SkillDetailEvents.SaveEditing)
            loadedState.eventSink(SkillDetailEvents.Delete)

            assertThat(updateCalls).isEqualTo(0)
            assertThat(deleteCalls).isEqualTo(0)
            assertThat(navigator.deletedIds).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - delete notifies navigator on success`() = runTest {
        val navigator = FakeSkillDetailNavigator()
        val presenter = createSkillDetailPresenter(navigator = navigator)

        presenter.test {
            awaitItem().eventSink(SkillDetailEvents.OnAppear)
            val loadedState = awaitStateWhere { it.skill != null && !it.isLoading }

            loadedState.eventSink(SkillDetailEvents.Delete)
            awaitStateWhere { !it.isDeleting && navigator.deletedIds.contains("skill") }
            assertThat(navigator.deletedIds).containsExactly("skill")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - save and delete failures preserve loaded skill`() = runTest {
        val navigator = FakeSkillDetailNavigator()
        val service = FakeChatbotApiService().apply {
            getUserSkillResult = {
                Result.success(ChatbotGetUserSkillResponse(skill = aSkill(id = it, name = "Stable")))
            }
            updateUserSkillResult = { _, _ -> Result.failure(IllegalStateException("update failed")) }
            deleteUserSkillResult = { Result.failure(IllegalStateException("delete failed")) }
        }
        val presenter = createSkillDetailPresenter(service = service, navigator = navigator)

        presenter.test {
            awaitItem().eventSink(SkillDetailEvents.OnAppear)
            val loadedState = awaitStateWhere { it.skill?.name == "Stable" && !it.isLoading }

            loadedState.eventSink(SkillDetailEvents.StartEditing)
            awaitStateWhere { it.isEditing }.eventSink(SkillDetailEvents.SaveEditing)
            val saveFailedState = awaitStateWhere { it.error == "update failed" && !it.isSaving }
            assertThat(saveFailedState.skill?.name).isEqualTo("Stable")
            assertThat(saveFailedState.isEditing).isTrue()

            saveFailedState.eventSink(SkillDetailEvents.ClearError)
            awaitStateWhere { it.error == null }.eventSink(SkillDetailEvents.Delete)
            val deleteFailedState = awaitStateWhere { it.error == "delete failed" && !it.isDeleting }
            assertThat(deleteFailedState.skill?.name).isEqualTo("Stable")
            assertThat(navigator.deletedIds).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createSkillDetailPresenter(
        service: FakeChatbotApiService = FakeChatbotApiService(),
        id: String = "skill",
        isOwner: Boolean = true,
        navigator: SkillDetailNavigator = FakeSkillDetailNavigator(),
    ): SkillDetailPresenter {
        return SkillDetailPresenter(
            id = id,
            isOwner = isOwner,
            navigator = navigator,
            matrixClient = FakeMatrixClient(),
            chatbotApiServiceFactory = FakeChatbotApiServiceFactory(service),
        )
    }
}

private class FakeSkillDetailNavigator : SkillDetailNavigator {
    val deletedIds = mutableListOf<String>()

    override fun onDeleted(id: String) {
        deletedIds += id
    }
}

private fun aSkill(
    id: String,
    name: String,
    description: String? = null,
    visibility: ChatbotSkillVisibility? = null,
): ChatbotUserSkill {
    return ChatbotUserSkill(
        id = id,
        name = name,
        description = description,
        visibility = visibility,
    )
}

private fun ChatbotJsonObject.stringValue(key: String): String? = get(key)?.jsonPrimitive?.contentOrNull

private suspend fun TurbineTestContext<SkillDetailState>.awaitStateWhere(
    predicate: (SkillDetailState) -> Boolean,
): SkillDetailState {
    while (true) {
        val state = awaitItem()
        if (predicate(state)) {
            return state
        }
    }
}

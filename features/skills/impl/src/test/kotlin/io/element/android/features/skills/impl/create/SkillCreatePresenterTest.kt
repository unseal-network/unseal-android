/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.skills.impl.create

import app.cash.turbine.TurbineTestContext
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.chatbot.test.FakeChatbotApiService
import io.element.android.libraries.chatbot.test.FakeChatbotApiServiceFactory
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class SkillCreatePresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `event - file import conflict can keep both by adding a numbered path`() = runTest {
        val presenter = createPresenter()

        presenter.test {
            val initialState = awaitItem()
            initialState.eventSink(SkillCreateEvents.FileEdited(initialState.manualFiles.single().id, "skill.md", "# Existing\n\nOld content"))
            val editedState = awaitStateWhere { it.manualFiles.single().content.contains("Old content") }

            editedState.eventSink(SkillCreateEvents.FilePicked("skill.md", "# Incoming\n\nNew content".encodeToByteArray()))
            val conflictState = awaitStateWhere { it.pendingFileConflict?.incomingFile?.path == "skill.md" }
            assertThat(conflictState.manualFiles).hasSize(1)
            assertThat(conflictState.manualFiles.single().content).contains("Old content")

            conflictState.eventSink(SkillCreateEvents.KeepBothConflictingFile)
            val keepBothState = awaitStateWhere { it.pendingFileConflict == null && it.manualFiles.size == 2 }
            assertThat(keepBothState.manualFiles.map { it.path }).containsExactly("skill.md", "skill2.md").inOrder()
            assertThat(keepBothState.manualFiles.map { it.content }).containsExactly("# Existing\n\nOld content", "# Incoming\n\nNew content").inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - file import conflict can overwrite existing file`() = runTest {
        val presenter = createPresenter()

        presenter.test {
            val initialState = awaitItem()
            initialState.eventSink(SkillCreateEvents.FileEdited(initialState.manualFiles.single().id, "skill.md", "# Existing\n\nOld content"))
            val editedState = awaitStateWhere { it.manualFiles.single().content.contains("Old content") }

            editedState.eventSink(SkillCreateEvents.FilePicked("skill.md", "# Incoming\n\nNew content".encodeToByteArray()))
            val conflictState = awaitStateWhere { it.pendingFileConflict != null }

            conflictState.eventSink(SkillCreateEvents.OverwriteConflictingFile)
            val overwriteState = awaitStateWhere { it.pendingFileConflict == null && it.manualFiles.single().content.contains("New content") }
            assertThat(overwriteState.manualFiles).hasSize(1)
            assertThat(overwriteState.manualFiles.single().path).isEqualTo("skill.md")
            assertThat(overwriteState.skillContent).contains("New content")
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createPresenter(
        service: FakeChatbotApiService = FakeChatbotApiService(),
        navigator: SkillCreateNavigator = FakeSkillCreateNavigator(),
    ): SkillCreatePresenter {
        return SkillCreatePresenter(
            navigator = navigator,
            matrixClient = FakeMatrixClient(),
            chatbotApiServiceFactory = FakeChatbotApiServiceFactory(service),
        )
    }
}

private class FakeSkillCreateNavigator : SkillCreateNavigator {
    override fun onViewDetail(id: String) = Unit
    override fun onBackToList() = Unit
}

private suspend fun TurbineTestContext<SkillCreateState>.awaitStateWhere(
    predicate: (SkillCreateState) -> Boolean,
): SkillCreateState {
    while (true) {
        val state = awaitItem()
        if (predicate(state)) {
            return state
        }
    }
}

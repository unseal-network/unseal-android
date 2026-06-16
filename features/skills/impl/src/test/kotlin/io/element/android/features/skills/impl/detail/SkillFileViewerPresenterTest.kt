/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.skills.impl.detail

import app.cash.turbine.TurbineTestContext
import com.google.common.truth.Truth.assertThat
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class SkillFileViewerPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `present - loads content once and exposes editable state`() = runTest {
        val client = FakeSkillFileClient(loadResults = ArrayDeque(listOf(Result.success("hello"))))
        val presenter = createPresenter(client = client, preuploadUrl = "https://upload.example/file")

        presenter.test {
            awaitItem().eventSink(SkillFileViewerEvents.OnAppear)
            val loaded = awaitStateWhere { !it.isLoading && it.content == "hello" }
            assertThat(loaded.isEditable).isTrue()
            assertThat(loaded.canSave).isTrue()
            loaded.eventSink(SkillFileViewerEvents.OnAppear)
            assertThat(client.loadCalls).containsExactly("https://download.example/file")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - retry reloads after failure`() = runTest {
        val client = FakeSkillFileClient(
            loadResults = ArrayDeque(
                listOf(
                    Result.failure(IllegalStateException("network")),
                    Result.success("reloaded"),
                )
            )
        )
        val presenter = createPresenter(client = client)

        presenter.test {
            awaitItem().eventSink(SkillFileViewerEvents.OnAppear)
            val failed = awaitStateWhere { !it.isLoading && it.loadError == "network" }
            failed.eventSink(SkillFileViewerEvents.RetryLoad)
            awaitStateWhere { !it.isLoading && it.content == "reloaded" && it.loadError == null }
            assertThat(client.loadCalls).hasSize(2)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - read only file ignores content changes and save`() = runTest {
        val client = FakeSkillFileClient(loadResults = ArrayDeque(listOf(Result.success("original"))))
        val presenter = createPresenter(client = client, preuploadUrl = null)

        presenter.test {
            awaitItem().eventSink(SkillFileViewerEvents.OnAppear)
            val loaded = awaitStateWhere { !it.isLoading && it.content == "original" }
            loaded.eventSink(SkillFileViewerEvents.ContentChanged("changed"))
            loaded.eventSink(SkillFileViewerEvents.Save)
            assertThat(loaded.content).isEqualTo("original")
            assertThat(client.saveCalls).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - save uploads content and reloads from presigned URL`() = runTest {
        val client = FakeSkillFileClient(
            loadResults = ArrayDeque(
                listOf(
                    Result.success("old"),
                    Result.success("saved from server"),
                )
            ),
            saveResult = Result.success(Unit),
        )
        val presenter = createPresenter(client = client, preuploadUrl = "https://upload.example/file")

        presenter.test {
            awaitItem().eventSink(SkillFileViewerEvents.OnAppear)
            val loaded = awaitStateWhere { !it.isLoading && it.content == "old" }
            loaded.eventSink(SkillFileViewerEvents.ContentChanged("new"))
            val edited = awaitStateWhere { it.content == "new" }
            edited.eventSink(SkillFileViewerEvents.Save)
            val saved = awaitStateWhere { !it.isSaving && it.content == "saved from server" && it.showSavedToast }
            assertThat(saved.saveError).isNull()
            assertThat(client.saveCalls).containsExactly(SaveCall("https://upload.example/file", "SKILL.md", "new"))
            assertThat(client.loadCalls).hasSize(2)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - save failure preserves edited content and shows error`() = runTest {
        val client = FakeSkillFileClient(
            loadResults = ArrayDeque(listOf(Result.success("old"))),
            saveResult = Result.failure(IllegalStateException("upload failed")),
        )
        val presenter = createPresenter(client = client, preuploadUrl = "https://upload.example/file")

        presenter.test {
            awaitItem().eventSink(SkillFileViewerEvents.OnAppear)
            awaitStateWhere { !it.isLoading && it.content == "old" }.eventSink(SkillFileViewerEvents.ContentChanged("new"))
            awaitStateWhere { it.content == "new" }.eventSink(SkillFileViewerEvents.Save)
            val failed = awaitStateWhere { !it.isSaving && it.saveError == "upload failed" }
            assertThat(failed.content).isEqualTo("new")
            failed.eventSink(SkillFileViewerEvents.ClearSaveError)
            assertThat(awaitStateWhere { it.saveError == null }.content).isEqualTo("new")
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createPresenter(
        client: SkillFileClient = FakeSkillFileClient(),
        preuploadUrl: String? = null,
    ): SkillFileViewerPresenter {
        return SkillFileViewerPresenter(
            fileName = "SKILL.md",
            presignedUrl = "https://download.example/file",
            preuploadUrl = preuploadUrl,
            client = client,
        )
    }
}

private data class SaveCall(val url: String, val fileName: String, val content: String)

private class FakeSkillFileClient(
    private val loadResults: ArrayDeque<Result<String>> = ArrayDeque(listOf(Result.success(""))),
    private val saveResult: Result<Unit> = Result.success(Unit),
) : SkillFileClient {
    val loadCalls = mutableListOf<String>()
    val saveCalls = mutableListOf<SaveCall>()

    override suspend fun load(url: String): Result<String> {
        loadCalls += url
        return loadResults.removeFirst()
    }

    override suspend fun save(url: String, fileName: String, content: String): Result<Unit> {
        saveCalls += SaveCall(url, fileName, content)
        return saveResult
    }
}

private suspend fun TurbineTestContext<SkillFileViewerState>.awaitStateWhere(
    predicate: (SkillFileViewerState) -> Boolean,
): SkillFileViewerState {
    while (true) {
        val state = awaitItem()
        if (predicate(state)) {
            return state
        }
    }
}

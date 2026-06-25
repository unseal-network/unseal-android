/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalTestApi::class)

package io.element.android.features.skills.impl.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.AndroidComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.element.android.features.skills.impl.shared.SkillFilterState
import io.element.android.libraries.chatbot.api.model.skills.ChatbotSkillFacetsResponse
import io.element.android.tests.testutils.EventsRecorder
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SkillsHomeViewTest {
    @Test
    fun `marketplace filtered empty state clears home filters`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<SkillsHomeEvents>()
        setView(state = aSkillsHomeState(eventSink = eventsRecorder))
        waitForIdle()
        eventsRecorder.clear()

        onNodeWithText("没有匹配的技能").assertIsDisplayed()
        onAllNodesWithText("暂无公开技能").assertCountEquals(0)
        onNodeWithText("清除筛选").performClick()

        eventsRecorder.assertSingle(SkillsHomeEvents.ClearFilters)
    }
}

private fun AndroidComposeUiTest<ComponentActivity>.setView(
    state: SkillsHomeState = aSkillsHomeState(),
) {
    setContent {
        SkillsHomeView(
            state = state,
            onBackClick = {},
        )
    }
}

private fun aSkillsHomeState(
    eventSink: (SkillsHomeEvents) -> Unit = EventsRecorder(),
) = SkillsHomeState(
    skills = persistentListOf(),
    selectedTab = SkillsHomeTab.Marketplace,
    marketplaceSkills = persistentListOf(),
    marketplaceTotal = 0,
    marketplacePage = 1,
    marketplacePageSize = 20,
    isLoading = false,
    isLoadingMarketplace = false,
    isLoadingMarketplaceNextPage = false,
    searchQuery = "missing",
    filterState = SkillFilterState(),
    facets = ChatbotSkillFacetsResponse(),
    isFilterSheetVisible = false,
    error = null,
    eventSink = eventSink,
)

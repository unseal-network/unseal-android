/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.features.home.impl.roomlist.SecurityBannerState
import io.element.android.features.home.impl.roomlist.aRoomListState
import io.element.android.features.home.impl.roomlist.aRoomsContentState
import io.element.android.features.home.impl.search.aRoomListSearchState
import io.element.android.features.home.impl.spacefilters.aSelectedSpaceFiltersState
import io.element.android.features.home.impl.spaces.aHomeSpacesState
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test

class HomePresentationModelTest {
    @Test
    fun `default chats state exposes iOS-style toolbar actions and bottom navigation`() {
        val model = aHomeState().toPresentationModel()

        assertThat(model.toolbar.actions).containsExactly(
            HomeToolbarAction.Settings,
            HomeToolbarAction.Search,
            HomeToolbarAction.StartChat,
            HomeToolbarAction.SpaceFilters,
        ).inOrder()
        assertThat(model.bottomNavigationPolicy).isEqualTo(HomeBottomNavigationPolicy.ChatsAndSpaces)
        assertThat(model.banner).isEqualTo(HomeBannerPresentation.None)
        assertThat(model.presentedSheet).isNull()
        assertThat(model.roomList.showFilters).isTrue()
    }

    @Test
    fun `presented sheet is exposed before UI rendering`() {
        val model = aHomeState(
            presentedSheet = HomePresentedSheet.AgentWelcome,
        ).toPresentationModel()

        assertThat(model.presentedSheet).isEqualTo(HomePresentedSheet.AgentWelcome)
    }

    @Test
    fun `selected space replaces start chat with filtered space menu`() {
        val model = aHomeState(
            roomListState = aRoomListState(
                spaceFiltersState = aSelectedSpaceFiltersState(),
            ),
        ).toPresentationModel()

        assertThat(model.toolbar.title).isEqualTo("Work")
        assertThat(model.toolbar.actions).contains(HomeToolbarAction.FilteredSpaceMenu)
        assertThat(model.toolbar.actions).doesNotContain(HomeToolbarAction.StartChat)
        assertThat(model.roomList.selectedSpaceName).isEqualTo("Work")
        assertThat(model.roomList.hasAnyFilterSelected).isTrue()
    }

    @Test
    fun `search active with empty query hides the room list`() {
        val model = aHomeState(
            roomListState = aRoomListState(
                searchState = aRoomListSearchState(
                    isSearchActive = true,
                    query = "",
                ),
            ),
        ).toPresentationModel()

        assertThat(model.roomList.hideRoomList).isTrue()
        assertThat(model.roomList.showFilters).isFalse()
        assertThat(model.toolbar.actions).doesNotContain(HomeToolbarAction.Search)
    }

    @Test
    fun `security and notification banners map into a single presentation slot`() {
        val recoveryModel = aHomeState(
            roomListState = aRoomListState(
                contentState = aRoomsContentState(securityBannerState = SecurityBannerState.RecoveryKeyConfirmation),
            ),
        ).toPresentationModel()
        val soundModel = aHomeState(
            roomListState = aRoomListState(
                contentState = aRoomsContentState(showNewNotificationSoundBanner = true),
            ),
        ).toPresentationModel()

        assertThat(recoveryModel.banner).isEqualTo(HomeBannerPresentation.RecoveryKeyConfirmation)
        assertThat(soundModel.banner).isEqualTo(HomeBannerPresentation.NewNotificationSound)
    }

    @Test
    fun `bottom navigation hides when there are no spaces`() {
        val model = aHomeState(
            homeSpacesState = aHomeSpacesState(spaceRooms = emptyList()),
        ).toPresentationModel()

        assertThat(model.bottomNavigationPolicy).isEqualTo(HomeBottomNavigationPolicy.Hidden)
    }

    @Test
    fun `empty filtered room list is represented before UI rendering`() {
        val model = aHomeState(
            roomListState = aRoomListState(
                spaceFiltersState = aSelectedSpaceFiltersState(),
                contentState = aRoomsContentState(summaries = persistentListOf()),
            ),
        ).toPresentationModel()

        assertThat(model.roomList.showEmptyFilterState).isTrue()
    }
}

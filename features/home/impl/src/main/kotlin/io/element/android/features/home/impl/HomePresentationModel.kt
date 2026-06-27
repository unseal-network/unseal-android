/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl

import io.element.android.features.home.impl.roomlist.RoomListContentState
import io.element.android.features.home.impl.roomlist.SecurityBannerState
import io.element.android.features.home.impl.spacefilters.SpaceFiltersState
import io.element.android.features.home.impl.spacefilters.selectedFilter

data class HomePresentationModel(
    val selectedSection: HomeNavigationBarItem,
    val toolbar: HomeToolbarPresentationModel,
    val presentedSheet: HomePresentedSheet?,
    val bottomNavigationPolicy: HomeBottomNavigationPolicy,
    val banner: HomeBannerPresentation,
    val roomList: HomeRoomListPresentationModel,
)

data class HomeToolbarPresentationModel(
    val showAccountAvatar: Boolean,
    val showAccountSetupBadge: Boolean,
    val title: String,
    val actions: List<HomeToolbarAction>,
)

enum class HomeToolbarAction {
    Search,
    StartChat,
    SpaceFilters,
    FilteredSpaceMenu,
    Settings,
}

enum class HomeBottomNavigationPolicy {
    Hidden,
    ChatsAndSpaces,
}

enum class HomeBannerPresentation {
    None,
    SetUpRecovery,
    RecoveryKeyConfirmation,
    NewNotificationSound,
}

data class HomeRoomListPresentationModel(
    val hideRoomList: Boolean,
    val showFilters: Boolean,
    val hasAnyFilterSelected: Boolean,
    val selectedSpaceName: String?,
    val showEmptyFilterState: Boolean,
)

fun HomeState.toPresentationModel(): HomePresentationModel {
    val roomListState = this.roomListState
    val selectedSpaceFilter = roomListState.spaceFiltersState.selectedFilter()
    val contentState = roomListState.contentState
    val isSearchActive = roomListState.searchState.isSearchActive
    val hasSearchQuery = roomListState.searchState.query.text.isNotBlank()
    val hideRoomList = isSearchActive && !hasSearchQuery
    val hasAnyFilterSelected = roomListState.filtersState.hasAnyFilterSelected || selectedSpaceFilter != null

    return HomePresentationModel(
        selectedSection = currentHomeNavigationBarItem,
        toolbar = HomeToolbarPresentationModel(
            showAccountAvatar = true,
            showAccountSetupBadge = showAvatarIndicator,
            title = selectedSpaceFilter?.spaceRoom?.displayName ?: "Chats",
            actions = buildToolbarActions(
                selectedSection = currentHomeNavigationBarItem,
                isSearchActive = isSearchActive,
                canShowSpaceFilters = roomListState.spaceFiltersState !is SpaceFiltersState.Disabled,
                hasSelectedSpace = selectedSpaceFilter != null,
                isRoomListReady = contentState is RoomListContentState.Rooms || contentState is RoomListContentState.Empty,
            ),
        ),
        presentedSheet = presentedSheet,
        bottomNavigationPolicy = HomeBottomNavigationPolicy.ChatsAndSpaces,
        banner = contentState.toBannerPresentation(),
        roomList = HomeRoomListPresentationModel(
            hideRoomList = hideRoomList,
            showFilters = !isSearchActive && contentState is RoomListContentState.Rooms,
            hasAnyFilterSelected = hasAnyFilterSelected,
            selectedSpaceName = selectedSpaceFilter?.spaceRoom?.displayName,
            showEmptyFilterState = !isSearchActive && hasAnyFilterSelected && contentState is RoomListContentState.Rooms && contentState.summaries.isEmpty(),
        ),
    )
}

private fun buildToolbarActions(
    selectedSection: HomeNavigationBarItem,
    isSearchActive: Boolean,
    canShowSpaceFilters: Boolean,
    hasSelectedSpace: Boolean,
    isRoomListReady: Boolean,
): List<HomeToolbarAction> {
    if (selectedSection != HomeNavigationBarItem.Chats) {
        return listOf(HomeToolbarAction.Settings)
    }
    return buildList {
        add(HomeToolbarAction.Settings)
        if (!isSearchActive) {
            add(HomeToolbarAction.Search)
        }
        if (isRoomListReady) {
            if (hasSelectedSpace) {
                add(HomeToolbarAction.FilteredSpaceMenu)
            } else {
                add(HomeToolbarAction.StartChat)
            }
        }
        if (canShowSpaceFilters) {
            add(HomeToolbarAction.SpaceFilters)
        }
    }
}

private fun RoomListContentState.toBannerPresentation(): HomeBannerPresentation {
    return when (this) {
        is RoomListContentState.Empty -> securityBannerState.toBannerPresentation(showNewNotificationSoundBanner = false)
        is RoomListContentState.Rooms -> securityBannerState.toBannerPresentation(showNewNotificationSoundBanner = showNewNotificationSoundBanner)
        is RoomListContentState.Skeleton -> HomeBannerPresentation.None
    }
}

private fun SecurityBannerState.toBannerPresentation(showNewNotificationSoundBanner: Boolean): HomeBannerPresentation {
    return when (this) {
        SecurityBannerState.SetUpRecovery -> HomeBannerPresentation.SetUpRecovery
        SecurityBannerState.RecoveryKeyConfirmation -> HomeBannerPresentation.RecoveryKeyConfirmation
        SecurityBannerState.None -> if (showNewNotificationSoundBanner) HomeBannerPresentation.NewNotificationSound else HomeBannerPresentation.None
    }
}

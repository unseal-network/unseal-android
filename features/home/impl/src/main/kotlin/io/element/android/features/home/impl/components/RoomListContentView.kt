/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.components

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.home.impl.R
import io.element.android.features.home.impl.contentType
import io.element.android.features.home.impl.filters.RoomListFilter
import io.element.android.features.home.impl.filters.RoomListFiltersEmptyStateResources
import io.element.android.features.home.impl.filters.RoomListFiltersState
import io.element.android.features.home.impl.filters.aRoomListFiltersState
import io.element.android.features.home.impl.filters.selection.FilterSelectionState
import io.element.android.features.home.impl.model.RoomListRoomSummary
import io.element.android.features.home.impl.model.RoomSummaryDisplayType
import io.element.android.features.home.impl.model.toHomeRoomRowRenderModel
import io.element.android.features.home.impl.roomlist.RoomListContentState
import io.element.android.features.home.impl.roomlist.RoomListContentStateProvider
import io.element.android.features.home.impl.roomlist.RoomListEvent
import io.element.android.features.home.impl.roomlist.SecurityBannerState
import io.element.android.features.home.impl.search.RoomListSearchEvent
import io.element.android.features.home.impl.search.RoomListSearchState
import io.element.android.features.home.impl.search.aRoomListSearchState
import io.element.android.features.home.impl.spacefilters.SpaceFiltersState
import io.element.android.features.home.impl.spacefilters.anUnselectedSpaceFiltersState
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.FilledTextField
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.utils.OnVisibleRangeChangeEffect
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun RoomListContentView(
    contentState: RoomListContentState,
    filtersState: RoomListFiltersState,
    searchState: RoomListSearchState,
    spaceFiltersState: SpaceFiltersState,
    lazyListState: LazyListState,
    hideInvitesAvatars: Boolean,
    eventSink: (RoomListEvent) -> Unit,
    onSetUpRecoveryClick: () -> Unit,
    onConfirmRecoveryKeyClick: () -> Unit,
    onRoomClick: (RoomListRoomSummary) -> Unit,
    onCreateRoomClick: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    when (contentState) {
        is RoomListContentState.Skeleton -> {
            SkeletonView(
                modifier = modifier,
                count = contentState.count,
                contentPadding = contentPadding,
            )
        }
        is RoomListContentState.Empty -> {
            EmptyView(
                modifier = modifier.padding(contentPadding),
                state = contentState,
                eventSink = eventSink,
                onSetUpRecoveryClick = onSetUpRecoveryClick,
                onConfirmRecoveryKeyClick = onConfirmRecoveryKeyClick,
                onCreateRoomClick = onCreateRoomClick,
            )
        }
        is RoomListContentState.Rooms -> {
            RoomsView(
                modifier = modifier,
                state = contentState,
                hideInvitesAvatars = hideInvitesAvatars,
                filtersState = filtersState,
                searchState = searchState,
                spaceFiltersState = spaceFiltersState,
                eventSink = eventSink,
                onSetUpRecoveryClick = onSetUpRecoveryClick,
                onConfirmRecoveryKeyClick = onConfirmRecoveryKeyClick,
                onRoomClick = onRoomClick,
                lazyListState = lazyListState,
                contentPadding = contentPadding,
            )
        }
    }
}

@Composable
private fun SkeletonView(
    count: Int,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
    ) {
        repeat(count) { index ->
            item {
                RoomSummaryPlaceholderRow()
                if (index != count - 1) {
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun EmptyView(
    state: RoomListContentState.Empty,
    eventSink: (RoomListEvent) -> Unit,
    onSetUpRecoveryClick: () -> Unit,
    onConfirmRecoveryKeyClick: () -> Unit,
    onCreateRoomClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        EmptyScaffold(
            title = R.string.screen_roomlist_empty_title,
            subtitle = R.string.screen_roomlist_empty_message,
            action = {
                Button(
                    text = stringResource(CommonStrings.action_start_chat),
                    leadingIcon = IconSource.Vector(CompoundIcons.Compose()),
                    onClick = onCreateRoomClick,
                )
            },
            modifier = Modifier.align(Alignment.Center),
        )
        Box {
            when (state.securityBannerState) {
                SecurityBannerState.SetUpRecovery -> {
                    SetUpRecoveryKeyBanner(
                        onContinueClick = onSetUpRecoveryClick,
                        onDismissClick = { eventSink(RoomListEvent.DismissBanner) },
                    )
                }
                SecurityBannerState.RecoveryKeyConfirmation -> {
                    ConfirmRecoveryKeyBanner(
                        onContinueClick = onConfirmRecoveryKeyClick,
                        onDismissClick = { eventSink(RoomListEvent.DismissBanner) },
                    )
                }
                SecurityBannerState.None -> Unit
            }
        }
    }
}

@Composable
private fun RoomsView(
    state: RoomListContentState.Rooms,
    hideInvitesAvatars: Boolean,
    filtersState: RoomListFiltersState,
    searchState: RoomListSearchState,
    spaceFiltersState: SpaceFiltersState,
    eventSink: (RoomListEvent) -> Unit,
    onSetUpRecoveryClick: () -> Unit,
    onConfirmRecoveryKeyClick: () -> Unit,
    onRoomClick: (RoomListRoomSummary) -> Unit,
    contentPadding: PaddingValues,
    lazyListState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val isSpaceFilterSelected = spaceFiltersState is SpaceFiltersState.Selected
    val hasAnyFilterSelected = filtersState.hasAnyFilterSelected || isSpaceFilterSelected
    if (state.summaries.isEmpty() && hasAnyFilterSelected) {
        EmptyViewForFilterStates(
            selectedFilters = filtersState.selectedFilters(),
            isSpaceFilterSelected = isSpaceFilterSelected,
            modifier = modifier.fillMaxSize()
        )
    } else {
        RoomsViewList(
            state = state,
            hideInvitesAvatars = hideInvitesAvatars,
            searchState = searchState,
            eventSink = eventSink,
            onSetUpRecoveryClick = onSetUpRecoveryClick,
            onConfirmRecoveryKeyClick = onConfirmRecoveryKeyClick,
            onRoomClick = onRoomClick,
            contentPadding = contentPadding,
            lazyListState = lazyListState,
            modifier = modifier.fillMaxSize(),
        )
    }
}

/** The single room currently being swiped / open, plus its reveal width and settled state. */
private data class ActiveSwipe(
    val id: String,
    val revealPx: Float,
    val settledOpen: Boolean,
)

@Composable
private fun RoomsViewList(
    state: RoomListContentState.Rooms,
    hideInvitesAvatars: Boolean,
    searchState: RoomListSearchState,
    eventSink: (RoomListEvent) -> Unit,
    onSetUpRecoveryClick: () -> Unit,
    onConfirmRecoveryKeyClick: () -> Unit,
    onRoomClick: (RoomListRoomSummary) -> Unit,
    contentPadding: PaddingValues,
    lazyListState: LazyListState,
    modifier: Modifier = Modifier,
) {
    // List-level swipe: one active row + one shared offset. Idle rows carry no gesture/animation/
    // offset nodes (the per-row machinery was the room-list scroll-jank cause). See
    // docs/perf/room-list-swipe-refactor.md.
    var activeSwipe by remember { mutableStateOf<ActiveSwipe?>(null) }
    // Live offset updated synchronously during a drag (no per-event coroutine race); the settle
    // animation runs on settleJob and is cancelled before any new drag/settle takes over.
    var swipeOffsetPx by remember { mutableFloatStateOf(0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val swipeScope = rememberCoroutineScope()
    val actionWidthPx = with(LocalDensity.current) { 64.dp.toPx() }
    val headerItemCount = roomListHeaderItemCount(
        securityBannerVisible = state.securityBannerState != SecurityBannerState.None,
        fullScreenIntentBannerVisible = state.fullScreenIntentPermissionsState.shouldDisplayBanner,
        batteryOptimizationBannerVisible = state.batteryOptimizationState.shouldDisplayBanner,
        newNotificationSoundBannerVisible = state.showNewNotificationSoundBanner,
    ) + 1
    // Read the latest values inside the (Unit-keyed, never-relaunched) gesture detector.
    val currentSummaries by rememberUpdatedState(state.summaries)
    val currentHeaderItemCount by rememberUpdatedState(headerItemCount)
    val currentSelectedRoomId by rememberUpdatedState(state.selectedRoomId)
    val currentActivityVisibility by rememberUpdatedState(state.activityVisibility)
    val searchQuery = searchState.query.text.toString()
    val isSearchActive = searchState.isSearchActive
    val displayedSummaries = if (searchQuery.isNotBlank()) searchState.results else state.summaries
    val focusManager = LocalFocusManager.current
    val currentSearchQuery by rememberUpdatedState(searchQuery)
    var hasHiddenSearchFieldInitially by remember { mutableStateOf(false) }

    BackHandler(enabled = isSearchActive) {
        focusManager.clearFocus()
        searchState.eventSink(RoomListSearchEvent.SetSearchActive(false))
    }

    LaunchedEffect(isSearchActive, searchQuery) {
        if (!hasHiddenSearchFieldInitially && !isSearchActive && searchQuery.isBlank()) {
            hasHiddenSearchFieldInitially = true
            lazyListState.scrollToItem(1)
        }
    }

    // Scrolling closes the open row — affects only the one active row (no per-row wrapper churn).
    LaunchedEffect(lazyListState.isScrollInProgress) {
        if (lazyListState.isScrollInProgress && activeSwipe != null) {
            settleJob?.cancel()
            activeSwipe = null
            swipeOffsetPx = 0f
        }
    }

    OnVisibleRangeChangeEffect(
        lazyListState = lazyListState,
        notifyWhileScrolling = false,
    ) { visibleRange ->
        if (searchQuery.isNotBlank()) {
            searchState.eventSink(RoomListSearchEvent.UpdateVisibleRange(visibleRange))
        } else {
            eventSink(RoomListEvent.UpdateVisibleRange(visibleRange))
        }
    }
    LazyColumn(
        state = lazyListState,
        modifier = modifier.pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = { position ->
                    if (currentSearchQuery.isNotBlank()) {
                        activeSwipe = null
                        swipeOffsetPx = 0f
                        return@detectHorizontalDragGestures
                    }
                    settleJob?.cancel()
                    val items = lazyListState.layoutInfo.visibleItemsInfo.map {
                        RoomRowBounds(index = it.index, top = it.offset, height = it.size)
                    }
                    val index = resolveSwipeTargetIndex(
                        y = position.y.roundToInt(),
                        visibleItems = items,
                        headerItemCount = currentHeaderItemCount,
                        summaryCount = currentSummaries.size,
                    )
                    val summary = index?.let { currentSummaries.getOrNull(it) }
                    val actions = summary
                        ?.toHomeRoomRowRenderModel(
                            isSelected = summary.roomId == currentSelectedRoomId,
                            activityVisibility = currentActivityVisibility,
                        )
                        ?.actions
                        ?.swipeActions
                        .orEmpty()
                    if (summary == null || actions.isEmpty()) {
                        activeSwipe = null
                        swipeOffsetPx = 0f
                        return@detectHorizontalDragGestures
                    }
                    val grabbedOpenRow = activeSwipe?.id == summary.id && activeSwipe?.settledOpen == true
                    activeSwipe = ActiveSwipe(id = summary.id, revealPx = actionWidthPx * actions.size, settledOpen = grabbedOpenRow)
                    // Continue from -reveal when re-grabbing the already-open row, else start closed.
                    swipeOffsetPx = if (grabbedOpenRow) -actionWidthPx * actions.size else 0f
                },
                onHorizontalDrag = { change, dragAmount ->
                    val active = activeSwipe ?: return@detectHorizontalDragGestures
                    change.consume()
                    swipeOffsetPx = (swipeOffsetPx + dragAmount).coerceIn(-active.revealPx - 16f, 10f)
                },
                onDragEnd = {
                    val active = activeSwipe ?: return@detectHorizontalDragGestures
                    val open = -swipeOffsetPx > active.revealPx * 0.35f
                    activeSwipe = active.copy(settledOpen = open)
                    settleJob = swipeScope.launch {
                        animate(swipeOffsetPx, if (open) -active.revealPx else 0f) { value, _ -> swipeOffsetPx = value }
                        if (!open) activeSwipe = null
                    }
                },
                onDragCancel = {
                    settleJob = swipeScope.launch {
                        animate(swipeOffsetPx, 0f) { value, _ -> swipeOffsetPx = value }
                        activeSwipe = null
                    }
                },
            )
        },
        contentPadding = contentPadding,
    ) {
        item {
            HomeRoomListSearchField(
                state = searchState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        when (state.securityBannerState) {
            SecurityBannerState.SetUpRecovery -> {
                item {
                    SetUpRecoveryKeyBanner(
                        onContinueClick = onSetUpRecoveryClick,
                        onDismissClick = { eventSink(RoomListEvent.DismissBanner) },
                    )
                }
            }
            SecurityBannerState.RecoveryKeyConfirmation -> {
                item {
                    ConfirmRecoveryKeyBanner(
                        onContinueClick = onConfirmRecoveryKeyClick,
                        onDismissClick = { eventSink(RoomListEvent.DismissBanner) },
                    )
                }
            }
            // Banner precedence (top-to-bottom): full-screen-intent > battery-optimization >
            // new-notification-sound > sound-unavailable. At most one renders at a time.
            SecurityBannerState.None -> when {
                state.fullScreenIntentPermissionsState.shouldDisplayBanner -> {
                    item {
                        FullScreenIntentPermissionBanner(state = state.fullScreenIntentPermissionsState)
                    }
                }
                state.batteryOptimizationState.shouldDisplayBanner -> {
                    item {
                        BatteryOptimizationBanner(state = state.batteryOptimizationState)
                    }
                }
                state.showNewNotificationSoundBanner -> {
                    item {
                        NewNotificationSoundBanner(
                            onDismissClick = { eventSink(RoomListEvent.DismissNewNotificationSoundBanner) },
                        )
                    }
                }
            }
        }

        if (!isSearchActive || searchQuery.isNotBlank()) {
            // Note: do not use a key for the LazyColumn, or the scroll will not behave as expected if a room
            // is moved to the top of the list.
            itemsIndexed(
                items = displayedSummaries,
                contentType = { _, room -> room.contentType() },
            ) { index, room ->
                RoomSummaryRow(
                    room = room,
                    hideInviteAvatars = hideInvitesAvatars,
                    isInviteSeen = room.displayType != RoomSummaryDisplayType.INVITE ||
                        state.seenRoomInvites.contains(room.roomId),
                    isSelected = room.roomId == state.selectedRoomId,
                    activityVisibility = state.activityVisibility,
                    showUnreadCount = state.showUnreadCount,
                    isSwipeActive = searchQuery.isBlank() && room.id == activeSwipe?.id,
                    swipeOffsetProvider = { swipeOffsetPx },
                    onCloseSwipe = {
                        settleJob?.cancel()
                        settleJob = swipeScope.launch {
                            animate(swipeOffsetPx, 0f) { value, _ -> swipeOffsetPx = value }
                            activeSwipe = null
                        }
                    },
                    onClick = onRoomClick,
                    eventSink = eventSink,
                )
                if (index != displayedSummaries.lastIndex) {
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun HomeRoomListSearchField(
    state: RoomListSearchState,
    modifier: Modifier = Modifier,
) {
    FilledTextField(
        modifier = modifier.onFocusChanged { focusState ->
            state.eventSink(RoomListSearchEvent.SetSearchActive(focusState.isFocused || state.query.text.isNotBlank()))
        },
        state = state.query,
        placeholder = {
            Text(
                text = stringResource(CommonStrings.action_search),
                color = ElementTheme.colors.textSecondary,
            )
        },
        lineLimits = TextFieldLineLimits.SingleLine,
        leadingIcon = {
            Icon(
                imageVector = CompoundIcons.Search(),
                contentDescription = null,
            )
        },
        trailingIcon = if (state.query.text.isNotEmpty()) {
            @Composable {
                IconButton(onClick = { state.eventSink(RoomListSearchEvent.ClearQuery) }) {
                    Icon(
                        imageVector = CompoundIcons.Close(),
                        contentDescription = stringResource(CommonStrings.action_cancel),
                    )
                }
            }
        } else {
            null
        },
    )
}

@Composable
private fun EmptyViewForFilterStates(
    selectedFilters: ImmutableList<RoomListFilter>,
    isSpaceFilterSelected: Boolean,
    modifier: Modifier = Modifier,
) {
    val emptyStateResources = RoomListFiltersEmptyStateResources.fromSelectedFilters(selectedFilters, isSpaceFilterSelected) ?: return
    EmptyScaffold(
        title = emptyStateResources.title,
        subtitle = emptyStateResources.subtitle,
        modifier = modifier,
    )
}

@Composable
private fun EmptyScaffold(
    @StringRes title: Int,
    @StringRes subtitle: Int,
    modifier: Modifier = Modifier,
    action: @Composable (ColumnScope.() -> Unit)? = null,
) {
    Column(
        modifier = modifier.padding(horizontal = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(title),
            style = ElementTheme.typography.fontHeadingMdBold,
            color = ElementTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(subtitle),
            style = ElementTheme.typography.fontBodyLgRegular,
            color = ElementTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(32.dp))
        action?.invoke(this)
    }
}

@PreviewsDayNight
@Composable
internal fun RoomListContentViewPreview(@PreviewParameter(RoomListContentStateProvider::class) state: RoomListContentState) = ElementPreview {
    RoomListContentView(
        contentState = state,
        filtersState = aRoomListFiltersState(
            filterSelectionStates = RoomListFilter.entries.map {
                FilterSelectionState(
                    filter = it,
                    isSelected = true
                )
            }
        ),
        searchState = aRoomListSearchState(),
        spaceFiltersState = anUnselectedSpaceFiltersState(),
        hideInvitesAvatars = false,
        eventSink = {},
        onSetUpRecoveryClick = {},
        onConfirmRecoveryKeyClick = {},
        onRoomClick = {},
        onCreateRoomClick = {},
        lazyListState = rememberLazyListState(),
        contentPadding = PaddingValues(0.dp),
    )
}

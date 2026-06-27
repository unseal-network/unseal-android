/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer.gamepicker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ripple
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.messages.impl.R
import okhttp3.OkHttpClient
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.ModalBottomSheet
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.gameapi.api.GameInfo
import io.element.android.libraries.gameapi.api.PlayingRoom
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GamePickerBottomSheet(
    state: GamePickerState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Build a custom Coil ImageLoader that adds `APP-U: s=<host>` to every request.
    // Icon URLs go through the homeserver's sign proxy which requires this header.
    // Re-created only when the homeserver host changes (effectively once per session).
    val context = LocalContext.current
    val iconImageLoader = remember(state.homeserverHost) {
        val host = state.homeserverHost
        ImageLoader.Builder(context)
            .components {
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = {
                            OkHttpClient.Builder()
                                .apply {
                                    if (host != null) {
                                        addInterceptor { chain ->
                                            chain.proceed(
                                                chain.request().newBuilder()
                                                    .header("APP-U", "s=$host")
                                                    .build()
                                            )
                                        }
                                    }
                                }
                                .build()
                        }
                    )
                )
            }
            .build()
    }

    // Dismiss the sheet only after the presenter signals that all async work (createGameRoom +
    // sendGameInviteMessage) has finished successfully. Dismissing early cancels the
    // rememberCoroutineScope and prevents the message from being sent.
    LaunchedEffect(state.shouldDismiss) {
        if (state.shouldDismiss) onDismiss()
    }

    ModalBottomSheet(
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        onDismissRequest = {
            state.eventSink(GamePickerEvent.Dismiss)
            onDismiss()
        },
        scrollable = false,
    ) {
        GamePickerContent(
            state = state,
            onDismiss = onDismiss,
            iconImageLoader = iconImageLoader,
        )
    }
}

@Composable
private fun GamePickerContent(
    state: GamePickerState,
    onDismiss: () -> Unit,
    iconImageLoader: ImageLoader,
    modifier: Modifier = Modifier,
) {
    /** Which game is showing its rooms sub-view (null = main view). */
    var selectedAppId by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = modifier
            .navigationBarsPadding()
            .imePadding()
    ) {
        if (selectedAppId != null) {
            RoomsView(
                appId = selectedAppId!!,
                allGames = state.allGames,
                myPlaying = state.myPlaying,
                iconImageLoader = iconImageLoader,
                onBack = { selectedAppId = null },
                onEnter = { appId, room ->
                    state.eventSink(GamePickerEvent.EnterPlayingRoom(appId, room))
                    onDismiss()
                },
            )
        } else {
            MainView(
                state = state,
                iconImageLoader = iconImageLoader,
                // Do NOT call onDismiss() here. Dismissing immediately would cancel the
                // rememberCoroutineScope in GamePickerPresenter and kill the in-flight
                // createGameRoom + sendGameInviteMessage coroutine. Instead, the presenter
                // sets shouldDismiss = true when the work completes, which triggers the
                // LaunchedEffect above to close the sheet.
                onGameClick = { game ->
                    state.eventSink(GamePickerEvent.CreateGame(game))
                },
                onMyGameClick = { appId ->
                    val rooms = state.myPlaying?.get(appId) ?: return@MainView
                    if (rooms.size == 1) {
                        state.eventSink(GamePickerEvent.EnterPlayingRoom(appId, rooms[0]))
                        onDismiss()
                    } else {
                        selectedAppId = appId
                    }
                },
            )
        }
    }
}

// ── Main view ────────────────────────────────────────────────────────────────

@Composable
private fun MainView(
    state: GamePickerState,
    iconImageLoader: ImageLoader,
    onGameClick: (GameInfo) -> Unit,
    onMyGameClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        if (state.creating) {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }

        // ── My games section ──────────────────────────────────────────────
        val hasMyPlaying = state.myPlaying?.isNotEmpty() == true
        val isMyPlayingLoading = state.myPlaying == null

        if (isMyPlayingLoading || hasMyPlaying) {
            Text(
                text = stringResource(R.string.screen_room_game_picker_my_games),
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textSecondary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            if (isMyPlayingLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                }
            } else {
                state.myPlaying!!.entries.forEach { (appId, rooms) ->
                    val gameInfo = state.allGames?.find { it.id == appId }
                    MyGameRow(
                        appId = appId,
                        gameInfo = gameInfo,
                        iconImageLoader = iconImageLoader,
                        roomCount = rooms.size,
                        onClick = { onMyGameClick(appId) },
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        }

        // ── All games section ─────────────────────────────────────────────
        Text(
            text = stringResource(R.string.screen_room_game_picker_all_games),
            style = ElementTheme.typography.fontBodySmRegular,
            color = ElementTheme.colors.textSecondary,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        when {
            state.allGames == null -> {
                // Still loading
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.allGames.isEmpty() && state.error != null -> {
                // Loaded but failed — show the error message
                Text(
                    text = state.error,
                    style = ElementTheme.typography.fontBodyMdRegular,
                    color = ElementTheme.colors.textCriticalPrimary,
                    modifier = Modifier.padding(vertical = 16.dp).fillMaxWidth(),
                )
            }
            state.allGames.isEmpty() -> {
                Text(
                    text = stringResource(R.string.screen_room_game_picker_no_games),
                    style = ElementTheme.typography.fontBodyMdRegular,
                    color = ElementTheme.colors.textSecondary,
                    modifier = Modifier.padding(vertical = 16.dp).fillMaxWidth(),
                )
            }
            else -> {
                AllGamesGrid(games = state.allGames, iconImageLoader = iconImageLoader, onClick = onGameClick)
            }
        }
    }
}

@Composable
private fun AllGamesGrid(
    games: ImmutableList<GameInfo>,
    iconImageLoader: ImageLoader,
    onClick: (GameInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    // LazyVerticalGrid inside a ScrollableColumn requires a fixed height — use a simple wrapping grid instead.
    val columns = 3
    val rows = (games.size + columns - 1) / columns
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(rows) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(columns) { col ->
                    val index = row * columns + col
                    if (index < games.size) {
                        GameCard(
                            game = games[index],
                            iconImageLoader = iconImageLoader,
                            onClick = { onClick(games[index]) },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun GameCard(
    game: GameInfo,
    iconImageLoader: ImageLoader,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .roundedClickable(RoundedCornerShape(12.dp), onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        GameIconImage(
            iconPath = game.icon,
            contentDescription = game.name,
            imageLoader = iconImageLoader,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)),
        )
        Text(
            text = game.name,
            style = ElementTheme.typography.fontBodySmRegular,
            color = ElementTheme.colors.textPrimary,
            maxLines = 2,
        )
    }
}

@Composable
private fun MyGameRow(
    appId: Int,
    gameInfo: GameInfo?,
    iconImageLoader: ImageLoader,
    roomCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .roundedClickable(RoundedCornerShape(8.dp), onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GameIconImage(
            iconPath = gameInfo?.icon,
            contentDescription = gameInfo?.name ?: "Game $appId",
            imageLoader = iconImageLoader,
            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = gameInfo?.name ?: "Game $appId",
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        if (roomCount > 1) {
            Text(
                text = pluralStringResource(R.plurals.game_picker_room_count, roomCount, roomCount),
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textSecondary,
            )
        }
    }
}

// ── Rooms sub-view ────────────────────────────────────────────────────────────

@Composable
private fun RoomsView(
    appId: Int,
    allGames: ImmutableList<GameInfo>?,
    myPlaying: ImmutableMap<Int, ImmutableList<PlayingRoom>>?,
    iconImageLoader: ImageLoader,
    onBack: () -> Unit,
    onEnter: (Int, PlayingRoom) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gameInfo = allGames?.find { it.id == appId }
    val rooms = myPlaying?.get(appId) ?: return

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        // Header: back + game icon + name
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 12.dp),
        ) {
            Text(
                text = "← ",
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .roundedClickable(RoundedCornerShape(50), onBack)
                    .padding(horizontal = 4.dp),
            )
            if (gameInfo?.icon != null) {
                GameIconImage(
                    iconPath = gameInfo.icon,
                    contentDescription = gameInfo.name,
                    imageLoader = iconImageLoader,
                    modifier = Modifier.size(20.dp).clip(RoundedCornerShape(4.dp)),
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = gameInfo?.name ?: "Game $appId",
                style = ElementTheme.typography.fontBodyMdMedium,
                color = ElementTheme.colors.textPrimary,
            )
        }

        rooms.forEach { playingRoom ->
            RoomRow(
                room = playingRoom,
                onClick = { onEnter(appId, playingRoom) },
            )
        }
    }
}

@Composable
private fun RoomRow(
    room: PlayingRoom,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .roundedClickable(RoundedCornerShape(8.dp), onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "💬", style = ElementTheme.typography.fontBodyMdRegular)
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = room.meetId,
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        if (room.isAdmin) {
            Text(
                text = stringResource(R.string.screen_room_game_picker_room_owner),
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textSecondary,
            )
        }
    }
}

// ── Shared helpers ────────────────────────────────────────────────────────────

@Composable
private fun Modifier.roundedClickable(
    shape: Shape,
    onClick: () -> Unit,
): Modifier = clip(shape).clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = ripple(bounded = true),
    onClick = onClick,
)

@Composable
private fun GameIconImage(
    iconPath: String?,
    contentDescription: String,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
) {
    if (iconPath != null) {
        AsyncImage(
            model = iconPath,
            contentDescription = contentDescription,
            imageLoader = imageLoader,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        Box(modifier = modifier)
    }
}

// ── Previews ──────────────────────────────────────────────────────────────────

@PreviewsDayNight
@Composable
internal fun GamePickerBottomSheetPreview() = ElementPreview {
    GamePickerBottomSheet(
        state = aGamePickerState(),
        onDismiss = {},
    )
}

@PreviewsDayNight
@Composable
internal fun GamePickerBottomSheetLoadingPreview() = ElementPreview {
    GamePickerBottomSheet(
        state = aGamePickerState(allGames = null, myPlaying = null),
        onDismiss = {},
    )
}

@PreviewsDayNight
@Composable
internal fun GamePickerBottomSheetCreatingPreview() = ElementPreview {
    GamePickerBottomSheet(
        state = aGamePickerState(creating = true),
        onDismiss = {},
    )
}

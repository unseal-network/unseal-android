/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.messages.impl.R
import io.element.android.features.messages.impl.messagecomposer.gamepicker.GamePickerBottomSheet
import io.element.android.features.messages.impl.roomdata.RoomAttachmentAction
import io.element.android.features.messages.impl.roomdata.RoomAttachmentActionEntry
import io.element.android.libraries.androidutils.ui.hideKeyboard
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.ModalBottomSheet
import io.element.android.libraries.designsystem.theme.components.Text

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AttachmentsBottomSheet(
    state: MessageComposerState,
    attachmentActions: List<RoomAttachmentActionEntry>,
    onSendLocationClick: () -> Unit,
    onCreatePollClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val localView = LocalView.current
    var isVisible by rememberSaveable { mutableStateOf(state.showAttachmentSourcePicker) }

    BackHandler(enabled = isVisible) {
        isVisible = false
    }

    LaunchedEffect(state.showAttachmentSourcePicker) {
        isVisible = if (state.showAttachmentSourcePicker) {
            // We need to use this instead of `LocalFocusManager.clearFocus()` to hide the keyboard when focus is on an Android View
            localView.hideKeyboard()
            true
        } else {
            false
        }
    }
    // Send 'DismissAttachmentMenu' event when the bottomsheet was just hidden
    LaunchedEffect(isVisible) {
        if (!isVisible) {
            state.eventSink(MessageComposerEvent.DismissAttachmentMenu)
        }
    }

    if (isVisible) {
        ModalBottomSheet(
            modifier = modifier,
            sheetState = rememberModalBottomSheetState(
                skipPartiallyExpanded = true
            ),
            onDismissRequest = { isVisible = false },
            scrollable = false,
        ) {
            AttachmentSourcePickerMenu(
                state = state,
                attachmentActions = attachmentActions,
                onSendLocationClick = onSendLocationClick,
                onCreatePollClick = onCreatePollClick,
            )
        }
    }

    // Game picker bottom sheet — shown after the attachment menu has been dismissed.
    val gamePickerState = state.gamePickerState
    if (gamePickerState != null) {
        GamePickerBottomSheet(
            state = gamePickerState,
            onDismiss = { state.eventSink(MessageComposerEvent.DismissGamePicker) },
        )
    }
}

@Composable
private fun AttachmentSourcePickerMenu(
    state: MessageComposerState,
    attachmentActions: List<RoomAttachmentActionEntry>,
    onSendLocationClick: () -> Unit,
    onCreatePollClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
    ) {
        attachmentActions.forEach { action ->
            AttachmentActionRow(
                state = state,
                entry = action,
                onSendLocationClick = onSendLocationClick,
                onCreatePollClick = onCreatePollClick,
            )
        }
    }
}

@Composable
private fun AttachmentActionRow(
    state: MessageComposerState,
    entry: RoomAttachmentActionEntry,
    onSendLocationClick: () -> Unit,
    onCreatePollClick: () -> Unit,
) {
    val action = entry.action
    when (action) {
        RoomAttachmentAction.PhotoFromCamera -> ListItem(
            modifier = entry.clickableIfAvailable { state.eventSink(MessageComposerEvent.PickAttachmentSource.PhotoFromCamera) },
            leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.TakePhoto())),
            headlineContent = { Text(stringResource(R.string.screen_room_attachment_source_camera_photo)) },
        )
        RoomAttachmentAction.VideoFromCamera -> ListItem(
            modifier = entry.clickableIfAvailable { state.eventSink(MessageComposerEvent.PickAttachmentSource.VideoFromCamera) },
            leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.VideoCall())),
            headlineContent = { Text(stringResource(R.string.screen_room_attachment_source_camera_video)) },
        )
        RoomAttachmentAction.Gallery -> ListItem(
            modifier = entry.clickableIfAvailable { state.eventSink(MessageComposerEvent.PickAttachmentSource.FromGallery) },
            leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Image())),
            headlineContent = { Text(stringResource(R.string.screen_room_attachment_source_gallery)) },
        )
        RoomAttachmentAction.Files -> ListItem(
            modifier = entry.clickableIfAvailable { state.eventSink(MessageComposerEvent.PickAttachmentSource.FromFiles) },
            leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Attachment())),
            headlineContent = { Text(stringResource(R.string.screen_room_attachment_source_files)) },
        )
        RoomAttachmentAction.Location -> ListItem(
            modifier = entry.clickableIfAvailable {
                state.eventSink(MessageComposerEvent.PickAttachmentSource.Location)
                onSendLocationClick()
            },
            leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.LocationPin())),
            headlineContent = { Text(stringResource(R.string.screen_room_attachment_source_location)) },
        )
        RoomAttachmentAction.Poll -> ListItem(
            modifier = entry.clickableIfAvailable {
                state.eventSink(MessageComposerEvent.PickAttachmentSource.Poll)
                onCreatePollClick()
            },
            leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Polls())),
            headlineContent = { Text(stringResource(R.string.screen_room_attachment_source_poll)) },
        )
        RoomAttachmentAction.Game -> ListItem(
            modifier = entry.clickableIfAvailable { state.eventSink(MessageComposerEvent.ShowGamePicker) },
            leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Play())),
            headlineContent = { Text(stringResource(R.string.screen_room_attachment_source_game)) },
        )
        RoomAttachmentAction.TextFormatting -> ListItem(
            modifier = entry.clickableIfAvailable { state.eventSink(MessageComposerEvent.ToggleTextFormatting(enabled = true)) },
            leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.TextFormatting())),
            headlineContent = { Text(stringResource(R.string.screen_room_attachment_text_formatting)) },
        )
        RoomAttachmentAction.Ping -> ListItem(
            modifier = Modifier,
            leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Mention())),
            headlineContent = { Text(stringResource(R.string.screen_room_attachment_source_ping)) },
        )
        RoomAttachmentAction.Sketch -> ListItem(
            modifier = Modifier,
            leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Edit())),
            headlineContent = { Text(stringResource(R.string.screen_room_attachment_source_sketch)) },
        )
    }
}

private fun RoomAttachmentActionEntry.clickableIfAvailable(onClick: () -> Unit): Modifier {
    return if (isAvailable) Modifier.clickable(onClick = onClick) else Modifier
}

@PreviewsDayNight
@Composable
internal fun AttachmentSourcePickerMenuPreview() = ElementPreview {
    AttachmentSourcePickerMenu(
        state = aMessageComposerState(
            canShareLocation = true,
        ),
        attachmentActions = RoomAttachmentAction.entries.map { action ->
            RoomAttachmentActionEntry(action = action, isAvailable = action != RoomAttachmentAction.Ping && action != RoomAttachmentAction.Sketch)
        },
        onSendLocationClick = {},
        onCreatePollClick = {},
    )
}

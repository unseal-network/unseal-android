/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.messages.impl.roomkey.RoomKeyRecoveryDisplayStage
import io.element.android.features.messages.impl.timeline.components.layout.ContentAvoidingLayoutData
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemEncryptedContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemEncryptedContentProvider
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemRoomKeyRecovery
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemRoomKeyRecoveryState
import io.element.android.libraries.designsystem.icons.CompoundDrawables
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.ButtonSize
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.LinearProgressIndicator
import io.element.android.libraries.designsystem.theme.components.Surface
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.matrix.api.encryption.roomkey.RoomKeyRecoveryRequest
import io.element.android.libraries.matrix.api.timeline.item.event.UnableToDecryptContent
import io.element.android.libraries.matrix.api.timeline.item.event.UtdCause
import io.element.android.libraries.ui.strings.CommonStrings
import kotlin.math.max
import kotlin.time.Duration

@Composable
fun TimelineItemEncryptedView(
    content: TimelineItemEncryptedContent,
    onContentLayoutChange: (ContentAvoidingLayoutData) -> Unit,
    onVerifyDeviceClick: () -> Unit = {},
    onRetryClick: (RoomKeyRecoveryRequest) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val recovery = content.recovery
    if (recovery != null) {
        TimelineItemRoomKeyRecoveryView(
            recovery = recovery,
            onVerifyDeviceClick = onVerifyDeviceClick,
            onRetryClick = onRetryClick,
            onContentLayoutChange = onContentLayoutChange,
            modifier = modifier,
        )
        return
    }

    TimelineItemEncryptedFallbackView(
        content = content,
        onContentLayoutChange = onContentLayoutChange,
        modifier = modifier,
    )
}

@Composable
private fun TimelineItemEncryptedFallbackView(
    content: TimelineItemEncryptedContent,
    onContentLayoutChange: (ContentAvoidingLayoutData) -> Unit,
    modifier: Modifier = Modifier,
) {
    val (textId, iconId) = when (content.data) {
        is UnableToDecryptContent.Data.MegolmV1AesSha2 -> {
            when (content.data.utdCause) {
                UtdCause.SentBeforeWeJoined -> {
                    CommonStrings.common_unable_to_decrypt_no_access to CompoundDrawables.ic_compound_block
                }
                UtdCause.VerificationViolation -> {
                    CommonStrings.common_unable_to_decrypt_verification_violation to CompoundDrawables.ic_compound_block
                }
                UtdCause.UnsignedDevice,
                UtdCause.UnknownDevice -> {
                    CommonStrings.common_unable_to_decrypt_insecure_device to CompoundDrawables.ic_compound_block
                }
                UtdCause.HistoricalMessageAndBackupIsDisabled -> {
                    CommonStrings.timeline_decryption_failure_historical_event_no_key_backup to CompoundDrawables.ic_compound_block
                }
                UtdCause.HistoricalMessageAndDeviceIsUnverified -> {
                    CommonStrings.timeline_decryption_failure_historical_event_unverified_device to CompoundDrawables.ic_compound_block
                }
                UtdCause.WithheldUnverifiedOrInsecureDevice -> {
                    CommonStrings.timeline_decryption_failure_withheld_unverified to CompoundDrawables.ic_compound_block
                }
                UtdCause.WithheldBySender -> {
                    CommonStrings.timeline_decryption_failure_unable_to_decrypt to CompoundDrawables.ic_compound_error
                }
                else -> {
                    CommonStrings.common_waiting_for_decryption_key to CompoundDrawables.ic_compound_time
                }
            }
        }
        else -> {
            // Should not happen, we only supports megolm in rooms
            CommonStrings.common_waiting_for_decryption_key to CompoundDrawables.ic_compound_time
        }
    }
    TimelineItemInformativeView(
        text = stringResource(id = textId),
        iconDescription = stringResource(id = CommonStrings.dialog_title_warning),
        iconResourceId = iconId,
        onContentLayoutChange = onContentLayoutChange,
        modifier = modifier
    )
}

@Composable
private fun TimelineItemRoomKeyRecoveryView(
    recovery: TimelineItemRoomKeyRecovery,
    onVerifyDeviceClick: () -> Unit,
    onRetryClick: (RoomKeyRecoveryRequest) -> Unit,
    onContentLayoutChange: (ContentAvoidingLayoutData) -> Unit,
    modifier: Modifier = Modifier,
) {
    val display = recovery.display()
    Surface(
        modifier = modifier.onSizeChanged { size ->
            onContentLayoutChange(
                ContentAvoidingLayoutData(
                    contentWidth = size.width,
                    contentHeight = size.height,
                )
            )
        },
        color = ElementTheme.colors.bgSubtleSecondary,
        border = BorderStroke(1.dp, ElementTheme.colors.borderDisabled),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    resourceId = display.iconResourceId,
                    tint = ElementTheme.colors.iconSecondary,
                    contentDescription = display.title,
                    modifier = Modifier.size(16.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = display.title,
                        color = ElementTheme.colors.textPrimary,
                        style = ElementTheme.typography.fontBodyMdMedium,
                    )
                    Text(
                        text = display.detail,
                        color = ElementTheme.colors.textSecondary,
                        style = ElementTheme.typography.fontBodySmRegular,
                    )
                }
            }
            if (recovery.planStages.isNotEmpty()) {
                LinearProgressIndicator(
                    progress = { recovery.progress() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                )
                Text(
                    text = recovery.stageSummary(),
                    color = ElementTheme.colors.textSecondary,
                    style = ElementTheme.typography.fontBodySmRegular,
                )
            }
            display.action?.let { action ->
                TextButton(
                    text = when (action) {
                        RoomKeyRecoveryAction.VerifyDevice -> stringResource(id = CommonStrings.common_verify_device)
                        RoomKeyRecoveryAction.Retry -> stringResource(id = CommonStrings.action_retry_decryption)
                    },
                    size = ButtonSize.Small,
                    onClick = {
                        when (action) {
                            RoomKeyRecoveryAction.VerifyDevice -> onVerifyDeviceClick()
                            RoomKeyRecoveryAction.Retry -> onRetryClick(recovery.request)
                        }
                    },
                )
            } ?: Spacer(modifier = Modifier.height(0.dp))
        }
    }
}

internal data class TimelineItemRoomKeyRecoveryDisplay(
    val title: String,
    val detail: String,
    val iconResourceId: Int,
    val action: RoomKeyRecoveryAction? = null,
)

internal enum class RoomKeyRecoveryAction {
    VerifyDevice,
    Retry,
}

internal fun TimelineItemRoomKeyRecovery.display(): TimelineItemRoomKeyRecoveryDisplay {
    return when (state) {
        TimelineItemRoomKeyRecoveryState.CheckingDeviceVerification -> TimelineItemRoomKeyRecoveryDisplay(
            title = "Checking device verification",
            detail = "Preparing to recover keys for ${eventCount.messageCountLabel()}",
            iconResourceId = CompoundDrawables.ic_compound_time,
        )
        TimelineItemRoomKeyRecoveryState.DeviceUnverified -> TimelineItemRoomKeyRecoveryDisplay(
            title = "Verify this device to recover keys",
            detail = "This protects encrypted history before key requests are sent.",
            iconResourceId = CompoundDrawables.ic_compound_block,
            action = RoomKeyRecoveryAction.VerifyDevice,
        )
        TimelineItemRoomKeyRecoveryState.Pending -> TimelineItemRoomKeyRecoveryDisplay(
            title = "Waiting before retrying key recovery",
            detail = "Next attempt in ${remaining.formatSeconds()} for ${eventCount.messageCountLabel()}",
            iconResourceId = CompoundDrawables.ic_compound_time,
        )
        TimelineItemRoomKeyRecoveryState.Active -> TimelineItemRoomKeyRecoveryDisplay(
            title = currentStage?.title() ?: "Recovering message keys",
            detail = remaining?.let { "Waiting up to ${it.formatSeconds()} for ${eventCount.messageCountLabel()}" }
                ?: "Recovering keys for ${eventCount.messageCountLabel()}",
            iconResourceId = CompoundDrawables.ic_compound_time,
        )
        TimelineItemRoomKeyRecoveryState.Resolved -> TimelineItemRoomKeyRecoveryDisplay(
            title = "Message keys recovered",
            detail = "Waiting for the timeline to render decrypted content.",
            iconResourceId = CompoundDrawables.ic_compound_time,
        )
        TimelineItemRoomKeyRecoveryState.Failed -> TimelineItemRoomKeyRecoveryDisplay(
            title = "Key recovery failed",
            detail = "No device returned keys for ${eventCount.messageCountLabel()}",
            iconResourceId = CompoundDrawables.ic_compound_error,
            action = RoomKeyRecoveryAction.Retry,
        )
    }
}

private fun TimelineItemRoomKeyRecovery.progress(): Float {
    val currentIndex = currentStage?.let { planStages.indexOf(it) }?.takeIf { it >= 0 } ?: 0
    return ((currentIndex + 1).toFloat() / planStages.size.toFloat()).coerceIn(0f, 1f)
}

private fun TimelineItemRoomKeyRecovery.stageSummary(): String {
    val currentIndex = currentStage?.let { planStages.indexOf(it) }?.takeIf { it >= 0 } ?: 0
    return "Step ${currentIndex + 1} of ${planStages.size}: ${planStages[currentIndex].label()}"
}

private fun RoomKeyRecoveryDisplayStage.title(): String {
    return when (this) {
        RoomKeyRecoveryDisplayStage.CheckingDeviceVerification -> "Checking device verification"
        RoomKeyRecoveryDisplayStage.DeviceUnverified -> "Device verification required"
        RoomKeyRecoveryDisplayStage.Backup -> "Requesting keys from backup"
        RoomKeyRecoveryDisplayStage.OwnDevices -> "Requesting keys from your devices"
        RoomKeyRecoveryDisplayStage.Sender -> "Requesting keys from the sender"
        RoomKeyRecoveryDisplayStage.Members -> "Requesting keys from room members"
        RoomKeyRecoveryDisplayStage.Resolved -> "Message keys recovered"
        RoomKeyRecoveryDisplayStage.Failed -> "Key recovery failed"
    }
}

private fun RoomKeyRecoveryDisplayStage.label(): String {
    return when (this) {
        RoomKeyRecoveryDisplayStage.CheckingDeviceVerification -> "verification"
        RoomKeyRecoveryDisplayStage.DeviceUnverified -> "verify device"
        RoomKeyRecoveryDisplayStage.Backup -> "backup"
        RoomKeyRecoveryDisplayStage.OwnDevices -> "your devices"
        RoomKeyRecoveryDisplayStage.Sender -> "sender"
        RoomKeyRecoveryDisplayStage.Members -> "room members"
        RoomKeyRecoveryDisplayStage.Resolved -> "resolved"
        RoomKeyRecoveryDisplayStage.Failed -> "failed"
    }
}

private fun Duration?.formatSeconds(): String {
    val seconds = max(0, this?.inWholeSeconds ?: 0)
    return "${seconds}s"
}

private fun Int.messageCountLabel(): String {
    return if (this == 1) "1 message" else "$this messages"
}

@PreviewsDayNight
@Composable
internal fun TimelineItemEncryptedViewPreview(
    @PreviewParameter(TimelineItemEncryptedContentProvider::class) content: TimelineItemEncryptedContent
) = ElementPreview {
    TimelineItemEncryptedView(
        content = content,
        onContentLayoutChange = {},
    )
}

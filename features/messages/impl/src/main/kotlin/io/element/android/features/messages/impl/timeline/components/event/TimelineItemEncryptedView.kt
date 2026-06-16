/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.ButtonSize
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
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
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { size ->
                onContentLayoutChange(
                    ContentAvoidingLayoutData(
                        contentWidth = size.width,
                        contentHeight = size.height,
                    )
                )
            },
        shape = RoundedCornerShape(18.dp),
        color = ElementTheme.colors.bgSubtleSecondary.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, ElementTheme.colors.borderDisabled.copy(alpha = 0.42f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    resourceId = display.iconResourceId,
                    tint = ElementTheme.colors.iconSecondary,
                    contentDescription = display.statusTitle,
                    modifier = Modifier.size(22.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        text = stringResource(id = CommonStrings.common_waiting_for_decryption_key),
                        color = ElementTheme.colors.textSecondary,
                        style = ElementTheme.typography.fontBodyMdMedium,
                    )
                    Text(
                        text = recovery.eventCount.messageCountLabel(),
                        color = ElementTheme.colors.textSecondary,
                        style = ElementTheme.typography.fontBodySmRegular,
                    )
                }
            }
            if (recovery.planStages.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(
                        text = display.statusTitle,
                        color = ElementTheme.colors.textPrimary,
                        style = ElementTheme.typography.fontBodySmMedium,
                    )
                    RecoveryStageBar(
                        recovery = recovery,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = recovery.stageSummary(),
                        color = ElementTheme.colors.textSecondary,
                        style = ElementTheme.typography.fontBodySmRegular,
                    )
                }
            }
            Text(
                text = display.detail,
                color = ElementTheme.colors.textSecondary,
                style = ElementTheme.typography.fontBodyMdRegular,
            )
            display.action?.let { action ->
                Button(
                    text = when (action) {
                        RoomKeyRecoveryAction.VerifyDevice -> stringResource(id = CommonStrings.common_verify_device)
                        RoomKeyRecoveryAction.Retry -> stringResource(id = CommonStrings.action_retry_decryption)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    size = ButtonSize.Medium,
                    onClick = {
                        when (action) {
                            RoomKeyRecoveryAction.VerifyDevice -> onVerifyDeviceClick()
                            RoomKeyRecoveryAction.Retry -> onRetryClick(recovery.request)
                        }
                    },
                )
            }
        }
    }
}

internal data class TimelineItemRoomKeyRecoveryDisplay(
    val statusTitle: String,
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
            statusTitle = "Checking device status",
            detail = "Preparing to recover keys for ${eventCount.messageCountLabel()}",
            iconResourceId = CompoundDrawables.ic_compound_time,
        )
        TimelineItemRoomKeyRecoveryState.DeviceUnverified -> TimelineItemRoomKeyRecoveryDisplay(
            statusTitle = "Verify device",
            detail = "This protects encrypted history before key requests are sent.",
            iconResourceId = CompoundDrawables.ic_compound_block,
            action = RoomKeyRecoveryAction.VerifyDevice,
        )
        TimelineItemRoomKeyRecoveryState.Pending -> TimelineItemRoomKeyRecoveryDisplay(
            statusTitle = "Switching recovery source",
            detail = "Next attempt in ${remaining.formatSeconds()} for ${eventCount.messageCountLabel()}",
            iconResourceId = CompoundDrawables.ic_compound_time,
        )
        TimelineItemRoomKeyRecoveryState.Active -> TimelineItemRoomKeyRecoveryDisplay(
            statusTitle = currentStage?.title() ?: "Recovering message keys",
            detail = remaining?.let { "Waiting up to ${it.formatSeconds()} for ${eventCount.messageCountLabel()}" }
                ?: "Recovering keys for ${eventCount.messageCountLabel()}",
            iconResourceId = CompoundDrawables.ic_compound_time,
        )
        TimelineItemRoomKeyRecoveryState.Resolved -> TimelineItemRoomKeyRecoveryDisplay(
            statusTitle = "Room key recovered",
            detail = "Waiting for the timeline to render decrypted content.",
            iconResourceId = CompoundDrawables.ic_compound_time,
        )
        TimelineItemRoomKeyRecoveryState.Failed -> TimelineItemRoomKeyRecoveryDisplay(
            statusTitle = "Still missing room key",
            detail = "No device returned keys for ${eventCount.messageCountLabel()}",
            iconResourceId = CompoundDrawables.ic_compound_error,
            action = RoomKeyRecoveryAction.Retry,
        )
    }
}

@Composable
private fun RecoveryStageBar(
    recovery: TimelineItemRoomKeyRecovery,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        recovery.planStages.forEachIndexed { index, _ ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(5.dp)
                    .background(recovery.stageSegmentColor(index), CircleShape)
            )
        }
    }
}

@Composable
private fun TimelineItemRoomKeyRecovery.stageSegmentColor(index: Int): Color {
    return when (state) {
        TimelineItemRoomKeyRecoveryState.Failed -> ElementTheme.colors.borderCriticalPrimary
        TimelineItemRoomKeyRecoveryState.Resolved -> ElementTheme.colors.iconSuccessPrimary
        else -> if (index <= stageIndex()) {
            ElementTheme.colors.bgActionPrimaryRest
        } else {
            ElementTheme.colors.borderDisabled
        }
    }
}

private fun TimelineItemRoomKeyRecovery.stageSummary(): String {
    val currentIndex = stageIndex().coerceAtLeast(0)
    return "Step ${currentIndex + 1} of ${planStages.size}: ${planStages[currentIndex].label()}"
}

private fun TimelineItemRoomKeyRecovery.stageIndex(): Int {
    currentStage?.let { stage ->
        val index = planStages.indexOf(stage)
        if (index >= 0) return index
    }
    return when (state) {
        TimelineItemRoomKeyRecoveryState.Resolved,
        TimelineItemRoomKeyRecoveryState.Failed -> (planStages.size - 1).coerceAtLeast(0)
        TimelineItemRoomKeyRecoveryState.CheckingDeviceVerification,
        TimelineItemRoomKeyRecoveryState.DeviceUnverified -> -1
        TimelineItemRoomKeyRecoveryState.Pending,
        TimelineItemRoomKeyRecoveryState.Active -> 0
    }
}

private fun RoomKeyRecoveryDisplayStage.title(): String {
    return when (this) {
        RoomKeyRecoveryDisplayStage.CheckingDeviceVerification -> "Checking device status"
        RoomKeyRecoveryDisplayStage.DeviceUnverified -> "Verify device"
        RoomKeyRecoveryDisplayStage.Backup -> "Checking key backup"
        RoomKeyRecoveryDisplayStage.OwnDevices -> "Recovering from your backup and devices"
        RoomKeyRecoveryDisplayStage.Sender -> "Asking sender"
        RoomKeyRecoveryDisplayStage.Members -> "Asking room members"
        RoomKeyRecoveryDisplayStage.Resolved -> "Room key recovered"
        RoomKeyRecoveryDisplayStage.Failed -> "Still missing room key"
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

/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2022-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.model.event

import io.element.android.features.messages.impl.roomkey.RoomKeyRecoveryDisplayStage
import io.element.android.features.messages.impl.roomkey.RoomKeyRecoveryStatus
import io.element.android.libraries.matrix.api.encryption.roomkey.RoomKeyRecoveryRequest
import io.element.android.libraries.matrix.api.timeline.item.event.UnableToDecryptContent
import kotlin.time.Duration

data class TimelineItemEncryptedContent(
    val data: UnableToDecryptContent.Data,
    val recovery: TimelineItemRoomKeyRecovery? = null,
) : TimelineItemEventContent {
    override val type: String = "TimelineItemEncryptedContent"
}

data class TimelineItemRoomKeyRecovery(
    val request: RoomKeyRecoveryRequest,
    val eventCount: Int,
    val state: TimelineItemRoomKeyRecoveryState,
    val planStages: List<RoomKeyRecoveryDisplayStage> = emptyList(),
    val currentStage: RoomKeyRecoveryDisplayStage? = null,
    val remaining: Duration? = null,
)

enum class TimelineItemRoomKeyRecoveryState {
    CheckingDeviceVerification,
    DeviceUnverified,
    Pending,
    Active,
    Resolved,
    Failed,
}

fun RoomKeyRecoveryStatus.toTimelineItemRoomKeyRecovery(): TimelineItemRoomKeyRecovery {
    return when (this) {
        is RoomKeyRecoveryStatus.CheckingDeviceVerification -> TimelineItemRoomKeyRecovery(
            request = request,
            eventCount = eventCount,
            state = TimelineItemRoomKeyRecoveryState.CheckingDeviceVerification,
            currentStage = RoomKeyRecoveryDisplayStage.CheckingDeviceVerification,
        )
        is RoomKeyRecoveryStatus.DeviceUnverified -> TimelineItemRoomKeyRecovery(
            request = request,
            eventCount = eventCount,
            state = TimelineItemRoomKeyRecoveryState.DeviceUnverified,
            currentStage = RoomKeyRecoveryDisplayStage.DeviceUnverified,
        )
        is RoomKeyRecoveryStatus.Pending -> TimelineItemRoomKeyRecovery(
            request = request,
            eventCount = eventCount,
            state = TimelineItemRoomKeyRecoveryState.Pending,
            remaining = remaining,
        )
        is RoomKeyRecoveryStatus.Active -> TimelineItemRoomKeyRecovery(
            request = request,
            eventCount = eventCount,
            state = TimelineItemRoomKeyRecoveryState.Active,
            planStages = planStages,
            currentStage = currentStage,
            remaining = remaining,
        )
        is RoomKeyRecoveryStatus.Resolved -> TimelineItemRoomKeyRecovery(
            request = request,
            eventCount = eventCount,
            state = TimelineItemRoomKeyRecoveryState.Resolved,
            currentStage = RoomKeyRecoveryDisplayStage.Resolved,
        )
        is RoomKeyRecoveryStatus.Failed -> TimelineItemRoomKeyRecovery(
            request = request,
            eventCount = eventCount,
            state = TimelineItemRoomKeyRecoveryState.Failed,
            planStages = planStages,
            currentStage = RoomKeyRecoveryDisplayStage.Failed,
        )
    }
}

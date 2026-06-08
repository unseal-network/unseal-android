/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.roomkey

import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.encryption.roomkey.RoomKeyRecoveryRequest
import io.element.android.libraries.matrix.api.encryption.roomkey.RoomKeyRecoveryTarget

enum class RoomKeyRecoveryDisplayStage {
    CheckingDeviceVerification,
    DeviceUnverified,
    Backup,
    OwnDevices,
    Sender,
    Members,
    Resolved,
    Failed,
}

data class RoomKeyRecoveryPlan(
    val stages: List<RoomKeyRecoveryDisplayStage>,
)

class RoomKeyRecoveryPlanner {
    fun buildPlan(
        isOwnMessage: Boolean,
        canUseKeyBackup: Boolean,
        hasMemberFallback: Boolean,
    ): RoomKeyRecoveryPlan {
        val stages = buildList {
            if (isOwnMessage) {
                add(RoomKeyRecoveryDisplayStage.OwnDevices)
            } else {
                if (canUseKeyBackup) {
                    add(RoomKeyRecoveryDisplayStage.Backup)
                }
                add(RoomKeyRecoveryDisplayStage.Sender)
            }
            if (hasMemberFallback) {
                add(RoomKeyRecoveryDisplayStage.Members)
            }
        }
        return RoomKeyRecoveryPlan(stages)
    }
}

fun RoomKeyRecoveryRequest.senderTarget(
    ownUserId: UserId,
    latestSenderDeviceIds: Set<String>? = null,
): RoomKeyRecoveryTarget {
    if (!isSameHomeserver(senderUserId, ownUserId)) {
        return RoomKeyRecoveryTarget(userId = senderUserId, deviceId = null)
    }

    if (latestSenderDeviceIds == null) {
        return RoomKeyRecoveryTarget(userId = senderUserId, deviceId = senderDeviceId)
    }

    val deviceId = senderDeviceId?.takeIf { it in latestSenderDeviceIds }
    return RoomKeyRecoveryTarget(userId = senderUserId, deviceId = deviceId)
}

private fun isSameHomeserver(firstUserId: UserId, secondUserId: UserId): Boolean {
    return homeserverName(firstUserId) == homeserverName(secondUserId)
}

private fun homeserverName(userId: UserId): String? {
    return userId.value.substringAfter(":", missingDelimiterValue = "")
        .takeIf { it.isNotBlank() }
        ?.lowercase()
}

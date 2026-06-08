/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.api.encryption.roomkey

import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId

data class RoomKeyRecoveryRequest(
    val roomId: RoomId,
    val senderUserId: UserId,
    val senderDeviceId: String?,
    val senderKey: String,
    val sessionId: String,
    val ciphertext: String?,
) {
    val identityKey: String = listOf(roomId.value, sessionId, senderKey).joinToString(separator = "|")
}

data class RoomKeyRecoveryTarget(
    val userId: UserId,
    val deviceId: String?,
)

enum class RoomKeyRecoveryScope {
    OwnDevices,
    Sender,
    RoomMember,
}

enum class RoomKeyRecoveryStage {
    BackupRequested,
    BackupMissed,
    SenderRequested,
    SenderTimedOut,
    MembersRequested,
    MembersUnavailable,
    Resolved,
    Failed,
}

data class RoomKeyRecoveryProgress(
    val roomId: RoomId,
    val sessionId: String,
    val senderKey: String,
    val stage: RoomKeyRecoveryStage,
    val message: String?,
    val targetCount: UInt,
    val manualRetryAvailable: Boolean,
)

data class RoomKeyForwardingAuthorization(
    val roomId: RoomId,
    val sessionId: String,
    val senderKey: String,
    val requesterUserId: UserId,
    val requesterDeviceId: String,
    val requestId: String,
    val requestedMessageIndex: UInt,
    val responderFirstKnownIndex: UInt,
)

data class RoomKeyForwardingDecision(
    val allow: Boolean,
    val exportIndex: UInt? = null,
    val reason: String? = null,
)

fun interface RoomKeyForwardingPolicy {
    fun allowForwarding(authorization: RoomKeyForwardingAuthorization): RoomKeyForwardingDecision
}

/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.fixtures.fakes

import io.element.android.tests.testutils.simulateLongTask
import org.matrix.rustcomponents.sdk.BackupState
import org.matrix.rustcomponents.sdk.BackupStateListener
import org.matrix.rustcomponents.sdk.Encryption
import org.matrix.rustcomponents.sdk.NoHandle
import org.matrix.rustcomponents.sdk.RecoveryState
import org.matrix.rustcomponents.sdk.RecoveryStateListener
import org.matrix.rustcomponents.sdk.RoomKeyForwardingPolicy
import org.matrix.rustcomponents.sdk.RoomKeyRecoveryProgress
import org.matrix.rustcomponents.sdk.RoomKeyRecoveryScope
import org.matrix.rustcomponents.sdk.RoomKeyRecoveryStage
import org.matrix.rustcomponents.sdk.RoomKeyRecoveryTarget
import org.matrix.rustcomponents.sdk.TaskHandle
import org.matrix.rustcomponents.sdk.VerificationStateListener

class FakeFfiEncryption(
    private val requestRoomKeyRecoveryResult: RoomKeyRecoveryProgress = RoomKeyRecoveryProgress(
        roomId = "!room:example.org",
        sessionId = "session",
        senderKey = "senderKey",
        stage = RoomKeyRecoveryStage.SENDER_REQUESTED,
        message = "queued",
        targetCount = 1u,
        manualRetryAvailable = true,
    ),
) : Encryption(NoHandle) {
    var roomKeyRequestsEnabled: Boolean? = null
        private set
    var roomKeyForwardingEnabled: Boolean? = null
        private set
    var roomKeyForwardingPolicy: RoomKeyForwardingPolicy? = null
        private set
    var requestRoomKeyRecoveryCall: RequestRoomKeyRecoveryCall? = null
        private set

    override fun verificationStateListener(listener: VerificationStateListener): TaskHandle {
        return FakeFfiTaskHandle()
    }

    override fun recoveryStateListener(listener: RecoveryStateListener): TaskHandle {
        return FakeFfiTaskHandle()
    }

    override suspend fun waitForE2eeInitializationTasks() = simulateLongTask {}

    override suspend fun isLastDevice(): Boolean {
        return false
    }

    override suspend fun hasDevicesToVerifyAgainst(): Boolean {
        return true
    }

    override fun backupState(): BackupState {
        return BackupState.ENABLED
    }

    override fun recoveryState(): RecoveryState {
        return RecoveryState.ENABLED
    }

    override fun backupStateListener(listener: BackupStateListener): TaskHandle {
        return FakeFfiTaskHandle()
    }

    override suspend fun setRoomKeyRequestsEnabled(enable: Boolean) {
        roomKeyRequestsEnabled = enable
    }

    override suspend fun setRoomKeyForwardingEnabled(enable: Boolean) {
        roomKeyForwardingEnabled = enable
    }

    override suspend fun setRoomKeyForwardingPolicy(policy: RoomKeyForwardingPolicy?) {
        roomKeyForwardingPolicy = policy
    }

    override suspend fun requestRoomKeyRecovery(
        roomId: String,
        sessionId: String,
        senderKey: String,
        targets: List<RoomKeyRecoveryTarget>,
        scope: RoomKeyRecoveryScope,
        ciphertext: String?,
    ): RoomKeyRecoveryProgress {
        requestRoomKeyRecoveryCall = RequestRoomKeyRecoveryCall(
            roomId = roomId,
            sessionId = sessionId,
            senderKey = senderKey,
            targets = targets,
            scope = scope,
            ciphertext = ciphertext,
        )
        return requestRoomKeyRecoveryResult
    }

    data class RequestRoomKeyRecoveryCall(
        val roomId: String,
        val sessionId: String,
        val senderKey: String,
        val targets: List<RoomKeyRecoveryTarget>,
        val scope: RoomKeyRecoveryScope,
        val ciphertext: String?,
    )
}

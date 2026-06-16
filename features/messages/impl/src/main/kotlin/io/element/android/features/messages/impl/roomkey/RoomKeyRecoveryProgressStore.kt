/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.roomkey

import io.element.android.libraries.matrix.api.encryption.roomkey.RoomKeyRecoveryRequest
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

data class RoomKeyRecoveryProgressRecord(
    val request: RoomKeyRecoveryRequest,
    val currentStage: RoomKeyRecoveryDisplayStage,
    val planStages: List<RoomKeyRecoveryDisplayStage>,
    val startedAt: Instant,
    val deadline: Instant?,
    val failedAt: Instant?,
)

class RoomKeyRecoveryProgressStore(
    private val clock: Clock = Clock.System,
) {
    private val recordsByIdentityKey = mutableMapOf<String, RoomKeyRecoveryProgressRecord>()

    fun record(request: RoomKeyRecoveryRequest): RoomKeyRecoveryProgressRecord {
        val record = RoomKeyRecoveryProgressRecord(
            request = request,
            currentStage = RoomKeyRecoveryDisplayStage.CheckingDeviceVerification,
            planStages = emptyList(),
            startedAt = clock.now(),
            deadline = null,
            failedAt = null,
        )
        recordsByIdentityKey[request.identityKey] = record
        return record
    }

    fun startStage(
        stage: RoomKeyRecoveryDisplayStage,
        request: RoomKeyRecoveryRequest,
        planStages: List<RoomKeyRecoveryDisplayStage>,
        duration: Duration,
    ): RoomKeyRecoveryProgressRecord {
        val record = RoomKeyRecoveryProgressRecord(
            request = request,
            currentStage = stage,
            planStages = planStages,
            startedAt = clock.now(),
            deadline = clock.now() + duration,
            failedAt = null,
        )
        recordsByIdentityKey[request.identityKey] = record
        return record
    }

    fun markFailed(
        request: RoomKeyRecoveryRequest,
        planStages: List<RoomKeyRecoveryDisplayStage>,
    ): RoomKeyRecoveryProgressRecord {
        val record = RoomKeyRecoveryProgressRecord(
            request = request,
            currentStage = RoomKeyRecoveryDisplayStage.Failed,
            planStages = planStages,
            startedAt = clock.now(),
            deadline = null,
            failedAt = clock.now(),
        )
        recordsByIdentityKey[request.identityKey] = record
        return record
    }

    fun get(request: RoomKeyRecoveryRequest): RoomKeyRecoveryProgressRecord? {
        return recordsByIdentityKey[request.identityKey]
    }

    fun remainingInterval(request: RoomKeyRecoveryRequest): Duration? {
        val deadline = get(request)?.deadline ?: return null
        val remaining = deadline - clock.now()
        return remaining.takeIf { it.isPositive() }
    }

    fun remove(request: RoomKeyRecoveryRequest) {
        recordsByIdentityKey.remove(request.identityKey)
    }

    fun retainOnly(requests: Collection<RoomKeyRecoveryRequest>) {
        val retainedIdentityKeys = requests.mapTo(mutableSetOf()) { it.identityKey }
        recordsByIdentityKey.keys.retainAll(retainedIdentityKeys)
    }
}

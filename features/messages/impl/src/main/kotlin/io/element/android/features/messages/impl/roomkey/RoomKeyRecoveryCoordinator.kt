/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.roomkey

import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.encryption.roomkey.RoomKeyRecoveryProgress
import io.element.android.libraries.matrix.api.encryption.roomkey.RoomKeyRecoveryRequest
import io.element.android.libraries.matrix.api.encryption.roomkey.RoomKeyRecoveryScope
import io.element.android.libraries.matrix.api.encryption.roomkey.RoomKeyRecoveryTarget
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

enum class RoomKeyRecoveryVerificationState {
    Unknown,
    NotVerified,
    Verified,
}

data class RoomKeyRecoveryCoordinatorInput(
    val requests: List<RoomKeyRecoveryRequest>,
    val ownUserId: UserId,
    val verificationState: RoomKeyRecoveryVerificationState,
    val canUseKeyBackup: Boolean,
    val roomMemberTargets: List<RoomKeyRecoveryTarget> = emptyList(),
    val latestSenderDeviceIds: Map<UserId, Set<String>> = emptyMap(),
)

data class RoomKeyRecoveryCoordinatorResult(
    val statuses: Map<String, RoomKeyRecoveryStatus>,
)

sealed interface RoomKeyRecoveryStatus {
    val request: RoomKeyRecoveryRequest
    val eventCount: Int

    data class CheckingDeviceVerification(
        override val request: RoomKeyRecoveryRequest,
        override val eventCount: Int,
    ) : RoomKeyRecoveryStatus

    data class DeviceUnverified(
        override val request: RoomKeyRecoveryRequest,
        override val eventCount: Int,
    ) : RoomKeyRecoveryStatus

    data class Pending(
        override val request: RoomKeyRecoveryRequest,
        override val eventCount: Int,
        val remaining: Duration,
    ) : RoomKeyRecoveryStatus

    data class Active(
        override val request: RoomKeyRecoveryRequest,
        override val eventCount: Int,
        val currentStage: RoomKeyRecoveryDisplayStage,
        val planStages: List<RoomKeyRecoveryDisplayStage>,
        val remaining: Duration?,
    ) : RoomKeyRecoveryStatus

    data class Resolved(
        override val request: RoomKeyRecoveryRequest,
        override val eventCount: Int,
    ) : RoomKeyRecoveryStatus

    data class Failed(
        override val request: RoomKeyRecoveryRequest,
        override val eventCount: Int,
        val planStages: List<RoomKeyRecoveryDisplayStage>,
    ) : RoomKeyRecoveryStatus
}

class RoomKeyRecoveryCoordinator(
    private val pendingStore: RoomKeyRecoveryPendingStore,
    private val progressStore: RoomKeyRecoveryProgressStore,
    private val planner: RoomKeyRecoveryPlanner = RoomKeyRecoveryPlanner(),
    private val stageWaitDuration: Duration = 60.seconds,
    private val requestRoomKeyRecovery: suspend (
        request: RoomKeyRecoveryRequest,
        targets: List<RoomKeyRecoveryTarget>,
        scope: RoomKeyRecoveryScope,
    ) -> Result<RoomKeyRecoveryProgress>,
    private val waitForDecryption: suspend (RoomKeyRecoveryRequest, Duration) -> Boolean,
    private val onStatusChanged: suspend (RoomKeyRecoveryStatus) -> Unit = {},
) {
    suspend fun recover(input: RoomKeyRecoveryCoordinatorInput): RoomKeyRecoveryCoordinatorResult {
        return recover(input, forceIdentityKeys = emptySet())
    }

    suspend fun manualRetry(
        input: RoomKeyRecoveryCoordinatorInput,
        identityKey: String,
    ): RoomKeyRecoveryCoordinatorResult {
        input.requests.firstOrNull { it.identityKey == identityKey }?.let { request ->
            pendingStore.removePending(request)
            progressStore.remove(request)
        }
        return recover(input, forceIdentityKeys = setOf(identityKey))
    }

    private suspend fun recover(
        input: RoomKeyRecoveryCoordinatorInput,
        forceIdentityKeys: Set<String>,
    ): RoomKeyRecoveryCoordinatorResult {
        val coalescedRequests = input.requests.groupBy { it.identityKey }
        pendingStore.retainOnly(input.requests)
        progressStore.retainOnly(input.requests)

        val statuses = coalescedRequests.mapValues { (identityKey, requests) ->
            val request = requests.first()
            val eventCount = requests.size
            when (input.verificationState) {
                RoomKeyRecoveryVerificationState.Unknown -> {
                    progressStore.record(request)
                    RoomKeyRecoveryStatus.CheckingDeviceVerification(request, eventCount)
                }
                RoomKeyRecoveryVerificationState.NotVerified -> {
                    progressStore.record(request)
                    RoomKeyRecoveryStatus.DeviceUnverified(request, eventCount)
                }
                RoomKeyRecoveryVerificationState.Verified -> {
                    recoverVerified(
                        request = request,
                        eventCount = eventCount,
                        input = input,
                        force = identityKey in forceIdentityKeys,
                    )
                }
            }
        }

        return RoomKeyRecoveryCoordinatorResult(statuses)
    }

    private suspend fun recoverVerified(
        request: RoomKeyRecoveryRequest,
        eventCount: Int,
        input: RoomKeyRecoveryCoordinatorInput,
        force: Boolean,
    ): RoomKeyRecoveryStatus {
        val existingRecord = progressStore.get(request)
        if (existingRecord?.currentStage == RoomKeyRecoveryDisplayStage.Failed && !force) {
            return RoomKeyRecoveryStatus.Failed(request, eventCount, existingRecord.planStages)
        }
        if (existingRecord != null && progressStore.remainingInterval(request) != null && !force) {
            return RoomKeyRecoveryStatus.Active(
                request = request,
                eventCount = eventCount,
                currentStage = existingRecord.currentStage,
                planStages = existingRecord.planStages,
                remaining = progressStore.remainingInterval(request),
            )
        }

        if (!pendingStore.markPendingIfNeeded(request, force = force)) {
            return RoomKeyRecoveryStatus.Pending(
                request = request,
                eventCount = eventCount,
                remaining = pendingStore.remainingInterval(request) ?: Duration.ZERO,
            )
        }

        val plan = planner.buildPlan(
            isOwnMessage = request.senderUserId == input.ownUserId,
            canUseKeyBackup = input.canUseKeyBackup,
            hasMemberFallback = input.roomMemberTargets.isNotEmpty(),
        )

        for (stage in plan.stages) {
            progressStore.startStage(stage, request, plan.stages, stageWaitDuration)
            onStatusChanged(
                RoomKeyRecoveryStatus.Active(
                    request = request,
                    eventCount = eventCount,
                    currentStage = stage,
                    planStages = plan.stages,
                    remaining = progressStore.remainingInterval(request),
                )
            )
            val requestResult = requestStage(stage, request, input)
            if (requestResult.isFailure) {
                progressStore.markFailed(request, plan.stages)
                return RoomKeyRecoveryStatus.Failed(request, eventCount, plan.stages)
            }
            if (waitForDecryption(request, stageWaitDuration)) {
                progressStore.remove(request)
                pendingStore.removePending(request)
                return RoomKeyRecoveryStatus.Resolved(request, eventCount)
            }
        }

        progressStore.markFailed(request, plan.stages)
        return RoomKeyRecoveryStatus.Failed(request, eventCount, plan.stages)
    }

    private suspend fun requestStage(
        stage: RoomKeyRecoveryDisplayStage,
        request: RoomKeyRecoveryRequest,
        input: RoomKeyRecoveryCoordinatorInput,
    ): Result<Unit> {
        return when (stage) {
            RoomKeyRecoveryDisplayStage.Backup -> Result.success(Unit)
            RoomKeyRecoveryDisplayStage.OwnDevices -> requestRoomKeyRecovery(
                request,
                listOf(RoomKeyRecoveryTarget(input.ownUserId, deviceId = null)),
                RoomKeyRecoveryScope.OwnDevices,
            ).map { }
            RoomKeyRecoveryDisplayStage.Sender -> requestRoomKeyRecovery(
                request,
                listOf(request.senderTarget(input.ownUserId, input.latestSenderDeviceIds[request.senderUserId])),
                RoomKeyRecoveryScope.Sender,
            ).map { }
            RoomKeyRecoveryDisplayStage.Members -> requestRoomKeyRecovery(
                request,
                input.roomMemberTargets,
                RoomKeyRecoveryScope.RoomMember,
            ).map { }
            RoomKeyRecoveryDisplayStage.CheckingDeviceVerification,
            RoomKeyRecoveryDisplayStage.DeviceUnverified,
            RoomKeyRecoveryDisplayStage.Resolved,
            RoomKeyRecoveryDisplayStage.Failed -> Result.success(Unit)
        }
    }
}

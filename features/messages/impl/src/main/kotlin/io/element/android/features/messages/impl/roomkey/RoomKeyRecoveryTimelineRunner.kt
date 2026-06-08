/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.roomkey

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.di.RoomScope
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.encryption.BackupState
import io.element.android.libraries.matrix.api.encryption.EncryptionService
import io.element.android.libraries.matrix.api.encryption.roomkey.MemberAwareRoomKeyForwardingPolicy
import io.element.android.libraries.matrix.api.encryption.roomkey.RoomKeyRecoveryRequest
import io.element.android.libraries.matrix.api.encryption.roomkey.RoomKeyRecoveryTarget
import io.element.android.libraries.matrix.api.room.RoomMember
import io.element.android.libraries.matrix.api.timeline.MatrixTimelineItem
import io.element.android.libraries.matrix.api.timeline.item.event.UnableToDecryptContent
import io.element.android.libraries.matrix.api.verification.SessionVerificationService
import io.element.android.libraries.matrix.api.verification.SessionVerifiedStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@SingleIn(RoomScope::class)
@Inject
class RoomKeyRecoveryTimelineRunner(
    private val encryptionService: EncryptionService,
    private val sessionVerificationService: SessionVerificationService,
    private val sessionId: SessionId,
    private val forwardingPolicy: MemberAwareRoomKeyForwardingPolicy,
    private val roomAgentResolver: RoomAgentResolver,
    @SessionCoroutineScope private val sessionCoroutineScope: CoroutineScope,
) {
    private val parser = RoomKeyRecoveryRequestParser()
    private val pendingStore = RoomKeyRecoveryPendingStore()
    private val progressStore = RoomKeyRecoveryProgressStore()
    private val coordinator = RoomKeyRecoveryCoordinator(
        pendingStore = pendingStore,
        progressStore = progressStore,
        requestRoomKeyRecovery = encryptionService::requestRoomKeyRecovery,
        waitForDecryption = { _, duration ->
            delay(duration)
            false
        },
        onStatusChanged = ::recordStatus,
    )
    private val _statuses = MutableStateFlow<Map<String, RoomKeyRecoveryStatus>>(emptyMap())
    private var lastInput: RoomKeyRecoveryCoordinatorInput? = null
    private var lastInputKey: RoomKeyRecoveryInputKey? = null
    private var recoveryJob: Job? = null

    val statuses: StateFlow<Map<String, RoomKeyRecoveryStatus>> = _statuses

    fun recoverVisibleItems(
        timelineItems: List<MatrixTimelineItem>,
        roomMembers: List<RoomMember>,
        sessionVerifiedStatus: SessionVerifiedStatus,
        backupState: BackupState,
    ) {
        val requests = timelineItems.mapNotNull { it.roomKeyRecoveryRequest() }
        val roomIds = requests.mapTo(mutableSetOf()) { it.roomId }
        val activeMemberIds = roomMembers.mapTo(mutableSetOf()) { it.userId }
        roomIds.forEach { roomId ->
            forwardingPolicy.updateRoomMembers(roomId, activeMemberIds)
        }

        val inputKey = RoomKeyRecoveryInputKey(
            identityKeys = requests.map { it.identityKey },
            activeMemberIds = activeMemberIds.map { it.value }.sorted(),
            sessionVerifiedStatus = sessionVerifiedStatus,
            backupState = backupState,
        )
        if (inputKey == lastInputKey) return
        lastInputKey = inputKey

        if (requests.isEmpty()) {
            recoveryJob?.cancel()
            _statuses.value = emptyMap()
            return
        }

        recoveryJob?.cancel()
        recoveryJob = sessionCoroutineScope.launch {
            val roomAgentUserIds = roomIds.flatMapTo(mutableSetOf()) { roomId ->
                roomAgentResolver.roomAgentUserIds(roomId, activeMemberIds)
            }
            val senderUserIds = requests.mapTo(mutableSetOf()) { it.senderUserId }
            val input = RoomKeyRecoveryCoordinatorInput(
                requests = requests,
                ownUserId = sessionId,
                verificationState = sessionVerifiedStatus.toRecoveryVerificationState(),
                canUseKeyBackup = backupState == BackupState.ENABLED,
                roomMemberTargets = roomMembers
                    .filter { it.userId != sessionId }
                    .filter { roomMember ->
                        roomMember.userId !in roomAgentUserIds || roomMember.userId in senderUserIds
                    }
                    .map { RoomKeyRecoveryTarget(userId = it.userId, deviceId = null) },
                latestSenderDeviceIds = requests
                    .groupBy { it.senderUserId }
                    .mapValues { (_, senderRequests) -> senderRequests.mapNotNullTo(mutableSetOf()) { it.senderDeviceId } },
            )
            lastInput = input
            _statuses.value = coordinator.recover(input).statuses
        }
    }

    fun retry(request: RoomKeyRecoveryRequest) {
        val input = lastInput ?: return
        recoveryJob?.cancel()
        recoveryJob = sessionCoroutineScope.launch {
            _statuses.value = coordinator.manualRetry(input, request.identityKey).statuses
        }
    }

    fun verifyCurrentSession() {
        sessionCoroutineScope.launch {
            sessionVerificationService.requestDeviceVerification()
        }
    }

    private fun MatrixTimelineItem.roomKeyRecoveryRequest(): RoomKeyRecoveryRequest? {
        val event = (this as? MatrixTimelineItem.Event)?.event ?: return null
        if (event.content !is UnableToDecryptContent) return null
        return parser.parse(event.timelineItemDebugInfoProvider().originalJson)
    }

    private fun SessionVerifiedStatus.toRecoveryVerificationState(): RoomKeyRecoveryVerificationState {
        return when (this) {
            SessionVerifiedStatus.Unknown -> RoomKeyRecoveryVerificationState.Unknown
            SessionVerifiedStatus.NotVerified -> RoomKeyRecoveryVerificationState.NotVerified
            SessionVerifiedStatus.Verified -> RoomKeyRecoveryVerificationState.Verified
        }
    }

    private suspend fun recordStatus(status: RoomKeyRecoveryStatus) {
        _statuses.value = _statuses.value + (status.request.identityKey to status)
    }

    private data class RoomKeyRecoveryInputKey(
        val identityKeys: List<String>,
        val activeMemberIds: List<String>,
        val sessionVerifiedStatus: SessionVerifiedStatus,
        val backupState: BackupState,
    )
}

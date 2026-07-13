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
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.encryption.BackupState
import io.element.android.libraries.matrix.api.encryption.EncryptionService
import io.element.android.libraries.matrix.api.encryption.roomkey.AgentRoomKeyRecoveryRequest
import io.element.android.libraries.matrix.api.encryption.roomkey.MemberAwareRoomKeyForwardingPolicy
import io.element.android.libraries.matrix.api.encryption.roomkey.RoomKeyRecoveryRequest
import io.element.android.libraries.matrix.api.encryption.roomkey.RoomKeyRecoveryTarget
import io.element.android.libraries.matrix.api.room.RoomMember
import io.element.android.libraries.matrix.api.room.RoomMembershipState
import io.element.android.libraries.matrix.api.timeline.MatrixTimelineItem
import io.element.android.libraries.matrix.api.timeline.item.event.UnableToDecryptContent
import io.element.android.libraries.matrix.api.verification.SessionVerificationService
import io.element.android.libraries.matrix.api.verification.SessionVerifiedStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@SingleIn(RoomScope::class)
@Inject
class RoomKeyRecoveryTimelineRunner(
    private val matrixClient: MatrixClient,
    private val encryptionService: EncryptionService,
    private val sessionVerificationService: SessionVerificationService,
    private val sessionId: SessionId,
    private val forwardingPolicy: MemberAwareRoomKeyForwardingPolicy,
    private val roomAgentResolver: RoomAgentResolver,
    private val decryptionRetrier: RoomKeyDecryptionRetrier,
    private val stores: RoomKeyRecoveryStores,
    private val senderDeviceResolver: RoomKeyRecoverySenderDeviceResolver,
    @SessionCoroutineScope private val sessionCoroutineScope: CoroutineScope,
) {
    private val parser = RoomKeyRecoveryRequestParser()
    private val agentParser = AgentRoomKeyRecoveryRequestParser()
    private val coordinator = RoomKeyRecoveryCoordinator(
        pendingStore = stores.pendingStore,
        progressStore = stores.progressStore,
        requestRoomKeyRecovery = encryptionService::requestRoomKeyRecovery,
        waitForDecryption = decryptionRetrier::waitForRecovery,
        onStatusChanged = ::recordStatus,
    )
    private val _statuses = MutableStateFlow<Map<String, RoomKeyRecoveryStatus>>(emptyMap())
    private var lastInput: RoomKeyRecoveryCoordinatorInput? = null
    private var lastAgentRequests: List<AgentRoomKeyRecoveryRequest> = emptyList()
    private var lastInputKey: RoomKeyRecoveryInputKey? = null
    private var recoveryJob: Job? = null

    val statuses: StateFlow<Map<String, RoomKeyRecoveryStatus>> = _statuses

    fun recoverVisibleItems(
        roomId: RoomId,
        timelineItems: List<MatrixTimelineItem>,
        roomMembers: List<RoomMember>,
        sessionVerifiedStatus: SessionVerifiedStatus,
        backupState: BackupState,
    ) {
        val requests = timelineItems.mapNotNull { it.roomKeyRecoveryRequest(roomId) }
        val agentRequests = timelineItems.mapNotNull { it.agentRoomKeyRecoveryRequest(roomId) }
        stores.agentPendingStore.retainOnly(agentRequests)
        lastAgentRequests = agentRequests
        val roomIds = requests.mapTo(mutableSetOf()) { it.roomId }
        val activeRoomMembers = roomMembers.filter { it.isActiveForRoomKeyRecovery() }
        val activeMemberIds = activeRoomMembers.mapTo(mutableSetOf()) { it.userId }
        roomIds.forEach { roomId ->
            forwardingPolicy.updateRoomMembers(roomId, activeMemberIds)
        }

        val inputKey = RoomKeyRecoveryInputKey(
            identityKeys = requests.map { it.identityKey },
            agentIdentityKeys = agentRequests.map { it.identityKey },
            activeMemberIds = activeMemberIds.map { it.value }.sorted(),
            sessionVerifiedStatus = sessionVerifiedStatus,
            backupState = backupState,
        )
        if (inputKey == lastInputKey) return
        lastInputKey = inputKey

        if (requests.isEmpty() && agentRequests.isEmpty()) {
            recoveryJob?.cancel()
            _statuses.value = emptyMap()
            return
        }

        recoveryJob?.cancel()
        recoveryJob = sessionCoroutineScope.launch {
            if (sessionVerifiedStatus == SessionVerifiedStatus.Verified) {
                agentRequests
                    .filter { stores.agentPendingStore.markPendingIfNeeded(it) }
                    .forEach { matrixClient.requestAgentRoomKeyRecovery(it) }
            }
            if (requests.isEmpty()) {
                _statuses.value = emptyMap()
                return@launch
            }
            val roomAgentUserIds = roomIds.flatMapTo(mutableSetOf()) { roomId ->
                roomAgentResolver.roomAgentUserIds(roomId, activeMemberIds)
            }
            val senderUserIds = requests.mapTo(mutableSetOf()) { it.senderUserId }
            val roomMemberSignature = activeMemberIds.map { it.value }.sorted().joinToString("|")
            val latestSenderDeviceIds = senderUserIds.associateWith { senderUserId ->
                senderDeviceResolver.latestSenderDeviceIds(senderUserId, roomMemberSignature)
            }.filterValues { it != null }
                .mapValues { (_, deviceIds) -> deviceIds.orEmpty() }
            val input = RoomKeyRecoveryCoordinatorInput(
                requests = requests,
                ownUserId = sessionId,
                verificationState = sessionVerifiedStatus.toRecoveryVerificationState(),
                canUseKeyBackup = backupState == BackupState.ENABLED,
                roomMemberTargets = activeRoomMembers
                    .filter { it.userId != sessionId }
                    .filter { it.userId !in senderUserIds }
                    .filter { roomMember ->
                        roomMember.userId !in roomAgentUserIds || roomMember.userId in senderUserIds
                    }
                    .map { RoomKeyRecoveryTarget(userId = it.userId, deviceId = null) },
                latestSenderDeviceIds = latestSenderDeviceIds,
            )
            lastInput = input
            _statuses.value = coordinator.recover(input).statuses
        }
    }

    fun retry(request: RoomKeyRecoveryRequest) {
        val agentRequest = lastAgentRequests.firstOrNull { it.matches(request) }
        agentRequest?.let(stores.agentPendingStore::removePending)
        val input = lastInput
        if (agentRequest == null && input == null) return
        recoveryJob?.cancel()
        recoveryJob = sessionCoroutineScope.launch {
            agentRequest
                ?.takeIf { stores.agentPendingStore.markPendingIfNeeded(it) }
                ?.let { matrixClient.requestAgentRoomKeyRecovery(it) }
            input?.let {
                _statuses.value = coordinator.manualRetry(it, request.identityKey).statuses
            }
        }
    }

    fun verifyCurrentSession() {
        sessionCoroutineScope.launch {
            sessionVerificationService.requestDeviceVerification()
        }
    }

    private fun MatrixTimelineItem.roomKeyRecoveryRequest(fallbackRoomId: RoomId): RoomKeyRecoveryRequest? {
        val event = (this as? MatrixTimelineItem.Event)?.event ?: return null
        val content = event.content as? UnableToDecryptContent ?: return null
        val data = content.data as? UnableToDecryptContent.Data.MegolmV1AesSha2
        return parser.parse(
            originalJson = event.timelineItemDebugInfoProvider().originalJson,
            fallbackRoomId = fallbackRoomId,
            fallbackSenderId = event.sender,
            fallbackSessionId = data?.sessionId,
        )
    }

    private fun MatrixTimelineItem.agentRoomKeyRecoveryRequest(fallbackRoomId: RoomId): AgentRoomKeyRecoveryRequest? {
        val event = (this as? MatrixTimelineItem.Event)?.event ?: return null
        val content = event.content as? UnableToDecryptContent ?: return null
        val data = content.data as? UnableToDecryptContent.Data.MegolmV1AesSha2 ?: return null
        return agentParser.parse(
            originalJson = event.timelineItemDebugInfoProvider().originalJson,
            fallbackRoomId = fallbackRoomId,
            fallbackSenderId = event.sender,
            fallbackSessionId = data.sessionId,
        )
    }

    private fun AgentRoomKeyRecoveryRequest.matches(request: RoomKeyRecoveryRequest): Boolean {
        return roomId == request.roomId &&
            senderUserId == request.senderUserId &&
            senderDeviceId == request.senderDeviceId &&
            senderKey == request.senderKey &&
            sessionId == request.sessionId
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

    private fun RoomMember.isActiveForRoomKeyRecovery(): Boolean {
        return when (membership) {
            RoomMembershipState.JOIN,
            RoomMembershipState.INVITE,
            RoomMembershipState.KNOCK -> true
            RoomMembershipState.BAN,
            RoomMembershipState.LEAVE -> false
        }
    }

    private data class RoomKeyRecoveryInputKey(
        val identityKeys: List<String>,
        val agentIdentityKeys: List<String>,
        val activeMemberIds: List<String>,
        val sessionVerifiedStatus: SessionVerifiedStatus,
        val backupState: BackupState,
    )
}

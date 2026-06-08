/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.encryption

import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.extensions.flatMap
import io.element.android.libraries.core.extensions.mapFailure
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.encryption.BackupState
import io.element.android.libraries.matrix.api.encryption.BackupUploadState
import io.element.android.libraries.matrix.api.encryption.EnableRecoveryProgress
import io.element.android.libraries.matrix.api.encryption.EncryptionService
import io.element.android.libraries.matrix.api.encryption.IdentityResetHandle
import io.element.android.libraries.matrix.api.encryption.RecoveryState
import io.element.android.libraries.matrix.api.encryption.identity.IdentityState
import io.element.android.libraries.matrix.api.encryption.roomkey.RoomKeyForwardingAuthorization
import io.element.android.libraries.matrix.api.encryption.roomkey.RoomKeyForwardingDecision
import io.element.android.libraries.matrix.api.encryption.roomkey.RoomKeyForwardingPolicy
import io.element.android.libraries.matrix.api.encryption.roomkey.RoomKeyRecoveryProgress
import io.element.android.libraries.matrix.api.encryption.roomkey.RoomKeyRecoveryRequest
import io.element.android.libraries.matrix.api.encryption.roomkey.RoomKeyRecoveryScope
import io.element.android.libraries.matrix.api.encryption.roomkey.RoomKeyRecoveryStage
import io.element.android.libraries.matrix.api.encryption.roomkey.RoomKeyRecoveryTarget
import io.element.android.libraries.matrix.api.sync.SyncState
import io.element.android.libraries.matrix.impl.exception.mapClientException
import io.element.android.libraries.matrix.impl.sync.RustSyncService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.matrix.rustcomponents.sdk.BackupSteadyStateListener
import org.matrix.rustcomponents.sdk.Client
import org.matrix.rustcomponents.sdk.EnableRecoveryProgressListener
import org.matrix.rustcomponents.sdk.Encryption
import org.matrix.rustcomponents.sdk.UserIdentity
import timber.log.Timber
import org.matrix.rustcomponents.sdk.BackupUploadState as RustBackupUploadState
import org.matrix.rustcomponents.sdk.EnableRecoveryProgress as RustEnableRecoveryProgress
import org.matrix.rustcomponents.sdk.RecoveryException as RustRecoveryException
import org.matrix.rustcomponents.sdk.RoomKeyForwardingAuthorization as RustRoomKeyForwardingAuthorization
import org.matrix.rustcomponents.sdk.RoomKeyForwardingDecision as RustRoomKeyForwardingDecision
import org.matrix.rustcomponents.sdk.RoomKeyForwardingPolicy as RustRoomKeyForwardingPolicy
import org.matrix.rustcomponents.sdk.RoomKeyRecoveryProgress as RustRoomKeyRecoveryProgress
import org.matrix.rustcomponents.sdk.RoomKeyRecoveryScope as RustRoomKeyRecoveryScope
import org.matrix.rustcomponents.sdk.RoomKeyRecoveryStage as RustRoomKeyRecoveryStage
import org.matrix.rustcomponents.sdk.RoomKeyRecoveryTarget as RustRoomKeyRecoveryTarget
import org.matrix.rustcomponents.sdk.SteadyStateException as RustSteadyStateException

class RustEncryptionService(
    client: Client,
    syncService: RustSyncService,
    sessionCoroutineScope: CoroutineScope,
    private val dispatchers: CoroutineDispatchers,
) : EncryptionService {
    private val service: Encryption = client.encryption()
    private val sessionId = SessionId(client.session().userId)

    private val enableRecoveryProgressMapper = EnableRecoveryProgressMapper()
    private val backupUploadStateMapper = BackupUploadStateMapper()
    private val steadyStateExceptionMapper = SteadyStateExceptionMapper()

    override val backupStateStateFlow = combine(
        service.backupStateFlow(),
        syncService.syncState,
    ) { backupState, syncState ->
        if (syncState == SyncState.Running) {
            backupState
        } else {
            BackupState.WAITING_FOR_SYNC
        }
    }.stateIn(sessionCoroutineScope, SharingStarted.Eagerly, BackupState.WAITING_FOR_SYNC)

    override val recoveryStateStateFlow = combine(
        service.recoveryStateFlow(),
        syncService.syncState,
    ) { recoveryState, syncState ->
        if (syncState == SyncState.Running) {
            recoveryState
        } else {
            RecoveryState.WAITING_FOR_SYNC
        }
    }.stateIn(sessionCoroutineScope, SharingStarted.Eagerly, RecoveryState.WAITING_FOR_SYNC)

    override val enableRecoveryProgressStateFlow: MutableStateFlow<EnableRecoveryProgress> = MutableStateFlow(EnableRecoveryProgress.Starting)

    /**
     * Check if the session is the last session every 5 seconds.
     * TODO This is a temporary workaround, when we will have a way to observe
     * the sessions, this code will have to be updated.
     */
    override val isLastDevice: StateFlow<Boolean> = flow {
        while (currentCoroutineContext().isActive) {
            val result = isLastDevice().getOrDefault(false)
            emit(result)
            delay(5_000)
        }
    }
        .stateIn(sessionCoroutineScope, SharingStarted.Eagerly, false)

    /**
     * Check if the user has any devices available to verify against every 5 seconds.
     * TODO This is a temporary workaround, when we will have a way to observe
     * the sessions, this code will have to be updated.
     */
    override val hasDevicesToVerifyAgainst: StateFlow<AsyncData<Boolean>> = flow {
        while (currentCoroutineContext().isActive) {
            val result = hasDevicesToVerifyAgainst()
            result
                .onSuccess {
                    emit(AsyncData.Success(it))
                }
                .onFailure {
                    Timber.e(it, "Failed to get hasDevicesToVerifyAgainst, retrying in 5s...")
                }
            delay(5_000)
        }
    }
        .stateIn(sessionCoroutineScope, SharingStarted.Eagerly, AsyncData.Uninitialized)

    override suspend fun enableBackups(): Result<Unit> = withContext(dispatchers.io) {
        runCatchingExceptions {
            service.enableBackups()
        }.mapFailure {
            it.mapRecoveryException()
        }
    }

    override suspend fun enableRecovery(
        waitForBackupsToUpload: Boolean,
    ): Result<Unit> = withContext(dispatchers.io) {
        runCatchingExceptions {
            service.enableRecovery(
                waitForBackupsToUpload = waitForBackupsToUpload,
                progressListener = object : EnableRecoveryProgressListener {
                    override fun onUpdate(status: RustEnableRecoveryProgress) {
                        enableRecoveryProgressStateFlow.value = enableRecoveryProgressMapper.map(status)
                    }
                },
                passphrase = null,
            )
                // enableRecovery returns the encryption key, but we read it from the state flow
                .let { }
        }.mapFailure {
            it.mapRecoveryException()
        }
    }

    override suspend fun doesBackupExistOnServer(): Result<Boolean> = withContext(dispatchers.io) {
        runCatchingExceptions {
            service.backupExistsOnServer()
        }
    }

    override fun waitForBackupUploadSteadyState(): Flow<BackupUploadState> {
        return callbackFlow {
            runCatchingExceptions {
                service.waitForBackupUploadSteadyState(
                    progressListener = object : BackupSteadyStateListener {
                        override fun onUpdate(status: RustBackupUploadState) {
                            trySend(backupUploadStateMapper.map(status))
                            if (status == RustBackupUploadState.Done) {
                                close()
                            }
                        }
                    }
                )
            }.onFailure {
                if (it is RustSteadyStateException) {
                    trySend(BackupUploadState.SteadyException(steadyStateExceptionMapper.map(it)))
                } else {
                    trySend(BackupUploadState.Error)
                }
                close()
            }
            awaitClose {}
        }
    }

    override suspend fun disableRecovery(): Result<Unit> = withContext(dispatchers.io) {
        runCatchingExceptions {
            service.disableRecovery()
        }.mapFailure {
            it.mapRecoveryException()
        }
    }

    private suspend fun isLastDevice(): Result<Boolean> = withContext(dispatchers.io) {
        runCatchingExceptions {
            service.isLastDevice()
        }.mapFailure {
            it.mapRecoveryException()
        }
    }

    private suspend fun hasDevicesToVerifyAgainst(): Result<Boolean> = withContext(dispatchers.io) {
        runCatchingExceptions {
            service.hasDevicesToVerifyAgainst()
        }.mapFailure {
            it.mapClientException()
        }
    }

    override suspend fun resetRecoveryKey(): Result<String> = withContext(dispatchers.io) {
        runCatchingExceptions {
            service.resetRecoveryKey()
        }.mapFailure {
            it.mapRecoveryException()
        }
    }

    override suspend fun recover(recoveryKey: String): Result<Unit> = withContext(dispatchers.io) {
        runCatchingExceptions {
            service.recover(recoveryKey)
        }.recoverCatching {
            when (it) {
                // We ignore import errors because the user will be notified about them via the "Key storage out of sync" detection.
                is RustRecoveryException.Import -> Unit
                else -> throw it.mapRecoveryException()
            }
        }
    }

    override suspend fun configureRoomKeyRecovery(policy: RoomKeyForwardingPolicy): Result<Unit> = withContext(dispatchers.io) {
        runCatchingExceptions {
            service.setRoomKeyRequestsEnabled(enable = true)
            service.setRoomKeyForwardingEnabled(enable = true)
            service.setRoomKeyForwardingPolicy(policy.toRustPolicy())
        }.mapFailure {
            it.mapClientException()
        }
    }

    override suspend fun requestRoomKeyRecovery(
        request: RoomKeyRecoveryRequest,
        targets: List<RoomKeyRecoveryTarget>,
        scope: RoomKeyRecoveryScope,
    ): Result<RoomKeyRecoveryProgress> = withContext(dispatchers.io) {
        runCatchingExceptions {
            service.requestRoomKeyRecovery(
                roomId = request.roomId.value,
                sessionId = request.sessionId,
                senderKey = request.senderKey,
                targets = targets.map { it.toRustTarget() },
                scope = scope.toRustScope(),
                ciphertext = request.ciphertext,
            ).map()
        }.mapFailure {
            it.mapClientException()
        }
    }

    override suspend fun deviceCurve25519(): String? {
        return runCatchingExceptions { service.curve25519Key() }.getOrNull()
    }

    override suspend fun deviceEd25519(): String? {
        return runCatchingExceptions { service.ed25519Key() }.getOrNull()
    }

    override suspend fun startIdentityReset(): Result<IdentityResetHandle?> {
        return runCatchingExceptions {
            service.resetIdentity()
        }.flatMap { handle ->
            RustIdentityResetHandleFactory.create(sessionId, handle)
        }
    }

    override suspend fun pinUserIdentity(userId: UserId): Result<Unit> = runCatchingExceptions {
        getUserIdentityInternal(userId).pin()
    }

    override suspend fun withdrawVerification(userId: UserId): Result<Unit> = runCatchingExceptions {
        getUserIdentityInternal(userId).withdrawVerification()
    }

    override suspend fun getUserIdentity(userId: UserId, fallbackToServer: Boolean): Result<IdentityState?> = runCatchingExceptions {
        val identity = getUserIdentityInternal(userId, fallbackToServer)
        val isVerified = identity.isVerified()
        when {
            identity.hasVerificationViolation() -> IdentityState.VerificationViolation
            isVerified -> IdentityState.Verified
            !isVerified -> IdentityState.Pinned
            else -> null
        }
    }

    private suspend fun getUserIdentityInternal(userId: UserId, fallbackToServer: Boolean = true): UserIdentity {
        return service.userIdentity(
            userId = userId.value,
            fallbackToServer = fallbackToServer,
        ) ?: error("User identity not found")
    }

    fun close() {
        service.close()
    }

    private fun RoomKeyRecoveryTarget.toRustTarget(): RustRoomKeyRecoveryTarget {
        return RustRoomKeyRecoveryTarget(
            userId = userId.value,
            deviceId = deviceId,
        )
    }

    private fun RoomKeyRecoveryScope.toRustScope(): RustRoomKeyRecoveryScope = when (this) {
        RoomKeyRecoveryScope.OwnDevices -> RustRoomKeyRecoveryScope.OWN_DEVICES
        RoomKeyRecoveryScope.Sender -> RustRoomKeyRecoveryScope.SENDER
        RoomKeyRecoveryScope.RoomMember -> RustRoomKeyRecoveryScope.ROOM_MEMBER
    }

    private fun RustRoomKeyRecoveryProgress.map(): RoomKeyRecoveryProgress {
        return RoomKeyRecoveryProgress(
            roomId = io.element.android.libraries.matrix.api.core.RoomId(roomId),
            sessionId = sessionId,
            senderKey = senderKey,
            stage = stage.map(),
            message = message,
            targetCount = targetCount,
            manualRetryAvailable = manualRetryAvailable,
        )
    }

    private fun RustRoomKeyRecoveryStage.map(): RoomKeyRecoveryStage = when (this) {
        RustRoomKeyRecoveryStage.BACKUP_REQUESTED -> RoomKeyRecoveryStage.BackupRequested
        RustRoomKeyRecoveryStage.BACKUP_MISSED -> RoomKeyRecoveryStage.BackupMissed
        RustRoomKeyRecoveryStage.SENDER_REQUESTED -> RoomKeyRecoveryStage.SenderRequested
        RustRoomKeyRecoveryStage.SENDER_TIMED_OUT -> RoomKeyRecoveryStage.SenderTimedOut
        RustRoomKeyRecoveryStage.MEMBERS_REQUESTED -> RoomKeyRecoveryStage.MembersRequested
        RustRoomKeyRecoveryStage.MEMBERS_UNAVAILABLE -> RoomKeyRecoveryStage.MembersUnavailable
        RustRoomKeyRecoveryStage.RESOLVED -> RoomKeyRecoveryStage.Resolved
        RustRoomKeyRecoveryStage.FAILED -> RoomKeyRecoveryStage.Failed
    }

    private fun RoomKeyForwardingPolicy.toRustPolicy(): RustRoomKeyForwardingPolicy {
        return object : RustRoomKeyForwardingPolicy {
            override fun allowForwarding(authorization: RustRoomKeyForwardingAuthorization): RustRoomKeyForwardingDecision {
                return this@toRustPolicy.allowForwarding(authorization.map()).map()
            }
        }
    }

    private fun RustRoomKeyForwardingAuthorization.map(): RoomKeyForwardingAuthorization {
        return RoomKeyForwardingAuthorization(
            roomId = io.element.android.libraries.matrix.api.core.RoomId(roomId),
            sessionId = sessionId,
            senderKey = senderKey,
            requesterUserId = UserId(requesterUserId),
            requesterDeviceId = requesterDeviceId,
            requestId = requestId,
            requestedMessageIndex = requestedMessageIndex,
            responderFirstKnownIndex = responderFirstKnownIndex,
        )
    }

    private fun RoomKeyForwardingDecision.map(): RustRoomKeyForwardingDecision {
        return RustRoomKeyForwardingDecision(
            allow = allow,
            exportIndex = exportIndex,
            reason = reason,
        )
    }
}

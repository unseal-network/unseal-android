/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.fixtures.fakes

import io.element.android.libraries.matrix.impl.fixtures.factories.aRustSession
import io.element.android.libraries.matrix.test.A_DEVICE_ID
import io.element.android.libraries.matrix.test.A_USER_ID
import io.element.android.tests.testutils.lambda.lambdaError
import io.element.android.tests.testutils.simulateLongTask
import org.matrix.rustcomponents.sdk.Client
import org.matrix.rustcomponents.sdk.ClientDelegate
import org.matrix.rustcomponents.sdk.CreateRoomParameters
import org.matrix.rustcomponents.sdk.Encryption
import org.matrix.rustcomponents.sdk.HomeserverLoginDetails
import org.matrix.rustcomponents.sdk.IgnoredUsersListener
import org.matrix.rustcomponents.sdk.NoHandle
import org.matrix.rustcomponents.sdk.NotificationClient
import org.matrix.rustcomponents.sdk.NotificationProcessSetup
import org.matrix.rustcomponents.sdk.NotificationSettings
import org.matrix.rustcomponents.sdk.PusherIdentifiers
import org.matrix.rustcomponents.sdk.PusherKind
import org.matrix.rustcomponents.sdk.RoomDirectorySearch
import org.matrix.rustcomponents.sdk.Session
import org.matrix.rustcomponents.sdk.SessionVerificationController
import org.matrix.rustcomponents.sdk.SpaceService
import org.matrix.rustcomponents.sdk.StoreSizes
import org.matrix.rustcomponents.sdk.SyncService
import org.matrix.rustcomponents.sdk.SyncServiceBuilder
import org.matrix.rustcomponents.sdk.TaskHandle
import org.matrix.rustcomponents.sdk.ToDeviceMessage
import org.matrix.rustcomponents.sdk.ToDeviceMessageListener
import org.matrix.rustcomponents.sdk.ToDeviceSendResult
import org.matrix.rustcomponents.sdk.UnableToDecryptDelegate
import org.matrix.rustcomponents.sdk.UserProfile
import uniffi.matrix_sdk_base.MediaRetentionPolicy

class FakeFfiClient(
    private val userId: String = A_USER_ID.value,
    private val deviceId: String = A_DEVICE_ID.value,
    private val notificationClient: NotificationClient = FakeFfiNotificationClient(),
    private val notificationSettings: NotificationSettings = FakeFfiNotificationSettings(),
    private val encryption: Encryption = FakeFfiEncryption(),
    private val session: Session = aRustSession(),
    private val clearCachesResult: () -> Unit = { lambdaError() },
    private val withUtdHook: (UnableToDecryptDelegate) -> Unit = { lambdaError() },
    private val getProfileResult: (String) -> UserProfile = { UserProfile(userId = userId, displayName = null, avatarUrl = null) },
    private val homeserverLoginDetailsResult: () -> HomeserverLoginDetails = { lambdaError() },
    private val getStoreSizesResult: () -> StoreSizes = { lambdaError() },
    private val createRoomResult: (CreateRoomParameters) -> String = { lambdaError() },
    private val sendToDeviceEventResult: () -> ToDeviceSendResult = { ToDeviceSendResult(emptyList()) },
    private val closeResult: () -> Unit = {},
) : Client(NoHandle) {
    var sendToDeviceEventCall: SendToDeviceEventCall? = null
        private set
    var observedToDeviceEventType: String? = null
        private set
    private var toDeviceMessageListener: ToDeviceMessageListener? = null

    override fun userId(): String = userId
    override fun deviceId(): String = deviceId
    override suspend fun notificationClient(processSetup: NotificationProcessSetup) = notificationClient
    override suspend fun getNotificationSettings(): NotificationSettings = notificationSettings
    override fun encryption(): Encryption = encryption
    override fun session(): Session = session
    override fun setDelegate(delegate: ClientDelegate?): TaskHandle = FakeFfiTaskHandle()
    override suspend fun cachedAvatarUrl(): String? = null
    override suspend fun restoreSession(session: Session) = Unit
    override fun syncService(): SyncServiceBuilder = FakeFfiSyncServiceBuilder()
    override suspend fun spaceService(): SpaceService = FakeFfiSpaceService()
    override fun roomDirectorySearch(): RoomDirectorySearch = FakeFfiRoomDirectorySearch()
    override suspend fun setPusher(
        identifiers: PusherIdentifiers,
        kind: PusherKind,
        appDisplayName: String,
        deviceDisplayName: String,
        profileTag: String?,
        lang: String,
    ) = Unit

    override suspend fun deletePusher(identifiers: PusherIdentifiers) = Unit
    override suspend fun clearCaches(syncService: SyncService?) = simulateLongTask { clearCachesResult() }
    override suspend fun setUtdDelegate(utdDelegate: UnableToDecryptDelegate) = withUtdHook(utdDelegate)
    override suspend fun getSessionVerificationController(): SessionVerificationController = FakeFfiSessionVerificationController()
    override suspend fun ignoredUsers(): List<String> {
        return emptyList()
    }

    override fun subscribeToIgnoredUsers(listener: IgnoredUsersListener): TaskHandle {
        return FakeFfiTaskHandle()
    }

    override fun observeToDeviceEvents(eventType: String, listener: ToDeviceMessageListener): TaskHandle {
        observedToDeviceEventType = eventType
        toDeviceMessageListener = listener
        return FakeFfiTaskHandle()
    }

    fun emitToDeviceMessage(message: ToDeviceMessage) {
        toDeviceMessageListener?.onMessage(message)
    }

    override suspend fun getProfile(userId: String): UserProfile {
        return getProfileResult(userId)
    }

    override suspend fun homeserverLoginDetails(): HomeserverLoginDetails {
        return homeserverLoginDetailsResult()
    }

    override suspend fun setMediaRetentionPolicy(policy: MediaRetentionPolicy) {}

    override suspend fun getStoreSizes(): StoreSizes {
        return getStoreSizesResult()
    }

    override suspend fun createRoom(request: CreateRoomParameters): String {
        return createRoomResult(request)
    }

    override suspend fun sendToDeviceEvent(
        eventType: String,
        userId: String,
        deviceId: String,
        content: String,
    ): ToDeviceSendResult {
        sendToDeviceEventCall = SendToDeviceEventCall(
            eventType = eventType,
            userId = userId,
            deviceId = deviceId,
            content = content,
        )
        return sendToDeviceEventResult()
    }

    override fun close() = closeResult()

    data class SendToDeviceEventCall(
        val eventType: String,
        val userId: String,
        val deviceId: String,
        val content: String,
    )
}

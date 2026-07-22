/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl.utils

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.call.api.isValidAudienceBroadcastId
import io.element.android.features.call.impl.audience.AudienceBroadcastHttpClient
import io.element.android.features.call.impl.audience.AudienceWidgetDriver
import io.element.android.libraries.chatbot.api.ChatbotBaseUrlResolver
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.matrix.api.MatrixClientProvider
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.widget.CallWidgetSettingsProvider
import io.element.android.libraries.preferences.api.store.AppPreferencesStore
import io.element.android.services.appnavstate.api.ActiveRoomsHolder
import kotlinx.coroutines.flow.firstOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

private const val EMBEDDED_CALL_WIDGET_BASE_URL = "https://appassets.androidplatform.net/element-call/index.html"
private const val EMBEDDED_CALL_WIDGET_ORIGIN = "https://appassets.androidplatform.net"

@ContributesBinding(AppScope::class)
class DefaultCallWidgetProvider(
    private val matrixClientsProvider: MatrixClientProvider,
    private val appPreferencesStore: AppPreferencesStore,
    private val callWidgetSettingsProvider: CallWidgetSettingsProvider,
    private val activeRoomsHolder: ActiveRoomsHolder,
    private val audienceBroadcastHttpClient: AudienceBroadcastHttpClient,
    private val baseUrlResolver: ChatbotBaseUrlResolver,
) : CallWidgetProvider {
    override suspend fun getWidget(
        sessionId: SessionId,
        roomId: RoomId,
        isAudioCall: Boolean,
        clientId: String,
        languageTag: String?,
        theme: String?,
        audienceBroadcastId: String?,
    ): Result<CallWidgetProvider.GetWidgetResult> = runCatchingExceptions {
        val matrixClient = matrixClientsProvider.getOrRestore(sessionId).getOrThrow()
        if (audienceBroadcastId != null) {
            require(audienceBroadcastId.isValidAudienceBroadcastId()) { "Invalid audience broadcast ID" }
            val mediaOrigin = baseUrlResolver
                .resolveHomeserverBaseUrl(matrixClient.userIdServerName())
                .toHttpUrl()
                .origin()
            val widgetId = "unseal-audience-$audienceBroadcastId"
            val audienceUrl = EMBEDDED_CALL_WIDGET_BASE_URL.toHttpUrl().newBuilder()
                .addQueryParameter("widgetId", widgetId)
                .addQueryParameter("parentUrl", EMBEDDED_CALL_WIDGET_ORIGIN)
                .addQueryParameter("baseUrl", mediaOrigin)
                .fragment("/audience/$audienceBroadcastId")
                .build()
                .toString()
            return@runCatchingExceptions CallWidgetProvider.GetWidgetResult(
                driver = AudienceWidgetDriver(
                    id = widgetId,
                    sessionId = sessionId,
                    broadcastId = audienceBroadcastId,
                    httpClient = audienceBroadcastHttpClient,
                ),
                url = audienceUrl,
            )
        }
        val customBaseUrl = appPreferencesStore.getCustomElementCallBaseUrlFlow().firstOrNull()
        val baseUrl = customBaseUrl ?: EMBEDDED_CALL_WIDGET_BASE_URL
        val room = activeRoomsHolder.getActiveRoomMatching(sessionId, roomId)
            ?: matrixClient.getJoinedRoom(roomId)
            ?: error("Room not found")

        val roomInfo = room.info()
        val isEncrypted = roomInfo.isEncrypted ?: room.getUpdatedIsEncrypted().getOrThrow()
        val widgetSettings = callWidgetSettingsProvider.provide(
            baseUrl = baseUrl,
            encrypted = isEncrypted,
            direct = room.isDm(),
            isAudioCall = isAudioCall,
            hasActiveCall = roomInfo.hasRoomCall,
        )
        val callUrl = room.generateWidgetWebViewUrl(
            widgetSettings = widgetSettings,
            clientId = clientId,
            languageTag = languageTag,
            theme = theme,
        ).getOrThrow()

        val driver = room.getWidgetDriver(widgetSettings).getOrThrow()

        CallWidgetProvider.GetWidgetResult(
            driver = driver,
            url = callUrl,
        )
    }
}

private fun HttpUrl.origin(): String = newBuilder()
    .encodedPath("/")
    .query(null)
    .fragment(null)
    .build()
    .toString()
    .removeSuffix("/")

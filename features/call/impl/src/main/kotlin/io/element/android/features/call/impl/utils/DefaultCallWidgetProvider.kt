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
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.matrix.api.MatrixClientProvider
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.widget.CallWidgetSettingsProvider
import io.element.android.libraries.preferences.api.store.AppPreferencesStore
import io.element.android.services.appnavstate.api.ActiveRoomsHolder
import kotlinx.coroutines.flow.firstOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import timber.log.Timber

private const val EMBEDDED_CALL_WIDGET_BASE_URL = "https://appassets.androidplatform.net/element-call/index.html"

@ContributesBinding(AppScope::class)
class DefaultCallWidgetProvider(
    private val matrixClientsProvider: MatrixClientProvider,
    private val appPreferencesStore: AppPreferencesStore,
    private val callWidgetSettingsProvider: CallWidgetSettingsProvider,
    private val activeRoomsHolder: ActiveRoomsHolder,
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
        audienceBroadcastId?.let {
            require(it.isValidAudienceBroadcastId()) { "Invalid audience broadcast ID" }
        }
        val room = activeRoomsHolder.getActiveRoomMatching(sessionId, roomId)
            ?: matrixClient.getJoinedRoom(roomId)
            ?: error("Room not found")
        val roomInfo = room.info()
        val isEncrypted = roomInfo.isEncrypted ?: room.getUpdatedIsEncrypted().getOrThrow()
        val customBaseUrl = appPreferencesStore.getCustomElementCallBaseUrlFlow().firstOrNull()
        val baseUrl = customBaseUrl ?: EMBEDDED_CALL_WIDGET_BASE_URL
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
        // Listener mode must retain the complete room-widget contract: room,
        // user, device, Widget API capability negotiation and host lifecycle
        // are identical to a normal Element Call. The broadcast id is an input
        // selection only; the web client swaps LiveKit for Cloudflare after the
        // normal room bootstrap has completed.
        val audienceCallUrl = audienceBroadcastId?.let { broadcastId ->
            val url = callUrl.toHttpUrl()
            // The embedded room-widget host uses its fragment for its signed
            // widget contract. Some Android WebView normalisation paths rebuild
            // that URL and drop an outer query, so carry the immutable input
            // selector in both places. The regular query remains primary for
            // web hosts; the fragment makes the same route survive Android.
            val fragment = url.fragment
            val audienceFragment = when {
                fragment.isNullOrEmpty() -> "?audienceBroadcastId=$broadcastId"
                fragment.contains("?") -> "$fragment&audienceBroadcastId=$broadcastId"
                else -> "$fragment?audienceBroadcastId=$broadcastId"
            }
            url.newBuilder()
                .addQueryParameter("audienceBroadcastId", broadcastId)
                .fragment(audienceFragment)
                .build()
                .toString()
        } ?: callUrl
        if (audienceBroadcastId != null) {
            // Do not log the signed widget URL. This one bit proves that the
            // Android listener route survived URL generation and is enough to
            // distinguish a stale APK from a routing regression in logcat.
            Timber.i(
                "Audience listener widget URL ready: broadcastId=%s, hasAudienceRoute=%s",
                audienceBroadcastId,
                audienceCallUrl.toHttpUrl().let { url ->
                    url.queryParameter("audienceBroadcastId") == audienceBroadcastId &&
                        url.fragment?.contains("audienceBroadcastId=$audienceBroadcastId") == true
                },
            )
        }

        val driver = room.getWidgetDriver(widgetSettings).getOrThrow()

        CallWidgetProvider.GetWidgetResult(
            driver = driver,
            url = audienceCallUrl,
        )
    }
}

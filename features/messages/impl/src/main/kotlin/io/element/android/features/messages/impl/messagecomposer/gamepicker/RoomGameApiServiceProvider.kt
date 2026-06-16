/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer.gamepicker

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.chatbot.api.ChatbotBaseUrlResolver
import io.element.android.libraries.di.RoomScope
import io.element.android.libraries.gameapi.api.GameApiService
import io.element.android.libraries.gameapi.impl.DefaultGameApiService
import io.element.android.libraries.matrix.api.MatrixClient
import okhttp3.OkHttpClient

interface RoomGameApiServiceProvider {
    suspend fun create(): Result<RoomGameApiServiceHandle>
}

data class RoomGameApiServiceHandle(
    val service: GameApiService,
    val homeserverHost: String,
)

@SingleIn(RoomScope::class)
@ContributesBinding(RoomScope::class)
@Inject
class DefaultRoomGameApiServiceProvider(
    private val matrixClient: MatrixClient,
    private val baseUrlResolver: ChatbotBaseUrlResolver,
    private val okHttpClient: () -> OkHttpClient,
) : RoomGameApiServiceProvider {
    override suspend fun create(): Result<RoomGameApiServiceHandle> = runCatching {
        val homeserverUrl = baseUrlResolver.resolveHomeserverBaseUrl(matrixClient.userIdServerName())
        RoomGameApiServiceHandle(
            service = DefaultGameApiService(
                homeserverUrl = homeserverUrl,
                matrixClient = matrixClient,
                okHttpClient = okHttpClient(),
            ),
            homeserverHost = homeserverUrl
                .removePrefix("https://")
                .removePrefix("http://")
                .trimEnd('/'),
        )
    }
}

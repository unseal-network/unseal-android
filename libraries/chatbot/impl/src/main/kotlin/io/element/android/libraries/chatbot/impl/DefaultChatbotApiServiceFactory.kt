/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbot.impl

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.binding
import io.element.android.libraries.chatbot.api.ChatbotApiService
import io.element.android.libraries.chatbot.api.ChatbotApiServiceFactory
import io.element.android.libraries.chatbot.api.ChatbotBaseUrlResolver
import io.element.android.libraries.chatbot.api.ChatbotConfig
import io.element.android.libraries.matrix.api.MatrixClient
import okhttp3.OkHttpClient

@ContributesBinding(AppScope::class, binding = binding<ChatbotApiServiceFactory>())
class DefaultChatbotApiServiceFactory(
    private val okHttpClient: () -> OkHttpClient,
    private val tokenProvider: ChatbotAccessTokenProvider,
    private val baseUrlResolver: ChatbotBaseUrlResolver,
) : ChatbotApiServiceFactory {
    override fun createForAiStream(matrixClient: MatrixClient): ChatbotApiService {
        return createForBaseUrl(ChatbotConfig.AI_STREAM_BASE_URL, matrixClient)
    }

    override suspend fun createForUnsealApi(matrixClient: MatrixClient): ChatbotApiService {
        return createForBaseUrl(baseUrlResolver.resolveUnsealApiBaseUrl(matrixClient.userIdServerName()), matrixClient)
    }

    override fun createForBaseUrl(baseUrl: String, matrixClient: MatrixClient): ChatbotApiService {
        return DefaultChatbotApiService(
            httpClient = ChatbotHttpClient(
                baseUrl = baseUrl,
                matrixClient = matrixClient,
                okHttpClient = okHttpClient(),
                tokenProvider = tokenProvider,
            )
        )
    }
}

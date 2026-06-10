/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbot.test

import io.element.android.libraries.chatbot.api.ChatbotApiService
import io.element.android.libraries.chatbot.api.ChatbotApiServiceFactory
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.tests.testutils.simulateLongTask

class FakeChatbotApiServiceFactory(
    private val service: ChatbotApiService = FakeChatbotApiService(),
) : ChatbotApiServiceFactory {
    val explicitBaseUrls = mutableListOf<String>()
    var createForUnsealApiResult: ChatbotApiService = service

    override fun createForAiStream(matrixClient: MatrixClient): ChatbotApiService = service

    override suspend fun createForUnsealApi(matrixClient: MatrixClient): ChatbotApiService = simulateLongTask {
        createForUnsealApiResult
    }

    override suspend fun createForHomeserver(matrixClient: MatrixClient): ChatbotApiService = simulateLongTask {
        createForUnsealApiResult
    }

    override fun createForBaseUrl(baseUrl: String, matrixClient: MatrixClient): ChatbotApiService {
        explicitBaseUrls += baseUrl
        return service
    }
}

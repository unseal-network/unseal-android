/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbot.api

import io.element.android.libraries.matrix.api.MatrixClient

interface ChatbotApiServiceFactory {
    fun createForAiStream(matrixClient: MatrixClient): ChatbotApiService
    suspend fun createForUnsealApi(matrixClient: MatrixClient): ChatbotApiService
    fun createForBaseUrl(baseUrl: String, matrixClient: MatrixClient): ChatbotApiService
}

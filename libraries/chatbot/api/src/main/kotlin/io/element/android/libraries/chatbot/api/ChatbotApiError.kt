/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbot.api

sealed class ChatbotApiError(message: String? = null, cause: Throwable? = null) : Exception(message, cause) {
    data object InvalidBaseUrl : ChatbotApiError("Invalid API base URL.")
    data object MissingAccessToken : ChatbotApiError("Missing access token.")
    data object InvalidResponse : ChatbotApiError("Invalid server response.")
    data class HttpError(val statusCode: Int, val body: String?) : ChatbotApiError("Server error ($statusCode).")
    data class DecodingError(val bodySnippet: String?) : ChatbotApiError("Failed to decode server response.")
    data object EncodingError : ChatbotApiError("Failed to encode request.")
    data class NetworkError(val description: String, val original: Throwable? = null) : ChatbotApiError("Network error: $description", original)
}

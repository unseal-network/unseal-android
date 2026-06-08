/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbot.api.model.storage

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class ChatbotPresignedUpload(
    val filepath: String?,
    val url: String,
    val s3Key: String?,
)

@Serializable
data class ChatbotStsTokenRequest(
    val scope: String,
    @SerialName("duration_seconds")
    val durationSeconds: Int,
)

@Serializable
data class ChatbotStsCredentials(
    val accessKeyId: String,
    val secretAccessKey: String,
    val sessionToken: String? = null,
    val expiration: String? = null,
)

@Serializable
data class ChatbotStsS3Config(
    val endpoint: String? = null,
    val region: String? = null,
    val bucket: String? = null,
    val prefix: String? = null,
)

@Serializable
data class ChatbotStsTokenResponse(
    val credentials: ChatbotStsCredentials,
    val s3Config: ChatbotStsS3Config? = null,
    val minioMode: Boolean? = null,
)

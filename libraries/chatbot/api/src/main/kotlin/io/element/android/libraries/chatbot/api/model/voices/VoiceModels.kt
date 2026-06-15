/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbot.api.model.voices

import kotlinx.serialization.Serializable

/**
 * A voice offered by a provider catalog (e.g. ElevenLabs). Mirrors iOS `ChatbotProviderVoice`.
 * The arbitrary-JSON metadata fields (labels/languageMetadata/providerMetadata) present on iOS are
 * intentionally omitted from this first Android version.
 */
@Serializable
data class ChatbotProviderVoice(
    val provider: String,
    val providerVoiceId: String,
    val displayName: String,
    val description: String? = null,
    val previewUrl: String? = null,
    val availabilityStatus: String = "",
)

/** A user's saved voice profile. Mirrors iOS `ChatbotVoiceProfile`. */
@Serializable
data class ChatbotVoiceProfile(
    val id: String,
    val provider: String,
    val providerVoiceId: String,
    val displayName: String,
    val ownerId: String? = null,
    val description: String? = null,
    val previewUrl: String? = null,
    val sourceType: String = "",
    val visibility: String = "",
    val status: String = "",
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

/** A shareable link for a voice profile. Mirrors iOS `ChatbotVoiceShare`. */
@Serializable
data class ChatbotVoiceShare(
    val id: String,
    val voiceProfileId: String,
    val ownerId: String? = null,
    val targetUserId: String? = null,
    val visibility: String = "",
    val status: String = "",
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class ChatbotDeleteVoiceProfileResponse(
    val deleted: Boolean = false,
    val deletedAgentVoiceConfigs: Int = 0,
)

@Serializable
data class ChatbotCreateVoiceProfileRequest(
    val provider: String,
    val providerVoiceId: String,
    val displayName: String,
    val sourceType: String = "saved_provider_voice",
    val description: String? = null,
    val previewUrl: String? = null,
)

@Serializable
data class ChatbotUploadVoiceProfileRequest(
    val displayName: String,
    val description: String? = null,
    val audioBase64: String,
    val filename: String,
    val mimeType: String,
    val removeBackgroundNoise: Boolean? = null,
)

@Serializable
data class ChatbotCreateVoiceShareRequest(
    val voiceProfileId: String,
    val visibility: String = "private_link",
    val targetUserId: String? = null,
)

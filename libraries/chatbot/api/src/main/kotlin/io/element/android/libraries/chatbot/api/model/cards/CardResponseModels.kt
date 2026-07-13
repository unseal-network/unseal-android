/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbot.api.model.cards

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatbotCardResponseResult(
    @SerialName("event_id")
    val eventId: String,
    @SerialName("room_id")
    val roomId: String,
    @SerialName("action_id")
    val actionId: String,
    val duplicate: Boolean = false,
)

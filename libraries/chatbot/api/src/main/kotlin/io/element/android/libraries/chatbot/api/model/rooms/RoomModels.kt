/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbot.api.model.rooms

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatbotWorkingMemoryResponse(
    @SerialName("working_memory")
    val workingMemory: String = "",
)

@Serializable
data class ChatbotWorkingMemoryRequest(
    @SerialName("working_memory")
    val workingMemory: String,
)

@Serializable
data class ChatbotRoomAgent(
    // The server returns the agent's Matrix id as `user_id` (not `agent_id`).
    @SerialName("user_id")
    val agentId: String,
    @SerialName("display_name")
    val displayName: String? = null,
    @SerialName("avatar_url")
    val avatarUrl: String? = null,
    @SerialName("user_type")
    val userType: String? = null,
    val membership: String? = null,
    val mxid: String? = null,
)

@Serializable
data class ChatbotGetRoomAgentsResponse(
    val agents: List<ChatbotRoomAgent> = emptyList(),
    val total: Int = 0,
)

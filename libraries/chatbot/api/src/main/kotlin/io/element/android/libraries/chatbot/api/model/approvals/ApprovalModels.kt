/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbot.api.model.approvals

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ChatbotApprovalAction {
    @SerialName("agent.join_room")
    AgentJoinRoom,
}

@Serializable
enum class ChatbotApprovalStatus {
    @SerialName("pending")
    Pending,
    @SerialName("approved")
    Approved,
    @SerialName("rejected")
    Rejected,
}

@Serializable
data class ChatbotApproval(
    @SerialName("approval_id")
    val approvalId: String,
    val action: ChatbotApprovalAction,
    val status: ChatbotApprovalStatus,
    @SerialName("requester_user_id")
    val requesterUserId: String,
    @SerialName("created_at")
    val createdAt: Long,
    @SerialName("updated_at")
    val updatedAt: Long,
    @SerialName("resolved_at")
    val resolvedAt: Long? = null,
    @SerialName("agent_id")
    val agentId: String,
    @SerialName("agent_name")
    val agentName: String? = null,
    @SerialName("room_id")
    val roomId: String,
)

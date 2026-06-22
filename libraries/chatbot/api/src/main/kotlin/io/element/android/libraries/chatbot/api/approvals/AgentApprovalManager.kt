/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbot.api.approvals

import io.element.android.libraries.chatbot.api.ChatbotApiService
import io.element.android.libraries.chatbot.api.model.approvals.ChatbotApproval
import io.element.android.libraries.chatbot.api.model.approvals.ChatbotApprovalAction
import io.element.android.libraries.chatbot.api.model.approvals.ChatbotApprovalStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

const val AGENT_APPROVAL_EVENT_TYPE = "io.unseal.approval"
const val AGENT_APPROVAL_JOIN_ROOM_ACTION = "agent.join_room"

class AgentApprovalManager(
    private val apiService: ChatbotApiService,
) {
    private val inFlightApprovalIds = mutableSetOf<String>()
    private val _currentApproval = MutableStateFlow<ChatbotApproval?>(null)
    val currentApproval: StateFlow<ChatbotApproval?> = _currentApproval

    suspend fun handleNotification(eventType: String, content: Map<String, String?>) {
        if (eventType != AGENT_APPROVAL_EVENT_TYPE) return

        val approvalId = content["approval_id"]?.trim().orEmpty()
        val action = content["action"].orEmpty()
        if (approvalId.isEmpty() || action != AGENT_APPROVAL_JOIN_ROOM_ACTION) {
            return
        }

        resolveApproval(approvalId)
    }

    suspend fun resolveApproval(approvalId: String) {
        if (!inFlightApprovalIds.add(approvalId)) return

        try {
            val approval = apiService.getApproval(approvalId).getOrThrow()
            if (approval.action != ChatbotApprovalAction.AgentJoinRoom) return

            if (approval.status == ChatbotApprovalStatus.Pending) {
                _currentApproval.value = approval
            } else if (_currentApproval.value?.approvalId == approval.approvalId) {
                _currentApproval.value = null
            }
        } catch (_: Throwable) {
            // Leave any existing approval visible; the notification can be retried by a later event.
        } finally {
            inFlightApprovalIds.remove(approvalId)
        }
    }

    suspend fun submitDecision(approve: Boolean) {
        val approval = _currentApproval.value ?: return

        try {
            val result = if (approve) {
                apiService.approveApproval(approval.approvalId).getOrThrow()
            } else {
                apiService.rejectApproval(approval.approvalId).getOrThrow()
            }
            if (result.status != ChatbotApprovalStatus.Pending) {
                _currentApproval.value = null
            }
        } catch (_: Throwable) {
            // Keep the approval visible on failure (network error, 5xx, ...) so the user can retry;
            // only dismiss once the server confirms the decision was applied (status left Pending).
            // Mirrors resolveApproval, which also preserves the approval when the request fails.
        }
    }

    fun dismiss() {
        _currentApproval.value = null
    }
}

/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbot.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.chatbot.api.approvals.AGENT_APPROVAL_EVENT_TYPE
import io.element.android.libraries.chatbot.api.approvals.AGENT_APPROVAL_JOIN_ROOM_ACTION
import io.element.android.libraries.chatbot.api.approvals.AgentApprovalManager
import io.element.android.libraries.chatbot.api.model.approvals.ChatbotApproval
import io.element.android.libraries.chatbot.api.model.approvals.ChatbotApprovalAction
import io.element.android.libraries.chatbot.api.model.approvals.ChatbotApprovalStatus
import io.element.android.libraries.chatbot.test.FakeChatbotApiService
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AgentApprovalManagerTest {
    @Test
    fun `handleNotification - fetches pending agent join approval`() = runTest {
        val service = FakeChatbotApiService().apply {
            getApprovalResult = { Result.success(aChatbotApproval()) }
        }
        val manager = AgentApprovalManager(service)

        manager.handleNotification(
            eventType = AGENT_APPROVAL_EVENT_TYPE,
            content = mapOf("approval_id" to " appr_test ", "action" to AGENT_APPROVAL_JOIN_ROOM_ACTION)
        )

        assertThat(manager.currentApproval.value?.approvalId).isEqualTo("appr_test")
    }

    @Test
    fun `submitDecision - keeps approval visible when approve request fails`() = runTest {
        val service = FakeChatbotApiService().apply {
            getApprovalResult = { Result.success(aChatbotApproval()) }
            approveApprovalResult = { Result.failure(RuntimeException("failed")) }
        }
        val manager = AgentApprovalManager(service)
        manager.resolveApproval("appr_test")

        manager.submitDecision(approve = true)

        // A failed request must not silently drop the pending approval; the user can retry.
        assertThat(manager.currentApproval.value?.approvalId).isEqualTo("appr_test")
    }

    @Test
    fun `submitDecision - keeps approval visible when reject request fails`() = runTest {
        val service = FakeChatbotApiService().apply {
            getApprovalResult = { Result.success(aChatbotApproval()) }
            rejectApprovalResult = { Result.failure(RuntimeException("failed")) }
        }
        val manager = AgentApprovalManager(service)
        manager.resolveApproval("appr_test")

        manager.submitDecision(approve = false)

        assertThat(manager.currentApproval.value?.approvalId).isEqualTo("appr_test")
    }
}

private fun aChatbotApproval(status: ChatbotApprovalStatus = ChatbotApprovalStatus.Pending): ChatbotApproval =
    ChatbotApproval(
        approvalId = "appr_test",
        action = ChatbotApprovalAction.AgentJoinRoom,
        status = status,
        requesterUserId = "@alice:example.org",
        createdAt = 1780560400000,
        updatedAt = 1780560400000,
        resolvedAt = null,
        agentId = "@agent:example.org",
        agentName = "Agent",
        roomId = "!room:example.org",
    )

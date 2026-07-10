/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbot.api

import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgent
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgentProvider
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgentRoom
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgentVoiceConfig
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgentVoiceConfigResolution
import io.element.android.libraries.chatbot.api.model.agent.ChatbotCreateAgentRequest
import io.element.android.libraries.chatbot.api.model.agent.ChatbotSetAgentVoiceConfigRequest
import io.element.android.libraries.chatbot.api.model.agent.ChatbotUpdateAgentRequest
import io.element.android.libraries.chatbot.api.model.analytics.AnalyticsTokensResponse
import io.element.android.libraries.chatbot.api.model.approvals.ChatbotApproval
import io.element.android.libraries.chatbot.api.model.channels.ChatbotChannelConnectBody
import io.element.android.libraries.chatbot.api.model.channels.ChatbotChannelCredentials
import io.element.android.libraries.chatbot.api.model.channels.ChatbotChannelSummary
import io.element.android.libraries.chatbot.api.model.channels.ChatbotConnectChannelResponse
import io.element.android.libraries.chatbot.api.model.connectors.ChatbotDisconnectAccountResponse
import io.element.android.libraries.chatbot.api.model.connectors.ChatbotInitiateConnectionResponse
import io.element.android.libraries.chatbot.api.model.connectors.ChatbotListConnectedAccountsResponse
import io.element.android.libraries.chatbot.api.model.connectors.ChatbotListToolkitCategoriesResponse
import io.element.android.libraries.chatbot.api.model.connectors.ChatbotListToolkitsResponse
import io.element.android.libraries.chatbot.api.model.credits.CreditBalance
import io.element.android.libraries.chatbot.api.model.credits.CreditDailyUsageResponse
import io.element.android.libraries.chatbot.api.model.credits.CreditLedgerResponse
import io.element.android.libraries.chatbot.api.model.credits.CreditPaymentIntentResponse
import io.element.android.libraries.chatbot.api.model.credits.CreditPaymentIntentStatusResponse
import io.element.android.libraries.chatbot.api.model.json.ChatbotJsonObject
import io.element.android.libraries.chatbot.api.model.rooms.ChatbotGetRoomAgentsResponse
import io.element.android.libraries.chatbot.api.model.schedules.ChatbotCreateScheduleRequest
import io.element.android.libraries.chatbot.api.model.schedules.ChatbotCreateScheduleResponse
import io.element.android.libraries.chatbot.api.model.schedules.ChatbotSchedule
import io.element.android.libraries.chatbot.api.model.schedules.ChatbotUpdateScheduleRequest
import io.element.android.libraries.chatbot.api.model.skills.ChatbotCreateUserSkillResponse
import io.element.android.libraries.chatbot.api.model.skills.ChatbotDeleteUserSkillResponse
import io.element.android.libraries.chatbot.api.model.skills.ChatbotGetUserSkillResponse
import io.element.android.libraries.chatbot.api.model.skills.ChatbotListPublicSkillCategoriesResponse
import io.element.android.libraries.chatbot.api.model.skills.ChatbotListPublicSkillTagsResponse
import io.element.android.libraries.chatbot.api.model.skills.ChatbotListPublicSkillsResponse
import io.element.android.libraries.chatbot.api.model.skills.ChatbotListRoomAgentSkillsResponse
import io.element.android.libraries.chatbot.api.model.skills.ChatbotSkillListFilters
import io.element.android.libraries.chatbot.api.model.skills.ChatbotSkillVisibility
import io.element.android.libraries.chatbot.api.model.skills.ChatbotUpdateUserSkillResponse
import io.element.android.libraries.chatbot.api.model.skills.ChatbotUserSkill
import io.element.android.libraries.chatbot.api.model.storage.ChatbotPresignedUpload
import io.element.android.libraries.chatbot.api.model.storage.ChatbotStsCredentials
import io.element.android.libraries.chatbot.api.model.storage.ChatbotStsTokenResponse
import io.element.android.libraries.chatbot.api.model.voices.ChatbotCreateVoiceProfileRequest
import io.element.android.libraries.chatbot.api.model.voices.ChatbotCreateVoiceShareRequest
import io.element.android.libraries.chatbot.api.model.voices.ChatbotDeleteVoiceProfileResponse
import io.element.android.libraries.chatbot.api.model.voices.ChatbotProviderVoice
import io.element.android.libraries.chatbot.api.model.voices.ChatbotUploadVoiceProfileRequest
import io.element.android.libraries.chatbot.api.model.voices.ChatbotVoiceProfile
import io.element.android.libraries.chatbot.api.model.voices.ChatbotVoiceShare
import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotCreateWebhookTriggerRequest
import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotUpdateWebhookTriggerRequest
import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotWebhookEventCatalogResponse
import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotWebhookTrigger
import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotWebhookTriggerDeleteResponse
import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotWebhookTriggerDraftResponse
import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotWebhookTriggerStatusResponse

interface ChatbotApiService {
    suspend fun listAgents(): Result<List<ChatbotAgent>>
    suspend fun getAgent(botName: String): Result<ChatbotAgent>
    suspend fun createAgent(request: ChatbotCreateAgentRequest): Result<ChatbotAgent>
    suspend fun updateAgent(botName: String, request: ChatbotUpdateAgentRequest): Result<ChatbotAgent>
    suspend fun getProviders(): Result<List<ChatbotAgentProvider>>
    suspend fun listAgentRooms(botName: String): Result<List<ChatbotAgentRoom>>
    suspend fun agentJoinRoom(botName: String, roomName: String): Result<Unit>
    suspend fun agentLeaveRoom(botName: String, roomId: String): Result<Unit>
    suspend fun abortRun(streamId: String, reason: String?): Result<Unit>
    suspend fun abortRoomAgent(roomId: String, agentId: String, reason: String?): Result<Unit>
    suspend fun abortAgent(agentId: String, reason: String?): Result<Unit>
    suspend fun listAgentSkills(botName: String): Result<List<ChatbotUserSkill>>
    suspend fun addAgentSkill(botName: String, skillId: String, name: String?): Result<Unit>
    suspend fun listRoomAgentSkills(roomId: String, agentId: String, runtimeOwnerUserId: String?): Result<ChatbotListRoomAgentSkillsResponse>
    suspend fun listUserSkills(visibility: ChatbotSkillVisibility?): Result<List<ChatbotUserSkill>>
    suspend fun listPublicSkills(page: Int, pageSize: Int, search: String?): Result<ChatbotListPublicSkillsResponse>
    suspend fun listPublicSkills(page: Int, pageSize: Int, filters: ChatbotSkillListFilters): Result<ChatbotListPublicSkillsResponse>
    suspend fun listPublicSkillCategories(): Result<ChatbotListPublicSkillCategoriesResponse>
    suspend fun listPublicSkillTags(): Result<ChatbotListPublicSkillTagsResponse>
    suspend fun getUserSkill(id: String): Result<ChatbotGetUserSkillResponse>
    suspend fun createUserSkill(body: ChatbotJsonObject): Result<ChatbotCreateUserSkillResponse>
    suspend fun updateUserSkill(id: String, body: ChatbotJsonObject): Result<ChatbotUpdateUserSkillResponse>
    suspend fun deleteUserSkill(id: String): Result<ChatbotDeleteUserSkillResponse>
    suspend fun presignedUploadUrls(body: ChatbotJsonObject): Result<List<ChatbotPresignedUpload>>
    suspend fun getStsToken(scope: String, durationSeconds: Int): Result<ChatbotStsTokenResponse>

    /**
     * Uploads bytes to S3 (or MinIO) using STS temporary credentials and AWS Signature V4.
     * Mirrors the iOS `ChatbotAPIClient.uploadToS3` flow.
     */
    suspend fun uploadToS3(
        endpoint: String,
        bucket: String,
        key: String,
        data: ByteArray,
        contentType: String,
        credentials: ChatbotStsCredentials,
        region: String,
        isMinIO: Boolean,
    ): Result<Unit>
    suspend fun listSchedules(roomId: String): Result<List<ChatbotSchedule>>
    suspend fun createSchedule(request: ChatbotCreateScheduleRequest): Result<ChatbotCreateScheduleResponse>
    suspend fun updateSchedule(scheduleId: String, request: ChatbotUpdateScheduleRequest): Result<ChatbotCreateScheduleResponse>
    suspend fun updateScheduleStatus(scheduleId: String, status: String): Result<Unit>
    suspend fun deleteSchedule(scheduleId: String): Result<Unit>
    suspend fun getRoomWorkingMemory(roomId: String): Result<String>
    suspend fun updateRoomWorkingMemory(roomId: String, content: String): Result<Unit>
    suspend fun getRoomAgents(roomId: String): Result<ChatbotGetRoomAgentsResponse>
    suspend fun getApproval(approvalId: String): Result<ChatbotApproval>
    suspend fun approveApproval(approvalId: String): Result<ChatbotApproval>
    suspend fun rejectApproval(approvalId: String): Result<ChatbotApproval>
    suspend fun listToolkitCategories(cursor: String?, limit: Int?): Result<ChatbotListToolkitCategoriesResponse>
    suspend fun listToolkits(search: String?, category: String?, cursor: String?, limit: Int?): Result<ChatbotListToolkitsResponse>
    suspend fun initiateConnection(toolkit: String, redirectUrl: String): Result<ChatbotInitiateConnectionResponse>
    suspend fun listConnectedAccounts(toolkit: String?, cursor: String?, limit: Int?): Result<ChatbotListConnectedAccountsResponse>
    suspend fun disconnectAccount(accountId: String): Result<ChatbotDisconnectAccountResponse>
    suspend fun listWebhookEventTypes(): Result<ChatbotWebhookEventCatalogResponse>
    suspend fun listWebhookTriggers(agentId: String?, source: String?, roomId: String?, status: String?): Result<List<ChatbotWebhookTrigger>>
    suspend fun createWebhookTrigger(request: ChatbotCreateWebhookTriggerRequest): Result<ChatbotWebhookTrigger>
    suspend fun updateWebhookTrigger(triggerId: String, request: ChatbotUpdateWebhookTriggerRequest): Result<ChatbotWebhookTrigger>
    suspend fun updateWebhookTriggerStatus(triggerId: String, enabled: Boolean): Result<ChatbotWebhookTriggerStatusResponse>
    suspend fun deleteWebhookTrigger(triggerId: String): Result<ChatbotWebhookTriggerDeleteResponse>
    suspend fun draftWebhookTrigger(prompt: String): Result<ChatbotWebhookTriggerDraftResponse>
    suspend fun getBalance(): Result<CreditBalance>
    suspend fun getLedger(limit: Int, cursor: String?): Result<CreditLedgerResponse>
    suspend fun getDailyUsage(start: Int, end: Int): Result<CreditDailyUsageResponse>
    suspend fun createPaymentIntent(amountCents: Int): Result<CreditPaymentIntentResponse>
    suspend fun getPaymentIntentStatus(paymentIntentId: String): Result<CreditPaymentIntentStatusResponse>
    suspend fun getAnalyticsTokens(period: String): Result<AnalyticsTokensResponse>
    suspend fun listProviderVoices(provider: String?, availabilityStatus: String?, search: String?, limit: Int?, offset: Int?): Result<List<ChatbotProviderVoice>>
    suspend fun listVoiceProfiles(provider: String?, status: String?, search: String?, limit: Int?, offset: Int?): Result<List<ChatbotVoiceProfile>>
    suspend fun createVoiceProfile(request: ChatbotCreateVoiceProfileRequest): Result<ChatbotVoiceProfile>
    suspend fun uploadVoiceProfile(request: ChatbotUploadVoiceProfileRequest): Result<ChatbotVoiceProfile>
    suspend fun deleteVoiceProfile(voiceProfileId: String): Result<ChatbotDeleteVoiceProfileResponse>
    suspend fun createVoiceShare(request: ChatbotCreateVoiceShareRequest): Result<ChatbotVoiceShare>
    suspend fun importVoiceShare(shareId: String): Result<ChatbotVoiceProfile>

    // Agent runtime environment (sandbox) — agent-api endpoints.
    suspend fun getAgentSandbox(agentId: String): Result<io.element.android.libraries.chatbot.api.model.agent.AgentSandboxResponse>
    suspend fun createAgentSandbox(agentId: String): Result<io.element.android.libraries.chatbot.api.model.agent.AgentSandboxStatus>
    suspend fun cloneAgentSandbox(agentId: String): Result<io.element.android.libraries.chatbot.api.model.agent.AgentSandboxCloneResponse>
    suspend fun listAgentVault(agentId: String): Result<List<io.element.android.libraries.chatbot.api.model.agent.AgentVaultEntry>>
    suspend fun cloneAgentVault(agentId: String, keys: List<String>): Result<io.element.android.libraries.chatbot.api.model.agent.AgentVaultCloneResponse>

    // Agent voice config — agent-api endpoints.
    suspend fun getAgentVoiceConfig(agentId: String): Result<ChatbotAgentVoiceConfigResolution>
    suspend fun setAgentVoiceConfig(agentId: String, request: ChatbotSetAgentVoiceConfigRequest): Result<ChatbotAgentVoiceConfig>
    suspend fun deleteAgentVoiceConfig(agentId: String): Result<Unit>

    // Agent channels (Telegram / WeCom bindings) — agent-api endpoints.
    suspend fun listAgentChannels(agentId: String): Result<List<ChatbotChannelSummary>>
    suspend fun connectAgentChannel(agentId: String, body: ChatbotChannelConnectBody): Result<ChatbotConnectChannelResponse>
    suspend fun disconnectAgentChannel(agentId: String, installationId: String): Result<Unit>
    suspend fun updateAgentChannel(agentId: String, installationId: String, token: String, encodingAESKey: String): Result<ChatbotConnectChannelResponse>
    suspend fun getAgentChannelCredentials(agentId: String, installationId: String): Result<ChatbotChannelCredentials>
    suspend fun getAgentChannel(agentId: String, installationId: String): Result<ChatbotChannelSummary>

    // Personal vault (secret store) — AI-stream base endpoints.
    suspend fun listVault(): Result<List<io.element.android.libraries.chatbot.api.model.vault.ChatbotVaultItem>>
    suspend fun getVaultValue(key: String): Result<String>
    suspend fun createVaultEntry(key: String, value: String, description: String?): Result<Unit>
    suspend fun updateVaultEntry(key: String, value: String, description: String?): Result<Unit>
    suspend fun deleteVaultEntry(key: String): Result<Unit>

    suspend fun streamAgentMessage(
        streamId: String,
        sender: String?,
        onChunk: suspend (String) -> Unit,
    ): Result<Unit>
}

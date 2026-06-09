/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbot.test

import io.element.android.libraries.chatbot.api.ChatbotApiService
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgent
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgentProvider
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgentRoom
import io.element.android.libraries.chatbot.api.model.agent.ChatbotCreateAgentRequest
import io.element.android.libraries.chatbot.api.model.agent.ChatbotUpdateAgentRequest
import io.element.android.libraries.chatbot.api.model.analytics.AnalyticsTokensResponse
import io.element.android.libraries.chatbot.api.model.connectors.ChatbotDisconnectAccountResponse
import io.element.android.libraries.chatbot.api.model.connectors.ChatbotInitiateConnectionResponse
import io.element.android.libraries.chatbot.api.model.connectors.ChatbotListConnectedAccountsResponse
import io.element.android.libraries.chatbot.api.model.connectors.ChatbotListToolkitCategoriesResponse
import io.element.android.libraries.chatbot.api.model.connectors.ChatbotListToolkitsResponse
import io.element.android.libraries.chatbot.api.model.voices.ChatbotCreateVoiceProfileRequest
import io.element.android.libraries.chatbot.api.model.voices.ChatbotCreateVoiceShareRequest
import io.element.android.libraries.chatbot.api.model.voices.ChatbotDeleteVoiceProfileResponse
import io.element.android.libraries.chatbot.api.model.voices.ChatbotProviderVoice
import io.element.android.libraries.chatbot.api.model.voices.ChatbotVoiceProfile
import io.element.android.libraries.chatbot.api.model.voices.ChatbotVoiceShare
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
import io.element.android.libraries.chatbot.api.model.storage.ChatbotPresignedUpload
import io.element.android.libraries.chatbot.api.model.storage.ChatbotStsCredentials
import io.element.android.libraries.chatbot.api.model.storage.ChatbotStsTokenResponse
import io.element.android.libraries.chatbot.api.model.skills.ChatbotCreateUserSkillResponse
import io.element.android.libraries.chatbot.api.model.skills.ChatbotDeleteUserSkillResponse
import io.element.android.libraries.chatbot.api.model.skills.ChatbotGetUserSkillResponse
import io.element.android.libraries.chatbot.api.model.skills.ChatbotListPublicSkillsResponse
import io.element.android.libraries.chatbot.api.model.skills.ChatbotListRoomAgentSkillsResponse
import io.element.android.libraries.chatbot.api.model.skills.ChatbotSkillVisibility
import io.element.android.libraries.chatbot.api.model.skills.ChatbotUpdateUserSkillResponse
import io.element.android.libraries.chatbot.api.model.skills.ChatbotUserSkill
import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotCreateWebhookTriggerRequest
import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotUpdateWebhookTriggerRequest
import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotWebhookEventCatalogResponse
import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotWebhookTrigger
import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotWebhookTriggerDeleteResponse
import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotWebhookTriggerDraftResponse
import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotWebhookTriggerStatusResponse
import io.element.android.tests.testutils.simulateLongTask

class FakeChatbotApiService : ChatbotApiService {
    var listAgentsResult: () -> Result<List<ChatbotAgent>> = { Result.success(emptyList()) }
    var getAgentResult: (String) -> Result<ChatbotAgent> = { Result.success(aChatbotAgent(botName = it)) }
    var createAgentResult: (ChatbotCreateAgentRequest) -> Result<ChatbotAgent> = { Result.success(aChatbotAgent(botName = it.botName)) }
    var updateAgentResult: (String, ChatbotUpdateAgentRequest) -> Result<ChatbotAgent> = { botName, _ -> Result.success(aChatbotAgent(botName = botName)) }
    var getProvidersResult: () -> Result<List<ChatbotAgentProvider>> = { Result.success(emptyList()) }
    var listAgentRoomsResult: (String) -> Result<List<ChatbotAgentRoom>> = { Result.success(emptyList()) }
    var agentJoinRoomResult: (String, String) -> Result<Unit> = { _, _ -> Result.success(Unit) }
    var agentLeaveRoomResult: (String, String) -> Result<Unit> = { _, _ -> Result.success(Unit) }
    var listAgentSkillsResult: (String) -> Result<List<ChatbotUserSkill>> = { Result.success(emptyList()) }
    var addAgentSkillResult: (String, String, String?) -> Result<Unit> = { _, _, _ -> Result.success(Unit) }
    var listRoomAgentSkillsResult: (String, String, String?) -> Result<ChatbotListRoomAgentSkillsResponse> = { _, _, _ -> Result.success(ChatbotListRoomAgentSkillsResponse()) }
    var listUserSkillsResult: (ChatbotSkillVisibility?) -> Result<List<ChatbotUserSkill>> = { Result.success(emptyList()) }
    var listPublicSkillsResult: (Int, Int, String?) -> Result<ChatbotListPublicSkillsResponse> = { _, _, _ -> Result.success(ChatbotListPublicSkillsResponse()) }
    var getUserSkillResult: (String) -> Result<ChatbotGetUserSkillResponse> = { Result.success(ChatbotGetUserSkillResponse(aChatbotUserSkill(id = it))) }
    var createUserSkillResult: (ChatbotJsonObject) -> Result<ChatbotCreateUserSkillResponse> = { Result.success(ChatbotCreateUserSkillResponse()) }
    var updateUserSkillResult: (String, ChatbotJsonObject) -> Result<ChatbotUpdateUserSkillResponse> = { _, _ -> Result.success(ChatbotUpdateUserSkillResponse()) }
    var deleteUserSkillResult: (String) -> Result<ChatbotDeleteUserSkillResponse> = { Result.success(ChatbotDeleteUserSkillResponse(success = true)) }
    var presignedUploadUrlsResult: (ChatbotJsonObject) -> Result<List<ChatbotPresignedUpload>> = { Result.success(emptyList()) }
    var getStsTokenResult: (String, Int) -> Result<ChatbotStsTokenResponse> = { _, _ ->
        Result.success(ChatbotStsTokenResponse(ChatbotStsCredentials(accessKeyId = "access", secretAccessKey = "secret")))
    }
    var listSchedulesResult: (String) -> Result<List<ChatbotSchedule>> = { Result.success(emptyList()) }
    var createScheduleResult: (ChatbotCreateScheduleRequest) -> Result<ChatbotCreateScheduleResponse> = { Result.success(ChatbotCreateScheduleResponse(success = true, ebScheduleId = "schedule")) }
    var updateScheduleResult: (String, ChatbotUpdateScheduleRequest) -> Result<ChatbotCreateScheduleResponse> = { _, _ -> Result.success(ChatbotCreateScheduleResponse(success = true, ebScheduleId = "schedule")) }
    var updateScheduleStatusResult: (String, String) -> Result<Unit> = { _, _ -> Result.success(Unit) }
    var deleteScheduleResult: (String) -> Result<Unit> = { Result.success(Unit) }
    var getRoomWorkingMemoryResult: (String) -> Result<String> = { Result.success("") }
    var updateRoomWorkingMemoryResult: (String, String) -> Result<Unit> = { _, _ -> Result.success(Unit) }
    var getRoomAgentsResult: (String) -> Result<ChatbotGetRoomAgentsResponse> = { Result.success(ChatbotGetRoomAgentsResponse()) }
    var listToolkitCategoriesResult: (String?, Int?) -> Result<ChatbotListToolkitCategoriesResponse> = { _, _ -> Result.success(ChatbotListToolkitCategoriesResponse()) }
    var listToolkitsResult: (String?, String?, String?, Int?) -> Result<ChatbotListToolkitsResponse> = { _, _, _, _ -> Result.success(ChatbotListToolkitsResponse()) }
    var initiateConnectionResult: (String, String) -> Result<ChatbotInitiateConnectionResponse> = { _, _ -> Result.success(ChatbotInitiateConnectionResponse(connectUrl = "https://connect.example")) }
    var listConnectedAccountsResult: (String?, String?, Int?) -> Result<ChatbotListConnectedAccountsResponse> = { _, _, _ -> Result.success(ChatbotListConnectedAccountsResponse()) }
    var disconnectAccountResult: (String) -> Result<ChatbotDisconnectAccountResponse> = { Result.success(ChatbotDisconnectAccountResponse(success = true)) }
    var listWebhookEventTypesResult: () -> Result<ChatbotWebhookEventCatalogResponse> = { Result.success(ChatbotWebhookEventCatalogResponse()) }
    var listWebhookTriggersResult: (String?, String?, String?, String?) -> Result<List<ChatbotWebhookTrigger>> = { _, _, _, _ -> Result.success(emptyList()) }
    var createWebhookTriggerResult: (ChatbotCreateWebhookTriggerRequest) -> Result<ChatbotWebhookTrigger> = { Result.success(aChatbotWebhookTrigger()) }
    var updateWebhookTriggerResult: (String, ChatbotUpdateWebhookTriggerRequest) -> Result<ChatbotWebhookTrigger> = { id, _ -> Result.success(aChatbotWebhookTrigger(id)) }
    var updateWebhookTriggerStatusResult: (String, Boolean) -> Result<ChatbotWebhookTriggerStatusResponse> = { id, enabled -> Result.success(ChatbotWebhookTriggerStatusResponse(triggerId = id, status = if (enabled) "enabled" else "disabled")) }
    var deleteWebhookTriggerResult: (String) -> Result<ChatbotWebhookTriggerDeleteResponse> = { Result.success(ChatbotWebhookTriggerDeleteResponse(triggerId = it, deleted = true)) }
    var draftWebhookTriggerResult: (String) -> Result<ChatbotWebhookTriggerDraftResponse> = { Result.success(ChatbotWebhookTriggerDraftResponse(source = "source", eventTypes = emptyList(), name = "Draft", actionPrompt = it)) }
    var getBalanceResult: () -> Result<CreditBalance> = { Result.success(aCreditBalance()) }
    var getLedgerResult: (Int, String?) -> Result<CreditLedgerResponse> = { _, _ -> Result.success(CreditLedgerResponse()) }
    var getDailyUsageResult: (Int, Int) -> Result<CreditDailyUsageResponse> = { start, end -> Result.success(CreditDailyUsageResponse(start = start, end = end, totalUsageMicros = "0")) }
    var createPaymentIntentResult: (Int) -> Result<CreditPaymentIntentResponse> = { Result.success(CreditPaymentIntentResponse(paymentIntentClientSecret = "pi_secret", ephemeralKeySecret = "ek_secret", customerId = "customer")) }
    var getPaymentIntentStatusResult: (String) -> Result<CreditPaymentIntentStatusResponse> = { Result.success(CreditPaymentIntentStatusResponse(status = "succeeded", amountCents = 100, ledgerSettled = true)) }
    var getAnalyticsTokensResult: (String) -> Result<AnalyticsTokensResponse> = { Result.success(AnalyticsTokensResponse(period = it)) }
    var listProviderVoicesResult: (String?, String?, String?, Int?, Int?) -> Result<List<ChatbotProviderVoice>> = { _, _, _, _, _ -> Result.success(emptyList()) }
    var listVoiceProfilesResult: (String?, String?, String?, Int?, Int?) -> Result<List<ChatbotVoiceProfile>> = { _, _, _, _, _ -> Result.success(emptyList()) }
    var createVoiceProfileResult: (ChatbotCreateVoiceProfileRequest) -> Result<ChatbotVoiceProfile> = { Result.success(aChatbotVoiceProfile(displayName = it.displayName)) }
    var deleteVoiceProfileResult: (String) -> Result<ChatbotDeleteVoiceProfileResponse> = { Result.success(ChatbotDeleteVoiceProfileResponse(deleted = true)) }
    var createVoiceShareResult: (ChatbotCreateVoiceShareRequest) -> Result<ChatbotVoiceShare> = { Result.success(ChatbotVoiceShare(id = "share-1", voiceProfileId = it.voiceProfileId)) }
    var importVoiceShareResult: (String) -> Result<ChatbotVoiceProfile> = { Result.success(aChatbotVoiceProfile()) }

    override suspend fun listAgents() = simulateLongTask { listAgentsResult() }
    override suspend fun getAgent(botName: String) = simulateLongTask { getAgentResult(botName) }
    override suspend fun createAgent(request: ChatbotCreateAgentRequest) = simulateLongTask { createAgentResult(request) }
    override suspend fun updateAgent(botName: String, request: ChatbotUpdateAgentRequest) = simulateLongTask { updateAgentResult(botName, request) }
    override suspend fun getProviders() = simulateLongTask { getProvidersResult() }
    override suspend fun listAgentRooms(botName: String) = simulateLongTask { listAgentRoomsResult(botName) }
    override suspend fun agentJoinRoom(botName: String, roomName: String) = simulateLongTask { agentJoinRoomResult(botName, roomName) }
    override suspend fun agentLeaveRoom(botName: String, roomId: String) = simulateLongTask { agentLeaveRoomResult(botName, roomId) }
    override suspend fun listAgentSkills(botName: String) = simulateLongTask { listAgentSkillsResult(botName) }
    override suspend fun addAgentSkill(botName: String, skillId: String, name: String?) = simulateLongTask { addAgentSkillResult(botName, skillId, name) }
    override suspend fun listRoomAgentSkills(roomId: String, agentId: String, runtimeOwnerUserId: String?) = simulateLongTask { listRoomAgentSkillsResult(roomId, agentId, runtimeOwnerUserId) }
    override suspend fun listUserSkills(visibility: ChatbotSkillVisibility?) = simulateLongTask { listUserSkillsResult(visibility) }
    override suspend fun listPublicSkills(page: Int, pageSize: Int, search: String?) = simulateLongTask { listPublicSkillsResult(page, pageSize, search) }
    override suspend fun getUserSkill(id: String) = simulateLongTask { getUserSkillResult(id) }
    override suspend fun createUserSkill(body: ChatbotJsonObject) = simulateLongTask { createUserSkillResult(body) }
    override suspend fun updateUserSkill(id: String, body: ChatbotJsonObject) = simulateLongTask { updateUserSkillResult(id, body) }
    override suspend fun deleteUserSkill(id: String) = simulateLongTask { deleteUserSkillResult(id) }
    override suspend fun presignedUploadUrls(body: ChatbotJsonObject) = simulateLongTask { presignedUploadUrlsResult(body) }
    override suspend fun getStsToken(scope: String, durationSeconds: Int) = simulateLongTask { getStsTokenResult(scope, durationSeconds) }
    override suspend fun listSchedules(roomId: String) = simulateLongTask { listSchedulesResult(roomId) }
    override suspend fun createSchedule(request: ChatbotCreateScheduleRequest) = simulateLongTask { createScheduleResult(request) }
    override suspend fun updateSchedule(scheduleId: String, request: ChatbotUpdateScheduleRequest) = simulateLongTask { updateScheduleResult(scheduleId, request) }
    override suspend fun updateScheduleStatus(scheduleId: String, status: String) = simulateLongTask { updateScheduleStatusResult(scheduleId, status) }
    override suspend fun deleteSchedule(scheduleId: String) = simulateLongTask { deleteScheduleResult(scheduleId) }
    override suspend fun getRoomWorkingMemory(roomId: String) = simulateLongTask { getRoomWorkingMemoryResult(roomId) }
    override suspend fun updateRoomWorkingMemory(roomId: String, content: String) = simulateLongTask { updateRoomWorkingMemoryResult(roomId, content) }
    override suspend fun getRoomAgents(roomId: String) = simulateLongTask { getRoomAgentsResult(roomId) }
    override suspend fun listToolkitCategories(cursor: String?, limit: Int?) = simulateLongTask { listToolkitCategoriesResult(cursor, limit) }
    override suspend fun listToolkits(search: String?, category: String?, cursor: String?, limit: Int?) = simulateLongTask { listToolkitsResult(search, category, cursor, limit) }
    override suspend fun initiateConnection(toolkit: String, redirectUrl: String) = simulateLongTask { initiateConnectionResult(toolkit, redirectUrl) }
    override suspend fun listConnectedAccounts(toolkit: String?, cursor: String?, limit: Int?) = simulateLongTask { listConnectedAccountsResult(toolkit, cursor, limit) }
    override suspend fun disconnectAccount(accountId: String) = simulateLongTask { disconnectAccountResult(accountId) }
    override suspend fun listWebhookEventTypes() = simulateLongTask { listWebhookEventTypesResult() }
    override suspend fun listWebhookTriggers(agentId: String?, source: String?, roomId: String?, status: String?) = simulateLongTask { listWebhookTriggersResult(agentId, source, roomId, status) }
    override suspend fun createWebhookTrigger(request: ChatbotCreateWebhookTriggerRequest) = simulateLongTask { createWebhookTriggerResult(request) }
    override suspend fun updateWebhookTrigger(triggerId: String, request: ChatbotUpdateWebhookTriggerRequest) = simulateLongTask { updateWebhookTriggerResult(triggerId, request) }
    override suspend fun updateWebhookTriggerStatus(triggerId: String, enabled: Boolean) = simulateLongTask { updateWebhookTriggerStatusResult(triggerId, enabled) }
    override suspend fun deleteWebhookTrigger(triggerId: String) = simulateLongTask { deleteWebhookTriggerResult(triggerId) }
    override suspend fun draftWebhookTrigger(prompt: String) = simulateLongTask { draftWebhookTriggerResult(prompt) }
    override suspend fun getBalance() = simulateLongTask { getBalanceResult() }
    override suspend fun getLedger(limit: Int, cursor: String?) = simulateLongTask { getLedgerResult(limit, cursor) }
    override suspend fun getDailyUsage(start: Int, end: Int) = simulateLongTask { getDailyUsageResult(start, end) }
    override suspend fun createPaymentIntent(amountCents: Int) = simulateLongTask { createPaymentIntentResult(amountCents) }
    override suspend fun getPaymentIntentStatus(paymentIntentId: String) = simulateLongTask { getPaymentIntentStatusResult(paymentIntentId) }
    override suspend fun getAnalyticsTokens(period: String) = simulateLongTask { getAnalyticsTokensResult(period) }
    override suspend fun listProviderVoices(provider: String?, availabilityStatus: String?, search: String?, limit: Int?, offset: Int?) = simulateLongTask { listProviderVoicesResult(provider, availabilityStatus, search, limit, offset) }
    override suspend fun listVoiceProfiles(provider: String?, status: String?, search: String?, limit: Int?, offset: Int?) = simulateLongTask { listVoiceProfilesResult(provider, status, search, limit, offset) }
    override suspend fun createVoiceProfile(request: ChatbotCreateVoiceProfileRequest) = simulateLongTask { createVoiceProfileResult(request) }
    override suspend fun deleteVoiceProfile(voiceProfileId: String) = simulateLongTask { deleteVoiceProfileResult(voiceProfileId) }
    override suspend fun createVoiceShare(request: ChatbotCreateVoiceShareRequest) = simulateLongTask { createVoiceShareResult(request) }
    override suspend fun importVoiceShare(shareId: String) = simulateLongTask { importVoiceShareResult(shareId) }
}

/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbot.impl

import io.element.android.libraries.chatbot.api.ChatbotApiError
import io.element.android.libraries.chatbot.api.ChatbotApiService
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgent
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgentProvider
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgentRoom
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgentVoiceConfig
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgentVoiceConfigResolution
import io.element.android.libraries.chatbot.api.model.agent.ChatbotCreateAgentRequest
import io.element.android.libraries.chatbot.api.model.agent.ChatbotGetAgentProvidersResponse
import io.element.android.libraries.chatbot.api.model.agent.ChatbotListAgentRoomsResponse
import io.element.android.libraries.chatbot.api.model.agent.ChatbotListAgentsResponse
import io.element.android.libraries.chatbot.api.model.agent.ChatbotSetAgentVoiceConfigRequest
import io.element.android.libraries.chatbot.api.model.agent.ChatbotUpdateAgentRequest
import io.element.android.libraries.chatbot.api.model.analytics.AnalyticsTokensResponse
import io.element.android.libraries.chatbot.api.model.approvals.ChatbotApproval
import io.element.android.libraries.chatbot.api.model.cards.ChatbotCardResponseResult
import io.element.android.libraries.chatbot.api.model.channels.ChatbotChannelConnectBody
import io.element.android.libraries.chatbot.api.model.channels.ChatbotChannelCredentials
import io.element.android.libraries.chatbot.api.model.channels.ChatbotChannelSummary
import io.element.android.libraries.chatbot.api.model.channels.ChatbotConnectChannelResponse
import io.element.android.libraries.chatbot.api.model.channels.ChatbotListChannelsResponse
import io.element.android.libraries.chatbot.api.model.connectors.ChatbotDisconnectAccountResponse
import io.element.android.libraries.chatbot.api.model.connectors.ChatbotInitiateConnectionResponse
import io.element.android.libraries.chatbot.api.model.connectors.ChatbotListConnectedAccountsResponse
import io.element.android.libraries.chatbot.api.model.connectors.ChatbotListToolkitCategoriesResponse
import io.element.android.libraries.chatbot.api.model.connectors.ChatbotListToolkitsResponse
import io.element.android.libraries.chatbot.api.model.credits.CreditBalance
import io.element.android.libraries.chatbot.api.model.credits.CreditDailyUsageResponse
import io.element.android.libraries.chatbot.api.model.credits.CreditLedgerResponse
import io.element.android.libraries.chatbot.api.model.credits.CreditPaymentIntentRequest
import io.element.android.libraries.chatbot.api.model.credits.CreditPaymentIntentResponse
import io.element.android.libraries.chatbot.api.model.credits.CreditPaymentIntentStatusResponse
import io.element.android.libraries.chatbot.api.model.json.ChatbotJsonObject
import io.element.android.libraries.chatbot.api.model.rooms.ChatbotGetRoomAgentsResponse
import io.element.android.libraries.chatbot.api.model.rooms.ChatbotWorkingMemoryRequest
import io.element.android.libraries.chatbot.api.model.rooms.ChatbotWorkingMemoryResponse
import io.element.android.libraries.chatbot.api.model.schedules.ChatbotCreateScheduleRequest
import io.element.android.libraries.chatbot.api.model.schedules.ChatbotCreateScheduleResponse
import io.element.android.libraries.chatbot.api.model.schedules.ChatbotListSchedulesResponse
import io.element.android.libraries.chatbot.api.model.schedules.ChatbotSchedule
import io.element.android.libraries.chatbot.api.model.schedules.ChatbotScheduleStatusRequest
import io.element.android.libraries.chatbot.api.model.schedules.ChatbotUpdateScheduleRequest
import io.element.android.libraries.chatbot.api.model.skills.ChatbotAgentSkillItem
import io.element.android.libraries.chatbot.api.model.skills.ChatbotCreateUserSkillResponse
import io.element.android.libraries.chatbot.api.model.skills.ChatbotDeleteUserSkillResponse
import io.element.android.libraries.chatbot.api.model.skills.ChatbotGetUserSkillResponse
import io.element.android.libraries.chatbot.api.model.skills.ChatbotListAgentSkillsResponse
import io.element.android.libraries.chatbot.api.model.skills.ChatbotListPublicSkillCategoriesResponse
import io.element.android.libraries.chatbot.api.model.skills.ChatbotListPublicSkillTagsResponse
import io.element.android.libraries.chatbot.api.model.skills.ChatbotListPublicSkillsResponse
import io.element.android.libraries.chatbot.api.model.skills.ChatbotListRoomAgentSkillsResponse
import io.element.android.libraries.chatbot.api.model.skills.ChatbotListUserSkillsResponse
import io.element.android.libraries.chatbot.api.model.skills.ChatbotSkillListFilters
import io.element.android.libraries.chatbot.api.model.skills.ChatbotSkillVisibility
import io.element.android.libraries.chatbot.api.model.skills.ChatbotUpdateUserSkillResponse
import io.element.android.libraries.chatbot.api.model.skills.ChatbotUserSkill
import io.element.android.libraries.chatbot.api.model.storage.ChatbotPresignedUpload
import io.element.android.libraries.chatbot.api.model.storage.ChatbotStsCredentials
import io.element.android.libraries.chatbot.api.model.storage.ChatbotStsTokenRequest
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

internal class DefaultChatbotApiService(
    private val httpClient: ChatbotHttpClient,
    private val okHttpClient: OkHttpClient,
) : ChatbotApiService {
    override suspend fun listAgents(): Result<List<ChatbotAgent>> {
        return httpClient.requestRaw("/chatbot/v1/agents", ChatbotHttpMethod.GET).mapCatching { raw ->
            try {
                decodeAgentsList(raw)
            } catch (e: Exception) {
                throw ChatbotApiError.DecodingError(ChatbotRedactor.redact(raw).take(2_000))
            }
        }
    }

    override suspend fun getAgent(botName: String): Result<ChatbotAgent> =
        httpClient.requestJson("/chatbot/v1/agents/${ChatbotUrlBuilder.path("{botName}", mapOf("botName" to botName))}", ChatbotHttpMethod.GET)

    override suspend fun createAgent(request: ChatbotCreateAgentRequest): Result<ChatbotAgent> =
        httpClient.requestJson("/chatbot/v1/agents", ChatbotHttpMethod.POST, encode(request))

    override suspend fun updateAgent(botName: String, request: ChatbotUpdateAgentRequest): Result<ChatbotAgent> =
        httpClient.requestJson("/chatbot/v1/agents/${path(botName)}", ChatbotHttpMethod.PUT, encode(request))

    override suspend fun getProviders(): Result<List<ChatbotAgentProvider>> =
        httpClient.requestJson<ChatbotGetAgentProvidersResponse>("/chatbot/v1/agents/providers", ChatbotHttpMethod.GET).map { it.providers }

    override suspend fun listAgentRooms(botName: String): Result<List<ChatbotAgentRoom>> =
        httpClient.requestJson<ChatbotListAgentRoomsResponse>("/chatbot/v1/agents/${path(botName)}/rooms", ChatbotHttpMethod.GET).map { it.rooms }

    override suspend fun agentJoinRoom(botName: String, roomName: String): Result<Unit> =
        rawUnit("/chatbot/v1/agents/${path(botName)}/join", ChatbotHttpMethod.POST, jsonObject("room_name" to roomName))

    override suspend fun agentLeaveRoom(botName: String, roomId: String): Result<Unit> =
        rawUnit("/chatbot/v1/agents/${path(botName)}/leave", ChatbotHttpMethod.POST, jsonObject("room_id" to roomId))

    override suspend fun listAgentSkills(botName: String): Result<List<ChatbotUserSkill>> {
        return httpClient.requestRaw("/chatbot/v1/agents/${path(botName)}/skills", ChatbotHttpMethod.GET).mapCatching { raw ->
            runCatching {
                ChatbotJson.decode<ChatbotListAgentSkillsResponse>(raw).skills.map(ChatbotAgentSkillItem::toUserSkill)
            }.getOrElse {
                runCatching {
                    ChatbotJson.json.decodeFromString(ListSerializer(serializer<ChatbotAgentSkillItem>()), raw).map(ChatbotAgentSkillItem::toUserSkill)
                }.getOrElse {
                    emptyList()
                }
            }
        }
    }

    override suspend fun addAgentSkill(botName: String, skillId: String, name: String?): Result<Unit> {
        val body = buildMap {
            put("skill_id", JsonPrimitive(skillId))
            if (!name.isNullOrEmpty()) put("name", JsonPrimitive(name))
        }
        return rawUnit("/chatbot/v1/agents/${path(botName)}/skills/add", ChatbotHttpMethod.POST, JsonObject(body).toString())
    }

    override suspend fun listRoomAgentSkills(roomId: String, agentId: String, runtimeOwnerUserId: String?): Result<ChatbotListRoomAgentSkillsResponse> {
        val query = ChatbotUrlBuilder.query(mapOf("runtimeOwnerUserId" to runtimeOwnerUserId))
        return httpClient.requestJson("/api/rooms/${path(roomId)}/agents/${path(agentId)}/skills$query", ChatbotHttpMethod.GET)
    }

    override suspend fun refreshRoomAgentSkills(roomId: String, agentId: String?, cacheKey: String, runtimeOwnerUserId: String?): Result<ChatbotListRoomAgentSkillsResponse> {
        val body = buildMap {
            put("cacheKey", JsonPrimitive(cacheKey))
            if (!agentId.isNullOrEmpty()) put("agentId", JsonPrimitive(agentId))
            if (!runtimeOwnerUserId.isNullOrEmpty()) put("runtimeOwnerUserId", JsonPrimitive(runtimeOwnerUserId))
        }
        return httpClient.requestJson(
            "/api/rooms/${path(roomId)}/agent-skills/refresh-workspace",
            ChatbotHttpMethod.POST,
            JsonObject(body).toString()
        )
    }

    override suspend fun listUserSkills(visibility: ChatbotSkillVisibility?): Result<List<ChatbotUserSkill>> {
        return httpClient.requestJson<ChatbotListUserSkillsResponse>("/chatbot/v1/skills${userSkillFilterQuery(visibility)}", ChatbotHttpMethod.GET)
            .map { it.skills }
    }

    override suspend fun listPublicSkills(page: Int, pageSize: Int, search: String?): Result<ChatbotListPublicSkillsResponse> =
        listPublicSkills(page, pageSize, ChatbotSkillListFilters(search = search.orEmpty()))

    override suspend fun listPublicSkills(page: Int, pageSize: Int, filters: ChatbotSkillListFilters): Result<ChatbotListPublicSkillsResponse> =
        httpClient.requestJson(
            "/api/skills/public${skillFilterQuery(filters, extra = mapOf("page" to page.toString(), "pageSize" to pageSize.toString()))}",
            ChatbotHttpMethod.GET
        )

    override suspend fun listPublicSkillCategories(): Result<ChatbotListPublicSkillCategoriesResponse> =
        httpClient.requestJson("/api/skills/public/categories", ChatbotHttpMethod.GET)

    override suspend fun listPublicSkillTags(): Result<ChatbotListPublicSkillTagsResponse> =
        httpClient.requestJson("/api/skills/public/tags", ChatbotHttpMethod.GET)

    override suspend fun getUserSkill(id: String): Result<ChatbotGetUserSkillResponse> =
        httpClient.requestJson("/chatbot/v1/skills/${path(id)}", ChatbotHttpMethod.GET)

    override suspend fun createUserSkill(body: ChatbotJsonObject): Result<ChatbotCreateUserSkillResponse> =
        httpClient.requestJson("/chatbot/v1/skills", ChatbotHttpMethod.POST, body.toString())

    override suspend fun updateUserSkill(id: String, body: ChatbotJsonObject): Result<ChatbotUpdateUserSkillResponse> =
        httpClient.requestJson("/chatbot/v1/skills/${path(id)}", ChatbotHttpMethod.PUT, body.toString())

    override suspend fun deleteUserSkill(id: String): Result<ChatbotDeleteUserSkillResponse> =
        httpClient.requestJson("/chatbot/v1/skills/${path(id)}", ChatbotHttpMethod.DELETE)

    override suspend fun presignedUploadUrls(body: ChatbotJsonObject): Result<List<ChatbotPresignedUpload>> {
        return httpClient.requestRaw("/chatbot/v1/storage/presigned-upload-urls", ChatbotHttpMethod.POST, body.toString()).mapCatching { raw ->
            parsePresignedUploads(raw)
        }
    }

    override suspend fun getStsToken(scope: String, durationSeconds: Int): Result<ChatbotStsTokenResponse> =
        httpClient.requestJson("/chatbot/v1/storage/sts_token", ChatbotHttpMethod.POST, encode(ChatbotStsTokenRequest(scope, durationSeconds)))

    override suspend fun uploadToS3(
        endpoint: String,
        bucket: String,
        key: String,
        data: ByteArray,
        contentType: String,
        credentials: ChatbotStsCredentials,
        region: String,
        isMinIO: Boolean,
    ): Result<Unit> {
        val base = endpoint.toHttpUrlOrNull() ?: return Result.failure(ChatbotApiError.InvalidBaseUrl)
        val objectUrl = if (isMinIO) {
            // MinIO: PUT {endpoint}/{bucket}/{key}
            base.newBuilder()
                .addPathSegment(bucket)
                .apply { key.split("/").filter { it.isNotEmpty() }.forEach { addPathSegment(it) } }
                .build()
        } else {
            // AWS S3: PUT https://{bucket}.s3.{region}.amazonaws.com/{key}
            base.newBuilder()
                .host("$bucket.s3.$region.amazonaws.com")
                .encodedPath("/")
                .apply { key.split("/").filter { it.isNotEmpty() }.forEach { addPathSegment(it) } }
                .build()
        }

        val signer = AwsV4Signer(
            accessKeyId = credentials.accessKeyId,
            secretAccessKey = credentials.secretAccessKey,
            sessionToken = credentials.sessionToken,
            region = region,
            service = "s3",
        )

        val requestBuilder = Request.Builder()
            .url(objectUrl)
            .put(data.toRequestBody(contentType.toMediaTypeOrNull()))
        signer.sign(requestBuilder, method = "PUT", url = objectUrl, contentType = contentType, body = data)
        val request = requestBuilder.build()

        return withContext(Dispatchers.IO) {
            try {
                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Result.success(Unit)
                    } else {
                        Result.failure(ChatbotApiError.HttpError(response.code, response.body.string().take(800)))
                    }
                }
            } catch (e: IOException) {
                Result.failure(ChatbotApiError.NetworkError(e.message.orEmpty(), e))
            }
        }
    }

    override suspend fun listSchedules(roomId: String): Result<List<ChatbotSchedule>> =
        httpClient.requestJson<ChatbotListSchedulesResponse>("/chatbot/v1/schedules${ChatbotUrlBuilder.query(mapOf("room_id" to roomId, "viewAll" to "true"))}", ChatbotHttpMethod.GET)
            .map { it.schedules }

    override suspend fun createSchedule(request: ChatbotCreateScheduleRequest): Result<ChatbotCreateScheduleResponse> {
        return httpClient.requestJson<ChatbotCreateScheduleResponse>("/chatbot/v1/schedules", ChatbotHttpMethod.POST, encode(request))
            .mapCatching {
                if (it.ebScheduleId.isNullOrEmpty()) {
                    throw ChatbotApiError.HttpError(200, "schedule not created on server")
                }
                it
            }
    }

    override suspend fun updateSchedule(scheduleId: String, request: ChatbotUpdateScheduleRequest): Result<ChatbotCreateScheduleResponse> =
        httpClient.requestJson("/chatbot/v1/schedules/${path(scheduleId)}", ChatbotHttpMethod.PUT, encode(request))

    override suspend fun updateScheduleStatus(scheduleId: String, status: String): Result<Unit> =
        rawUnit("/chatbot/v1/schedules/${path(scheduleId)}/status", ChatbotHttpMethod.POST, encode(ChatbotScheduleStatusRequest(status)))

    override suspend fun deleteSchedule(scheduleId: String): Result<Unit> =
        rawUnit("/chatbot/v1/schedules/${path(scheduleId)}", ChatbotHttpMethod.DELETE)

    override suspend fun getRoomWorkingMemory(roomId: String): Result<String> =
        httpClient.requestJson<ChatbotWorkingMemoryResponse>("/chatbot/v1/rooms/${path(roomId)}/working-memory", ChatbotHttpMethod.GET)
            .map { it.workingMemory }

    override suspend fun updateRoomWorkingMemory(roomId: String, content: String): Result<Unit> =
        rawUnit("/chatbot/v1/rooms/${path(roomId)}/working-memory", ChatbotHttpMethod.PUT, encode(ChatbotWorkingMemoryRequest(content)))

    override suspend fun getRoomAgents(roomId: String): Result<ChatbotGetRoomAgentsResponse> =
        httpClient.requestJson("/chatbot/v1/rooms/${path(roomId)}/agents", ChatbotHttpMethod.GET)

    override suspend fun getApproval(approvalId: String): Result<ChatbotApproval> =
        httpClient.requestJson("/chatbot/v1/approvals/${path(approvalId)}", ChatbotHttpMethod.GET)

    override suspend fun approveApproval(approvalId: String): Result<ChatbotApproval> =
        httpClient.requestJson("/chatbot/v1/approvals/${path(approvalId)}/approve", ChatbotHttpMethod.POST)

    override suspend fun rejectApproval(approvalId: String): Result<ChatbotApproval> =
        httpClient.requestJson("/chatbot/v1/approvals/${path(approvalId)}/reject", ChatbotHttpMethod.POST)

    override suspend fun sendCardResponse(roomId: String, eventId: String, actionId: String): Result<ChatbotCardResponseResult> =
        httpClient.requestJson(
            "/chatbot/v1/cards/${path(roomId)}/${path(eventId)}/responses",
            ChatbotHttpMethod.POST,
            jsonObject("action_id" to actionId),
        )

    override suspend fun listToolkitCategories(cursor: String?, limit: Int?): Result<ChatbotListToolkitCategoriesResponse> =
        httpClient.requestJson("/api/integrations/composio/toolkit-categories${ChatbotUrlBuilder.query(mapOf("cursor" to cursor, "limit" to limit?.toString()))}", ChatbotHttpMethod.GET)

    override suspend fun listToolkits(search: String?, category: String?, cursor: String?, limit: Int?): Result<ChatbotListToolkitsResponse> =
        httpClient.requestJson(
            "/api/integrations/composio/toolkits${ChatbotUrlBuilder.query(mapOf("search" to search?.takeIf { it.isNotEmpty() }, "category" to category?.takeIf { it.isNotEmpty() }, "cursor" to cursor, "limit" to limit?.toString()))}",
            ChatbotHttpMethod.GET
        )

    override suspend fun initiateConnection(toolkit: String, redirectUrl: String): Result<ChatbotInitiateConnectionResponse> =
        httpClient.requestJson("/api/integrations/composio/connections", ChatbotHttpMethod.POST, jsonObject("toolkit" to toolkit, "redirectUrl" to redirectUrl))

    override suspend fun listConnectedAccounts(toolkit: String?, cursor: String?, limit: Int?): Result<ChatbotListConnectedAccountsResponse> =
        httpClient.requestJson("/api/integrations/composio/accounts${ChatbotUrlBuilder.query(mapOf("toolkit" to toolkit?.takeIf { it.isNotEmpty() }, "cursor" to cursor, "limit" to limit?.toString()))}", ChatbotHttpMethod.GET)

    override suspend fun disconnectAccount(accountId: String): Result<ChatbotDisconnectAccountResponse> =
        httpClient.requestJson("/api/integrations/composio/accounts/disconnect", ChatbotHttpMethod.POST, jsonObject("accountId" to accountId))

    override suspend fun listWebhookEventTypes(): Result<ChatbotWebhookEventCatalogResponse> =
        httpClient.requestJson("/api/webhook-event-types", ChatbotHttpMethod.GET)

    override suspend fun listWebhookTriggers(agentId: String?, source: String?, roomId: String?, status: String?): Result<List<ChatbotWebhookTrigger>> =
        httpClient.requestJson("/api/webhook-triggers${ChatbotUrlBuilder.query(mapOf("agentId" to agentId, "source" to source, "roomId" to roomId, "status" to status))}", ChatbotHttpMethod.GET)

    override suspend fun createWebhookTrigger(request: ChatbotCreateWebhookTriggerRequest): Result<ChatbotWebhookTrigger> =
        httpClient.requestJson("/api/webhook-triggers", ChatbotHttpMethod.POST, encode(request))

    override suspend fun updateWebhookTrigger(triggerId: String, request: ChatbotUpdateWebhookTriggerRequest): Result<ChatbotWebhookTrigger> =
        httpClient.requestJson("/api/webhook-triggers/${path(triggerId)}", ChatbotHttpMethod.PUT, encode(request))

    override suspend fun updateWebhookTriggerStatus(triggerId: String, enabled: Boolean): Result<ChatbotWebhookTriggerStatusResponse> =
        httpClient.requestJson("/api/webhook-triggers/${path(triggerId)}/${if (enabled) "enable" else "disable"}", ChatbotHttpMethod.POST)

    override suspend fun deleteWebhookTrigger(triggerId: String): Result<ChatbotWebhookTriggerDeleteResponse> =
        httpClient.requestJson("/api/webhook-triggers/${path(triggerId)}", ChatbotHttpMethod.DELETE)

    override suspend fun draftWebhookTrigger(prompt: String): Result<ChatbotWebhookTriggerDraftResponse> =
        httpClient.requestJson("/api/system-agent/draft/webhook-trigger", ChatbotHttpMethod.POST, jsonObject("prompt" to prompt))

    override suspend fun getBalance(): Result<CreditBalance> =
        httpClient.requestJson("/api/credits/balance", ChatbotHttpMethod.GET)

    override suspend fun getLedger(limit: Int, cursor: String?): Result<CreditLedgerResponse> =
        httpClient.requestJson("/api/credits/ledger${ChatbotUrlBuilder.query(mapOf("limit" to limit.toString(), "cursor" to cursor?.takeIf { it.isNotEmpty() }))}", ChatbotHttpMethod.GET)

    override suspend fun getDailyUsage(start: Int, end: Int): Result<CreditDailyUsageResponse> =
        httpClient.requestJson("/api/credits/daily-usage${ChatbotUrlBuilder.query(mapOf("start" to start.toString(), "end" to end.toString()))}", ChatbotHttpMethod.GET)

    override suspend fun createPaymentIntent(amountCents: Int): Result<CreditPaymentIntentResponse> =
        httpClient.requestJson("/api/credits/topup/payment-intent", ChatbotHttpMethod.POST, encode(CreditPaymentIntentRequest(amountCents)))

    override suspend fun getPaymentIntentStatus(paymentIntentId: String): Result<CreditPaymentIntentStatusResponse> =
        httpClient.requestJson("/api/credits/topup/payment-intent/status${ChatbotUrlBuilder.query(mapOf("paymentIntentId" to paymentIntentId))}", ChatbotHttpMethod.GET)

    override suspend fun getAnalyticsTokens(period: String): Result<AnalyticsTokensResponse> =
        httpClient.requestJson("/chatbot/v1/analytics/tokens${ChatbotUrlBuilder.query(mapOf("period" to period))}", ChatbotHttpMethod.GET)

    override suspend fun listProviderVoices(provider: String?, availabilityStatus: String?, search: String?, limit: Int?, offset: Int?): Result<List<ChatbotProviderVoice>> =
        httpClient.requestJson(
            "/api/voices/provider-catalog${ChatbotUrlBuilder.query(mapOf("provider" to provider?.takeIf { it.isNotEmpty() }, "availabilityStatus" to availabilityStatus?.takeIf { it.isNotEmpty() }, "search" to search?.takeIf { it.isNotEmpty() }, "limit" to limit?.toString(), "offset" to offset?.toString()))}",
            ChatbotHttpMethod.GET
        )

    override suspend fun listVoiceProfiles(provider: String?, status: String?, search: String?, limit: Int?, offset: Int?): Result<List<ChatbotVoiceProfile>> =
        httpClient.requestJson(
            "/api/voices/profiles${ChatbotUrlBuilder.query(mapOf("provider" to provider?.takeIf { it.isNotEmpty() }, "status" to status?.takeIf { it.isNotEmpty() }, "search" to search?.takeIf { it.isNotEmpty() }, "limit" to limit?.toString(), "offset" to offset?.toString()))}",
            ChatbotHttpMethod.GET
        )

    override suspend fun createVoiceProfile(request: ChatbotCreateVoiceProfileRequest): Result<ChatbotVoiceProfile> =
        httpClient.requestJson("/api/voices/profiles", ChatbotHttpMethod.POST, encode(request))

    override suspend fun uploadVoiceProfile(request: ChatbotUploadVoiceProfileRequest): Result<ChatbotVoiceProfile> =
        httpClient.requestJson("/api/voices/profiles/upload-clone", ChatbotHttpMethod.POST, encode(request))

    override suspend fun deleteVoiceProfile(voiceProfileId: String): Result<ChatbotDeleteVoiceProfileResponse> =
        httpClient.requestJson("/api/voices/profiles/${path(voiceProfileId)}", ChatbotHttpMethod.DELETE)

    override suspend fun createVoiceShare(request: ChatbotCreateVoiceShareRequest): Result<ChatbotVoiceShare> =
        httpClient.requestJson("/api/voices/shares", ChatbotHttpMethod.POST, encode(request))

    override suspend fun importVoiceShare(shareId: String): Result<ChatbotVoiceProfile> =
        httpClient.requestJson("/api/voices/shares/${path(shareId)}/import", ChatbotHttpMethod.POST)

    override suspend fun getAgentSandbox(agentId: String) =
        httpClient.requestJson<io.element.android.libraries.chatbot.api.model.agent.AgentSandboxResponse>("/api/agent/${path(agentId)}/sandbox", ChatbotHttpMethod.GET)

    override suspend fun createAgentSandbox(agentId: String) =
        httpClient.requestJson<io.element.android.libraries.chatbot.api.model.agent.AgentSandboxStatus>("/api/agent/${path(agentId)}/sandbox", ChatbotHttpMethod.POST)

    override suspend fun cloneAgentSandbox(agentId: String) =
        httpClient.requestJson<io.element.android.libraries.chatbot.api.model.agent.AgentSandboxCloneResponse>("/api/agent/${path(agentId)}/sandbox/clone", ChatbotHttpMethod.POST)

    override suspend fun listAgentVault(agentId: String) =
        httpClient.requestJson<List<io.element.android.libraries.chatbot.api.model.agent.AgentVaultEntry>>("/api/agent/${path(agentId)}/vault", ChatbotHttpMethod.GET)

    override suspend fun cloneAgentVault(agentId: String, keys: List<String>) =
        httpClient.requestJson<io.element.android.libraries.chatbot.api.model.agent.AgentVaultCloneResponse>(
            "/api/agent/${path(agentId)}/vault/clone",
            ChatbotHttpMethod.POST,
            encode(io.element.android.libraries.chatbot.api.model.agent.AgentVaultCloneRequest(keys = keys)),
        )

    override suspend fun getAgentVoiceConfig(agentId: String): Result<ChatbotAgentVoiceConfigResolution> =
        httpClient.requestJson("/api/agents/${path(agentId)}/voice-config", ChatbotHttpMethod.GET)

    override suspend fun setAgentVoiceConfig(agentId: String, request: ChatbotSetAgentVoiceConfigRequest): Result<ChatbotAgentVoiceConfig> =
        httpClient.requestJson("/api/agents/${path(agentId)}/voice-config", ChatbotHttpMethod.PUT, encode(request))

    override suspend fun deleteAgentVoiceConfig(agentId: String): Result<Unit> =
        rawUnit("/api/agents/${path(agentId)}/voice-config", ChatbotHttpMethod.DELETE)

    override suspend fun listAgentChannels(agentId: String): Result<List<ChatbotChannelSummary>> =
        httpClient.requestJson<ChatbotListChannelsResponse>("/api/agents/${path(agentId)}/channels", ChatbotHttpMethod.GET).map { it.channels }

    override suspend fun connectAgentChannel(agentId: String, body: ChatbotChannelConnectBody): Result<ChatbotConnectChannelResponse> {
        val credentials = when (body) {
            is ChatbotChannelConnectBody.Telegram -> JsonObject(
                mapOf(
                    "platform" to JsonPrimitive("telegram"),
                    "botToken" to JsonPrimitive(body.botToken),
                )
            )
            is ChatbotChannelConnectBody.WeCom -> JsonObject(
                mapOf(
                    "platform" to JsonPrimitive("wecom"),
                    "token" to JsonPrimitive(body.token),
                    "encodingAESKey" to JsonPrimitive(body.encodingAESKey),
                )
            )
            ChatbotChannelConnectBody.Feishu -> JsonObject(
                mapOf(
                    "platform" to JsonPrimitive("feishu"),
                )
            )
            is ChatbotChannelConnectBody.Discord -> JsonObject(
                mapOf(
                    "platform" to JsonPrimitive("discord"),
                    "botToken" to JsonPrimitive(body.botToken),
                    "publicKey" to JsonPrimitive(body.publicKey),
                    "applicationId" to JsonPrimitive(body.applicationId),
                )
            )
        }
        val payload = JsonObject(mapOf("credentials" to credentials)).toString()
        return httpClient.requestJson("/api/agents/${path(agentId)}/channels", ChatbotHttpMethod.POST, payload)
    }

    override suspend fun disconnectAgentChannel(agentId: String, installationId: String): Result<Unit> =
        rawUnit("/api/agents/${path(agentId)}/channels/${path(installationId)}", ChatbotHttpMethod.DELETE)

    override suspend fun updateAgentChannel(agentId: String, installationId: String, token: String, encodingAESKey: String): Result<ChatbotConnectChannelResponse> {
        val payload = JsonObject(
            mapOf(
                "credentials" to JsonObject(
                    mapOf(
                        "token" to JsonPrimitive(token),
                        "encodingAESKey" to JsonPrimitive(encodingAESKey),
                    )
                )
            )
        ).toString()
        return httpClient.requestJson("/api/agents/${path(agentId)}/channels/${path(installationId)}", ChatbotHttpMethod.PUT, payload)
    }

    override suspend fun getAgentChannelCredentials(agentId: String, installationId: String): Result<ChatbotChannelCredentials> =
        httpClient.requestJson("/api/agents/${path(agentId)}/channels/${path(installationId)}/credentials", ChatbotHttpMethod.GET)

    override suspend fun getAgentChannel(agentId: String, installationId: String): Result<ChatbotChannelSummary> =
        httpClient.requestJson("/api/agents/${path(agentId)}/channels/${path(installationId)}", ChatbotHttpMethod.GET)

    override suspend fun listVault(): Result<List<io.element.android.libraries.chatbot.api.model.vault.ChatbotVaultItem>> =
        httpClient.requestJson<io.element.android.libraries.chatbot.api.model.vault.ChatbotVaultListResponse>("/chatbot/v1/vault", ChatbotHttpMethod.GET).map { it.items }

    override suspend fun getVaultValue(key: String): Result<String> =
        httpClient.requestJson<io.element.android.libraries.chatbot.api.model.vault.ChatbotVaultDetailResponse>("/chatbot/v1/vault/${path(key)}", ChatbotHttpMethod.GET).map { it.value.orEmpty() }

    override suspend fun createVaultEntry(key: String, value: String, description: String?): Result<Unit> =
        rawUnit("/chatbot/v1/vault", ChatbotHttpMethod.POST, jsonObject("key" to key, "value" to value, "description" to description.orEmpty()))

    override suspend fun updateVaultEntry(key: String, value: String, description: String?): Result<Unit> =
        rawUnit("/chatbot/v1/vault/${path(key)}", ChatbotHttpMethod.PATCH, jsonObject("key" to key, "value" to value, "description" to description.orEmpty()))

    override suspend fun deleteVaultEntry(key: String): Result<Unit> =
        rawUnit("/chatbot/v1/vault/${path(key)}", ChatbotHttpMethod.DELETE)

    override suspend fun streamAgentMessage(
        streamId: String,
        sender: String?,
        onChunk: suspend (String) -> Unit,
    ): Result<Unit> {
        val query = ChatbotUrlBuilder.query(mapOf("sender" to sender?.takeIf { it.isNotBlank() }))
        return httpClient.streamRaw("/chatbot/v1/stream/${path(streamId)}$query", onChunk)
    }

    private suspend fun rawUnit(path: String, method: ChatbotHttpMethod, body: String? = null): Result<Unit> =
        httpClient.requestRaw(path, method, body).map { }

    private inline fun <reified T> encode(value: T): String = ChatbotJson.encode(value)

    private fun path(value: String): String = ChatbotUrlBuilder.path("{value}", mapOf("value" to value))

    private fun skillFilterQuery(
        filters: ChatbotSkillListFilters,
        extra: Map<String, String?> = emptyMap(),
    ): String {
        return ChatbotUrlBuilder.query(
            extra + mapOf(
                "search" to filters.search.trim().takeIf { it.isNotEmpty() },
                "categorySlugs" to filters.categorySlug?.trim()?.takeIf { it.isNotEmpty() },
                "sourceSlugs" to filters.sourceSlug?.trim()?.takeIf { it.isNotEmpty() },
                "tagSlugs" to filters.tagSlugs.mapNotNull { it.trim().takeIf(String::isNotEmpty) }.takeIf { it.isNotEmpty() }?.joinToString(","),
                "tagMode" to filters.tagMode.queryValue,
            )
        )
    }

    private fun userSkillFilterQuery(visibility: ChatbotSkillVisibility?): String =
        ChatbotUrlBuilder.query(mapOf("visibility" to visibility?.name?.lowercase()))

    private fun jsonObject(vararg values: Pair<String, String>): String {
        return JsonObject(values.associate { (key, value) -> key to JsonPrimitive(value) }).toString()
    }

    private fun parsePresignedUploads(raw: String): List<ChatbotPresignedUpload> {
        val root = runCatching { ChatbotJson.json.parseToJsonElement(raw) }.getOrNull() ?: return emptyList()
        val candidates = when (root) {
            is JsonArray -> root
            is JsonObject -> {
                root["uploads"]?.jsonArrayOrNull()
                    ?: root["presigned_urls"]?.jsonArrayOrNull()
                    ?: root["urls"]?.jsonArrayOrNull()
                    ?: root["data"]?.jsonArrayOrNull()
            }
            else -> null
        } ?: return emptyList()

        return candidates.mapNotNull { item ->
            when (item) {
                is JsonObject -> {
                    val url = item.stringOrNull("url")
                        ?: item.stringOrNull("presigned_url")
                        ?: item.stringOrNull("presignedUrl")
                        ?: return@mapNotNull null
                    ChatbotPresignedUpload(
                        filepath = item.stringOrNull("filepath") ?: item.stringOrNull("path") ?: item.stringOrNull("file"),
                        url = url,
                        s3Key = item.stringOrNull("s3_key") ?: item.stringOrNull("key") ?: item.stringOrNull("object_key"),
                    )
                }
                else -> item.jsonPrimitiveOrNull()?.content?.let { url ->
                    ChatbotPresignedUpload(filepath = null, url = url, s3Key = null)
                }
            }
        }
    }

    private fun decodeAgentsList(raw: String): List<ChatbotAgent> {
        return runCatching {
            ChatbotJson.json.decodeFromString(ListSerializer(serializer<ChatbotAgent>()), raw)
        }.getOrElse {
            val root = ChatbotJson.json.parseToJsonElement(raw)
            if (root !is JsonObject || "agents" !in root) {
                throw it
            }
            ChatbotJson.decode<ChatbotListAgentsResponse>(raw).agents
        }
    }

    private fun JsonElement.jsonArrayOrNull(): JsonArray? = this as? JsonArray

    private fun JsonElement.jsonPrimitiveOrNull() = runCatching { jsonPrimitive }.getOrNull()

    private fun JsonObject.stringOrNull(key: String): String? = get(key)?.jsonPrimitiveOrNull()?.content?.takeIf { it.isNotBlank() }
}

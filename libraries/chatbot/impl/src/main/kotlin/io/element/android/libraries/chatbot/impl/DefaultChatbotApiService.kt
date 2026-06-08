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
import io.element.android.libraries.chatbot.api.model.agent.ChatbotCreateAgentRequest
import io.element.android.libraries.chatbot.api.model.agent.ChatbotGetAgentProvidersResponse
import io.element.android.libraries.chatbot.api.model.agent.ChatbotListAgentRoomsResponse
import io.element.android.libraries.chatbot.api.model.agent.ChatbotListAgentsResponse
import io.element.android.libraries.chatbot.api.model.agent.ChatbotUpdateAgentRequest
import io.element.android.libraries.chatbot.api.model.analytics.AnalyticsTokensResponse
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
import io.element.android.libraries.chatbot.api.model.storage.ChatbotPresignedUpload
import io.element.android.libraries.chatbot.api.model.storage.ChatbotStsTokenRequest
import io.element.android.libraries.chatbot.api.model.storage.ChatbotStsTokenResponse
import io.element.android.libraries.chatbot.api.model.skills.ChatbotAgentSkillItem
import io.element.android.libraries.chatbot.api.model.skills.ChatbotCreateUserSkillResponse
import io.element.android.libraries.chatbot.api.model.skills.ChatbotDeleteUserSkillResponse
import io.element.android.libraries.chatbot.api.model.skills.ChatbotGetUserSkillResponse
import io.element.android.libraries.chatbot.api.model.skills.ChatbotListAgentSkillsResponse
import io.element.android.libraries.chatbot.api.model.skills.ChatbotListPublicSkillsResponse
import io.element.android.libraries.chatbot.api.model.skills.ChatbotListRoomAgentSkillsResponse
import io.element.android.libraries.chatbot.api.model.skills.ChatbotListUserSkillsResponse
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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer

internal class DefaultChatbotApiService(
    private val httpClient: ChatbotHttpClient,
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

    override suspend fun listUserSkills(visibility: ChatbotSkillVisibility?): Result<List<ChatbotUserSkill>> {
        val visibilityValue = visibility?.name?.lowercase()
        return httpClient.requestJson<ChatbotListUserSkillsResponse>("/chatbot/v1/skills${ChatbotUrlBuilder.query(mapOf("visibility" to visibilityValue))}", ChatbotHttpMethod.GET)
            .map { it.skills }
    }

    override suspend fun listPublicSkills(page: Int, pageSize: Int, search: String?): Result<ChatbotListPublicSkillsResponse> =
        httpClient.requestJson(
            "/chatbot/v1/skills/public${ChatbotUrlBuilder.query(mapOf("page" to page.toString(), "page_size" to pageSize.toString(), "search" to search?.takeIf { it.isNotEmpty() }))}",
            ChatbotHttpMethod.GET
        )

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

    private suspend fun rawUnit(path: String, method: ChatbotHttpMethod, body: String? = null): Result<Unit> =
        httpClient.requestRaw(path, method, body).map { }

    private inline fun <reified T> encode(value: T): String = ChatbotJson.encode(value)

    private fun path(value: String): String = ChatbotUrlBuilder.path("{value}", mapOf("value" to value))

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

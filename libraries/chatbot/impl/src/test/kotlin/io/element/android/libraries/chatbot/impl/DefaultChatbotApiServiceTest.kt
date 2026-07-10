/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbot.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.chatbot.api.ChatbotApiError
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgent
import io.element.android.libraries.chatbot.api.model.approvals.ChatbotApprovalStatus
import io.element.android.libraries.chatbot.api.model.schedules.ChatbotCreateScheduleRequest
import io.element.android.libraries.chatbot.api.model.skills.ChatbotUserSkill
import io.element.android.libraries.chatbot.api.model.skills.ChatbotSkillListFilters
import io.element.android.libraries.chatbot.api.model.skills.ChatbotSkillVisibility
import io.element.android.libraries.chatbot.api.model.voices.ChatbotUploadVoiceProfileRequest
import io.element.android.libraries.matrix.test.FakeMatrixClient
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class DefaultChatbotApiServiceTest {
    private lateinit var server: MockWebServer
    private lateinit var service: DefaultChatbotApiService

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val okHttpClient = OkHttpClient()
        service = DefaultChatbotApiService(
            ChatbotHttpClient(
                baseUrl = server.url("/").toString(),
                matrixClient = FakeMatrixClient(),
                okHttpClient = okHttpClient,
                tokenProvider = ChatbotAccessTokenProvider { "mx-token" },
            ),
            okHttpClient = okHttpClient,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `listAgents - accepts direct array response`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""[{"bot_name":"helper"}]"""))

        val agents = service.listAgents().getOrThrow()

        assertThat(agents).containsExactly(ChatbotAgent(botName = "helper"))
        assertThat(server.takeRequest().path).isEqualTo("/chatbot/v1/agents")
    }

    @Test
    fun `listAgents - accepts wrapped response`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"agents":[{"bot_name":"helper"}]}"""))

        val agents = service.listAgents().getOrThrow()

        assertThat(agents).containsExactly(ChatbotAgent(botName = "helper"))
    }

    @Test
    fun `listAgents - maps invalid response to redacted decoding error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"api_key":"secret"}"""))

        val error = service.listAgents().exceptionOrNull()

        assertThat(error).isInstanceOf(ChatbotApiError.DecodingError::class.java)
        val decodingError = error as ChatbotApiError.DecodingError
        assertThat(decodingError.bodySnippet).contains("[REDACTED]")
        assertThat(decodingError.bodySnippet).doesNotContain("secret")
    }

    @Test
    fun `abort APIs - send the scoped identifiers to Synapse`() = runTest {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(200).setBody("{}")) }

        service.abortRun("stream-1", "Stopped by user").getOrThrow()
        service.abortRoomAgent("!room:example.org", "@agent:example.org", null).getOrThrow()
        service.abortAgent("@agent:example.org", null).getOrThrow()

        val runRequest = server.takeRequest()
        assertThat(runRequest.path).isEqualTo("/chatbot/v1/abort/run")
        assertThat(runRequest.body.readUtf8()).isEqualTo("""{"stream_id":"stream-1","reason":"Stopped by user"}""")

        val roomRequest = server.takeRequest()
        assertThat(roomRequest.path).isEqualTo("/chatbot/v1/abort/room")
        assertThat(roomRequest.body.readUtf8()).isEqualTo("""{"room_id":"!room:example.org","agent_id":"@agent:example.org"}""")

        val agentRequest = server.takeRequest()
        assertThat(agentRequest.path).isEqualTo("/chatbot/v1/abort/agent")
        assertThat(agentRequest.body.readUtf8()).isEqualTo("""{"agent_id":"@agent:example.org"}""")
    }

    @Test
    fun `listAgentSkills - accepts wrapped and direct response shapes`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"skills":[{"skill_id":"skill-1","name":"Search"}]}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""[{"id":"skill-2","name":"Calendar"}]"""))

        val wrapped = service.listAgentSkills("helper").getOrThrow()
        val direct = service.listAgentSkills("helper").getOrThrow()

        assertThat(wrapped).containsExactly(ChatbotUserSkill(id = "skill-1", name = "Search", originalSkillId = "skill-1"))
        assertThat(direct).containsExactly(ChatbotUserSkill(id = "skill-2", name = "Calendar"))
    }

    @Test
    fun `listAgentSkills - returns empty list for unknown iOS-compatible fallback shape`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"unexpected":true}"""))

        assertThat(service.listAgentSkills("helper").getOrThrow()).isEmpty()
    }

    @Test
    fun `listUserSkills - sends visibility only`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"skills":[]}"""))

        service.listUserSkills(visibility = ChatbotSkillVisibility.Private).getOrThrow()

        assertThat(server.takeRequest().path)
            .isEqualTo("/chatbot/v1/skills?visibility=private")
    }

    @Test
    fun `listPublicSkills - sends canonical pageSize and structured filters`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"skills":[],"total":0}"""))

        service.listPublicSkills(
            page = 2,
            pageSize = 30,
            filters = ChatbotSkillListFilters(
                search = "qa",
                categorySlug = "Testing",
                sourceSlug = "GitHub",
                tagSlugs = listOf("browser"),
            ),
        ).getOrThrow()

        assertThat(server.takeRequest().path)
            .isEqualTo("/api/skills/public?page=2&pageSize=30&search=qa&categorySlugs=Testing&sourceSlugs=GitHub&tagSlugs=browser&tagMode=any")
    }

    @Test
    fun `listPublicSkillCategories - decodes public category taxonomy`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"categories":[{"id":1,"name":"Testing","slug":"testing","sortOrder":10,"parentId":null,"categoryType":1}]}"""
            )
        )

        val response = service.listPublicSkillCategories().getOrThrow()

        assertThat(server.takeRequest().path).isEqualTo("/api/skills/public/categories")
        assertThat(response.categories.single().name).isEqualTo("Testing")
        assertThat(response.categories.single().slug).isEqualTo("testing")
    }

    @Test
    fun `listPublicSkillTags - decodes public tag taxonomy`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"tags":[{"id":2,"name":"Browser","slug":"browser","sortOrder":20}]}"""
            )
        )

        val response = service.listPublicSkillTags().getOrThrow()

        assertThat(server.takeRequest().path).isEqualTo("/api/skills/public/tags")
        assertThat(response.tags.single().name).isEqualTo("Browser")
        assertThat(response.tags.single().slug).isEqualTo("browser")
    }

    @Test
    fun `getUserSkill - decodes camelCase detail file URL fields`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"skill":{"id":"skill","name":"Skill"},"presignedUrls":["https://download.example/SKILL.md"],"preuploadUrls":["https://upload.example/SKILL.md"]}"""
            )
        )

        val response = service.getUserSkill("skill").getOrThrow()

        assertThat(response.presignedUrls).containsExactly("https://download.example/SKILL.md")
        assertThat(response.preuploadUrls).containsExactly("https://upload.example/SKILL.md")
    }

    @Test
    fun `presignedUploadUrls - accepts iOS response shapes`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"uploads":[{"url":"https://upload.example/one","filepath":"one.png","s3_key":"skills/one.png"}]}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""["https://upload.example/two"]"""))

        val wrapped = service.presignedUploadUrls(JsonObject(emptyMap())).getOrThrow()
        val direct = service.presignedUploadUrls(JsonObject(emptyMap())).getOrThrow()

        assertThat(wrapped.single().url).isEqualTo("https://upload.example/one")
        assertThat(wrapped.single().filepath).isEqualTo("one.png")
        assertThat(wrapped.single().s3Key).isEqualTo("skills/one.png")
        assertThat(direct.single().url).isEqualTo("https://upload.example/two")
    }

    @Test
    fun `getStsToken - posts iOS request body and decodes response`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"credentials":{"accessKeyId":"access","secretAccessKey":"secret"},"s3Config":{"bucket":"bucket"},"minioMode":false}"""))

        val response = service.getStsToken("skills", 300).getOrThrow()

        assertThat(response.credentials.accessKeyId).isEqualTo("access")
        assertThat(response.s3Config?.bucket).isEqualTo("bucket")
        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/chatbot/v1/storage/sts_token")
        assertThat(request.body.readUtf8()).contains("\"duration_seconds\":300")
    }

    @Test
    fun `createSchedule - fails when server omits eb schedule id`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"success":true}"""))

        val error = service.createSchedule(aScheduleRequest()).exceptionOrNull()

        assertThat(error).isInstanceOf(ChatbotApiError.HttpError::class.java)
        assertThat((error as ChatbotApiError.HttpError).body).isEqualTo("schedule not created on server")
    }

    @Test
    fun `createSchedule - returns response when server provides eb schedule id`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"success":true,"eb_schedule_id":"schedule-1"}"""))

        val response = service.createSchedule(aScheduleRequest()).getOrThrow()

        assertThat(response.ebScheduleId).isEqualTo("schedule-1")
        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/chatbot/v1/schedules")
        assertThat(request.body.readUtf8()).contains("!room:example")
    }

    @Test
    fun `getRoomWorkingMemory - encodes room id path segment`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"working_memory":"note"}"""))

        assertThat(service.getRoomWorkingMemory("!room:example").getOrThrow()).isEqualTo("note")
        assertThat(server.takeRequest().path).isEqualTo("/chatbot/v1/rooms/%21room%3Aexample/working-memory")
    }

    @Test
    fun `approval endpoints - use chatbot approval routes and encode approval id`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(approvalJson(status = "pending")))
        server.enqueue(MockResponse().setResponseCode(200).setBody(approvalJson(status = "approved")))
        server.enqueue(MockResponse().setResponseCode(200).setBody(approvalJson(status = "rejected")))

        assertThat(service.getApproval("appr/test").getOrThrow().status).isEqualTo(ChatbotApprovalStatus.Pending)
        assertThat(service.approveApproval("appr/test").getOrThrow().status).isEqualTo(ChatbotApprovalStatus.Approved)
        assertThat(service.rejectApproval("appr/test").getOrThrow().status).isEqualTo(ChatbotApprovalStatus.Rejected)

        assertThat(server.takeRequest().path).isEqualTo("/chatbot/v1/approvals/appr%2Ftest")
        assertThat(server.takeRequest().path).isEqualTo("/chatbot/v1/approvals/appr%2Ftest/approve")
        assertThat(server.takeRequest().path).isEqualTo("/chatbot/v1/approvals/appr%2Ftest/reject")
    }

    @Test
    fun `vault endpoints - use chatbot vault route and key path segment`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"items":[{"id":"id-1","key":"API_KEY"}]}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"item":{"id":"id-1","key":"API_KEY"},"value":"secret"}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{}"""))

        service.listVault().getOrThrow()
        service.getVaultValue("API/KEY").getOrThrow()
        service.createVaultEntry("API_KEY", "secret", "desc").getOrThrow()
        service.updateVaultEntry("API/KEY", "secret", "desc").getOrThrow()
        service.deleteVaultEntry("API/KEY").getOrThrow()

        assertThat(server.takeRequest().path).isEqualTo("/chatbot/v1/vault")
        assertThat(server.takeRequest().path).isEqualTo("/chatbot/v1/vault/API%2FKEY")
        assertThat(server.takeRequest().path).isEqualTo("/chatbot/v1/vault")
        assertThat(server.takeRequest().path).isEqualTo("/chatbot/v1/vault/API%2FKEY")
        assertThat(server.takeRequest().path).isEqualTo("/chatbot/v1/vault/API%2FKEY")
    }

    @Test
    fun `uploadVoiceProfile - uses iOS upload clone route and body`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":"voice-1","provider":"elevenlabs","providerVoiceId":"generated","displayName":"Recorded","sourceType":"voice_clone","visibility":"private","status":"available"}"""))

        val response = service.uploadVoiceProfile(
            ChatbotUploadVoiceProfileRequest(
                displayName = "Recorded",
                description = null,
                audioBase64 = "YWJj",
                filename = "voice.m4a",
                mimeType = "audio/m4a",
                removeBackgroundNoise = true,
            )
        ).getOrThrow()

        assertThat(response.id).isEqualTo("voice-1")
        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/api/voices/profiles/upload-clone")
        val body = request.body.readUtf8()
        assertThat(body).contains("\"displayName\":\"Recorded\"")
        assertThat(body).contains("\"audioBase64\":\"YWJj\"")
        assertThat(body).contains("\"filename\":\"voice.m4a\"")
        assertThat(body).contains("\"mimeType\":\"audio/m4a\"")
        assertThat(body).contains("\"removeBackgroundNoise\":true")
    }

    @Test
    fun `updateWebhookTriggerStatus - maps enabled flag to enable and disable endpoints`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"triggerId":"trigger-1","status":"enabled"}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"triggerId":"trigger-1","status":"disabled"}"""))

        service.updateWebhookTriggerStatus("trigger-1", enabled = true).getOrThrow()
        service.updateWebhookTriggerStatus("trigger-1", enabled = false).getOrThrow()

        assertThat(server.takeRequest().path).isEqualTo("/api/webhook-triggers/trigger-1/enable")
        assertThat(server.takeRequest().path).isEqualTo("/api/webhook-triggers/trigger-1/disable")
    }

    @Test
    fun `listToolkits - decodes iOS connector payload shape`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"items":[{"name":"GitHub","slug":"github","categories":[],"authSchemes":["oauth"],"noAuth":false,"connected":true,"connectedAccountId":"account"}],"totalItems":1,"totalPages":1,"nextCursor":null}"""))

        val response = service.listToolkits(search = "git", category = null, cursor = null, limit = 10).getOrThrow()

        assertThat(response.items.single().slug).isEqualTo("github")
        assertThat(response.items.single().connectedAccountId).isEqualTo("account")
        assertThat(server.takeRequest().path).isEqualTo("/api/integrations/composio/toolkits?search=git&limit=10")
    }

    @Test
    fun `getBalance - decodes iOS credit payload shape`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"user_id":"@alice:server.org","balance_micros":"1000000","balance_usd":"1.00"}"""))

        val balance = service.getBalance().getOrThrow()

        assertThat(balance.userId).isEqualTo("@alice:server.org")
        assertThat(balance.balanceMicros).isEqualTo("1000000")
    }

    @Test
    fun `listWebhookTriggers - decodes iOS webhook payload shape`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""[{"triggerId":"trigger-1","agentId":"agent-1","name":"GitHub","eventTypes":["push"],"actionPrompt":"summarize","roomId":"!room:example","status":"provider_disabled"}]"""))

        val triggers = service.listWebhookTriggers(agentId = "agent-1", source = null, roomId = null, status = null).getOrThrow()

        assertThat(triggers.single().triggerId).isEqualTo("trigger-1")
        assertThat(triggers.single().status.name).isEqualTo("ProviderDisabled")
    }

    private fun aScheduleRequest(): ChatbotCreateScheduleRequest =
        ChatbotCreateScheduleRequest(
            roomId = "!room:example",
            agentId = "agent-1",
            name = "Morning ping",
            cron = "0 9 * * *",
            action = "ping",
            timezone = "UTC",
        )
}

private fun approvalJson(status: String): String =
    """
    {
      "approval_id": "appr/test",
      "action": "agent.join_room",
      "status": "$status",
      "requester_user_id": "@alice:example.org",
      "created_at": 1780560400000,
      "updated_at": 1780560400000,
      "resolved_at": null,
      "agent_id": "@agent:example.org",
      "agent_name": "Agent",
      "room_id": "!room:example.org"
    }
    """.trimIndent()

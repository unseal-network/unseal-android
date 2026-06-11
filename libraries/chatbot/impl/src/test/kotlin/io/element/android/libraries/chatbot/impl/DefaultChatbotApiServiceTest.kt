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
import io.element.android.libraries.chatbot.api.model.schedules.ChatbotCreateScheduleRequest
import io.element.android.libraries.chatbot.api.model.skills.ChatbotUserSkill
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

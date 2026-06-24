/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.agentmanagement.impl.channels

import app.cash.turbine.TurbineTestContext
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.androidutils.clipboard.FakeClipboardHelper
import io.element.android.libraries.chatbot.api.model.channels.ChatbotChannelConnectBody
import io.element.android.libraries.chatbot.api.model.channels.ChatbotChannelPlatform
import io.element.android.libraries.chatbot.api.model.channels.ChatbotChannelSummary
import io.element.android.libraries.chatbot.api.model.channels.ChatbotConnectChannelResponse
import io.element.android.libraries.chatbot.test.FakeChatbotApiService
import io.element.android.libraries.chatbot.test.FakeChatbotApiServiceFactory
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class AgentChannelsPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `present - loads channels on appear`() = runTest {
        val service = FakeChatbotApiService().apply {
            listAgentChannelsResult = {
                Result.success(
                    listOf(
                        ChatbotChannelSummary(installationId = "i1", platform = ChatbotChannelPlatform.Telegram, status = "active", label = "alpha_bot"),
                    )
                )
            }
        }
        val presenter = createPresenter(service)
        presenter.test {
            awaitItem().eventSink(AgentChannelsEvents.OnAppear)
            val loaded = awaitStateWhere { !it.isLoading && it.channels.isNotEmpty() }
            assertThat(loaded.channels.first().label).isEqualTo("alpha_bot")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - telegram connect closes sheet`() = runTest {
        val service = FakeChatbotApiService().apply {
            connectAgentChannelResult = { _, _ ->
                Result.success(ChatbotConnectChannelResponse(installationId = "i1", platform = ChatbotChannelPlatform.Telegram))
            }
        }
        val presenter = createPresenter(service)
        presenter.test {
            val initial = awaitItem()
            initial.eventSink(AgentChannelsEvents.OpenAdd)
            awaitStateWhere { it.sheet != null }.eventSink(AgentChannelsEvents.SetBotToken("123:ABC"))
            awaitStateWhere { it.sheet?.botToken == "123:ABC" }.eventSink(AgentChannelsEvents.Connect)
            val afterConnect = awaitStateWhere { it.sheet == null }
            assertThat(afterConnect.sheet).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - wecom connect shows callback panel`() = runTest {
        val service = FakeChatbotApiService().apply {
            connectAgentChannelResult = { _, body ->
                assertThat(body).isInstanceOf(ChatbotChannelConnectBody.WeCom::class.java)
                Result.success(ChatbotConnectChannelResponse(installationId = "i2", platform = ChatbotChannelPlatform.WeCom, callbackUrl = "https://h.test/cb"))
            }
        }
        val presenter = createPresenter(service)
        presenter.test {
            awaitItem().eventSink(AgentChannelsEvents.OpenAdd)
            awaitStateWhere { it.sheet != null }.eventSink(AgentChannelsEvents.SetPlatform(ChatbotChannelPlatform.WeCom))
            awaitStateWhere { it.sheet?.platform == ChatbotChannelPlatform.WeCom }.eventSink(AgentChannelsEvents.GenerateToken)
            awaitStateWhere { it.sheet?.wecomToken?.isNotEmpty() == true }.eventSink(AgentChannelsEvents.GenerateAesKey)
            awaitStateWhere { it.sheet?.wecomAesKey?.length == WECOM_AES_KEY_LENGTH }.eventSink(AgentChannelsEvents.Connect)
            val withCallback = awaitStateWhere { it.sheet?.callbackUrl != null }
            assertThat(withCallback.sheet?.callbackUrl).isEqualTo("https://h.test/cb")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - confirm delete calls disconnect`() = runTest {
        var disconnected: Pair<String, String>? = null
        var deleted = false
        val channel = ChatbotChannelSummary(installationId = "i9", platform = ChatbotChannelPlatform.WeCom, status = "active", label = "wecom-x")
        val service = FakeChatbotApiService().apply {
            listAgentChannelsResult = { if (deleted) Result.success(emptyList()) else Result.success(listOf(channel)) }
            disconnectAgentChannelResult = { agentId, installationId ->
                disconnected = agentId to installationId
                deleted = true
                Result.success(Unit)
            }
        }
        val presenter = createPresenter(service)
        presenter.test {
            awaitItem().eventSink(AgentChannelsEvents.OnAppear)
            awaitStateWhere { it.channels.isNotEmpty() }.eventSink(AgentChannelsEvents.ConfirmDelete("i9"))
            awaitStateWhere { it.channels.isEmpty() }
            assertThat(disconnected?.second).isEqualTo("i9")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - feishu connect enters panel without closing`() = runTest {
        val service = FakeChatbotApiService().apply {
            connectAgentChannelResult = { _, body ->
                assertThat(body).isEqualTo(ChatbotChannelConnectBody.Feishu)
                Result.success(ChatbotConnectChannelResponse(installationId = "fs1", platform = ChatbotChannelPlatform.Feishu, status = "pending", qrUrl = "https://accounts.feishu.cn/x?user_code=A"))
            }
            getAgentChannelResult = { _, installationId ->
                Result.success(ChatbotChannelSummary(installationId = installationId, platform = ChatbotChannelPlatform.Feishu, status = "pending", label = "", qrUrl = "https://accounts.feishu.cn/x?user_code=A"))
            }
        }
        val presenter = createPresenter(service)
        presenter.test {
            awaitItem().eventSink(AgentChannelsEvents.OpenAdd)
            awaitStateWhere { it.sheet != null }.eventSink(AgentChannelsEvents.SetPlatform(ChatbotChannelPlatform.Feishu))
            awaitStateWhere { it.sheet?.platform == ChatbotChannelPlatform.Feishu }.eventSink(AgentChannelsEvents.Connect)
            val panel = awaitStateWhere { it.sheet?.isFeishuPanel == true }
            assertThat(panel.sheet?.feishuQrUrl).isEqualTo("https://accounts.feishu.cn/x?user_code=A")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - feishu poll fills late qrUrl then closes on active`() = runTest {
        var tick = 0
        val service = FakeChatbotApiService().apply {
            connectAgentChannelResult = { _, _ ->
                Result.success(ChatbotConnectChannelResponse(installationId = "fs2", platform = ChatbotChannelPlatform.Feishu, status = "pending", qrUrl = null))
            }
            getAgentChannelResult = { _, installationId ->
                tick += 1
                if (tick == 1) {
                    Result.success(ChatbotChannelSummary(installationId = installationId, platform = ChatbotChannelPlatform.Feishu, status = "pending", label = "", qrUrl = "https://accounts.feishu.cn/x?user_code=B"))
                } else {
                    Result.success(ChatbotChannelSummary(installationId = installationId, platform = ChatbotChannelPlatform.Feishu, status = "active", label = "Acme"))
                }
            }
        }
        val presenter = createPresenter(service)
        presenter.test {
            awaitItem().eventSink(AgentChannelsEvents.OpenAdd)
            awaitStateWhere { it.sheet != null }.eventSink(AgentChannelsEvents.SetPlatform(ChatbotChannelPlatform.Feishu))
            awaitStateWhere { it.sheet?.platform == ChatbotChannelPlatform.Feishu }.eventSink(AgentChannelsEvents.Connect)
            awaitStateWhere { it.sheet?.feishuQrUrl == "https://accounts.feishu.cn/x?user_code=B" }
            awaitStateWhere { it.sheet == null }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - feishu poll error sets sheet error`() = runTest {
        val service = FakeChatbotApiService().apply {
            connectAgentChannelResult = { _, _ ->
                Result.success(ChatbotConnectChannelResponse(installationId = "fs3", platform = ChatbotChannelPlatform.Feishu, status = "pending", qrUrl = "https://accounts.feishu.cn/x?user_code=C"))
            }
            getAgentChannelResult = { _, installationId ->
                Result.success(ChatbotChannelSummary(installationId = installationId, platform = ChatbotChannelPlatform.Feishu, status = "error", label = ""))
            }
        }
        val presenter = createPresenter(service)
        presenter.test {
            awaitItem().eventSink(AgentChannelsEvents.OpenAdd)
            awaitStateWhere { it.sheet != null }.eventSink(AgentChannelsEvents.SetPlatform(ChatbotChannelPlatform.Feishu))
            awaitStateWhere { it.sheet?.platform == ChatbotChannelPlatform.Feishu }.eventSink(AgentChannelsEvents.Connect)
            val errored = awaitStateWhere { it.sheet?.error != null }
            assertThat(errored.sheet?.isFeishuPanel).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createPresenter(service: FakeChatbotApiService) = AgentChannelsPresenter(
        agentId = "@bot:server",
        matrixClient = FakeMatrixClient(),
        chatbotApiServiceFactory = FakeChatbotApiServiceFactory(service),
        clipboardHelper = FakeClipboardHelper(),
    )
}

private suspend fun TurbineTestContext<AgentChannelsState>.awaitStateWhere(
    predicate: (AgentChannelsState) -> Boolean,
): AgentChannelsState {
    while (true) {
        val state = awaitItem()
        if (predicate(state)) {
            return state
        }
    }
}

/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.agentmanagement.impl.channels

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.features.agentmanagement.impl.R
import io.element.android.libraries.androidutils.clipboard.ClipboardHelper
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.chatbot.api.ChatbotApiServiceFactory
import io.element.android.libraries.chatbot.api.model.channels.ChatbotChannelConnectBody
import io.element.android.libraries.chatbot.api.model.channels.ChatbotChannelPlatform
import io.element.android.libraries.chatbot.api.model.channels.ChatbotChannelSummary
import io.element.android.libraries.chatbot.api.model.channels.ChatbotConnectChannelResponse
import io.element.android.libraries.matrix.api.MatrixClient
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.security.SecureRandom

private const val RANDOM_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
private const val FEISHU_POLL_DELAY_MILLIS = 2_000L
private const val FEISHU_MAX_POLL_ATTEMPTS = 150

private fun randomString(length: Int): String {
    val random = SecureRandom()
    return buildString { repeat(length) { append(RANDOM_CHARS[random.nextInt(RANDOM_CHARS.length)]) } }
}

@AssistedInject
class AgentChannelsPresenter(
    @Assisted private val agentId: String,
    private val matrixClient: MatrixClient,
    private val chatbotApiServiceFactory: ChatbotApiServiceFactory,
    private val clipboardHelper: ClipboardHelper,
) : Presenter<AgentChannelsState> {
    @AssistedFactory
    interface Factory {
        fun create(agentId: String): AgentChannelsPresenter
    }

    @Composable
    override fun present(): AgentChannelsState {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        var channels by remember { mutableStateOf(emptyList<ChatbotChannelSummary>()) }
        var isLoading by remember { mutableStateOf(true) }
        var error by remember { mutableStateOf<String?>(null) }
        var sheet by remember { mutableStateOf<ChannelSheetState?>(null) }
        var pendingDelete by remember { mutableStateOf<ChatbotChannelSummary?>(null) }
        var hasLoadedOnce by remember { mutableStateOf(false) }

        suspend fun api() = chatbotApiServiceFactory.createForUnsealApi(matrixClient)
        fun string(resId: Int, vararg args: Any): String = context.getString(resId, *args)
        fun errorString(resId: Int, vararg args: Any): String = string(resId, *args)

        fun loadChannels(isInitial: Boolean) {
            if (isInitial && hasLoadedOnce) return
            coroutineScope.launch {
                if (isInitial) isLoading = true
                api().listAgentChannels(agentId)
                    .onSuccess { error = null; channels = it }
                    .onFailure { channels = emptyList(); error = errorString(R.string.agent_channels_error_load) }
                isLoading = false
                hasLoadedOnce = true
            }
        }

        fun validateWecom(current: ChannelSheetState): String? = when {
            current.wecomToken.isBlank() -> string(R.string.agent_channels_error_token_required)
            current.wecomAesKey.length != WECOM_AES_KEY_LENGTH -> string(R.string.agent_channels_error_aes_key_length, WECOM_AES_KEY_LENGTH)
            else -> null
        }

        fun loadForEdit(installationId: String) {
            coroutineScope.launch {
                api().getAgentChannelCredentials(agentId, installationId)
                    .onSuccess { creds ->
                        sheet = sheet?.copy(
                            platform = ChatbotChannelPlatform.WeCom,
                            wecomToken = creds.token,
                            wecomAesKey = creds.encodingAESKey,
                            connectedToken = creds.token,
                            connectedAesKey = creds.encodingAESKey,
                            callbackUrl = creds.callbackUrl,
                            installationId = creds.installationId,
                            busy = false,
                        )
                    }
                    .onFailure { sheet = sheet?.copy(busy = false, error = errorString(R.string.agent_channels_error_load_channel)) }
            }
        }

        fun connect() {
            val current = sheet ?: return
            if (current.platform == ChatbotChannelPlatform.Feishu) {
                sheet = current.copy(busy = true, error = null)
                coroutineScope.launch {
                    api().connectAgentChannel(agentId, ChatbotChannelConnectBody.Feishu)
                        .onSuccess { res ->
                            sheet = sheet?.copy(busy = false, feishuInstallationId = res.installationId, feishuQrUrl = res.qrUrl)
                        }
                        .onFailure { sheet = sheet?.copy(busy = false, error = errorString(R.string.agent_channels_error_connect_feishu)) }
                }
                return
            }
            val body: ChatbotChannelConnectBody = if (current.platform == ChatbotChannelPlatform.Telegram) {
                val trimmed = current.botToken.trim()
                if (trimmed.isEmpty()) {
                    sheet = current.copy(error = string(R.string.agent_channels_error_bot_token_required))
                    return
                }
                ChatbotChannelConnectBody.Telegram(trimmed)
            } else if (current.platform == ChatbotChannelPlatform.Discord) {
                val botToken = current.discordBotToken.trim()
                val publicKey = current.discordPublicKey.trim()
                val applicationId = current.discordApplicationId.trim()
                if (botToken.isEmpty() || publicKey.isEmpty() || applicationId.isEmpty()) {
                    sheet = current.copy(error = string(R.string.agent_channels_error_discord_required))
                    return
                }
                ChatbotChannelConnectBody.Discord(botToken, publicKey, applicationId)
            } else {
                val invalid = validateWecom(current)
                if (invalid != null) {
                    sheet = current.copy(error = invalid)
                    return
                }
                ChatbotChannelConnectBody.WeCom(current.wecomToken, current.wecomAesKey)
            }
            sheet = current.copy(busy = true, error = null)
            coroutineScope.launch {
                api().connectAgentChannel(agentId, body)
                    .onSuccess { res: ChatbotConnectChannelResponse ->
                        val callbackUrl = res.callbackUrl
                        if (res.platform == ChatbotChannelPlatform.WeCom && callbackUrl != null) {
                            sheet = sheet?.copy(
                                busy = false,
                                callbackUrl = callbackUrl,
                                installationId = res.installationId,
                                connectedToken = current.wecomToken,
                                connectedAesKey = current.wecomAesKey,
                            )
                        } else {
                            sheet = null
                            loadChannels(isInitial = false)
                        }
                    }
                    .onFailure { sheet = sheet?.copy(busy = false, error = errorString(R.string.agent_channels_error_connect_channel)) }
            }
        }

        fun updateCreds() {
            val current = sheet ?: return
            val invalid = validateWecom(current)
            if (invalid != null) {
                sheet = current.copy(error = invalid)
                return
            }
            val installationId = current.installationId ?: current.editInstallationId ?: return
            sheet = current.copy(busy = true, error = null)
            coroutineScope.launch {
                api().updateAgentChannel(agentId, installationId, current.wecomToken, current.wecomAesKey)
                    .onSuccess { res ->
                        sheet = sheet?.copy(
                            busy = false,
                            callbackUrl = res.callbackUrl ?: current.callbackUrl,
                            connectedToken = current.wecomToken,
                            connectedAesKey = current.wecomAesKey,
                        )
                    }
                    .onFailure { sheet = sheet?.copy(busy = false, error = errorString(R.string.agent_channels_error_update_credentials)) }
            }
        }

        fun delete(installationId: String) {
            coroutineScope.launch {
                api().disconnectAgentChannel(agentId, installationId)
                    .onSuccess { loadChannels(isInitial = false) }
                    .onFailure { error = errorString(R.string.agent_channels_error_remove) }
            }
        }

        fun handleEvent(event: AgentChannelsEvents) {
            when (event) {
                AgentChannelsEvents.OnAppear -> loadChannels(isInitial = true)
                AgentChannelsEvents.Refresh -> loadChannels(isInitial = false)
                AgentChannelsEvents.OpenAdd -> sheet = newSheet(editInstallationId = null)
                is AgentChannelsEvents.OpenEdit -> {
                    sheet = newSheet(editInstallationId = event.installationId).copy(platform = ChatbotChannelPlatform.WeCom, busy = true)
                    loadForEdit(event.installationId)
                }
                AgentChannelsEvents.CloseSheet -> sheet = null
                is AgentChannelsEvents.RequestDelete -> pendingDelete = event.channel
                AgentChannelsEvents.CancelDelete -> pendingDelete = null
                is AgentChannelsEvents.ConfirmDelete -> {
                    pendingDelete = null
                    delete(event.installationId)
                }
                is AgentChannelsEvents.SetPlatform -> sheet = sheet?.copy(platform = event.platform, error = null)
                is AgentChannelsEvents.SetBotToken -> sheet = sheet?.copy(botToken = event.value)
                is AgentChannelsEvents.SetWecomToken -> sheet = sheet?.copy(wecomToken = event.value)
                is AgentChannelsEvents.SetWecomAesKey -> sheet = sheet?.copy(wecomAesKey = event.value)
                is AgentChannelsEvents.SetDiscordBotToken -> sheet = sheet?.copy(discordBotToken = event.value)
                is AgentChannelsEvents.SetDiscordPublicKey -> sheet = sheet?.copy(discordPublicKey = event.value)
                is AgentChannelsEvents.SetDiscordApplicationId -> sheet = sheet?.copy(discordApplicationId = event.value)
                AgentChannelsEvents.GenerateToken -> sheet = sheet?.copy(wecomToken = randomString(WECOM_TOKEN_LENGTH))
                AgentChannelsEvents.GenerateAesKey -> sheet = sheet?.copy(wecomAesKey = randomString(WECOM_AES_KEY_LENGTH))
                AgentChannelsEvents.Connect -> connect()
                AgentChannelsEvents.UpdateCreds -> updateCreds()
                AgentChannelsEvents.FinishSheet -> {
                    sheet = null
                    loadChannels(isInitial = false)
                }
                is AgentChannelsEvents.Copy -> clipboardHelper.copyPlainText(event.value)
                AgentChannelsEvents.ClearError -> error = null
            }
        }

        LaunchedEffect(sheet?.feishuInstallationId) {
            val installationId = sheet?.feishuInstallationId ?: return@LaunchedEffect
            repeat(FEISHU_MAX_POLL_ATTEMPTS) {
                delay(FEISHU_POLL_DELAY_MILLIS)
                val summary = api().getAgentChannel(agentId, installationId).getOrNull()
                if (summary != null) {
                    if (sheet?.feishuQrUrl == null && summary.qrUrl != null) {
                        sheet = sheet?.copy(feishuQrUrl = summary.qrUrl)
                    }
                    when (summary.status) {
                        "active" -> {
                            sheet = null
                            loadChannels(isInitial = false)
                            return@LaunchedEffect
                        }
                        "error" -> {
                            sheet = sheet?.copy(error = string(R.string.agent_channels_error_feishu_retry))
                            return@LaunchedEffect
                        }
                    }
                }
            }
            sheet = sheet?.copy(feishuExpired = true)
        }

        return AgentChannelsState(
            channels = channels.toImmutableList(),
            isLoading = isLoading,
            error = error,
            sheet = sheet,
            pendingDelete = pendingDelete,
            eventSink = ::handleEvent,
        )
    }

    private fun newSheet(editInstallationId: String?) = ChannelSheetState(
        editInstallationId = editInstallationId,
        platform = ChatbotChannelPlatform.Telegram,
        botToken = "",
        wecomToken = "",
        wecomAesKey = "",
        discordBotToken = "",
        discordPublicKey = "",
        discordApplicationId = "",
        busy = false,
        error = null,
        callbackUrl = null,
        installationId = null,
        connectedToken = "",
        connectedAesKey = "",
    )
}

/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package io.element.android.features.agentmanagement.impl.channels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.chatbot.api.model.channels.ChatbotChannelPlatform
import io.element.android.libraries.chatbot.api.model.channels.ChatbotChannelSummary
import io.element.android.libraries.qrcode.QrCodeImage
import java.net.URLEncoder

private val TelegramBrand = Color(0xFF229ED9)
private val WeComBrand = Color(0xFF07C160)

private val FeishuBrand = Color(0xFF3370FF)

private fun ChatbotChannelPlatform.brandColor(): Color = when (this) {
    ChatbotChannelPlatform.Telegram -> TelegramBrand
    ChatbotChannelPlatform.WeCom -> WeComBrand
    ChatbotChannelPlatform.Feishu -> FeishuBrand
}

private fun ChatbotChannelPlatform.displayName(): String = when (this) {
    ChatbotChannelPlatform.Telegram -> "Telegram"
    ChatbotChannelPlatform.WeCom -> "WeCom"
    ChatbotChannelPlatform.Feishu -> "飞书"
}

private fun feishuAppLink(verificationUrl: String): String {
    val host = if (verificationUrl.contains("larksuite")) "applink.larksuite.com" else "applink.feishu.cn"
    val encoded = URLEncoder.encode(verificationUrl, "UTF-8")
    return "https://$host/client/web_url/open?url=$encoded"
}

@Composable
fun AgentChannelsView(
    state: AgentChannelsState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { state.eventSink(AgentChannelsEvents.OnAppear) }
    androidx.compose.material3.Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Channels") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) { Icon(CompoundIcons.ChevronLeft(), "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading && state.channels.isEmpty() -> item {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.channels.isEmpty() -> item { EmptyState() }
                else -> items(state.channels, key = { it.installationId }) { channel ->
                    ChannelRow(channel = channel, eventSink = state.eventSink)
                }
            }
            state.error?.let { error ->
                item {
                    Text(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            item { AddChannelButton { state.eventSink(AgentChannelsEvents.OpenAdd) } }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    state.sheet?.let { sheet ->
        AddChannelSheet(sheet = sheet, eventSink = state.eventSink)
    }

    state.pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { state.eventSink(AgentChannelsEvents.CancelDelete) },
            title = { Text("Remove channel") },
            text = { Text("Remove \"${target.label}\"? Inbound messages from this channel will stop.") },
            confirmButton = {
                TextButton(onClick = { state.eventSink(AgentChannelsEvents.ConfirmDelete(target.installationId)) }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { state.eventSink(AgentChannelsEvents.CancelDelete) }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun PlatformBadge(platform: ChatbotChannelPlatform, size: Dp = 36.dp) {
    Box(
        modifier = Modifier.size(size).background(platform.brandColor(), RoundedCornerShape(size * 0.28f)),
        contentAlignment = Alignment.Center,
    ) {
        val icon = if (platform == ChatbotChannelPlatform.Telegram) CompoundIcons.Send() else CompoundIcons.Chat()
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
private fun ChannelRow(channel: ChatbotChannelSummary, eventSink: (AgentChannelsEvents) -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }
    ListItem(
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        leadingContent = { PlatformBadge(channel.platform) },
        headlineContent = { Text(channel.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(channel.platform.displayName(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(CompoundIcons.OverflowVertical(), "Manage", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    if (channel.platform == ChatbotChannelPlatform.WeCom) {
                        DropdownMenuItem(
                            text = { Text("Edit credentials") },
                            leadingIcon = { Icon(CompoundIcons.Edit(), null) },
                            onClick = {
                                menuExpanded = false
                                eventSink(AgentChannelsEvents.OpenEdit(channel.installationId))
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Remove channel", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(CompoundIcons.Delete(), null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menuExpanded = false
                            eventSink(AgentChannelsEvents.RequestDelete(channel))
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier.size(56.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(CompoundIcons.Link(), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
        }
        Text("No channels bound yet", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(
            "Connect a channel so this agent can reply there.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AddChannelButton(onClick: () -> Unit) {
    OutlinedButton(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        onClick = onClick,
    ) {
        Icon(CompoundIcons.Plus(), null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text("Add channel")
    }
}

@Composable
private fun AddChannelSheet(sheet: ChannelSheetState, eventSink: (AgentChannelsEvents) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { eventSink(AgentChannelsEvents.CloseSheet) },
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val title = when {
                sheet.isFeishuPanel -> "Authorize in Feishu"
                sheet.isEditMode -> "Edit WeCom channel"
                sheet.callbackUrl != null -> "Finish WeCom setup"
                else -> "Add a channel"
            }
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)

            when {
                sheet.isFeishuPanel -> FeishuPanel(sheet)
                sheet.isEditMode && sheet.callbackUrl == null -> {
                    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                        if (sheet.error != null) {
                            Text(sheet.error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                        } else {
                            CircularProgressIndicator()
                        }
                    }
                }
                sheet.callbackUrl != null -> CallbackPanel(sheet, eventSink)
                else -> AddForm(sheet, eventSink)
            }
        }
    }
}

@Composable
private fun AddForm(sheet: ChannelSheetState, eventSink: (AgentChannelsEvents) -> Unit) {
    SectionLabel("Platform")
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PlatformCard(ChatbotChannelPlatform.Telegram, sheet.platform == ChatbotChannelPlatform.Telegram) {
            eventSink(AgentChannelsEvents.SetPlatform(ChatbotChannelPlatform.Telegram))
        }
        PlatformCard(ChatbotChannelPlatform.WeCom, sheet.platform == ChatbotChannelPlatform.WeCom) {
            eventSink(AgentChannelsEvents.SetPlatform(ChatbotChannelPlatform.WeCom))
        }
        PlatformCard(ChatbotChannelPlatform.Feishu, sheet.platform == ChatbotChannelPlatform.Feishu) {
            eventSink(AgentChannelsEvents.SetPlatform(ChatbotChannelPlatform.Feishu))
        }
    }

    when (sheet.platform) {
        ChatbotChannelPlatform.Telegram -> {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = sheet.botToken,
                onValueChange = { eventSink(AgentChannelsEvents.SetBotToken(it)) },
                label = { Text("Bot Token") },
                placeholder = { Text("123456:ABC-DEF…") },
                singleLine = true,
            )
            Text("Get this from @BotFather in Telegram.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ChatbotChannelPlatform.WeCom -> {
            GeneratableField("Token", sheet.wecomToken, { eventSink(AgentChannelsEvents.SetWecomToken(it)) }) {
                eventSink(AgentChannelsEvents.GenerateToken)
            }
            GeneratableField("EncodingAESKey", sheet.wecomAesKey, { eventSink(AgentChannelsEvents.SetWecomAesKey(it)) }) {
                eventSink(AgentChannelsEvents.GenerateAesKey)
            }
        }
        ChatbotChannelPlatform.Feishu -> {
            Text(
                "Feishu needs no keys — tap Connect, then approve in the Feishu app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    sheet.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(modifier = Modifier.weight(1f), enabled = !sheet.busy, onClick = { eventSink(AgentChannelsEvents.CloseSheet) }) {
            Text("Cancel")
        }
        Button(modifier = Modifier.weight(1f), enabled = !sheet.busy && sheet.canConnect, onClick = { eventSink(AgentChannelsEvents.Connect) }) {
            Text(if (sheet.busy) "Connecting…" else "Connect")
        }
    }
}

@Composable
private fun CallbackPanel(sheet: ChannelSheetState, eventSink: (AgentChannelsEvents) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp)).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(CompoundIcons.Link(), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(
            "Paste these three values into the WeCom admin console (智能机器人 → 接收消息), then verify there. Updating credentials keeps the same Callback URL.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }

    sheet.callbackUrl?.let { url ->
        SectionLabel("Callback URL")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                modifier = Modifier.weight(1f).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(10.dp),
                text = url,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface,
            )
            CopyButton { eventSink(AgentChannelsEvents.Copy(url)) }
        }
    }

    SectionLabel("Token")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            modifier = Modifier.weight(1f),
            value = sheet.wecomToken,
            onValueChange = { eventSink(AgentChannelsEvents.SetWecomToken(it)) },
            singleLine = true,
        )
        CopyButton { eventSink(AgentChannelsEvents.Copy(sheet.wecomToken)) }
    }

    SectionLabel("EncodingAESKey")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            modifier = Modifier.weight(1f),
            value = sheet.wecomAesKey,
            onValueChange = { eventSink(AgentChannelsEvents.SetWecomAesKey(it)) },
            singleLine = true,
        )
        CopyButton { eventSink(AgentChannelsEvents.Copy(sheet.wecomAesKey)) }
    }

    sheet.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

    if (sheet.credsChanged) {
        Button(modifier = Modifier.fillMaxWidth(), enabled = !sheet.busy, onClick = { eventSink(AgentChannelsEvents.UpdateCreds) }) {
            Text(if (sheet.busy) "Updating…" else "Update")
        }
    } else {
        Button(modifier = Modifier.fillMaxWidth(), enabled = !sheet.busy, onClick = { eventSink(AgentChannelsEvents.FinishSheet) }) {
            Text("Done")
        }
    }
}

@Composable
private fun FeishuPanel(sheet: ChannelSheetState) {
    val uriHandler = LocalUriHandler.current
    var showQr by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PlatformBadge(ChatbotChannelPlatform.Feishu, 60.dp)
        Text("Authorize in Feishu", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(
            "Open Feishu as a workspace admin and approve — it connects automatically in a few seconds.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        when {
            sheet.feishuExpired -> {
                Text(
                    "This link has expired. Close and try again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
            sheet.feishuQrUrl != null -> {
                val qrUrl = sheet.feishuQrUrl
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { uriHandler.openUri(feishuAppLink(qrUrl)) },
                ) {
                    Text("Open in Feishu to authorize")
                }
                TextButton(onClick = { showQr = !showQr }) { Text("Scan with another device") }
                if (showQr) {
                    Box(
                        modifier = Modifier
                            .background(Color.White, RoundedCornerShape(18.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        QrCodeImage(data = qrUrl, modifier = Modifier.size(180.dp))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Text("Waiting for you to approve…", style = MaterialTheme.typography.bodySmall, color = FeishuBrand)
                }
            }
            else -> {
                CircularProgressIndicator()
                Text("Generating a secure link…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun GeneratableField(label: String, value: String, onValueChange: (String) -> Unit, onGenerate: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            modifier = Modifier.weight(1f),
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
        )
        TextButton(onClick = onGenerate) { Text("Generate") }
    }
}

@Composable
private fun CopyButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(CompoundIcons.Copy(), "Copy", tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun RowScope.PlatformCard(platform: ChatbotChannelPlatform, selected: Boolean, onClick: () -> Unit) {
    val borderColor = if (selected) platform.brandColor() else MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = Modifier
            .weight(1f)
            .border(if (selected) 2.dp else 1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PlatformBadge(platform, 44.dp)
        Text(platform.displayName(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun SectionLabel(title: String) {
    Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

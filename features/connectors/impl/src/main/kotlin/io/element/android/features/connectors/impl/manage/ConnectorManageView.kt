/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package io.element.android.features.connectors.impl.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.chatbot.api.model.connectors.ChatbotConnectedAccount
import io.element.android.libraries.chatbot.api.model.connectors.ChatbotConnectedAccountProfile
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@Composable
fun ConnectorManageView(
    state: ConnectorManageState,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        state.eventSink(ConnectorManageEvents.OnAppear)
    }
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("管理 ${state.toolkitName}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { state.eventSink(ConnectorManageEvents.Dismiss) }) {
                        Icon(imageVector = CompoundIcons.ChevronLeft(), contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        enabled = !state.connecting,
                        onClick = { state.eventSink(ConnectorManageEvents.Connect) },
                    ) {
                        if (state.connecting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(imageVector = CompoundIcons.Plus(), contentDescription = "连接新账户")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            state.error?.let { error ->
                ErrorBanner(error = error, onDismiss = { state.eventSink(ConnectorManageEvents.ClearError) })
            }
            when {
                state.isLoading && state.accounts.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.accounts.isEmpty() -> {
                    Text(
                        modifier = Modifier.fillMaxWidth().padding(top = 48.dp, start = 16.dp, end = 16.dp),
                        text = "暂无已连接的账户",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.accounts, key = { it.id }) { account ->
                            ConnectedAccountRow(state = state, account = account)
                        }
                    }
                }
            }
        }
    }

    state.confirmingDisconnectId?.let { accountId ->
        AlertDialog(
            onDismissRequest = { state.eventSink(ConnectorManageEvents.CancelDisconnect) },
            icon = { Icon(imageVector = CompoundIcons.Error(), contentDescription = null) },
            title = { Text("确认断开连接") },
            text = {
                Text(
                    "确定要断开此账户的连接吗？助手将无法再使用它。\n\n" +
                        "使用此连接配置的所有触发器也将被永久删除。",
                )
            },
            confirmButton = {
                TextButton(
                    enabled = state.disconnectingId == null,
                    onClick = { state.eventSink(ConnectorManageEvents.Disconnect(accountId)) },
                ) {
                    Text("断开连接", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { state.eventSink(ConnectorManageEvents.CancelDisconnect) }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun ErrorBanner(error: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = CompoundIcons.Error(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(18.dp),
        )
        Text(
            modifier = Modifier.weight(1f),
            text = error,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
            Icon(
                imageVector = CompoundIcons.Close(),
                contentDescription = "关闭",
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun ConnectedAccountRow(state: ConnectorManageState, account: ChatbotConnectedAccount) {
    val title = account.profile?.displayName?.takeIf { it.isNotBlank() } ?: account.toolkit
    val isDisconnecting = state.disconnectingId == account.id
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RemoteAccountLogo(
            imageUrl = account.profile?.image,
            slug = account.toolkit,
            name = title,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (account.status == "ACTIVE") "已连接" else "已连接 · ${account.status}",
                style = MaterialTheme.typography.bodySmall,
                color = if (account.status == "ACTIVE") {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isDisconnecting) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            TextButton(onClick = { state.eventSink(ConnectorManageEvents.ConfirmDisconnect(account.id)) }) {
                Icon(
                    imageVector = CompoundIcons.Delete(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text("断开连接", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/**
 * Renders the connected-account logo as a remote image (mirrors iOS). Prefers the account
 * profile [imageUrl]; otherwise falls back to the composio logo URL scheme
 * `https://logos.composio.dev/api/<slug>`. Shows a letter-box fallback on failure or when no
 * URL can be resolved.
 */
@Composable
private fun RemoteAccountLogo(
    imageUrl: String?,
    slug: String,
    name: String,
) {
    val resolvedUrl = imageUrl?.takeIf { it.isNotBlank() }
        ?: slug.takeIf { it.isNotBlank() }?.let { "https://logos.composio.dev/api/$it" }
    val shape = androidx.compose.foundation.shape.CircleShape
    if (resolvedUrl == null) {
        AccountLetterFallback(name = name, shape = shape)
        return
    }
    SubcomposeAsyncImage(
        model = resolvedUrl,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(36.dp).clip(shape),
    ) {
        val painterState by painter.state.collectAsState()
        when (painterState) {
            is AsyncImagePainter.State.Success -> SubcomposeAsyncImageContent()
            else -> AccountLetterFallback(name = name, shape = shape)
        }
    }
}

@Composable
private fun AccountLetterFallback(name: String, shape: Shape) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.take(1).uppercase(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal class ConnectorManageStateProvider : PreviewParameterProvider<ConnectorManageState> {
    override val values: Sequence<ConnectorManageState>
        get() = sequenceOf(
            aConnectorManageState(),
            aConnectorManageState(accounts = persistentListOf()),
            aConnectorManageState(confirmingDisconnectId = "acc-1"),
            aConnectorManageState(error = "Failed to load connected accounts"),
        )
}

private fun aConnectorManageState(
    accounts: kotlinx.collections.immutable.ImmutableList<ChatbotConnectedAccount> = aSampleAccounts(),
    confirmingDisconnectId: String? = null,
    error: String? = null,
) = ConnectorManageState(
    toolkitName = "GitHub",
    accounts = accounts,
    isLoading = false,
    connecting = false,
    disconnectingId = null,
    confirmingDisconnectId = confirmingDisconnectId,
    error = error,
    eventSink = {},
)

private fun aSampleAccounts() = persistentListOf(
    ChatbotConnectedAccount(
        id = "acc-1",
        toolkit = "github",
        status = "ACTIVE",
        createdAt = "2026-01-01T00:00:00Z",
        updatedAt = "2026-01-01T00:00:00Z",
        profile = ChatbotConnectedAccountProfile(displayName = "octocat"),
    ),
    ChatbotConnectedAccount(
        id = "acc-2",
        toolkit = "github",
        status = "EXPIRED",
        createdAt = "2026-02-01T00:00:00Z",
        updatedAt = "2026-02-01T00:00:00Z",
        profile = ChatbotConnectedAccountProfile(displayName = "monalisa"),
    ),
).toImmutableList()

@PreviewsDayNight
@Composable
internal fun ConnectorManageViewPreview(@PreviewParameter(ConnectorManageStateProvider::class) state: ConnectorManageState) = ElementPreview {
    ConnectorManageView(state = state)
}

/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package io.element.android.features.preferences.impl.vault

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import android.text.format.DateUtils
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.preferences.impl.R
import io.element.android.libraries.chatbot.api.model.vault.ChatbotVaultItem
import io.element.android.libraries.designsystem.components.management.ManagementCreateFloatingActionButton
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.ui.strings.CommonStrings
import java.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@Composable
fun VaultManagementView(
    state: VaultManagementState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        state.eventSink(VaultManagementEvents.OnAppear)
    }
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.screen_vault_management_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = CompoundIcons.ChevronLeft(), contentDescription = stringResource(CommonStrings.action_go_back))
                    }
                },
            )
        },
        floatingActionButton = {
            ManagementCreateFloatingActionButton(
                text = stringResource(R.string.screen_vault_management_add_entry),
                onClick = { state.eventSink(VaultManagementEvents.AddEntry) },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            isRefreshing = state.isLoading,
            onRefresh = { state.eventSink(VaultManagementEvents.Refresh) },
        ) {
            when {
                state.isFullScreenError -> VaultErrorContent(
                    message = state.error.orEmpty(),
                    onRetry = { state.eventSink(VaultManagementEvents.Retry) },
                )
                state.isEmpty -> VaultEmptyContent()
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            OutlinedTextField(
                                modifier = Modifier.fillMaxWidth(),
                                value = state.searchQuery,
                                onValueChange = { state.eventSink(VaultManagementEvents.SearchQueryChanged(it)) },
                                placeholder = { Text(stringResource(R.string.screen_vault_management_search_placeholder)) },
                                leadingIcon = { Icon(imageVector = CompoundIcons.Search(), contentDescription = null) },
                                trailingIcon = if (state.searchQuery.isNotEmpty()) {
                                    {
                                        IconButton(onClick = { state.eventSink(VaultManagementEvents.SearchQueryChanged("")) }) {
                                            Icon(imageVector = CompoundIcons.Close(), contentDescription = stringResource(CommonStrings.action_clear))
                                        }
                                    }
                                } else {
                                    null
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(28.dp),
                            )
                        }
                        state.error?.let { error ->
                            item {
                                Text(
                                    text = error,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        if (state.isSearchEmpty) {
                            item {
                                Text(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 48.dp),
                                    text = stringResource(R.string.screen_vault_management_no_matching_entries),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                        items(items = state.filteredItems, key = { it.id.ifBlank { it.key } }) { item ->
                            VaultItemCard(
                                item = item,
                                onClick = { state.eventSink(VaultManagementEvents.EditEntry(item)) },
                                onEdit = { state.eventSink(VaultManagementEvents.EditEntry(item)) },
                                onDelete = { state.eventSink(VaultManagementEvents.ConfirmDelete(item)) },
                            )
                        }
                    }
                }
            }
        }
    }

    state.pendingDelete?.let {
        AlertDialog(
            onDismissRequest = { state.eventSink(VaultManagementEvents.DismissDelete) },
            title = { Text(stringResource(R.string.screen_vault_management_delete_title)) },
            text = { Text(stringResource(R.string.screen_vault_management_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = { state.eventSink(VaultManagementEvents.DeleteConfirmed) },
                    enabled = !state.isDeleting,
                ) {
                    Text(if (state.isDeleting) stringResource(R.string.screen_vault_management_deleting) else stringResource(CommonStrings.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { state.eventSink(VaultManagementEvents.DismissDelete) }) {
                    Text(stringResource(CommonStrings.action_cancel))
                }
            },
        )
    }

    state.successMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { state.eventSink(VaultManagementEvents.ClearSuccess) },
            title = { Text(stringResource(CommonStrings.dialog_title_success)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { state.eventSink(VaultManagementEvents.ClearSuccess) }) {
                    Text(stringResource(CommonStrings.action_ok))
                }
            },
        )
    }
}

@Composable
private fun VaultItemCard(
    item: ChatbotVaultItem,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { menuExpanded = true },
                )
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = CompoundIcons.Key(),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = item.key,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                item.description?.takeIf { it.isNotBlank() }?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                (item.updatedAt ?: item.createdAt)?.takeIf { it.isNotBlank() }?.let { date ->
                    Text(
                        text = formatVaultRelativeDate(date),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                imageVector = CompoundIcons.ChevronRight(),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(CommonStrings.action_edit)) },
                leadingIcon = { Icon(CompoundIcons.Edit(), null, modifier = Modifier.size(18.dp)) },
                onClick = {
                    menuExpanded = false
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(CommonStrings.action_delete)) },
                leadingIcon = { Icon(CompoundIcons.Delete(), null, modifier = Modifier.size(18.dp)) },
                onClick = {
                    menuExpanded = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun VaultErrorContent(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = CompoundIcons.Warning(),
            contentDescription = null,
            modifier = Modifier.size(52.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Text(
            modifier = Modifier.padding(top = 16.dp),
            text = stringResource(CommonStrings.common_error),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            modifier = Modifier.padding(top = 8.dp),
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(
            modifier = Modifier.padding(top = 20.dp),
            onClick = onRetry,
        ) {
            Text(stringResource(CommonStrings.action_retry))
        }
    }
}

@Composable
private fun VaultEmptyContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = CompoundIcons.Lock(),
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            modifier = Modifier.padding(top = 16.dp),
            text = stringResource(R.string.screen_vault_management_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            modifier = Modifier.padding(top = 8.dp),
            text = stringResource(R.string.screen_vault_management_empty_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun formatVaultRelativeDate(raw: String): String {
    val epochMillis = runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull() ?: return raw
    return DateUtils.getRelativeTimeSpanString(
        epochMillis,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
}

internal class VaultManagementStateProvider : PreviewParameterProvider<VaultManagementState> {
    override val values: Sequence<VaultManagementState>
        get() = sequenceOf(
            aVaultManagementState(),
            aVaultManagementState(items = persistentListOf(), isLoading = false),
            aVaultManagementState(items = persistentListOf(), isLoading = true),
        )
}

private fun aVaultManagementState(
    items: kotlinx.collections.immutable.ImmutableList<ChatbotVaultItem> = aSampleVaultItems(),
    isLoading: Boolean = false,
) = VaultManagementState(
    items = items,
    filteredItems = items,
    searchQuery = "",
    isLoading = isLoading,
    error = null,
    successMessage = null,
    pendingDelete = null,
    isDeleting = false,
    eventSink = {},
)

private fun aSampleVaultItems() = persistentListOf(
    ChatbotVaultItem(
        id = "1",
        key = "OPENAI_API_KEY",
        description = "OpenAI 服务密钥",
        updatedAt = "2026-01-01T00:00:00Z",
    ),
    ChatbotVaultItem(
        id = "2",
        key = "STRIPE_SECRET",
        description = null,
        createdAt = "2026-01-02T00:00:00Z",
    ),
).toImmutableList()

@PreviewsDayNight
@Composable
internal fun VaultManagementViewPreview(@PreviewParameter(VaultManagementStateProvider::class) state: VaultManagementState) = ElementPreview {
    VaultManagementView(state = state, onBackClick = {})
}

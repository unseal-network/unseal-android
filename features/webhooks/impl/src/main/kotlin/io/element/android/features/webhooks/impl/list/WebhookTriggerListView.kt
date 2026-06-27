/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package io.element.android.features.webhooks.impl.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import coil3.svg.SvgDecoder
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.webhooks.impl.R
import io.element.android.features.webhooks.impl.shared.composioLogoUrl
import io.element.android.features.webhooks.impl.shared.isEnabled
import io.element.android.features.webhooks.impl.shared.resolveSourceSlug
import io.element.android.libraries.chatbot.api.model.rooms.ChatbotRoomAgent
import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotWebhookTrigger
import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotWebhookTriggerStatus
import io.element.android.libraries.designsystem.components.management.ManagementCreateFloatingActionButton
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@Composable
fun WebhookTriggerListView(
    state: WebhookTriggerListState,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        state.eventSink(WebhookTriggerListEvents.OnAppear)
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = when (val mode = state.mode) {
                            WebhookTriggerListMode.Global -> stringResource(R.string.webhook_triggers_title)
                            is WebhookTriggerListMode.Room -> stringResource(R.string.webhook_triggers_room_title, mode.roomName)
                        },
                    )
                },
                navigationIcon = {
                    if (state.mode is WebhookTriggerListMode.Room) {
                        IconButton(onClick = { state.eventSink(WebhookTriggerListEvents.Dismiss) }) {
                            Icon(CompoundIcons.Close(), contentDescription = stringResource(CommonStrings.action_close))
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ManagementCreateFloatingActionButton(
                text = stringResource(R.string.webhook_triggers_create),
                onClick = { state.eventSink(WebhookTriggerListEvents.CreateTrigger) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            FilterSection(state)
            HorizontalDivider()
            state.error?.let { error ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clickable { state.eventSink(WebhookTriggerListEvents.ClearError) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        CompoundIcons.Error(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            when {
                state.isLoading && state.filteredTriggers.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                state.filteredTriggers.isEmpty() -> EmptyState(state.mode)
                else -> PullToRefreshBox(
                    isRefreshing = state.isLoading,
                    onRefresh = { state.eventSink(WebhookTriggerListEvents.Refresh) },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.filteredTriggers, key = { it.triggerId }) { trigger ->
                            WebhookTriggerItem(
                                trigger = trigger,
                                sourceSlug = resolveSourceSlug(trigger, state.eventSources),
                                roomName = state.roomName(trigger.roomId),
                                showRoom = state.mode is WebhookTriggerListMode.Global,
                                isToggling = state.togglingTriggerId == trigger.triggerId,
                                onToggle = { state.eventSink(WebhookTriggerListEvents.ToggleStatus(trigger)) },
                                onEdit = { state.eventSink(WebhookTriggerListEvents.EditTrigger(trigger)) },
                                onDelete = { state.eventSink(WebhookTriggerListEvents.RequestDelete(trigger)) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (state.deleteConfirmationTriggerId != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { state.eventSink(WebhookTriggerListEvents.CancelDelete) },
            title = { Text(stringResource(R.string.webhook_triggers_delete_title)) },
            text = { Text(stringResource(R.string.webhook_triggers_delete_message)) },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = { state.eventSink(WebhookTriggerListEvents.ConfirmDelete) },
                ) {
                    Text(stringResource(CommonStrings.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { state.eventSink(WebhookTriggerListEvents.CancelDelete) },
                ) {
                    Text(stringResource(CommonStrings.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun FilterSection(state: WebhookTriggerListState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.mode is WebhookTriggerListMode.Global) {
            val allRoomsLabel = stringResource(R.string.webhook_triggers_all_rooms)
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.searchQuery,
                onValueChange = { state.eventSink(WebhookTriggerListEvents.SearchChanged(it)) },
                placeholder = { Text(stringResource(R.string.webhook_triggers_search_placeholder)) },
                leadingIcon = { Icon(CompoundIcons.Search(), contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
            )
            FilterDropdown(
                label = stringResource(R.string.webhook_triggers_room_filter),
                value = state.availableRooms.firstOrNull { it.roomId.value == state.selectedRoomId }
                    ?.let { it.info.name ?: it.roomId.value }
                    ?: allRoomsLabel,
                options = buildList {
                    add(null to allRoomsLabel)
                    state.availableRooms.forEach { add(it.roomId.value to (it.info.name ?: it.roomId.value)) }
                },
                onSelect = { state.eventSink(WebhookTriggerListEvents.SelectRoomFilter(it)) },
            )
        } else {
            val allAgentsLabel = stringResource(R.string.webhook_triggers_all_agents)
            FilterDropdown(
                label = stringResource(R.string.webhook_triggers_agent_filter),
                value = state.availableAgents.firstOrNull { it.agentId == state.selectedAgentId }
                    ?.let { it.displayName ?: it.agentId }
                    ?: allAgentsLabel,
                options = buildList {
                    add(null to allAgentsLabel)
                    state.availableAgents.forEach { add(it.agentId to (it.displayName ?: it.agentId)) }
                },
                onSelect = { state.eventSink(WebhookTriggerListEvents.SelectAgentFilter(it)) },
            )
        }
    }
}

@Composable
private fun FilterDropdown(
    label: String,
    value: String,
    options: List<Pair<String?, String>>,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            Row(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                Icon(CompoundIcons.ChevronDown(), contentDescription = null, modifier = Modifier.size(16.dp))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (id, optionLabel) ->
                    DropdownMenuItem(
                        text = { Text(optionLabel) },
                        onClick = {
                            onSelect(id)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun WebhookTriggerItem(
    trigger: ChatbotWebhookTrigger,
    sourceSlug: String?,
    roomName: String,
    showRoom: Boolean,
    isToggling: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (trigger.isEnabled()) 1f else 0.7f),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SourceLogo(
                slug = sourceSlug,
                modifier = Modifier.size(36.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = trigger.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isToggling) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Switch(
                            checked = trigger.isEnabled(),
                            onCheckedChange = { onToggle() },
                        )
                    }
                }
                trigger.description?.takeIf { it.isNotBlank() }?.let { desc ->
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (showRoom) {
                        MetadataChip(CompoundIcons.Room(), roomName)
                    }
                    MetadataChip(CompoundIcons.Computer(), trigger.agentId)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                        Icon(
                            CompoundIcons.Edit(),
                            contentDescription = stringResource(CommonStrings.action_edit),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(
                            CompoundIcons.Delete(),
                            contentDescription = stringResource(CommonStrings.action_delete),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceLogo(
    slug: String?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    val url = slug?.let { composioLogoUrl(it) }
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (url == null) {
            LogoFallback()
        } else {
            val context = androidx.compose.ui.platform.LocalContext.current
            val model = remember(url) {
                ImageRequest.Builder(context)
                    .data(url)
                    .decoderFactory(SvgDecoder.Factory())
                    .build()
            }
            SubcomposeAsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            ) {
                val painterState by painter.state.collectAsState()
                when (painterState) {
                    is AsyncImagePainter.State.Success -> SubcomposeAsyncImageContent()
                    else -> LogoFallback()
                }
            }
        }
    }
}

@Composable
private fun LogoFallback() {
    // iOS falls back to a bolt icon; Compound has no bolt glyph so we keep the existing link icon.
    Icon(
        CompoundIcons.Link(),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(18.dp),
    )
}

@Composable
private fun MetadataChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EmptyState(mode: WebhookTriggerListMode) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Icon(
            CompoundIcons.Notifications(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = stringResource(R.string.webhook_triggers_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = when (mode) {
                WebhookTriggerListMode.Global -> stringResource(R.string.webhook_triggers_empty_global_message)
                is WebhookTriggerListMode.Room -> stringResource(R.string.webhook_triggers_empty_room_message)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

internal class WebhookTriggerListStateProvider : PreviewParameterProvider<WebhookTriggerListState> {
    override val values: Sequence<WebhookTriggerListState>
        get() = sequenceOf(
            aWebhookTriggerListState(),
            aWebhookTriggerListState(mode = WebhookTriggerListMode.Global, triggers = persistentListOf()),
            aWebhookTriggerListState(isLoading = true, triggers = persistentListOf()),
        )
}

private fun aWebhookTriggerListState(
    mode: WebhookTriggerListMode = WebhookTriggerListMode.Global,
    triggers: kotlinx.collections.immutable.ImmutableList<ChatbotWebhookTrigger> = aSampleTriggers(),
    isLoading: Boolean = false,
) = WebhookTriggerListState(
    mode = mode,
    triggers = triggers,
    filteredTriggers = triggers,
    eventSources = persistentListOf(),
    availableRooms = persistentListOf(),
    selectedRoomId = null,
    availableAgents = persistentListOf(
        ChatbotRoomAgent(agentId = "assistant", displayName = "Assistant"),
    ),
    selectedAgentId = null,
    searchQuery = "",
    isLoading = isLoading,
    error = null,
    togglingTriggerId = null,
    deletingTriggerId = null,
    deleteConfirmationTriggerId = null,
    eventSink = {},
)

private fun aSampleTriggers() = persistentListOf(
    ChatbotWebhookTrigger(
        triggerId = "t1",
        agentId = "assistant",
        name = "GitHub push notifier",
        description = "Summarises new commits on push.",
        source = "github",
        eventTypes = listOf("github.push"),
        actionPrompt = "Summarise the push payload.",
        roomId = "#dev:unseal.network",
        status = ChatbotWebhookTriggerStatus.Enabled,
    ),
    ChatbotWebhookTrigger(
        triggerId = "t2",
        agentId = "researcher",
        name = "Stripe payment alert",
        description = "Posts when a payment succeeds.",
        source = "stripe",
        eventTypes = listOf("stripe.payment_intent.succeeded"),
        actionPrompt = "Notify the room about the payment.",
        roomId = "#finance:unseal.network",
        status = ChatbotWebhookTriggerStatus.Disabled,
    ),
).toImmutableList()

@PreviewsDayNight
@Composable
internal fun WebhookTriggerListViewPreview(
    @PreviewParameter(WebhookTriggerListStateProvider::class) state: WebhookTriggerListState,
) = ElementPreview {
    WebhookTriggerListView(state = state)
}

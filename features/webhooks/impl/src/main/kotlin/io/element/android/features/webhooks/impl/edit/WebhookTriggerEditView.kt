/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package io.element.android.features.webhooks.impl.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
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
import io.element.android.features.webhooks.api.WebhookTriggerEditMode
import io.element.android.features.webhooks.impl.R
import io.element.android.features.webhooks.impl.shared.composioLogoUrl
import io.element.android.libraries.chatbot.api.model.connectors.ChatbotConnectedAccount
import io.element.android.libraries.chatbot.api.model.connectors.ChatbotConnectedAccountProfile
import io.element.android.libraries.chatbot.api.model.rooms.ChatbotRoomAgent
import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotWebhookEventSource
import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotWebhookEventType
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

private const val MAX_VISIBLE_EVENT_CHIPS = 2

@Composable
fun WebhookTriggerEditView(
    state: WebhookTriggerEditState,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        state.eventSink(WebhookTriggerEditEvents.OnAppear)
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (state.isCreateMode) stringResource(R.string.webhook_trigger_edit_create_title) else stringResource(R.string.webhook_trigger_edit_edit_title)) },
                navigationIcon = {
                    TextButton(onClick = { state.eventSink(WebhookTriggerEditEvents.Cancel) }) {
                        Text(stringResource(CommonStrings.action_cancel))
                    }
                },
                actions = {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        TextButton(
                            onClick = { state.eventSink(WebhookTriggerEditEvents.Save) },
                            enabled = state.canSave,
                        ) {
                            Text(stringResource(CommonStrings.action_save))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            EditForm(
                state = state,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        }
    }
}

@Composable
private fun EditForm(state: WebhookTriggerEditState, modifier: Modifier = Modifier) {
    var showSourceSheet by remember { mutableStateOf(false) }
    var showAccountSheet by remember { mutableStateOf(false) }
    var showEventTypeSheet by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (state.isCreateMode) {
            SectionHeader(stringResource(R.string.webhook_trigger_edit_ai_generate))
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.draftPrompt,
                onValueChange = { state.eventSink(WebhookTriggerEditEvents.DraftPromptChanged(it)) },
                label = { Text(stringResource(R.string.webhook_trigger_edit_draft_prompt_label)) },
                minLines = 2,
                enabled = !state.isDrafting,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = { state.eventSink(WebhookTriggerEditEvents.GenerateDraft) },
                    enabled = state.draftPrompt.trim().isNotEmpty() && !state.isDrafting,
                ) {
                    if (state.isDrafting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(8.dp))
                    }
                    Text(if (state.isDrafting) stringResource(R.string.webhook_trigger_edit_generating) else stringResource(R.string.webhook_trigger_edit_generate))
                }
            }
        }

        SectionHeader(stringResource(R.string.webhook_trigger_edit_event_source), locked = !state.isCreateMode)
        SelectorRow(
            label = stringResource(R.string.webhook_trigger_edit_source),
            value = state.selectedSource?.name ?: "—",
            sourceSlug = state.selectedSource?.source,
            enabled = state.isCreateMode,
            onClick = { showSourceSheet = true },
        )

        if (state.selectedSource != null && state.connectedAccounts.isNotEmpty()) {
            SectionHeader(stringResource(R.string.webhook_trigger_edit_account))
            AccountRow(
                account = state.selectedAccount,
                showChevron = state.isCreateMode && state.connectedAccounts.size > 1,
                onClick = {
                    if (state.isCreateMode && state.connectedAccounts.size > 1) showAccountSheet = true
                },
            )
        }

        SectionHeader(stringResource(R.string.webhook_trigger_edit_event_type), locked = !state.isCreateMode)
        EventTypeRow(
            state = state,
            enabled = state.isCreateMode && state.selectedSource != null,
            onClick = { showEventTypeSheet = true },
        )

        SectionHeader(stringResource(R.string.webhook_trigger_edit_target))
        DropdownField(
            label = stringResource(R.string.webhook_trigger_edit_room),
            value = state.availableRooms.firstOrNull { it.roomId.value == state.selectedRoomId }
                ?.let { it.info.name ?: it.roomId.value }
                ?: stringResource(R.string.webhook_trigger_edit_select_room),
            options = state.availableRooms.map { it.roomId.value to (it.info.name ?: it.roomId.value) },
            enabled = true,
            onSelect = { state.eventSink(WebhookTriggerEditEvents.SelectRoom(it)) },
        )
        if (state.isCreateMode) {
            DropdownField(
                label = stringResource(R.string.webhook_trigger_edit_agent),
                value = state.availableAgents.firstOrNull { it.agentId == state.selectedAgentId }
                    ?.let { it.displayName ?: it.agentId }
                    ?: stringResource(R.string.webhook_trigger_edit_select_agent),
                options = state.availableAgents.map { it.agentId to (it.displayName ?: it.agentId) },
                enabled = state.selectedRoomId != null,
                onSelect = { state.eventSink(WebhookTriggerEditEvents.SelectAgent(it)) },
            )
        }

        SectionHeader(stringResource(R.string.webhook_trigger_edit_details))
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.name,
            onValueChange = { state.eventSink(WebhookTriggerEditEvents.NameChanged(it)) },
            label = { Text(stringResource(R.string.webhook_trigger_edit_name)) },
            singleLine = true,
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.description,
            onValueChange = { state.eventSink(WebhookTriggerEditEvents.DescriptionChanged(it)) },
            label = { Text(stringResource(R.string.webhook_trigger_edit_description)) },
            singleLine = true,
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.actionPrompt,
            onValueChange = { state.eventSink(WebhookTriggerEditEvents.ActionPromptChanged(it)) },
            label = { Text(stringResource(R.string.webhook_trigger_edit_action_prompt)) },
            minLines = 4,
        )

        state.error?.let { error ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(8.dp))
                    .clickable { state.eventSink(WebhookTriggerEditEvents.ClearError) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    CompoundIcons.Error(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(18.dp),
                )
                Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    if (showSourceSheet) {
        SourcePickerSheet(state = state, onDismiss = { showSourceSheet = false })
    }
    if (showAccountSheet) {
        AccountPickerSheet(state = state, onDismiss = { showAccountSheet = false })
    }
    if (showEventTypeSheet) {
        EventTypePickerSheet(state = state, onDismiss = { showEventTypeSheet = false })
    }
}

@Composable
private fun SectionHeader(title: String, locked: Boolean = false) {
    Row(
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        if (locked) {
            Icon(
                CompoundIcons.Lock(),
                contentDescription = stringResource(R.string.webhook_trigger_edit_locked),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun SelectorRow(
    label: String,
    value: String,
    sourceSlug: String?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.weight(1f))
            sourceSlug?.let { SourceLogo(slug = it) }
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (enabled) {
                Icon(
                    CompoundIcons.ChevronRight(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * Renders the real composio source logo (https://logos.composio.dev/api/<slug>) via Coil,
 * mirroring iOS, falling back to the source initial when the slug is blank or the image fails.
 */
@Composable
private fun SourceLogo(
    slug: String,
    size: androidx.compose.ui.unit.Dp = 24.dp,
) {
    val shape = RoundedCornerShape(6.dp)
    if (slug.isBlank()) {
        SourceInitial(slug, size)
        return
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface, shape),
        contentAlignment = Alignment.Center,
    ) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val model = remember(slug) {
            ImageRequest.Builder(context)
                .data(composioLogoUrl(slug))
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
                else -> SourceInitial(slug, size)
            }
        }
    }
}

@Composable
private fun SourceInitial(
    source: String,
    size: androidx.compose.ui.unit.Dp = 24.dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = source.take(1).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AccountRow(
    account: ChatbotConnectedAccount?,
    showChevron: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = showChevron, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (account != null) {
                AccountAvatar(account)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = account.profile?.displayName ?: account.toolkit,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    account.alias?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            } else {
                Text(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.webhook_trigger_edit_select_account),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (showChevron) {
                Icon(
                    CompoundIcons.ChevronRight(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun AccountAvatar(account: ChatbotConnectedAccount) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(MaterialTheme.colorScheme.surface, androidx.compose.foundation.shape.CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = (account.profile?.displayName ?: account.toolkit).take(1).uppercase(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EventTypeRow(
    state: WebhookTriggerEditState,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val names = (state.selectedSource?.eventTypes ?: emptyList())
        .filter { it.eventType in state.selectedEventTypes }
        .map { it.name }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (names.isEmpty()) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.webhook_trigger_edit_select_event_type),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    names.take(MAX_VISIBLE_EVENT_CHIPS).forEach { name ->
                        AssistChip(
                            onClick = onClick,
                            enabled = enabled,
                            label = { Text(name, style = MaterialTheme.typography.labelSmall) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                        )
                    }
                    val remaining = names.size - MAX_VISIBLE_EVENT_CHIPS
                    if (remaining > 0) {
                        Text(
                            text = "+$remaining 更多",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                    }
                }
            }
            if (enabled) {
                Icon(
                    CompoundIcons.ChevronRight(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun DropdownField(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
    ) {
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled),
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, optionLabel) ->
                DropdownMenuItem(
                    text = { Text(optionLabel) },
                    onClick = {
                        onSelect(id)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@Composable
private fun SourcePickerSheet(state: WebhookTriggerEditState, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.webhook_trigger_edit_select_source), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.webhook_trigger_edit_search_source)) },
                leadingIcon = { Icon(CompoundIcons.Search(), contentDescription = null) },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            val filtered = state.eventSources.filter {
                query.isBlank() || it.name.contains(query, ignoreCase = true) || it.source.contains(query, ignoreCase = true)
            }
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                filtered.forEach { source ->
                    ListItem(
                        modifier = Modifier.clickable {
                            state.eventSink(WebhookTriggerEditEvents.SelectSource(source))
                            onDismiss()
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = { SourceLogo(slug = source.source, size = 36.dp) },
                        headlineContent = { Text(source.name) },
                        trailingContent = if (state.selectedSource?.source == source.source) {
                            { Icon(CompoundIcons.Check(), contentDescription = null) }
                        } else {
                            null
                        },
                    )
                }
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                onClick = { state.eventSink(WebhookTriggerEditEvents.ConnectSource) },
                enabled = state.selectedSource != null,
            ) {
                Icon(CompoundIcons.Link(), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.webhook_trigger_edit_connect_now))
            }
        }
    }
}

@Composable
private fun AccountPickerSheet(state: WebhookTriggerEditState, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.webhook_trigger_edit_select_account), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                state.connectedAccounts.forEach { account ->
                    ListItem(
                        modifier = Modifier.clickable {
                            state.eventSink(WebhookTriggerEditEvents.SelectAccount(account))
                            onDismiss()
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = { AccountAvatar(account) },
                        headlineContent = { Text(account.profile?.displayName ?: account.toolkit) },
                        supportingContent = account.alias?.takeIf { it.isNotBlank() }?.let { { Text(it, maxLines = 1) } },
                        trailingContent = if (state.selectedAccount?.id == account.id) {
                            { Icon(CompoundIcons.Check(), contentDescription = null) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EventTypePickerSheet(state: WebhookTriggerEditState, onDismiss: () -> Unit) {
    val eventTypes = state.selectedSource?.eventTypes ?: emptyList()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.webhook_trigger_edit_event_type), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(CommonStrings.action_done)) }
            }
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                eventTypes.forEach { eventType ->
                    ListItem(
                        modifier = Modifier.clickable {
                            state.eventSink(WebhookTriggerEditEvents.ToggleEventType(eventType.eventType))
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text(eventType.name) },
                        supportingContent = eventType.introduce?.takeIf { it.isNotBlank() }?.let { { Text(it, maxLines = 2) } },
                        trailingContent = {
                            Checkbox(
                                checked = eventType.eventType in state.selectedEventTypes,
                                onCheckedChange = { state.eventSink(WebhookTriggerEditEvents.ToggleEventType(eventType.eventType)) },
                            )
                        },
                    )
                }
            }
        }
    }
}

internal class WebhookTriggerEditStateProvider : PreviewParameterProvider<WebhookTriggerEditState> {
    override val values: Sequence<WebhookTriggerEditState>
        get() = sequenceOf(
            aWebhookTriggerEditState(isCreate = true),
            aWebhookTriggerEditState(isCreate = false),
        )
}

private fun aWebhookTriggerEditState(isCreate: Boolean): WebhookTriggerEditState {
    val source = ChatbotWebhookEventSource(
        source = "github",
        name = "GitHub",
        eventTypes = listOf(
            ChatbotWebhookEventType(eventType = "github.push", name = "Push"),
            ChatbotWebhookEventType(eventType = "github.pr", name = "Pull request"),
            ChatbotWebhookEventType(eventType = "github.issue", name = "Issue"),
        ),
    )
    val account = ChatbotConnectedAccount(
        id = "acc-1",
        toolkit = "github",
        status = "active",
        alias = "octocat",
        createdAt = "2026-01-01",
        updatedAt = "2026-01-01",
        profile = ChatbotConnectedAccountProfile(displayName = "Octocat"),
    )
    return WebhookTriggerEditState(
        mode = if (isCreate) WebhookTriggerEditMode.Create() else WebhookTriggerEditMode.Edit(
            io.element.android.libraries.chatbot.api.model.webhooks.ChatbotWebhookTrigger(
                triggerId = "t1",
                agentId = "assistant",
                name = "GitHub push notifier",
                source = "github",
                eventTypes = listOf("github.push"),
                actionPrompt = "Summarise the push.",
                roomId = "#dev:unseal.network",
                status = io.element.android.libraries.chatbot.api.model.webhooks.ChatbotWebhookTriggerStatus.Enabled,
            ),
        ),
        eventSources = persistentListOf(source),
        selectedSource = source,
        selectedEventTypes = persistentSetOf("github.push", "github.pr", "github.issue"),
        selectedConnection = null,
        connectedAccounts = persistentListOf(account),
        selectedAccount = account,
        availableRooms = persistentListOf(),
        selectedRoomId = null,
        availableAgents = persistentListOf(
            ChatbotRoomAgent(agentId = "assistant", displayName = "Assistant"),
        ),
        selectedAgentId = if (isCreate) "assistant" else null,
        name = "GitHub push notifier",
        description = "Summarises new commits on push.",
        actionPrompt = "Summarise the push payload for the room.",
        draftPrompt = "",
        isLoading = false,
        isSaving = false,
        isDrafting = false,
        error = null,
        eventSink = {},
    )
}

@PreviewsDayNight
@Composable
internal fun WebhookTriggerEditViewPreview(
    @PreviewParameter(WebhookTriggerEditStateProvider::class) state: WebhookTriggerEditState,
) = ElementPreview {
    WebhookTriggerEditView(state = state)
}

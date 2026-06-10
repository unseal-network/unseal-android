/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package io.element.android.features.agentmanagement.impl.edit

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.agentmanagement.impl.shared.AgentVoiceSelection
import io.element.android.libraries.chatbot.api.model.agent.AgentSandboxMode
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgentProvider
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgentProviderInfo
import io.element.android.libraries.chatbot.api.model.agent.ChatbotProviderModel
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import kotlinx.collections.immutable.persistentListOf

@Composable
fun AgentEditView(
    state: AgentEditState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    onSetAvatar: () -> Unit = {},
) {
    LaunchedEffect(Unit) { state.eventSink(AgentEditEvents.OnAppear) }
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (state.isCreate) "Create agent" else "Edit agent") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) { Icon(CompoundIcons.ChevronLeft(), "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (val phase = state.phase) {
                AgentEditPhase.Editing -> AgentForm(state, submittingStep = null, onSetAvatar = onSetAvatar)
                is AgentEditPhase.Submitting -> AgentForm(state, submittingStep = phase.step, onSetAvatar = onSetAvatar)
                is AgentEditPhase.Success -> AgentCreateSuccess(state, phase.summary)
            }
            state.error?.let { error ->
                Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AgentForm(state: AgentEditState, submittingStep: AgentEditSubmittingStep?, onSetAvatar: () -> Unit) {
    val form = state.form
    val isSubmitting = submittingStep != null
    var showSkillSheet by remember { mutableStateOf(false) }
    var showVaultSheet by remember { mutableStateOf(false) }

    SectionHeader("Avatar")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Avatar(
            avatarData = AvatarData(form.botName.ifEmpty { "agent" }, form.displayName.ifEmpty { form.botName }, form.avatarUrl.ifEmpty { null }, AvatarSize.SelectedRoom),
            avatarType = AvatarType.Room(),
            forcedAvatarSize = 64.dp,
        )
        TextButton(onClick = onSetAvatar, enabled = !isSubmitting) {
            Icon(CompoundIcons.Image(), null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Set avatar")
        }
    }

    SectionHeader("Basic info")
    if (state.isCreate) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = form.botName,
            onValueChange = { state.eventSink(AgentEditEvents.BotNameChanged(it)) },
            label = { Text("Unique identifier (required)") },
            singleLine = true,
            isError = state.nameAvailability == AgentNameAvailability.Taken,
            supportingText = availabilitySupport(state.nameAvailability)?.let { { Text(it) } },
            enabled = !isSubmitting,
        )
    } else {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = "@${form.botName}",
            onValueChange = {},
            label = { Text("Identifier") },
            singleLine = true,
            readOnly = true,
            enabled = false,
        )
    }
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = form.displayName,
        onValueChange = { state.eventSink(AgentEditEvents.DisplayNameChanged(it)) },
        label = { Text("Display name") },
        singleLine = true,
        enabled = !isSubmitting,
    )
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = form.description,
        onValueChange = { state.eventSink(AgentEditEvents.DescriptionChanged(it)) },
        label = { Text("Description") },
        minLines = 2,
        enabled = !isSubmitting,
    )
    if (state.isCreate) {
        Text(
            "The identifier can't be changed later. Use lowercase letters and numbers.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    SectionHeader("Access control")
    ToggleRow("Public agent", "Anyone can discover and use this agent", CompoundIcons.Public(), form.isPublic, !isSubmitting) {
        state.eventSink(AgentEditEvents.IsPublicChanged(it))
    }
    ToggleRow("Auto-join new rooms", "Automatically accept invites", CompoundIcons.Devices(), form.autoJoin, !isSubmitting && form.isPublic) {
        state.eventSink(AgentEditEvents.AutoJoinChanged(it))
    }

    SectionHeader("AI engine")
    DropdownField(
        label = "Provider",
        value = state.selectedProvider?.let { it.displayName ?: it.info?.displayName ?: it.id } ?: "—",
        options = state.providers.map { it.id to (it.displayName ?: it.info?.displayName ?: it.id) },
        enabled = !isSubmitting && state.providers.isNotEmpty(),
        onSelect = { state.eventSink(AgentEditEvents.ProviderChanged(it)) },
    )
    if (state.availableModels.isNotEmpty()) {
        DropdownField(
            label = "Model",
            value = state.availableModels.firstOrNull { it.id == form.model }?.let { it.displayName ?: it.id } ?: form.model.ifEmpty { "—" },
            options = state.availableModels.map { it.id to (it.displayName ?: it.id) },
            enabled = !isSubmitting,
            onSelect = { state.eventSink(AgentEditEvents.ModelChanged(it)) },
        )
    } else {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = form.model,
            onValueChange = { state.eventSink(AgentEditEvents.ModelChanged(it)) },
            label = { Text("Model") },
            singleLine = true,
            enabled = !isSubmitting,
        )
    }
    if (state.supportsBaseUrl) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = form.baseUrl,
            onValueChange = { state.eventSink(AgentEditEvents.BaseUrlChanged(it)) },
            label = { Text("Base URL") },
            singleLine = true,
            enabled = !isSubmitting,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
    }
    if (state.needsApiKey) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = form.apiKey,
            onValueChange = { state.eventSink(AgentEditEvents.ApiKeyChanged(it)) },
            label = { Text("API key") },
            singleLine = true,
            enabled = !isSubmitting,
            visualTransformation = remember { PasswordVisualTransformation() },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
    }

    SectionHeader("Voice")
    DropdownField(
        label = "Voice",
        value = AgentVoiceSelection.label(state.voiceSelection, state.voiceProfiles, state.providerVoices),
        options = buildList {
            add(AgentVoiceSelection.DEFAULT to "Use server default")
            state.voiceProfiles.forEach { add(AgentVoiceSelection.profile(it.id) to it.displayName) }
            state.providerVoices.forEach { add(AgentVoiceSelection.provider(it.provider, it.providerVoiceId) to it.displayName) }
        },
        enabled = !isSubmitting,
        onSelect = { state.eventSink(AgentEditEvents.VoiceSelectionChanged(it)) },
    )

    SectionHeader("Personality")
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = form.soul,
        onValueChange = { state.eventSink(AgentEditEvents.SoulChanged(it)) },
        label = { Text("Soul") },
        minLines = 4,
        enabled = !isSubmitting,
    )

    SectionHeader("Runtime environment")
    DropdownField(
        label = "Runtime",
        value = sandboxModeLabel(form.sandboxMode),
        options = AgentSandboxMode.entries.map { it.name to sandboxModeLabel(it) },
        enabled = !isSubmitting,
        onSelect = { state.eventSink(AgentEditEvents.SandboxModeChanged(AgentSandboxMode.valueOf(it))) },
    )
    Text(
        text = if (form.sandboxMode == AgentSandboxMode.PerUser) {
            "Each user chats with the agent in their own runtime; their data stays isolated."
        } else {
            "The agent uses a single dedicated runtime shared across all conversations."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (form.sandboxMode == AgentSandboxMode.AgentDedicated) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(8.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(CompoundIcons.Error(), null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(18.dp))
            Text(
                "A dedicated runtime is shared by everyone using this agent — avoid storing personal data in it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
        if (state.isCreate) {
            Text("Initial state", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            SandboxInitOption(
                title = "Empty",
                subtitle = "Start from a clean runtime",
                selected = form.sandboxInitMethod == AgentSandboxInitMethod.Empty,
                enabled = !isSubmitting,
                onClick = { state.eventSink(AgentEditEvents.SandboxInitMethodChanged(AgentSandboxInitMethod.Empty)) },
            )
            SandboxInitOption(
                title = "Clone owner's runtime",
                subtitle = "Copy your current runtime as the starting point",
                selected = form.sandboxInitMethod == AgentSandboxInitMethod.CloneOwner,
                enabled = !isSubmitting,
                onClick = { state.eventSink(AgentEditEvents.SandboxInitMethodChanged(AgentSandboxInitMethod.CloneOwner)) },
            )
        } else {
            if (state.sandboxStatus != null) {
                Text(
                    "Configured · ${if (state.sandboxStatus?.sourceUserId != null) "cloned from owner" else "empty"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Text("No dedicated runtime configured yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    enabled = !state.sandboxBusy,
                    onClick = { state.eventSink(AgentEditEvents.SandboxActionRequested(AgentSandboxInitMethod.Empty)) },
                ) { Text("Create empty") }
                TextButton(
                    enabled = !state.sandboxBusy,
                    onClick = { state.eventSink(AgentEditEvents.SandboxActionRequested(AgentSandboxInitMethod.CloneOwner)) },
                ) { Text("Clone owner") }
                if (state.sandboxBusy) CircularProgressIndicator(modifier = Modifier.size(18.dp))
            }
            state.sandboxMessage?.let { message ->
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
    state.pendingSandboxAction?.let { pending ->
        val cloning = pending == AgentSandboxInitMethod.CloneOwner
        val hasExisting = state.sandboxStatus != null
        AlertDialog(
            onDismissRequest = { state.eventSink(AgentEditEvents.SandboxActionDismissed) },
            title = {
                Text(
                    when {
                        hasExisting && cloning -> "Re-clone runtime?"
                        hasExisting -> "Replace runtime?"
                        cloning -> "Clone your runtime?"
                        else -> "Create empty runtime?"
                    },
                )
            },
            text = {
                Text(
                    when {
                        hasExisting -> "This overwrites the agent's current dedicated runtime. This cannot be undone."
                        cloning -> "Copies your current runtime as the agent's dedicated starting point."
                        else -> "Starts the agent with a clean dedicated runtime."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { state.eventSink(AgentEditEvents.SandboxActionConfirmed) }) {
                    Text(if (cloning) "Clone" else "Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { state.eventSink(AgentEditEvents.SandboxActionDismissed) }) { Text("Cancel") }
            },
        )
    }

    SectionHeader("Secret variables")
    if (state.selectedVaultKeys.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.selectedVaultKeys.forEach { key ->
                InputChip(
                    selected = true,
                    onClick = { state.eventSink(AgentEditEvents.ToggleVaultKey(key)) },
                    enabled = !isSubmitting,
                    label = { Text(key) },
                    trailingIcon = { Icon(CompoundIcons.Close(), null, modifier = Modifier.size(16.dp)) },
                )
            }
        }
    }
    OutlinedButton(
        modifier = Modifier.fillMaxWidth(),
        enabled = !isSubmitting && state.personalVaultKeys.isNotEmpty(),
        onClick = { showVaultSheet = true },
    ) {
        Icon(CompoundIcons.Key(), null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text(if (state.personalVaultKeys.isEmpty()) "No vault keys" else "Select from my vault")
    }
    if (!state.isCreate) {
        form.vaultEntries.forEachIndexed { index, entry ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = entry.key,
                    onValueChange = { state.eventSink(AgentEditEvents.UpdateVaultEntry(index, it, entry.value, entry.description.orEmpty())) },
                    label = { Text("Key") },
                    singleLine = true,
                    enabled = !isSubmitting,
                )
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = entry.value,
                    onValueChange = { state.eventSink(AgentEditEvents.UpdateVaultEntry(index, entry.key, it, entry.description.orEmpty())) },
                    label = { Text("Value") },
                    singleLine = true,
                    enabled = !isSubmitting,
                )
                IconButton(onClick = { state.eventSink(AgentEditEvents.RemoveVaultEntry(index)) }) {
                    Icon(CompoundIcons.Close(), "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        OutlinedButton(onClick = { state.eventSink(AgentEditEvents.AddVaultEntry) }, enabled = !isSubmitting) {
            Icon(CompoundIcons.Plus(), null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Add secret manually")
        }
    }
    if (showVaultSheet) {
        VaultKeyPickerSheet(state = state, onDismiss = { showVaultSheet = false })
    }

    SectionHeader("Skills")
    val selectedSkills = state.availableSkills.filter { it.id in state.selectedSkillIds }
    if (selectedSkills.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            selectedSkills.forEach { skill ->
                InputChip(
                    selected = true,
                    onClick = { state.eventSink(AgentEditEvents.ToggleSkill(skill.id)) },
                    enabled = !isSubmitting,
                    label = { Text(skill.name) },
                    trailingIcon = { Icon(CompoundIcons.Close(), null, modifier = Modifier.size(16.dp)) },
                )
            }
        }
    }
    OutlinedButton(
        modifier = Modifier.fillMaxWidth(),
        enabled = !isSubmitting && state.availableSkills.isNotEmpty(),
        onClick = { showSkillSheet = true },
    ) {
        Icon(CompoundIcons.Plus(), null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text(if (state.availableSkills.isEmpty()) "No skills available" else "Add skills")
    }

    if (showSkillSheet) {
        SkillPickerSheet(state = state, onDismiss = { showSkillSheet = false })
    }

    if (submittingStep != null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
            Text(
                when (submittingStep) {
                    AgentEditSubmittingStep.CreateAgent -> "Saving agent…"
                    AgentEditSubmittingStep.CreateDM -> "Creating direct chat…"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Spacer(Modifier.height(8.dp))
    Button(
        modifier = Modifier.fillMaxWidth(),
        onClick = { state.eventSink(AgentEditEvents.Submit) },
        enabled = !isSubmitting,
    ) {
        Text(if (state.isCreate) "Create agent" else "Save changes")
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
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
private fun VaultKeyPickerSheet(state: AgentEditState, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text("Select from my vault", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search keys") },
                leadingIcon = { Icon(CompoundIcons.Search(), null) },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            val filtered = state.personalVaultKeys.filter {
                query.isBlank() || it.key.contains(query, ignoreCase = true) || it.description.orEmpty().contains(query, ignoreCase = true)
            }
            if (filtered.isEmpty()) {
                Text(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    text = "No keys found.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    filtered.forEach { item ->
                        ListItem(
                            modifier = Modifier.clickable { state.eventSink(AgentEditEvents.ToggleVaultKey(item.key)) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text(item.key) },
                            supportingContent = item.description?.takeIf { it.isNotBlank() }?.let { { Text(it, maxLines = 1) } },
                            trailingContent = {
                                Checkbox(
                                    checked = item.key in state.selectedVaultKeys,
                                    onCheckedChange = { state.eventSink(AgentEditEvents.ToggleVaultKey(item.key)) },
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SkillPickerSheet(state: AgentEditState, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text("Add skills", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search skills") },
                leadingIcon = { Icon(CompoundIcons.Search(), null) },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            val filtered = state.availableSkills.filter {
                query.isBlank() || it.name.contains(query, ignoreCase = true) || it.description.orEmpty().contains(query, ignoreCase = true)
            }
            if (filtered.isEmpty()) {
                Text(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    text = "No skills found.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    filtered.forEach { skill ->
                        ListItem(
                            modifier = Modifier.clickable { state.eventSink(AgentEditEvents.ToggleSkill(skill.id)) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text(skill.name) },
                            supportingContent = skill.description?.takeIf { it.isNotBlank() }?.let { { Text(it, maxLines = 1) } },
                            trailingContent = {
                                Checkbox(
                                    checked = skill.id in state.selectedSkillIds,
                                    onCheckedChange = { state.eventSink(AgentEditEvents.ToggleSkill(skill.id)) },
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentCreateSuccess(state: AgentEditState, summary: AgentCreateSuccessSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(16.dp))
        Text(summary.displayName ?: summary.botName, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        Text(
            listOfNotNull(summary.provider, summary.model).joinToString(" · ").ifBlank { "Agent created successfully." },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Button(modifier = Modifier.fillMaxWidth(), enabled = summary.directRoomId != null, onClick = { state.eventSink(AgentEditEvents.GoToChat) }) {
            Text("Go to chat")
        }
        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { state.eventSink(AgentEditEvents.CreateAnother) }) {
            Text("Create another")
        }
    }
}

private fun availabilitySupport(availability: AgentNameAvailability): String? = when (availability) {
    AgentNameAvailability.Unknown -> null
    AgentNameAvailability.Checking -> "Checking availability…"
    AgentNameAvailability.Available -> "Available"
    AgentNameAvailability.Taken -> "This identifier is already taken"
}

@Composable
private fun SandboxInitOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick, enabled = enabled)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun sandboxModeLabel(mode: AgentSandboxMode): String = when (mode) {
    AgentSandboxMode.PerUser -> "Shared (per user)"
    AgentSandboxMode.AgentDedicated -> "Dedicated (agent)"
}

internal class AgentEditStateProvider : PreviewParameterProvider<AgentEditState> {
    override val values: Sequence<AgentEditState>
        get() = sequenceOf(
            anAgentEditState(AgentEditMode.Create),
            anAgentEditState(AgentEditMode.Edit("assistant")),
        )
}

private fun anAgentEditState(mode: AgentEditMode) = AgentEditState(
    mode = mode,
    form = AgentEditFormState(
        botName = "assistant",
        displayName = "Assistant",
        description = "A helpful general-purpose assistant.",
        isPublic = true,
        autoJoin = true,
        providerId = "openai",
        model = "gpt-4o",
        soul = "You are a helpful assistant.",
    ),
    providers = persistentListOf(
        ChatbotAgentProvider(
            id = "openai",
            displayName = "OpenAI",
            info = ChatbotAgentProviderInfo(
                displayName = "OpenAI",
                models = listOf(
                    ChatbotProviderModel(id = "gpt-4o", displayName = "GPT-4o"),
                    ChatbotProviderModel(id = "gpt-4o-mini", displayName = "GPT-4o mini"),
                ),
            ),
        ),
        ChatbotAgentProvider(id = "anthropic", displayName = "Anthropic"),
    ),
    nameAvailability = if (mode is AgentEditMode.Create) AgentNameAvailability.Available else AgentNameAvailability.Unknown,
    phase = AgentEditPhase.Editing,
    isLoading = false,
    error = null,
    eventSink = {},
)

@PreviewsDayNight
@Composable
internal fun AgentEditViewPreview(@PreviewParameter(AgentEditStateProvider::class) state: AgentEditState) = ElementPreview {
    AgentEditView(state = state, onBackClick = {})
}

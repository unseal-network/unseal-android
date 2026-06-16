/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package io.element.android.features.agentmanagement.impl.edit

import androidx.compose.foundation.background
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
import io.element.android.features.agentmanagement.impl.shared.shapeAwareClickable
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
    val renderModel = state.renderModel
    LaunchedEffect(Unit) { state.eventSink(AgentEditEvents.OnAppear) }
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(renderModel.title) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) { Icon(CompoundIcons.ChevronLeft(), "返回") }
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
    val renderModel = state.renderModel
    val labels = renderModel.sectionLabels
    val isSubmitting = submittingStep != null
    var showSkillSheet by remember { mutableStateOf(false) }
    var showVaultSheet by remember { mutableStateOf(false) }

    SectionHeader(labels.avatar)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Avatar(
            avatarData = AvatarData(form.botName.ifEmpty { "agent" }, form.displayName.ifEmpty { form.botName }, form.avatarUrl.ifEmpty { null }, AvatarSize.SelectedRoom),
            avatarType = AvatarType.Room(),
            forcedAvatarSize = 64.dp,
        )
        TextButton(onClick = onSetAvatar, enabled = !isSubmitting) {
            Icon(CompoundIcons.Image(), null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("设置头像")
        }
    }

    SectionHeader(labels.basicInfo)
    if (state.isCreate) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = form.botName,
            onValueChange = { state.eventSink(AgentEditEvents.BotNameChanged(it)) },
            label = { Text(renderModel.botNameLabel) },
            singleLine = true,
            isError = state.nameAvailability == AgentNameAvailability.Taken,
            supportingText = renderModel.botNameAvailabilityLabel?.let { { Text(it) } },
            enabled = !isSubmitting,
        )
    } else {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = "@${form.botName}",
            onValueChange = {},
            label = { Text(renderModel.botNameLabel) },
            singleLine = true,
            readOnly = true,
            enabled = false,
        )
    }
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = form.displayName,
        onValueChange = { state.eventSink(AgentEditEvents.DisplayNameChanged(it)) },
        label = { Text("显示名称") },
        singleLine = true,
        enabled = !isSubmitting,
    )
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = form.description,
        onValueChange = { state.eventSink(AgentEditEvents.DescriptionChanged(it)) },
        label = { Text("描述") },
        minLines = 2,
        enabled = !isSubmitting,
    )
    if (state.isCreate) {
        Text(
            renderModel.botNameHelper.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    SectionHeader(labels.accessControl)
    ToggleRow("公开 Agent", "所有用户都可以发现并使用此 Agent", CompoundIcons.Public(), form.isPublic, !isSubmitting) {
        state.eventSink(AgentEditEvents.IsPublicChanged(it))
    }
    ToggleRow("自动加入房间", "自动接受邀请，无需手动批准", CompoundIcons.Devices(), form.autoJoin, !isSubmitting && form.isPublic) {
        state.eventSink(AgentEditEvents.AutoJoinChanged(it))
    }

    SectionHeader(labels.aiEngine)
    DropdownField(
        label = "提供商",
        value = renderModel.selectedProviderLabel,
        options = renderModel.providerOptions.map { it.id to it.label },
        enabled = !isSubmitting && renderModel.providerOptions.isNotEmpty(),
        onSelect = { state.eventSink(AgentEditEvents.ProviderChanged(it)) },
    )
    if (state.availableModels.isNotEmpty()) {
        DropdownField(
            label = "模型",
            value = renderModel.selectedModelLabel,
            options = renderModel.modelOptions.map { it.id to it.label },
            enabled = !isSubmitting,
            onSelect = { state.eventSink(AgentEditEvents.ModelChanged(it)) },
        )
    } else {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = form.model,
            onValueChange = { state.eventSink(AgentEditEvents.ModelChanged(it)) },
            label = { Text("模型") },
            singleLine = true,
            enabled = !isSubmitting,
        )
    }
    if (renderModel.supportsBaseUrl) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = form.baseUrl,
            onValueChange = { state.eventSink(AgentEditEvents.BaseUrlChanged(it)) },
            label = { Text("API 地址") },
            singleLine = true,
            enabled = !isSubmitting,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
    }
    if (renderModel.needsApiKey) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = form.apiKey,
            onValueChange = { state.eventSink(AgentEditEvents.ApiKeyChanged(it)) },
            label = { Text("API 密钥") },
            singleLine = true,
            enabled = !isSubmitting,
            visualTransformation = remember { PasswordVisualTransformation() },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
    }

    SectionHeader(labels.voice)
    DropdownField(
        label = "语音",
        value = renderModel.selectedVoiceLabel,
        options = renderModel.voiceOptions.map { it.id to it.label },
        enabled = !isSubmitting,
        onSelect = { state.eventSink(AgentEditEvents.VoiceSelectionChanged(it)) },
    )

    SectionHeader(labels.soul)
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = form.soul,
        onValueChange = { state.eventSink(AgentEditEvents.SoulChanged(it)) },
        label = { Text("角色设定") },
        minLines = 4,
        enabled = !isSubmitting,
    )

    SectionHeader(labels.runtime)
    DropdownField(
        label = "运行环境",
        value = renderModel.sandbox.modeLabel,
        options = renderModel.sandbox.modeOptions.map { it.id to it.label },
        enabled = !isSubmitting,
        onSelect = { state.eventSink(AgentEditEvents.SandboxModeChanged(AgentSandboxMode.valueOf(it))) },
    )
    Text(
        text = renderModel.sandbox.description,
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
                renderModel.sandbox.warning.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
        if (state.isCreate) {
            Text("初始化方式", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            renderModel.sandbox.initOptions.forEach { option ->
                SandboxInitOption(
                    title = option.title,
                    subtitle = option.subtitle,
                    selected = option.isSelected,
                    enabled = !isSubmitting,
                    onClick = { state.eventSink(AgentEditEvents.SandboxInitMethodChanged(option.method)) },
                )
            }
        } else {
            if (renderModel.sandbox.statusLabel != null) {
                Text(
                    renderModel.sandbox.statusLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Text("尚未初始化 Agent 专属环境。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    enabled = !renderModel.sandbox.isBusy,
                    onClick = { state.eventSink(AgentEditEvents.SandboxActionRequested(AgentSandboxInitMethod.Empty)) },
                ) { Text("创建空白环境") }
                TextButton(
                    enabled = !renderModel.sandbox.isBusy,
                    onClick = { state.eventSink(AgentEditEvents.SandboxActionRequested(AgentSandboxInitMethod.CloneOwner)) },
                ) { Text("从我的环境复制") }
                if (renderModel.sandbox.isBusy) CircularProgressIndicator(modifier = Modifier.size(18.dp))
            }
            renderModel.sandbox.message?.let { message ->
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
                        hasExisting && cloning -> "覆盖运行环境"
                        hasExisting -> "替换现有环境"
                        cloning -> "复制运行环境"
                        else -> "创建专属运行环境"
                    },
                )
            },
            text = {
                Text(
                    when {
                        hasExisting && cloning -> "你当前的运行环境将覆盖 Agent 现有的环境，原有数据将被替换。"
                        hasExisting -> "Agent 已有运行环境，此操作将替换其中的全部数据。请确认没有重要文件遗留。"
                        cloning -> "将复制你当前的运行环境作为 Agent 的专属起点。所有用户将共享此环境。"
                        else -> "将为 Agent 创建一个空白、隔离的运行环境。所有用户将共享此环境，请确保不包含私有数据。"
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { state.eventSink(AgentEditEvents.SandboxActionConfirmed) }) {
                    Text(if (cloning) "复制我的环境" else "创建")
                }
            },
            dismissButton = {
                TextButton(onClick = { state.eventSink(AgentEditEvents.SandboxActionDismissed) }) { Text("取消") }
            },
        )
    }

    SectionHeader(labels.vault)
    if (renderModel.selectedVaultKeys.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            renderModel.selectedVaultKeys.forEach { item ->
                InputChip(
                    selected = true,
                    onClick = { state.eventSink(AgentEditEvents.ToggleVaultKey(item.id)) },
                    enabled = !isSubmitting,
                    label = { Text(item.label) },
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
        Text(renderModel.vaultPickerLabel)
    }
    if (!state.isCreate) {
        form.vaultEntries.forEachIndexed { index, entry ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = entry.key,
                    onValueChange = { state.eventSink(AgentEditEvents.UpdateVaultEntry(index, it, entry.value, entry.description.orEmpty())) },
                    label = { Text("键") },
                    singleLine = true,
                    enabled = !isSubmitting,
                )
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = entry.value,
                    onValueChange = { state.eventSink(AgentEditEvents.UpdateVaultEntry(index, entry.key, it, entry.description.orEmpty())) },
                    label = { Text("值") },
                    singleLine = true,
                    enabled = !isSubmitting,
                )
                IconButton(onClick = { state.eventSink(AgentEditEvents.RemoveVaultEntry(index)) }) {
                    Icon(CompoundIcons.Close(), "删除", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        OutlinedButton(onClick = { state.eventSink(AgentEditEvents.AddVaultEntry) }, enabled = !isSubmitting) {
            Icon(CompoundIcons.Plus(), null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("手动添加密钥")
        }
    }
    if (showVaultSheet) {
        VaultKeyPickerSheet(state = state, onDismiss = { showVaultSheet = false })
    }

    SectionHeader(labels.skills)
    if (renderModel.selectedSkills.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            renderModel.selectedSkills.forEach { skill ->
                InputChip(
                    selected = true,
                    onClick = { state.eventSink(AgentEditEvents.ToggleSkill(skill.id)) },
                    enabled = !isSubmitting,
                    label = { Text(skill.label) },
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
        Text(renderModel.skillPickerLabel)
    }

    if (showSkillSheet) {
        SkillPickerSheet(state = state, onDismiss = { showSkillSheet = false })
    }

    if (submittingStep != null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
            Text(
                when (submittingStep) {
                    AgentEditSubmittingStep.CreateAgent -> renderModel.submittingLabel.orEmpty()
                    AgentEditSubmittingStep.CreateDM -> renderModel.submittingLabel.orEmpty()
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
        Text(renderModel.submitLabel)
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
            Text("选择密钥", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索…") },
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
                    text = "密钥库为空，你可以在「设置 → 密钥库」中添加。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    filtered.forEach { item ->
                        ListItem(
                            modifier = Modifier.shapeAwareClickable(RoundedCornerShape(12.dp)) { state.eventSink(AgentEditEvents.ToggleVaultKey(item.key)) },
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
            Text("添加技能", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索…") },
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
                    text = "没有结果",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    filtered.forEach { skill ->
                        ListItem(
                            modifier = Modifier.shapeAwareClickable(RoundedCornerShape(12.dp)) { state.eventSink(AgentEditEvents.ToggleSkill(skill.id)) },
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
            listOfNotNull(summary.provider, summary.model).joinToString(" · ").ifBlank { "Agent 创建成功" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Button(modifier = Modifier.fillMaxWidth(), enabled = summary.directRoomId != null, onClick = { state.eventSink(AgentEditEvents.GoToChat) }) {
            Text("开始对话")
        }
        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { state.eventSink(AgentEditEvents.CreateAnother) }) {
            Text("再创建一个")
        }
    }
}

private fun availabilitySupport(availability: AgentNameAvailability): String? = when (availability) {
    AgentNameAvailability.Unknown -> null
    AgentNameAvailability.Checking -> "检查中..."
    AgentNameAvailability.Available -> "可用"
    AgentNameAvailability.Taken -> "已被占用"
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
            .shapeAwareClickable(RoundedCornerShape(12.dp), enabled = enabled, onClick = onClick)
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
    AgentSandboxMode.PerUser -> "每用户各自的环境"
    AgentSandboxMode.AgentDedicated -> "Agent 专属环境"
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

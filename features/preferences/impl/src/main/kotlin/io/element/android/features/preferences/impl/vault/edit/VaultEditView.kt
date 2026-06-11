/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package io.element.android.features.preferences.impl.vault.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight

@Composable
fun VaultEditView(
    state: VaultEditState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        state.eventSink(VaultEditEvents.OnAppear)
    }
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = CompoundIcons.ChevronLeft(), contentDescription = "返回")
                    }
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader("基本信息")

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.key,
                onValueChange = { state.eventSink(VaultEditEvents.KeyChanged(it)) },
                label = { Text("密钥") },
                placeholder = { Text("键") },
                singleLine = true,
                enabled = !state.isEditingExisting && !state.isSaving,
            )

            if (state.isLoadingValue) {
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    Text(
                        text = "正在加载值…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = state.value,
                    onValueChange = { state.eventSink(VaultEditEvents.ValueChanged(it)) },
                    label = { Text("值") },
                    placeholder = { Text("值") },
                    singleLine = true,
                    enabled = !state.isSaving,
                    visualTransformation = if (state.isValueVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions.Default,
                    trailingIcon = {
                        IconButton(onClick = { state.eventSink(VaultEditEvents.ToggleValueVisibility) }) {
                            Icon(
                                imageVector = if (state.isValueVisible) {
                                    CompoundIcons.VisibilityOff()
                                } else {
                                    CompoundIcons.VisibilityOn()
                                },
                                contentDescription = if (state.isValueVisible) "隐藏值" else "显示值",
                            )
                        }
                    },
                )
            }

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.description,
                onValueChange = { state.eventSink(VaultEditEvents.DescriptionChanged(it)) },
                label = { Text("描述（可选）") },
                minLines = 3,
                enabled = !state.isSaving,
            )

            state.error?.takeIf { it.isNotEmpty() }?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { state.eventSink(VaultEditEvents.Save) },
                enabled = !state.isSaving && !state.isLoadingValue,
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Text(state.saveButtonLabel)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

internal class VaultEditStateProvider : PreviewParameterProvider<VaultEditState> {
    override val values: Sequence<VaultEditState>
        get() = sequenceOf(
            aVaultEditState(),
            aVaultEditState(isEditingExisting = true, isLoadingValue = true),
            aVaultEditState(isEditingExisting = true, isLoadingValue = false, error = "Value不能为空。"),
        )
}

private fun aVaultEditState(
    isEditingExisting: Boolean = false,
    isLoadingValue: Boolean = false,
    error: String? = null,
) = VaultEditState(
    isEditingExisting = isEditingExisting,
    key = if (isEditingExisting) "OPENAI_API_KEY" else "",
    value = if (isEditingExisting && !isLoadingValue) "sk-1234567890" else "",
    description = if (isEditingExisting) "OpenAI 服务密钥" else "",
    isValueVisible = false,
    isLoadingValue = isLoadingValue,
    isSaving = false,
    error = error,
    eventSink = {},
)

@PreviewsDayNight
@Composable
internal fun VaultEditViewPreview(@PreviewParameter(VaultEditStateProvider::class) state: VaultEditState) = ElementPreview {
    VaultEditView(state = state, onBackClick = {})
}

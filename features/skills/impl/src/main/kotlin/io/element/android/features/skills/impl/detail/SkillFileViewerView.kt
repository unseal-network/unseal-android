/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package io.element.android.features.skills.impl.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.skills.impl.R
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
fun SkillFileViewerView(
    state: SkillFileViewerState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        state.eventSink(SkillFileViewerEvents.OnAppear)
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = state.fileName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = CompoundIcons.ChevronLeft(), contentDescription = stringResource(CommonStrings.action_go_back))
                    }
                },
                actions = {
                    if (state.canSave) {
                        if (state.isSaving) {
                            CircularProgressIndicator(modifier = Modifier.padding(end = 16.dp))
                        } else {
                            TextButton(onClick = { state.eventSink(SkillFileViewerEvents.Save) }) {
                                Text(stringResource(CommonStrings.action_save))
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                state.loadError != null -> {
                    LoadErrorContent(
                        error = state.loadError,
                        onRetry = { state.eventSink(SkillFileViewerEvents.RetryLoad) },
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                state.isEditable -> {
                    OutlinedTextField(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        value = state.content,
                        onValueChange = { state.eventSink(SkillFileViewerEvents.ContentChanged(it)) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    )
                }
                else -> {
                    SelectionContainer {
                        Text(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            text = state.content,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            if (state.showSavedToast) {
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(24.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Text(stringResource(R.string.skill_file_saved))
                }
            }
        }
    }

    state.saveError?.let { error ->
        AlertDialog(
            onDismissRequest = { state.eventSink(SkillFileViewerEvents.ClearSaveError) },
            title = { Text(stringResource(R.string.skill_file_save_failed)) },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { state.eventSink(SkillFileViewerEvents.ClearSaveError) }) {
                    Text(stringResource(CommonStrings.action_ok))
                }
            },
        )
    }
}

@Composable
private fun LoadErrorContent(
    error: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = CompoundIcons.Error(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
        )
        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onRetry) {
            Text(stringResource(R.string.skill_file_retry))
        }
    }
}

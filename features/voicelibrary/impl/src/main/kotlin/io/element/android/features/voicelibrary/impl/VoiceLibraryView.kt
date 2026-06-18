/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package io.element.android.features.voicelibrary.impl

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.chatbot.api.model.voices.ChatbotProviderVoice
import io.element.android.libraries.chatbot.api.model.voices.ChatbotVoiceProfile
import io.element.android.libraries.designsystem.components.media.WaveformPlaybackView
import io.element.android.libraries.designsystem.components.management.ManagementListRow
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun VoiceLibraryView(
    state: VoiceLibraryState,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        state.eventSink(VoiceLibraryEvents.OnAppear)
    }

    // iOS copies the share ID to the system clipboard (UIPasteboard) on Share. Mirror that here:
    // whenever a new share ID arrives in state, copy it to the clipboard. The notice is still shown.
    val clipboardManager = LocalClipboardManager.current
    LaunchedEffect(state.lastShareId) {
        state.lastShareId?.let { clipboardManager.setText(AnnotatedString(it)) }
    }

    val previewController = rememberVoicePreviewController()
    val context = LocalContext.current
    val previewFileCache = remember(context) {
        VoicePreviewFileCache(File(context.cacheDir, "VoiceLibraryPreviews"))
    }
    val recordingController = remember(context) {
        VoiceLibraryRecordingController(context.applicationContext)
    }
    DisposableEffect(recordingController) {
        onDispose { recordingController.cancel() }
    }
    LaunchedEffect(state.previewTarget) {
        val target = state.previewTarget
        if (target == null) {
            previewController.stop()
        } else {
            previewController.play(
                item = target,
                fileCache = previewFileCache,
                onPlaying = { state.eventSink(VoiceLibraryEvents.PreviewPlaying(it)) },
                onStopped = { state.eventSink(VoiceLibraryEvents.PreviewStopped) },
                onFailed = { itemId, reason -> state.eventSink(VoiceLibraryEvents.PreviewFailed(itemId, reason)) },
            )
        }
    }

    if (state.isPresentingCreateVoice) {
        CreateVoiceDialog(
            state = state,
            recordingController = recordingController,
            previewController = previewController,
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("语音库") },
                actions = {
                    TextButton(onClick = { state.eventSink(VoiceLibraryEvents.Dismiss) }) {
                        Text("完成")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TabPicker(
                selectedTab = state.selectedTab,
                onSelect = { state.eventSink(VoiceLibraryEvents.SelectTab(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                value = state.searchQuery,
                onValueChange = { state.eventSink(VoiceLibraryEvents.SearchChanged(it)) },
                placeholder = {
                    Text(
                        if (state.selectedTab == VoiceLibraryTab.Mine) "搜索我的语音" else "搜索公开语音"
                    )
                },
                leadingIcon = { Icon(CompoundIcons.Search(), contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
            )

            Notices(state)

            PullToRefreshBox(
                modifier = Modifier.fillMaxSize(),
                isRefreshing = state.isLoading,
                onRefresh = { state.eventSink(VoiceLibraryEvents.Refresh) },
            ) {
                when {
                    state.isLoading -> LoadingState()
                    state.selectedTab == VoiceLibraryTab.Mine -> MyVoices(state)
                    else -> PublicVoices(state)
                }
            }
        }
    }
}

@Composable
private fun TabPicker(
    selectedTab: VoiceLibraryTab,
    onSelect: (VoiceLibraryTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = listOf(VoiceLibraryTab.Mine to "我的", VoiceLibraryTab.Public to "公开")
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        tabs.forEachIndexed { index, (tab, label) ->
            SegmentedButton(
                selected = selectedTab == tab,
                onClick = { onSelect(tab) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun Notices(state: VoiceLibraryState) {
    state.error?.let { message ->
        NoticeCard(
            text = message,
            container = MaterialTheme.colorScheme.errorContainer,
            content = MaterialTheme.colorScheme.onErrorContainer,
            onDismiss = { state.eventSink(VoiceLibraryEvents.ClearError) },
        )
    }
    state.lastShareId?.let { shareId ->
        NoticeCard(
            text = "分享 ID 已复制：$shareId",
            container = MaterialTheme.colorScheme.tertiaryContainer,
            content = MaterialTheme.colorScheme.onTertiaryContainer,
            onDismiss = { state.eventSink(VoiceLibraryEvents.ClearShareId) },
        )
    }
    state.deleteNotice?.let { notice ->
        NoticeCard(
            text = notice,
            container = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.onSecondaryContainer,
            onDismiss = { state.eventSink(VoiceLibraryEvents.ClearDeleteNotice) },
        )
    }
}

@Composable
private fun NoticeCard(
    text: String,
    container: Color,
    content: Color,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        color = container,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = content,
            )
            IconButton(onClick = onDismiss) {
                Icon(CompoundIcons.Close(), contentDescription = "关闭", tint = content)
            }
        }
    }
}

@Composable
private fun LoadingState() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(count = 5) { SkeletonRow() }
    }
}

@Composable
private fun MyVoices(state: VoiceLibraryState) {
    val profiles = state.filteredProfiles
    Column(modifier = Modifier.fillMaxSize()) {
        CreateVoiceRow(state)
        ImportRow(state)
        if (profiles.isEmpty()) {
            EmptyState("暂无保存的语音")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item {
                    SectionHeader(title = "我的语音", trailing = null)
                }
                items(profiles, key = { it.id }) { profile ->
                    ProfileRow(state, profile)
                }
            }
        }
    }
}

@Composable
private fun CreateVoiceRow(state: VoiceLibraryState) {
    ManagementListRow(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp),
        title = "创建语音克隆",
        description = "录制一段样本并上传到语音库",
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = CompoundIcons.MicOn(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        },
        trailingContent = {
            FilledTonalButton(onClick = { state.eventSink(VoiceLibraryEvents.ShowCreateVoice) }) {
                Text("录制")
            }
        },
    )
}

@Composable
private fun ImportRow(state: VoiceLibraryState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            modifier = Modifier.weight(1f),
            value = state.importShareId,
            onValueChange = { state.eventSink(VoiceLibraryEvents.ImportShareChanged(it)) },
            label = { Text("导入分享 ID") },
            singleLine = true,
        )
        Button(
            enabled = state.busyId != "import" && state.importShareId.isNotBlank(),
            onClick = { state.eventSink(VoiceLibraryEvents.ImportShare) },
        ) {
            Text(if (state.busyId == "import") "导入中…" else "导入")
        }
    }
}

@Composable
private fun CreateVoiceDialog(
    state: VoiceLibraryState,
    recordingController: VoiceLibraryRecordingController,
    previewController: VoicePreviewController,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    fun failRecording(reason: String) {
        recordingController.cancel()
        state.eventSink(VoiceLibraryEvents.RecordingFailed(reason))
    }

    fun startRecording() {
        state.eventSink(VoiceLibraryEvents.StartRecording)
        runCatching {
            recordingController.start()
        }.onSuccess {
            state.eventSink(VoiceLibraryEvents.RecordingStarted)
        }.onFailure {
            failRecording(it.message ?: "Unable to start microphone recording.")
        }
    }

    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startRecording()
        } else {
            failRecording("Microphone permission is required to record a voice.")
        }
    }

    fun requestStartRecording() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            startRecording()
        } else {
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun stopRecording() {
        coroutineScope.launch {
            runCatching {
                recordingController.stop(state.recordingName)
            }.onSuccess { sample ->
                state.eventSink(VoiceLibraryEvents.RecordingReady(sample))
            }.onFailure {
                failRecording(it.message ?: "Unable to stop microphone recording.")
            }
        }
    }

    fun clearRecordedSample() {
        previewController.stop()
        recordingController.deleteSample(state.recordingSample)
    }

    LaunchedEffect(state.isRecordingPreviewPlaying, state.recordingSample?.localFilePath) {
        val sample = state.recordingSample
        if (!state.isRecordingPreviewPlaying) {
            previewController.stop()
        } else {
            previewController.playLocalFile(
                filePath = sample?.localFilePath.orEmpty(),
                onPlaying = { state.eventSink(VoiceLibraryEvents.RecordingPreviewPlaying) },
                onStopped = { state.eventSink(VoiceLibraryEvents.RecordingPreviewStopped) },
                onFailed = { state.eventSink(VoiceLibraryEvents.RecordingPreviewFailed(it)) },
                onProgress = { positionMillis, durationMillis ->
                    state.eventSink(VoiceLibraryEvents.RecordingPreviewProgress(positionMillis, durationMillis))
                },
            )
        }
    }

    AlertDialog(
        onDismissRequest = {
            recordingController.cancel()
            clearRecordedSample()
            state.eventSink(VoiceLibraryEvents.DismissCreateVoice)
        },
        title = { Text("创建语音") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = state.recordingName,
                    onValueChange = { state.eventSink(VoiceLibraryEvents.RecordingNameChanged(it)) },
                    label = { Text("语音名称") },
                    singleLine = true,
                    enabled = state.busyId != VoiceLibraryBusyIds.RecordingUpload,
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (state.isStartingRecording) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                            Text("正在启动麦克风…", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            Icon(
                                imageVector = when (state.recordingState) {
                                    VoiceLibraryRecordingState.Recording -> CompoundIcons.Stop()
                                    VoiceLibraryRecordingState.Recorded -> CompoundIcons.CheckCircle()
                                    VoiceLibraryRecordingState.Idle -> CompoundIcons.MicOn()
                                },
                                contentDescription = null,
                                tint = when (state.recordingState) {
                                    VoiceLibraryRecordingState.Recording -> MaterialTheme.colorScheme.error
                                    VoiceLibraryRecordingState.Recorded -> MaterialTheme.colorScheme.primary
                                    VoiceLibraryRecordingState.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(36.dp),
                            )
                            Text(
                                text = when (state.recordingState) {
                                    VoiceLibraryRecordingState.Idle -> "点击开始录音"
                                    VoiceLibraryRecordingState.Recording -> "录音中，完成后点击停止"
                                    VoiceLibraryRecordingState.Recorded -> "录音已准备好，可以上传"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }

                        state.recordingSample?.let { sample ->
                            RecordingWaveformPreview(
                                state = state,
                                sample = sample,
                                onSeek = { progress ->
                                    state.eventSink(VoiceLibraryEvents.RecordingPreviewScrubbing(true))
                                    state.eventSink(VoiceLibraryEvents.SeekRecordingPreview(progress))
                                    previewController.seekToProgress(progress)
                                    state.eventSink(VoiceLibraryEvents.RecordingPreviewScrubbing(false))
                                },
                                onTogglePlayback = { state.eventSink(VoiceLibraryEvents.ToggleRecordingPreview) },
                            )
                        }
                    }
                }

                state.recordingValidationMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            when (state.recordingState) {
                VoiceLibraryRecordingState.Idle -> Button(
                    enabled = !state.isStartingRecording,
                    onClick = ::requestStartRecording,
                ) {
                    Text(if (state.isStartingRecording) "启动中…" else "开始录音")
                }
                VoiceLibraryRecordingState.Recording -> Button(onClick = ::stopRecording) {
                    Text("停止")
                }
                VoiceLibraryRecordingState.Recorded -> Button(
                    enabled = state.canUploadRecording,
                    onClick = {
                        previewController.stop()
                        state.eventSink(VoiceLibraryEvents.UploadCurrentRecording)
                    },
                ) {
                    Text(if (state.busyId == VoiceLibraryBusyIds.RecordingUpload) "上传中…" else "上传")
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.recordingState == VoiceLibraryRecordingState.Recorded) {
                    TextButton(onClick = {
                        recordingController.cancel()
                        clearRecordedSample()
                        state.eventSink(VoiceLibraryEvents.DiscardRecording)
                    }) {
                        Text("重录")
                    }
                }
                TextButton(onClick = {
                    recordingController.cancel()
                    clearRecordedSample()
                    state.eventSink(VoiceLibraryEvents.DismissCreateVoice)
                }) {
                    Text("取消")
                }
            }
        },
    )
}

@Composable
private fun RecordingWaveformPreview(
    state: VoiceLibraryState,
    sample: VoiceLibraryRecordingSample,
    onSeek: (Float) -> Unit,
    onTogglePlayback: () -> Unit,
) {
    val isInteractive = sample.localFilePath != null && state.busyId != VoiceLibraryBusyIds.RecordingUpload
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ElementTheme.colors.bgSubtleSecondary,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                IconButton(
                    enabled = isInteractive,
                    onClick = onTogglePlayback,
                ) {
                    Icon(
                        imageVector = if (state.isRecordingPreviewPlaying) CompoundIcons.Pause() else CompoundIcons.Play(),
                        contentDescription = if (state.isRecordingPreviewPlaying) "停止试听" else "试听录音",
                        tint = if (isInteractive) ElementTheme.colors.iconSecondary else ElementTheme.colors.iconDisabled,
                    )
                }
                Text(
                    text = formatDuration(state.recordingPreviewPositionMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                WaveformPlaybackView(
                    modifier = Modifier
                        .weight(1f)
                        .height(30.dp),
                    playbackProgress = state.recordingPreviewProgress,
                    showCursor = state.isRecordingPreviewPlaying ||
                        state.isRecordingPreviewScrubbing ||
                        state.recordingPreviewProgress > 0f,
                    waveform = sample.waveform,
                    seekEnabled = isInteractive,
                    onSeek = onSeek,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Recording",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${formatDuration(sample.durationMillis)} · ${formatFileSize(sample.fileSizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ProfileRow(state: VoiceLibraryState, profile: ChatbotVoiceProfile) {
    val isBusy = state.busyId == profile.id
    ManagementListRow(
        modifier = Modifier.padding(vertical = 4.dp),
        title = profile.displayName,
        subtitle = "${profile.sourceType.replace('_', ' ')} · ${profile.provider}".trim().trimStart('·', ' '),
        description = profile.description,
        leadingContent = {
            VoiceThumbnail(seed = profile.id)
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PreviewControl(state = state, item = VoiceLibraryPreviewItem.fromProfile(profile))
                if (state.deleteConfirmationProfileId == profile.id) {
                    Column(horizontalAlignment = Alignment.End) {
                        TextButton(onClick = { state.eventSink(VoiceLibraryEvents.ConfirmDelete) }) {
                            Text("删除", color = MaterialTheme.colorScheme.error)
                        }
                        TextButton(onClick = { state.eventSink(VoiceLibraryEvents.CancelDelete) }) {
                            Text("取消")
                        }
                    }
                } else {
                    ProfileOverflowMenu(
                        enabled = !isBusy,
                        onShare = { state.eventSink(VoiceLibraryEvents.ShareVoice(profile.id)) },
                        onDelete = { state.eventSink(VoiceLibraryEvents.RequestDelete(profile.id)) },
                    )
                }
            }
        },
    )
}

@Composable
private fun ProfileOverflowMenu(
    enabled: Boolean,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(enabled = enabled, onClick = { expanded = true }) {
            Icon(CompoundIcons.Share(), contentDescription = "语音操作")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("分享") },
                leadingIcon = { Icon(CompoundIcons.Share(), contentDescription = null) },
                onClick = {
                    expanded = false
                    onShare()
                },
            )
            DropdownMenuItem(
                text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                leadingIcon = {
                    Icon(CompoundIcons.Delete(), contentDescription = null, tint = MaterialTheme.colorScheme.error)
                },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun PublicVoices(state: VoiceLibraryState) {
    val catalog = state.filteredCatalog
    if (catalog.isEmpty()) {
        EmptyState("暂无公开语音")
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                // Trailing count mirrors iOS, which shows the full (unfiltered) catalog size.
                SectionHeader(title = "公开语音", trailing = state.catalog.size.toString())
            }
            items(catalog, key = { it.providerVoiceId }) { voice ->
                CatalogRow(state, voice)
            }
        }
    }
}

@Composable
private fun CatalogRow(state: VoiceLibraryState, voice: ChatbotProviderVoice) {
    val isSaved = state.profiles.any { it.provider == voice.provider && it.providerVoiceId == voice.providerVoiceId }
    val isBusy = state.busyId == voice.providerVoiceId
    ManagementListRow(
        modifier = Modifier.padding(vertical = 4.dp),
        title = voice.displayName,
        subtitle = voice.provider,
        description = voice.description,
        leadingContent = {
            VoiceThumbnail(seed = voice.providerVoiceId)
        },
        titleTrailingContent = {
            if (isSaved) {
                SavedBadge()
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PreviewControl(state = state, item = VoiceLibraryPreviewItem.fromProviderVoice(voice))
                FilledTonalButton(
                    enabled = !isSaved && !isBusy,
                    onClick = { state.eventSink(VoiceLibraryEvents.SaveVoice(voice)) },
                ) {
                    Text(
                        when {
                            isSaved -> "已保存"
                            isBusy -> "保存中…"
                            else -> "保存"
                        }
                    )
                }
            }
        },
    )
}

/**
 * Preview affordance mirroring iOS `togglePreview`: streams the preview URL with a shared
 * MediaPlayer and shows play / pause / loading per row.
 */
@Composable
private fun PreviewControl(state: VoiceLibraryState, item: VoiceLibraryPreviewItem) {
    val hasPreview = item.hasPreview
    val isPlaying = hasPreview && state.remotePreviewId == item.id
    val isLoading = hasPreview && state.loadingPreviewId == item.id
    IconButton(
        enabled = true,
        onClick = { state.eventSink(VoiceLibraryEvents.TogglePreview(item)) },
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Icon(
                imageVector = when {
                    !hasPreview -> CompoundIcons.VolumeOff()
                    isPlaying -> CompoundIcons.Pause()
                    else -> CompoundIcons.Play()
                },
                contentDescription = if (hasPreview) "试听语音" else "暂无试听",
                tint = if (hasPreview) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                },
            )
        }
    }
}

@Composable
private fun VoiceThumbnail(seed: String) {
    val container = MaterialTheme.colorScheme.secondaryContainer
    val content = MaterialTheme.colorScheme.onSecondaryContainer
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(container, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        // Deterministic glyph derived from the seed (no random / time) mirroring the iOS waveform tile.
        Text(
            text = seed.firstOrNull()?.uppercaseChar()?.toString() ?: "V",
            style = MaterialTheme.typography.titleMedium,
            color = content,
        )
    }
}

@Composable
private fun SavedBadge() {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = CircleShape,
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            text = "已保存",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

@Composable
private fun SectionHeader(title: String, trailing: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        trailing?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun formatDuration(durationMillis: Long): String {
    val totalSeconds = (durationMillis / 1_000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun formatFileSize(fileSizeBytes: Long): String {
    if (fileSizeBytes <= 0) return "0 KB"
    val kb = fileSizeBytes / 1024.0
    return if (kb < 1024) {
        "%.1f KB".format(kb)
    } else {
        "%.1f MB".format(kb / 1024.0)
    }
}

@Composable
private fun SkeletonRow() {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(900), repeatMode = RepeatMode.Reverse),
        label = "skeleton-alpha",
    )
    val placeholder = MaterialTheme.colorScheme.surfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(placeholder.copy(alpha = alpha), RoundedCornerShape(12.dp))
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .size(width = 120.dp, height = 14.dp)
                    .background(placeholder.copy(alpha = alpha), RoundedCornerShape(4.dp))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .size(width = 200.dp, height = 10.dp)
                    .background(placeholder.copy(alpha = alpha), RoundedCornerShape(4.dp))
            )
        }
        Spacer(modifier = Modifier.size(24.dp))
    }
}

internal class VoiceLibraryStateProvider : PreviewParameterProvider<VoiceLibraryState> {
    override val values: Sequence<VoiceLibraryState>
        get() = sequenceOf(
            aVoiceLibraryState(),
            aVoiceLibraryState(selectedTab = VoiceLibraryTab.Public),
            aVoiceLibraryState(isLoading = true),
            aVoiceLibraryState(profiles = persistentListOf(), error = "Failed to load voices"),
            aVoiceLibraryState(
                deleteConfirmationProfileId = "profile-1",
                lastShareId = "share-abc123",
                deleteNotice = "Deleted voice and cleared 2 agent voice bindings.",
            ),
            aVoiceLibraryState(
                isPresentingCreateVoice = true,
                recordingName = "Recorded voice",
                recordingState = VoiceLibraryRecordingState.Recorded,
                recordingSample = VoiceLibraryRecordingSample(
                    displayName = "Recorded voice",
                    audioBase64 = "YWJj",
                    durationMillis = 12_000,
                    fileSizeBytes = 256_000,
                ),
            ),
        )
}

private fun aVoiceLibraryState(
    selectedTab: VoiceLibraryTab = VoiceLibraryTab.Mine,
    profiles: ImmutableList<ChatbotVoiceProfile> = aSampleProfiles(),
    catalog: ImmutableList<ChatbotProviderVoice> = aSampleCatalog(),
    searchQuery: String = "",
    importShareId: String = "",
    isPresentingCreateVoice: Boolean = false,
    recordingName: String = "",
    recordingValidationMessage: String? = null,
    isStartingRecording: Boolean = false,
    recordingState: VoiceLibraryRecordingState = VoiceLibraryRecordingState.Idle,
    recordingSample: VoiceLibraryRecordingSample? = null,
    isRecordingPreviewPlaying: Boolean = false,
    isRecordingPreviewScrubbing: Boolean = false,
    recordingPreviewProgress: Float = 0f,
    recordingPreviewPositionMillis: Long = 0L,
    isLoading: Boolean = false,
    busyId: String? = null,
    deleteConfirmationProfileId: String? = null,
    lastShareId: String? = null,
    deleteNotice: String? = null,
    previewTarget: VoiceLibraryPreviewItem? = null,
    loadingPreviewId: String? = null,
    remotePreviewId: String? = null,
    error: String? = null,
) = VoiceLibraryState(
    selectedTab = selectedTab,
    profiles = profiles,
    catalog = catalog,
    searchQuery = searchQuery,
    importShareId = importShareId,
    isPresentingCreateVoice = isPresentingCreateVoice,
    recordingName = recordingName,
    recordingValidationMessage = recordingValidationMessage,
    isStartingRecording = isStartingRecording,
    recordingState = recordingState,
    recordingSample = recordingSample,
    isRecordingPreviewPlaying = isRecordingPreviewPlaying,
    isRecordingPreviewScrubbing = isRecordingPreviewScrubbing,
    recordingPreviewProgress = recordingPreviewProgress,
    recordingPreviewPositionMillis = recordingPreviewPositionMillis,
    isLoading = isLoading,
    busyId = busyId,
    deleteConfirmationProfileId = deleteConfirmationProfileId,
    lastShareId = lastShareId,
    deleteNotice = deleteNotice,
    previewTarget = previewTarget,
    loadingPreviewId = loadingPreviewId,
    remotePreviewId = remotePreviewId,
    error = error,
    eventSink = {},
)

private fun aSampleProfiles() = persistentListOf(
    ChatbotVoiceProfile(
        id = "profile-1",
        provider = "elevenlabs",
        providerVoiceId = "voice-aria",
        displayName = "Aria",
        description = "Warm narration voice cloned from a studio sample.",
        previewUrl = "https://example.com/aria.mp3",
        sourceType = "voice_clone",
    ),
    ChatbotVoiceProfile(
        id = "profile-2",
        provider = "elevenlabs",
        providerVoiceId = "voice-river",
        displayName = "River",
        description = null,
        previewUrl = null,
        sourceType = "saved_provider_voice",
    ),
).toImmutableList()

private fun aSampleCatalog() = persistentListOf(
    ChatbotProviderVoice(
        provider = "elevenlabs",
        providerVoiceId = "voice-aria",
        displayName = "Aria",
        description = "A confident, expressive English voice.",
        previewUrl = "https://example.com/aria.mp3",
    ),
    ChatbotProviderVoice(
        provider = "elevenlabs",
        providerVoiceId = "voice-sol",
        displayName = "Sol",
        description = "Calm and measured, ideal for long-form reading.",
        previewUrl = null,
    ),
).toImmutableList()

@PreviewsDayNight
@Composable
internal fun VoiceLibraryViewPreview(
    @PreviewParameter(VoiceLibraryStateProvider::class) state: VoiceLibraryState,
) = ElementPreview {
    VoiceLibraryView(state = state)
}

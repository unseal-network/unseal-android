/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.skills.impl.create

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import com.bumble.appyx.core.plugin.plugins
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.libraries.di.SessionScope

@ContributesNode(SessionScope::class)
@AssistedInject
class SkillCreateNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    presenterFactory: SkillCreatePresenter.Factory,
) : Node(buildContext, plugins = plugins) {
    interface Callback : Plugin {
        fun onDone()
        fun onViewDetail(id: String)
    }

    private val callback = plugins<Callback>().first()
    private val presenter = presenterFactory.create(
        navigator = object : SkillCreateNavigator {
            override fun onViewDetail(id: String) = callback.onViewDetail(id)
            override fun onBackToList() = callback.onDone()
        }
    )

    @Composable
    override fun View(modifier: Modifier) {
        val state = presenter.present()
        val context = LocalContext.current

        // File / zip pickers are registered here (not inside the screen composable) so that
        // SkillCreateView stays free of activity-result wiring and Paparazzi previews don't crash.
        fun resolveFileName(uri: Uri): String {
            val resolver = context.contentResolver
            return runCatching {
                resolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
                }
            }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file"
        }

        fun readBytes(uri: Uri): ByteArray? =
            runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()

        val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                val bytes = readBytes(uri)
                if (bytes != null) {
                    state.eventSink(SkillCreateEvents.FilePicked(resolveFileName(uri), bytes))
                }
            }
        }
        val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                val bytes = readBytes(uri)
                if (bytes != null) {
                    state.eventSink(SkillCreateEvents.ZipPicked(resolveFileName(uri), bytes))
                }
            }
        }

        SkillCreateView(
            state = state,
            onBackClick = callback::onDone,
            onPickFile = { filePicker.launch("*/*") },
            onPickZip = { zipPicker.launch("application/zip") },
            modifier = modifier,
        )
    }
}

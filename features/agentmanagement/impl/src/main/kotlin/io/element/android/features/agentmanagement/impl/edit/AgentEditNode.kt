/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.agentmanagement.impl.edit

import android.os.Parcelable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.parcelize.Parcelize

@ContributesNode(SessionScope::class)
@AssistedInject
class AgentEditNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    presenterFactory: AgentEditPresenter.Factory,
) : Node(buildContext, plugins = plugins) {
    sealed interface Inputs : Plugin, Parcelable {
        @Parcelize
        data object Create : Inputs

        @Parcelize
        data class Edit(val botName: String) : Inputs
    }

    interface Callback : Plugin {
        fun onDone()
        fun onCreated(botName: String, directRoomId: RoomId?)
        fun onUpdated(botName: String)
    }

    private val inputs = plugins<Inputs>().first()
    private val callback = plugins<Callback>().first()
    private val presenter = presenterFactory.create(
        mode = when (inputs) {
            Inputs.Create -> AgentEditMode.Create
            is Inputs.Edit -> AgentEditMode.Edit(inputs.botName)
        },
        navigator = object : AgentEditNavigator {
            override fun onCreated(botName: String, directRoomId: RoomId?) = callback.onCreated(botName, directRoomId)
            override fun onUpdated(botName: String) = callback.onUpdated(botName)
        }
    )

    @Composable
    override fun View(modifier: Modifier) {
        val state = presenter.present()
        val context = LocalContext.current
        val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                val resolver = context.contentResolver
                val bytes = runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                val mimeType = resolver.getType(uri) ?: "image/jpeg"
                if (bytes != null) state.eventSink(AgentEditEvents.AvatarPicked(bytes, mimeType))
            }
        }
        AgentEditView(
            state = state,
            onBackClick = callback::onDone,
            onSetAvatar = { avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            modifier = modifier,
        )
    }
}

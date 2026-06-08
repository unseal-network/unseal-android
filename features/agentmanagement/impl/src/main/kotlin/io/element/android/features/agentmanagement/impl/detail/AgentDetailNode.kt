/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.agentmanagement.impl.detail

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.core.RoomIdOrAlias
import kotlinx.parcelize.Parcelize

@ContributesNode(SessionScope::class)
@AssistedInject
class AgentDetailNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
) : Node(buildContext, plugins = plugins) {
    @Parcelize
    data class Inputs(val botName: String) : Plugin, Parcelable

    interface Callback : Plugin {
        fun onDone()
        fun onEdit(botName: String)
        fun onOpenRoom(roomIdOrAlias: RoomIdOrAlias)
        fun onOpenSkills(botName: String)
    }

    @Composable
    override fun View(modifier: Modifier) = Unit
}

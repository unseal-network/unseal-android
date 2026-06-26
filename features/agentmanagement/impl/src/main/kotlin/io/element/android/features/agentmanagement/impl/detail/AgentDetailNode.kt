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
import com.bumble.appyx.core.plugin.plugins
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
    presenterFactory: AgentDetailPresenter.Factory,
) : Node(buildContext, plugins = plugins) {
    @Parcelize
    data class Inputs(val botName: String) : Plugin, Parcelable

    interface Callback : Plugin {
        fun onDone()
        fun onEdit(botName: String)
        fun onOpenRoom(roomIdOrAlias: RoomIdOrAlias)
        fun onOpenSkills(botName: String)
        fun onManageChannels(agentId: String)
    }

    private val inputs = plugins<Inputs>().first()
    private val callback = plugins<Callback>().first()
    private val presenter = presenterFactory.create(
        botName = inputs.botName,
        initialMatrixUserId = null,
        navigator = object : AgentDetailNavigator {
            override fun onEdit(botName: String) = callback.onEdit(botName)
            override fun onOpenRoom(roomIdOrAlias: RoomIdOrAlias) = callback.onOpenRoom(roomIdOrAlias)
            override fun onOpenSkills(botName: String) = callback.onOpenSkills(botName)
            override fun onManageChannels(agentId: String) = callback.onManageChannels(agentId)
        }
    )

    @Composable
    override fun View(modifier: Modifier) {
        AgentDetailView(
            state = presenter.present(),
            onBackClick = callback::onDone,
            modifier = modifier,
        )
    }
}

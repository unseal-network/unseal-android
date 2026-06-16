/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.agentmanagement.impl.profile

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
import io.element.android.features.agentmanagement.impl.detail.AgentDetailNavigator
import io.element.android.features.agentmanagement.impl.detail.AgentDetailPresenter
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.core.RoomIdOrAlias
import kotlinx.parcelize.Parcelize

/**
 * Read-only agent profile shown when an agent is opened from a room (instead of the normal Matrix
 * user profile). Reuses [AgentDetailPresenter] as the data layer (agent + skills + rooms) and only
 * swaps the rendering to a profile-styled [AgentProfileView], mirroring the unseal-webapp agent
 * showcase page (identity / connect link / skills / rooms).
 */
@ContributesNode(SessionScope::class)
@AssistedInject
class AgentProfileNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    presenterFactory: AgentDetailPresenter.Factory,
) : Node(buildContext, plugins = plugins) {
    @Parcelize
    data class Inputs(val botName: String, val matrixUserId: String? = null) : Plugin, Parcelable

    interface Callback : Plugin {
        fun onDone()
        fun onEdit(botName: String)
        fun onOpenRoom(roomIdOrAlias: RoomIdOrAlias)
        fun onOpenSkills(botName: String)
    }

    private val inputs = plugins<Inputs>().first()
    private val callback = plugins<Callback>().first()
    private val presenter = presenterFactory.create(
        botName = inputs.botName,
        initialMatrixUserId = inputs.matrixUserId,
        navigator = object : AgentDetailNavigator {
            override fun onEdit(botName: String) = callback.onEdit(botName)
            override fun onOpenRoom(roomIdOrAlias: RoomIdOrAlias) = callback.onOpenRoom(roomIdOrAlias)
            override fun onOpenSkills(botName: String) = callback.onOpenSkills(botName)
        }
    )

    @Composable
    override fun View(modifier: Modifier) {
        AgentProfileView(
            state = presenter.present(),
            onBackClick = callback::onDone,
            modifier = modifier,
        )
    }
}

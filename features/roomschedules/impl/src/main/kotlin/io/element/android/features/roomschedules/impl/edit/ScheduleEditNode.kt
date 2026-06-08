/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.roomschedules.impl.edit

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
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.JoinedRoom

@ContributesNode(SessionScope::class)
@AssistedInject
class ScheduleEditNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    presenterFactory: ScheduleEditPresenter.Factory,
) : Node(buildContext, plugins = plugins) {
    data class Inputs(
        val mode: ScheduleEditMode,
        val roomId: RoomId,
        val joinedRoom: JoinedRoom,
    ) : Plugin

    interface Callback : Plugin {
        fun onSaved()
        fun onCancelled()
    }

    private val inputs = plugins<Inputs>().first()
    private val callback = plugins<Callback>().first()
    private val presenter = presenterFactory.create(
        mode = inputs.mode,
        roomId = inputs.roomId,
        joinedRoom = inputs.joinedRoom,
        navigator = object : ScheduleEditNavigator {
            override fun onSaved() = callback.onSaved()
            override fun onCancelled() = callback.onCancelled()
        },
    )

    @Composable
    override fun View(modifier: Modifier) {
        ScheduleEditView(
            state = presenter.present(),
            modifier = modifier,
        )
    }
}

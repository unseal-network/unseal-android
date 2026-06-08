/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.roomschedules.impl.config

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import com.bumble.appyx.core.plugin.plugins
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.features.roomschedules.impl.edit.ScheduleEditMode
import io.element.android.libraries.chatbot.api.model.schedules.ChatbotSchedule
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.JoinedRoom
import kotlinx.coroutines.flow.SharedFlow

@ContributesNode(SessionScope::class)
@AssistedInject
class RoomSchedulesNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    presenterFactory: RoomSchedulesPresenter.Factory,
) : Node(buildContext, plugins = plugins) {
    data class Inputs(
        val roomId: RoomId,
        val roomName: String,
        val joinedRoom: JoinedRoom,
        val reloadRequests: SharedFlow<Unit>,
    ) : Plugin

    interface Callback : Plugin {
        fun onDone()
        fun onCreateSchedule()
        fun onEditSchedule(mode: ScheduleEditMode.Edit)
        fun onSchedulesChanged()
    }

    private val inputs = plugins<Inputs>().first()
    private val callback = plugins<Callback>().first()
    private val presenter = presenterFactory.create(
        roomId = inputs.roomId,
        roomName = inputs.roomName,
        joinedRoom = inputs.joinedRoom,
        navigator = object : RoomSchedulesNavigator {
            override fun onCreateSchedule() = callback.onCreateSchedule()
            override fun onEditSchedule(schedule: ChatbotSchedule) = callback.onEditSchedule(ScheduleEditMode.Edit(schedule))
            override fun onDone() = callback.onDone()
            override fun onSchedulesChanged() = callback.onSchedulesChanged()
        },
    )

    @Composable
    override fun View(modifier: Modifier) {
        val state = presenter.present()
        val latestState by rememberUpdatedState(state)
        LaunchedEffect(Unit) {
            inputs.reloadRequests.collect {
                latestState.eventSink(RoomSchedulesEvents.Refresh)
            }
        }
        RoomSchedulesView(
            state = state,
            modifier = modifier,
        )
    }
}

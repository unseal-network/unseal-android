/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.roomschedules.impl

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import com.bumble.appyx.navmodel.backstack.BackStack
import com.bumble.appyx.navmodel.backstack.operation.pop
import com.bumble.appyx.navmodel.backstack.operation.push
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.features.roomschedules.api.RoomSchedulesEntryPoint
import io.element.android.features.roomschedules.impl.config.RoomSchedulesNode
import io.element.android.features.roomschedules.impl.edit.ScheduleEditMode
import io.element.android.features.roomschedules.impl.edit.ScheduleEditNode
import io.element.android.libraries.architecture.BackstackView
import io.element.android.libraries.architecture.BaseFlowNode
import io.element.android.libraries.architecture.appyx.canPop
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.architecture.createNode
import io.element.android.libraries.di.SessionScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.parcelize.Parcelize

@ContributesNode(SessionScope::class)
@AssistedInject
class RoomSchedulesFlowNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
) : BaseFlowNode<RoomSchedulesFlowNode.NavTarget>(
    backstack = BackStack(
        initialElement = plugins.filterIsInstance<RoomSchedulesEntryPoint.Params>().first().initialTarget.toNavTarget(),
        savedStateMap = buildContext.savedStateMap,
    ),
    buildContext = buildContext,
    plugins = plugins,
) {
    sealed interface NavTarget : Parcelable {
        @Parcelize
        data object RoomAiConfig : NavTarget

        @Parcelize
        data class EditSchedule(val mode: ScheduleEditMode) : NavTarget
    }

    private val params = plugins.filterIsInstance<RoomSchedulesEntryPoint.Params>().first()
    private val callback: RoomSchedulesEntryPoint.Callback = callback()
    private val reloadRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun resolve(navTarget: NavTarget, buildContext: BuildContext): Node {
        return when (navTarget) {
            NavTarget.RoomAiConfig -> createNode<RoomSchedulesNode>(
                buildContext = buildContext,
                plugins = listOf(
                    RoomSchedulesNode.Inputs(
                        roomId = params.roomId,
                        roomName = params.roomName,
                        joinedRoom = params.joinedRoom,
                        reloadRequests = reloadRequests,
                    ),
                    roomSchedulesCallback,
                ),
            )
            is NavTarget.EditSchedule -> createNode<ScheduleEditNode>(
                buildContext = buildContext,
                plugins = listOf(
                    ScheduleEditNode.Inputs(
                        mode = navTarget.mode,
                        roomId = params.roomId,
                        joinedRoom = params.joinedRoom,
                    ),
                    scheduleEditCallback,
                ),
            )
        }
    }

    @Composable
    override fun View(modifier: Modifier) {
        BackstackView(modifier)
    }

    private fun closeOrPop() {
        if (backstack.canPop()) {
            backstack.pop()
        } else {
            callback.onDone()
        }
    }

    private fun notifySchedulesChanged() {
        callback.onSchedulesChanged()
        reloadRequests.tryEmit(Unit)
    }

    private val roomSchedulesCallback = object : RoomSchedulesNode.Callback {
        override fun onDone() = closeOrPop()

        override fun onCreateSchedule() {
            backstack.push(NavTarget.EditSchedule(ScheduleEditMode.Create))
        }

        override fun onEditSchedule(mode: ScheduleEditMode.Edit) {
            backstack.push(NavTarget.EditSchedule(mode))
        }

        override fun onSchedulesChanged() = notifySchedulesChanged()
    }

    private val scheduleEditCallback = object : ScheduleEditNode.Callback {
        override fun onSaved() {
            notifySchedulesChanged()
            closeOrPop()
        }

        override fun onCancelled() = closeOrPop()
    }
}

private fun RoomSchedulesEntryPoint.InitialTarget.toNavTarget(): RoomSchedulesFlowNode.NavTarget = when (this) {
    RoomSchedulesEntryPoint.InitialTarget.RoomAiConfig -> RoomSchedulesFlowNode.NavTarget.RoomAiConfig
}

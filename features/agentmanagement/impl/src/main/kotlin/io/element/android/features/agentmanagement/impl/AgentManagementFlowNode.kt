/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.agentmanagement.impl

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
import io.element.android.features.agentmanagement.api.AgentManagementEntryPoint
import io.element.android.features.agentmanagement.impl.detail.AgentDetailNode
import io.element.android.features.agentmanagement.impl.profile.AgentProfileNode
import io.element.android.features.agentmanagement.impl.edit.AgentEditNode
import io.element.android.features.agentmanagement.impl.list.AgentListNode
import io.element.android.libraries.architecture.BackstackView
import io.element.android.libraries.architecture.BaseFlowNode
import io.element.android.libraries.architecture.appyx.canPop
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.architecture.createNode
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.RoomIdOrAlias
import kotlinx.parcelize.Parcelize

@ContributesNode(SessionScope::class)
@AssistedInject
class AgentManagementFlowNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
) : BaseFlowNode<AgentManagementFlowNode.NavTarget>(
    backstack = BackStack(
        initialElement = plugins.filterIsInstance<AgentManagementEntryPoint.Params>().first().initialTarget.toNavTarget(),
        savedStateMap = buildContext.savedStateMap,
    ),
    buildContext = buildContext,
    plugins = plugins,
) {
    sealed interface NavTarget : Parcelable {
        @Parcelize
        data object List : NavTarget

        @Parcelize
        data class Detail(val botName: String) : NavTarget

        @Parcelize
        data class Profile(val botName: String, val matrixUserId: String? = null) : NavTarget

        @Parcelize
        data object Create : NavTarget

        @Parcelize
        data class Edit(val botName: String) : NavTarget
    }

    private val callback: AgentManagementEntryPoint.Callback = callback()

    override fun resolve(navTarget: NavTarget, buildContext: BuildContext): Node {
        return when (navTarget) {
            NavTarget.List -> createNode<AgentListNode>(
                buildContext = buildContext,
                plugins = listOf(
                    object : AgentListNode.Callback {
                        override fun onDone() = closeFlow()
                        override fun onCreateAgent() = backstack.push(NavTarget.Create)
                        override fun onOpenAgent(botName: String) = backstack.push(NavTarget.Detail(botName))
                        override fun onOpenSkills() = callback.onOpenSkills(null)
                    }
                ),
            )
            is NavTarget.Detail -> createNode<AgentDetailNode>(
                buildContext = buildContext,
                plugins = listOf(
                    AgentDetailNode.Inputs(navTarget.botName),
                    object : AgentDetailNode.Callback {
                        override fun onDone() = closeOrPop()
                        override fun onEdit(botName: String) = backstack.push(NavTarget.Edit(botName))
                        override fun onOpenRoom(roomIdOrAlias: RoomIdOrAlias) = callback.onOpenRoom(roomIdOrAlias)
                        override fun onOpenSkills(botName: String) = callback.onOpenSkills(botName)
                    }
                ),
            )
            is NavTarget.Profile -> createNode<AgentProfileNode>(
                buildContext = buildContext,
                plugins = listOf(
                    AgentProfileNode.Inputs(navTarget.botName, navTarget.matrixUserId),
                    object : AgentProfileNode.Callback {
                        override fun onDone() = closeOrPop()
                        override fun onEdit(botName: String) = backstack.push(NavTarget.Edit(botName))
                        override fun onOpenRoom(roomIdOrAlias: RoomIdOrAlias) = callback.onOpenRoom(roomIdOrAlias)
                        override fun onOpenSkills(botName: String) = callback.onOpenSkills(botName)
                    }
                ),
            )
            NavTarget.Create -> createNode<AgentEditNode>(
                buildContext = buildContext,
                plugins = listOf(
                    AgentEditNode.Inputs.Create,
                    object : AgentEditNode.Callback {
                        override fun onDone() = closeOrPop()
                        override fun onCreated(botName: String, directRoomId: RoomId?) {
                            if (directRoomId != null) {
                                callback.onOpenCreatedDirectRoom(directRoomId)
                            } else {
                                backstack.push(NavTarget.Detail(botName))
                            }
                        }
                        override fun onUpdated(botName: String) = backstack.push(NavTarget.Detail(botName))
                    }
                ),
            )
            is NavTarget.Edit -> createNode<AgentEditNode>(
                buildContext = buildContext,
                plugins = listOf(
                    AgentEditNode.Inputs.Edit(navTarget.botName),
                    object : AgentEditNode.Callback {
                        override fun onDone() = closeOrPop()
                        override fun onCreated(botName: String, directRoomId: RoomId?) = backstack.push(NavTarget.Detail(botName))
                        override fun onUpdated(botName: String) {
                            backstack.pop()
                        }
                    }
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

    private fun closeFlow() {
        callback.onDone()
    }
}

private fun AgentManagementEntryPoint.InitialTarget.toNavTarget(): AgentManagementFlowNode.NavTarget = when (this) {
    AgentManagementEntryPoint.InitialTarget.List -> AgentManagementFlowNode.NavTarget.List
    is AgentManagementEntryPoint.InitialTarget.Detail -> AgentManagementFlowNode.NavTarget.Detail(botName)
    is AgentManagementEntryPoint.InitialTarget.Profile -> AgentManagementFlowNode.NavTarget.Profile(botName, matrixUserId)
    AgentManagementEntryPoint.InitialTarget.Create -> AgentManagementFlowNode.NavTarget.Create
    is AgentManagementEntryPoint.InitialTarget.Edit -> AgentManagementFlowNode.NavTarget.Edit(botName)
}

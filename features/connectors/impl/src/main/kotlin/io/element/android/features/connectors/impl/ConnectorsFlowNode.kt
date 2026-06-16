/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.connectors.impl

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
import io.element.android.features.connectors.api.ConnectorsEntryPoint
import io.element.android.features.connectors.impl.list.ConnectorListNode
import io.element.android.features.connectors.impl.manage.ConnectorManageNode
import io.element.android.libraries.architecture.BackstackView
import io.element.android.libraries.architecture.BaseFlowNode
import io.element.android.libraries.architecture.appyx.canPop
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.architecture.createNode
import io.element.android.libraries.di.SessionScope
import kotlinx.parcelize.Parcelize

@ContributesNode(SessionScope::class)
@AssistedInject
class ConnectorsFlowNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
) : BaseFlowNode<ConnectorsFlowNode.NavTarget>(
    backstack = BackStack(
        initialElement = NavTarget.List,
        savedStateMap = buildContext.savedStateMap,
    ),
    buildContext = buildContext,
    plugins = plugins,
) {
    sealed interface NavTarget : Parcelable {
        @Parcelize
        data object List : NavTarget

        @Parcelize
        data class Manage(val toolkitSlug: String, val toolkitName: String) : NavTarget
    }

    private val callback: ConnectorsEntryPoint.Callback = callback()

    override fun resolve(navTarget: NavTarget, buildContext: BuildContext): Node {
        return when (navTarget) {
            NavTarget.List -> createNode<ConnectorListNode>(
                buildContext = buildContext,
                plugins = listOf(listCallback),
            )
            is NavTarget.Manage -> createNode<ConnectorManageNode>(
                buildContext = buildContext,
                plugins = listOf(
                    ConnectorManageNode.Inputs(
                        toolkitSlug = navTarget.toolkitSlug,
                        toolkitName = navTarget.toolkitName,
                    ),
                    manageCallback,
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

    private val listCallback = object : ConnectorListNode.Callback {
        override fun onDone() = closeOrPop()

        override fun onManageToolkit(toolkitSlug: String, toolkitName: String) {
            backstack.push(NavTarget.Manage(toolkitSlug, toolkitName))
        }

        override fun onOpenConnectUrl(url: String) = callback.onOpenConnectUrl(url)
    }

    private val manageCallback = object : ConnectorManageNode.Callback {
        override fun onDone() = closeOrPop()

        override fun onOpenConnectUrl(url: String) = callback.onOpenConnectUrl(url)
    }
}

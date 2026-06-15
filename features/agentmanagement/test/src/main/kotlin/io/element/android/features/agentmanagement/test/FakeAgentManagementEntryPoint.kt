/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.agentmanagement.test

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import io.element.android.features.agentmanagement.api.AgentManagementEntryPoint

class FakeAgentManagementEntryPoint(
    private val onCreateNode: (
        parentNode: Node,
        buildContext: BuildContext,
        params: AgentManagementEntryPoint.Params,
        callback: AgentManagementEntryPoint.Callback,
    ) -> Node = { _, buildContext, _, _ ->
        object : Node(buildContext) {
            @Composable
            override fun View(modifier: Modifier) = Unit
        }
    },
) : AgentManagementEntryPoint {
    val createdNodes = mutableListOf<AgentManagementEntryPoint.Params>()

    override fun createNode(
        parentNode: Node,
        buildContext: BuildContext,
        params: AgentManagementEntryPoint.Params,
        callback: AgentManagementEntryPoint.Callback,
    ): Node {
        createdNodes += params
        return onCreateNode(parentNode, buildContext, params, callback)
    }
}

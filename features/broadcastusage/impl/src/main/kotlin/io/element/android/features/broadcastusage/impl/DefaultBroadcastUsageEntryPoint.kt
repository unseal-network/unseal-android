/*
 * Copyright (c) 2026 Unseal
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.broadcastusage.impl

import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.broadcastusage.api.BroadcastUsageEntryPoint
import io.element.android.libraries.architecture.createNode

@ContributesBinding(AppScope::class)
class DefaultBroadcastUsageEntryPoint : BroadcastUsageEntryPoint {
    override fun createNode(parentNode: Node, buildContext: BuildContext, callback: BroadcastUsageEntryPoint.Callback): Node =
        parentNode.createNode<BroadcastUsageNode>(buildContext, plugins = listOf(callback))
}

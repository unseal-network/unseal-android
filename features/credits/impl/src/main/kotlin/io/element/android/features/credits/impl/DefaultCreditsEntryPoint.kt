/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.credits.impl

import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.credits.api.CreditsEntryPoint
import io.element.android.libraries.architecture.createNode

@ContributesBinding(AppScope::class)
class DefaultCreditsEntryPoint : CreditsEntryPoint {
    override fun createNode(
        parentNode: Node,
        buildContext: BuildContext,
        params: CreditsEntryPoint.Params,
        callback: CreditsEntryPoint.Callback,
    ): Node {
        return parentNode.createNode<CreditsFlowNode>(
            buildContext = buildContext,
            plugins = listOf(params, callback),
        )
    }
}

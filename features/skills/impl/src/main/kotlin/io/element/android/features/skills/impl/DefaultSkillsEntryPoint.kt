/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.skills.impl

import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.skills.api.SkillsEntryPoint
import io.element.android.libraries.architecture.createNode

@ContributesBinding(AppScope::class)
class DefaultSkillsEntryPoint : SkillsEntryPoint {
    override fun createNode(
        parentNode: Node,
        buildContext: BuildContext,
        params: SkillsEntryPoint.Params,
        callback: SkillsEntryPoint.Callback,
    ): Node {
        return parentNode.createNode<SkillsFlowNode>(
            buildContext = buildContext,
            plugins = listOf(params, callback),
        )
    }
}

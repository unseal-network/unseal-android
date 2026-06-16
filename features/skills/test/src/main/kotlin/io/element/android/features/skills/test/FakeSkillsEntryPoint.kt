/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.skills.test

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import io.element.android.features.skills.api.SkillsEntryPoint

class FakeSkillsEntryPoint : SkillsEntryPoint {
    var lastParams: SkillsEntryPoint.Params? = null
        private set
    var lastCallback: SkillsEntryPoint.Callback? = null
        private set

    override fun createNode(
        parentNode: Node,
        buildContext: BuildContext,
        params: SkillsEntryPoint.Params,
        callback: SkillsEntryPoint.Callback,
    ): Node {
        lastParams = params
        lastCallback = callback
        return FakeSkillsNode(buildContext)
    }
}

private class FakeSkillsNode(
    buildContext: BuildContext,
) : Node(buildContext, plugins = emptyList<Plugin>()) {
    @Composable
    override fun View(modifier: Modifier) = Unit
}

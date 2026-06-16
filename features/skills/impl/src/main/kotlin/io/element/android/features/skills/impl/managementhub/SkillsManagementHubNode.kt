/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.skills.impl.managementhub

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

@ContributesNode(SessionScope::class)
@AssistedInject
class SkillsManagementHubNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
) : Node(buildContext, plugins = plugins) {
    interface Callback : Plugin {
        fun onDone()
        fun onOpenAgentManagement()
        fun onOpenSkillsHome()
    }

    private val callback = plugins<Callback>().first()

    @Composable
    override fun View(modifier: Modifier) {
        SkillsManagementHubView(
            onBackClick = callback::onDone,
            onOpenAgentManagement = callback::onOpenAgentManagement,
            onOpenSkills = callback::onOpenSkillsHome,
            modifier = modifier,
        )
    }
}

/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.skills.impl.home

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
class SkillsHomeNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    presenterFactory: SkillsHomePresenter.Factory,
) : Node(buildContext, plugins = plugins) {
    interface Callback : Plugin {
        fun onDone()
        fun onCreateSkill()
        fun onOpenSkill(id: String, isOwner: Boolean)
    }

    private val callback = plugins<Callback>().first()
    private val presenter = presenterFactory.create(
        object : SkillsHomeNavigator {
            override fun onCreateSkill() = callback.onCreateSkill()
            override fun onOpenSkill(id: String, isOwner: Boolean) = callback.onOpenSkill(id, isOwner)
        }
    )

    @Composable
    override fun View(modifier: Modifier) {
        SkillsHomeView(
            state = presenter.present(),
            onBackClick = callback::onDone,
            modifier = modifier,
        )
    }
}

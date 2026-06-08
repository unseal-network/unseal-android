/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.skills.impl

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
import io.element.android.features.skills.api.SkillsEntryPoint
import io.element.android.features.skills.impl.detail.SkillDetailNode
import io.element.android.features.skills.impl.home.SkillsHomeNode
import io.element.android.features.skills.impl.marketplace.SkillMarketplaceNode
import io.element.android.libraries.architecture.BackstackView
import io.element.android.libraries.architecture.BaseFlowNode
import io.element.android.libraries.architecture.appyx.canPop
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.architecture.createNode
import io.element.android.libraries.di.SessionScope
import kotlinx.parcelize.Parcelize

@ContributesNode(SessionScope::class)
@AssistedInject
class SkillsFlowNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
) : BaseFlowNode<SkillsFlowNode.NavTarget>(
    backstack = BackStack(
        initialElement = plugins.filterIsInstance<SkillsEntryPoint.Params>().first().initialTarget.toNavTarget(),
        savedStateMap = buildContext.savedStateMap,
    ),
    buildContext = buildContext,
    plugins = plugins,
) {
    sealed interface NavTarget : Parcelable {
        @Parcelize
        data object Home : NavTarget

        @Parcelize
        data object Marketplace : NavTarget

        @Parcelize
        data class Detail(val id: String, val isOwner: Boolean) : NavTarget

        @Parcelize
        data object Create : NavTarget
    }

    private val callback: SkillsEntryPoint.Callback = callback()

    override fun resolve(navTarget: NavTarget, buildContext: BuildContext): Node {
        return when (navTarget) {
            NavTarget.Home -> createNode<SkillsHomeNode>(
                buildContext = buildContext,
                plugins = listOf(homeCallback),
            )
            NavTarget.Marketplace -> createNode<SkillMarketplaceNode>(
                buildContext = buildContext,
                plugins = listOf(marketplaceCallback),
            )
            is NavTarget.Detail -> createNode<SkillDetailNode>(
                buildContext = buildContext,
                plugins = listOf(SkillDetailNode.Inputs(navTarget.id, navTarget.isOwner), detailCallback),
            )
            NavTarget.Create -> {
                callback.onCreateSkill()
                createNode<SkillsHomeNode>(
                    buildContext = buildContext,
                    plugins = listOf(homeCallback),
                )
            }
        }
    }

    @Composable
    override fun View(modifier: Modifier) {
        BackstackView(modifier)
    }

    fun openDetail(id: String, isOwner: Boolean) {
        backstack.push(NavTarget.Detail(id, isOwner))
    }

    fun openCreateSkill() {
        callback.onCreateSkill()
    }

    fun onSkillDeleted(id: String) {
        callback.onSkillDeleted(id)
        closeOrPop()
    }

    private fun closeOrPop() {
        if (backstack.canPop()) {
            backstack.pop()
        } else {
            callback.onDone()
        }
    }

    private val homeCallback = object : SkillsHomeNode.Callback {
        override fun onDone() = closeOrPop()
        override fun onCreateSkill() = openCreateSkill()
        override fun onOpenSkill(id: String, isOwner: Boolean) = openDetail(id, isOwner)
    }

    private val marketplaceCallback = object : SkillMarketplaceNode.Callback {
        override fun onDone() = closeOrPop()
        override fun onOpenSkill(id: String) = openDetail(id, isOwner = false)
    }

    private val detailCallback = object : SkillDetailNode.Callback {
        override fun onDone() = closeOrPop()
        override fun onDeleted(id: String) = onSkillDeleted(id)
    }
}

private fun SkillsEntryPoint.InitialTarget.toNavTarget(): SkillsFlowNode.NavTarget = when (this) {
    SkillsEntryPoint.InitialTarget.Home -> SkillsFlowNode.NavTarget.Home
    SkillsEntryPoint.InitialTarget.Marketplace -> SkillsFlowNode.NavTarget.Marketplace
    is SkillsEntryPoint.InitialTarget.Detail -> SkillsFlowNode.NavTarget.Detail(id, isOwner)
    SkillsEntryPoint.InitialTarget.Create -> SkillsFlowNode.NavTarget.Create
}

/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.skills.api

import android.os.Parcelable
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import io.element.android.libraries.architecture.FeatureEntryPoint
import io.element.android.libraries.architecture.NodeInputs
import kotlinx.parcelize.Parcelize

interface SkillsEntryPoint : FeatureEntryPoint {
    sealed interface InitialTarget : Parcelable {
        @Parcelize
        data object Home : InitialTarget

        @Parcelize
        data object Marketplace : InitialTarget

        @Parcelize
        data class Detail(val id: String, val isOwner: Boolean) : InitialTarget

        @Parcelize
        data object Create : InitialTarget

        @Parcelize
        data class AgentSkills(val botName: String) : InitialTarget

        @Parcelize
        data object ManagementHub : InitialTarget
    }

    data class Params(val initialTarget: InitialTarget = InitialTarget.Home) : NodeInputs

    fun createNode(
        parentNode: Node,
        buildContext: BuildContext,
        params: Params,
        callback: Callback,
    ): Node

    interface Callback : Plugin {
        fun onDone()
        fun onCreateSkill()
        fun onSkillDeleted(id: String)
        fun onOpenAgentManagement()
    }
}

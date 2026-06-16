/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.agentmanagement.api

import android.os.Parcelable
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import io.element.android.libraries.architecture.FeatureEntryPoint
import io.element.android.libraries.architecture.NodeInputs
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.RoomIdOrAlias
import kotlinx.parcelize.Parcelize

interface AgentManagementEntryPoint : FeatureEntryPoint {
    sealed interface InitialTarget : Parcelable {
        @Parcelize
        data object List : InitialTarget

        @Parcelize
        data class Detail(val botName: String) : InitialTarget

        /** Read-only agent profile, opened from a room when a member is an agent. */
        @Parcelize
        data class Profile(val botName: String, val matrixUserId: String? = null) : InitialTarget

        @Parcelize
        data object Create : InitialTarget

        @Parcelize
        data class Edit(val botName: String) : InitialTarget
    }

    data class Params(val initialTarget: InitialTarget = InitialTarget.List) : NodeInputs

    fun createNode(
        parentNode: Node,
        buildContext: BuildContext,
        params: Params,
        callback: Callback,
    ): Node

    interface Callback : Plugin {
        fun onDone()
        fun onOpenRoom(roomIdOrAlias: RoomIdOrAlias)
        fun onOpenSkills(botName: String?)
        fun onOpenCreatedDirectRoom(roomId: RoomId)
    }
}

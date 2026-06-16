/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.webhooks.api

import android.os.Parcelable
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import io.element.android.libraries.architecture.FeatureEntryPoint
import io.element.android.libraries.architecture.NodeInputs
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.parcelize.Parcelize

interface WebhookTriggersEntryPoint : FeatureEntryPoint {
    sealed interface InitialTarget : Parcelable {
        @Parcelize
        data object Global : InitialTarget

        @Parcelize
        data class Room(val roomId: RoomId, val roomName: String) : InitialTarget

        @Parcelize
        data class Edit(val mode: WebhookTriggerEditMode) : InitialTarget
    }

    data class Params(
        val initialTarget: InitialTarget = InitialTarget.Global,
    ) : NodeInputs

    fun createNode(
        parentNode: Node,
        buildContext: BuildContext,
        params: Params,
        callback: Callback,
    ): Node

    interface Callback : Plugin {
        fun onDone()
        fun onTriggersChanged()
        fun onOpenConnectUrl(url: String)
    }
}

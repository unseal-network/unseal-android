/*
 * Copyright (c) 2026 Unseal
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.broadcastusage.api

import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import io.element.android.libraries.architecture.FeatureEntryPoint

interface BroadcastUsageEntryPoint : FeatureEntryPoint {
    fun createNode(parentNode: Node, buildContext: BuildContext, callback: Callback): Node

    interface Callback : Plugin {
        fun onDone()
    }
}

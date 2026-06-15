/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.credits.api

import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import io.element.android.libraries.architecture.FeatureEntryPoint
import io.element.android.libraries.architecture.NodeInputs
import io.element.android.libraries.chatbot.api.model.credits.CreditBalance

interface CreditsEntryPoint : FeatureEntryPoint {
    enum class CreditsTab {
        Balance,
        DailyUsage,
        Usage,
    }

    data class Params(
        val initialTab: CreditsTab = CreditsTab.Balance,
        val openTopUpInitially: Boolean = false,
    ) : NodeInputs

    fun createNode(
        parentNode: Node,
        buildContext: BuildContext,
        params: Params,
        callback: Callback,
    ): Node

    interface Callback : Plugin {
        fun onDone()
        fun onTopUpRequested(balance: CreditBalance?)
    }
}

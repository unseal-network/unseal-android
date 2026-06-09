/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.credits.impl

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import com.bumble.appyx.core.plugin.plugins
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.features.credits.api.CreditsEntryPoint
import io.element.android.libraries.architecture.createNode
import io.element.android.libraries.chatbot.api.model.credits.CreditBalance
import io.element.android.libraries.di.SessionScope

@ContributesNode(SessionScope::class)
@AssistedInject
class CreditsFlowNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
) : Node(buildContext, plugins = plugins) {
    private val params = plugins<CreditsEntryPoint.Params>().first()
    private val callback = plugins<CreditsEntryPoint.Callback>().first()
    private val node = createNode<CreditsNode>(
        buildContext = buildContext,
        plugins = listOf(
            CreditsNode.Inputs(params.initialTab),
            object : CreditsNode.Callback {
                override fun onDone() = callback.onDone()
                override fun onTopUpRequested(balance: CreditBalance?) = callback.onTopUpRequested(balance)
            },
        ),
    )

    @Composable
    override fun View(modifier: Modifier) {
        node.View(modifier)
    }
}

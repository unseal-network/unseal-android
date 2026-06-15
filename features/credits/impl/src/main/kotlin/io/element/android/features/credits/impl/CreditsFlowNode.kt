/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.credits.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import com.bumble.appyx.core.plugin.plugins
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.features.credits.api.CreditsEntryPoint
import io.element.android.features.credits.impl.topup.TopupNode
import io.element.android.libraries.architecture.createNode
import io.element.android.libraries.chatbot.api.model.credits.CreditBalance
import io.element.android.libraries.di.SessionScope

@ContributesNode(SessionScope::class)
@AssistedInject
class CreditsFlowNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
) : Node(buildContext, plugins = plugins) {
    private val nodeBuildContext = buildContext
    private val params = plugins<CreditsEntryPoint.Params>().first()
    private val callback = plugins<CreditsEntryPoint.Callback>().first()
    private var topupNode by mutableStateOf<TopupNode?>(
        if (params.openTopUpInitially) {
            createTopupNode(balance = null)
        } else {
            null
        }
    )
    private val node = createNode<CreditsNode>(
        buildContext = buildContext,
        plugins = listOf(
            CreditsNode.Inputs(params.initialTab),
            object : CreditsNode.Callback {
                override fun onDone() = callback.onDone()
                override fun onTopUpRequested(balance: CreditBalance?) {
                    topupNode = createTopupNode(balance)
                }
            },
        ),
    )

    @Composable
    override fun View(modifier: Modifier) {
        topupNode?.View(modifier) ?: node.View(modifier)
    }

    private fun createTopupNode(balance: CreditBalance?): TopupNode {
        return createNode<TopupNode>(
            buildContext = nodeBuildContext,
            plugins = listOf(
                TopupNode.Inputs(balance),
                object : TopupNode.Callback {
                    override fun onCompleted() {
                        topupNode = null
                        callback.onTopUpRequested(balance)
                    }

                    override fun onCancel() {
                        topupNode = null
                    }
                },
            ),
        )
    }
}

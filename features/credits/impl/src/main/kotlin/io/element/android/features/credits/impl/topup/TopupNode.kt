/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.credits.impl.topup

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import com.bumble.appyx.core.plugin.plugins
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.libraries.chatbot.api.model.credits.CreditBalance
import io.element.android.libraries.di.SessionScope

@ContributesNode(SessionScope::class)
@AssistedInject
class TopupNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    presenterFactory: TopupPresenter.Factory,
) : Node(buildContext, plugins = plugins) {
    data class Inputs(val balance: CreditBalance?) : Plugin

    interface Callback : Plugin {
        fun onCompleted()
        fun onCancel()
    }

    private val inputs = plugins<Inputs>().first()
    private val callback = plugins<Callback>().first()
    private val presenter = presenterFactory.create(
        initialBalance = inputs.balance,
        navigator = object : TopupNavigator {
            override fun onCompleted() = callback.onCompleted()
            override fun onCancel() = callback.onCancel()
        },
    )

    @Composable
    override fun View(modifier: Modifier) {
        TopupView(state = presenter.present(), modifier = modifier)
    }
}

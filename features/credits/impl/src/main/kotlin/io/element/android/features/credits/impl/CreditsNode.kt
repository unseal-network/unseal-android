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
import io.element.android.libraries.chatbot.api.model.credits.CreditBalance
import io.element.android.libraries.di.SessionScope

@ContributesNode(SessionScope::class)
@AssistedInject
class CreditsNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    presenterFactory: CreditsPresenter.Factory,
) : Node(buildContext, plugins = plugins) {
    data class Inputs(val initialTab: CreditsEntryPoint.CreditsTab) : Plugin

    interface Callback : Plugin {
        fun onDone()
        fun onTopUpRequested(balance: CreditBalance?)
    }

    private val inputs = plugins<Inputs>().first()
    private val callback = plugins<Callback>().first()
    private val presenter = presenterFactory.create(
        initialTab = inputs.initialTab,
        navigator = object : CreditsNavigator {
            override fun onDone() = callback.onDone()
            override fun onTopUpRequested(balance: CreditBalance?) = callback.onTopUpRequested(balance)
        },
    )

    @Composable
    override fun View(modifier: Modifier) {
        CreditsView(state = presenter.present(), modifier = modifier)
    }
}

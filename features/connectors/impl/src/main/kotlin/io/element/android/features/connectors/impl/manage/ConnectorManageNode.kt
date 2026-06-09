/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.connectors.impl.manage

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import com.bumble.appyx.core.plugin.plugins
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.libraries.di.SessionScope

@ContributesNode(SessionScope::class)
@AssistedInject
class ConnectorManageNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    presenterFactory: ConnectorManagePresenter.Factory,
) : Node(buildContext, plugins = plugins) {
    data class Inputs(
        val toolkitSlug: String,
        val toolkitName: String,
    ) : Plugin

    interface Callback : Plugin {
        fun onOpenConnectUrl(url: String)
        fun onDone()
    }

    private val inputs = plugins<Inputs>().first()
    private val callback = plugins<Callback>().first()
    private val presenter = presenterFactory.create(
        toolkitSlug = inputs.toolkitSlug,
        toolkitName = inputs.toolkitName,
        navigator = object : ConnectorManageNavigator {
            override fun onOpenConnectUrl(url: String) = callback.onOpenConnectUrl(url)
            override fun onDone() = callback.onDone()
        },
    )

    @Composable
    override fun View(modifier: Modifier) {
        val state = presenter.present()
        ConnectorManageView(state = state, modifier = modifier)
    }
}

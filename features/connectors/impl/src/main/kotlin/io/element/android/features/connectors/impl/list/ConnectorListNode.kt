/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.connectors.impl.list

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
class ConnectorListNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    presenterFactory: ConnectorListPresenter.Factory,
) : Node(buildContext, plugins = plugins) {
    interface Callback : Plugin {
        fun onManageToolkit(toolkitSlug: String, toolkitName: String)
        fun onOpenConnectUrl(url: String)
        fun onDone()
    }

    private val callback = plugins<Callback>().first()
    private val presenter = presenterFactory.create(
        navigator = object : ConnectorListNavigator {
            override fun onManageToolkit(toolkitSlug: String, toolkitName: String) =
                callback.onManageToolkit(toolkitSlug, toolkitName)

            override fun onOpenConnectUrl(url: String) = callback.onOpenConnectUrl(url)
            override fun onDone() = callback.onDone()
        },
    )

    @Composable
    override fun View(modifier: Modifier) {
        val state = presenter.present()
        ConnectorListView(state = state, modifier = modifier)
    }
}

/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.vault

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import com.bumble.appyx.core.plugin.plugins
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.libraries.chatbot.api.model.vault.ChatbotVaultItem
import io.element.android.libraries.di.SessionScope

@ContributesNode(SessionScope::class)
@AssistedInject
class VaultManagementNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    presenterFactory: VaultManagementPresenter.Factory,
) : Node(buildContext, plugins = plugins) {
    interface Callback : Plugin {
        fun onDone()
        fun onAddEntry()
        fun onEditEntry(item: ChatbotVaultItem)
    }

    private val callback = plugins<Callback>().first()
    private val presenter = presenterFactory.create(
        navigator = object : VaultManagementNavigator {
            override fun onAddEntry() = callback.onAddEntry()
            override fun onEditEntry(item: ChatbotVaultItem) = callback.onEditEntry(item)
        }
    )

    @Composable
    override fun View(modifier: Modifier) {
        VaultManagementView(
            state = presenter.present(),
            onBackClick = callback::onDone,
            modifier = modifier,
        )
    }
}

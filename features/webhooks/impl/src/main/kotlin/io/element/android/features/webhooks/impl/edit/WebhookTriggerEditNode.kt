/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.webhooks.impl.edit

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import com.bumble.appyx.core.plugin.plugins
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.features.webhooks.api.WebhookTriggerEditMode
import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotWebhookTrigger
import io.element.android.libraries.di.SessionScope

@ContributesNode(SessionScope::class)
@AssistedInject
class WebhookTriggerEditNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    presenterFactory: WebhookTriggerEditPresenter.Factory,
) : Node(buildContext, plugins = plugins) {
    data class Inputs(
        val mode: WebhookTriggerEditMode,
    ) : Plugin

    interface Callback : Plugin {
        fun onSaved(trigger: ChatbotWebhookTrigger)
        fun onCancelled()
        fun onOpenConnectUrl(url: String)
    }

    private val inputs = plugins<Inputs>().first()
    private val callback = plugins<Callback>().first()
    private val presenter = presenterFactory.create(
        mode = inputs.mode,
        navigator = object : WebhookTriggerEditNavigator {
            override fun onSaved(trigger: ChatbotWebhookTrigger) = callback.onSaved(trigger)
            override fun onCancelled() = callback.onCancelled()
            override fun onOpenConnectUrl(url: String) = callback.onOpenConnectUrl(url)
        },
    )

    @Composable
    override fun View(modifier: Modifier) {
        WebhookTriggerEditView(state = presenter.present(), modifier = modifier)
    }
}

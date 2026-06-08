/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.webhooks.impl

import android.os.Parcelable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import com.bumble.appyx.navmodel.backstack.BackStack
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.features.webhooks.api.WebhookTriggerEditMode
import io.element.android.features.webhooks.api.WebhookTriggersEntryPoint
import io.element.android.features.webhooks.impl.list.WebhookTriggerListMode
import io.element.android.libraries.architecture.BackstackView
import io.element.android.libraries.architecture.BaseFlowNode
import io.element.android.libraries.di.SessionScope
import kotlinx.parcelize.Parcelize

@ContributesNode(SessionScope::class)
@AssistedInject
class WebhookTriggersFlowNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
) : BaseFlowNode<WebhookTriggersFlowNode.NavTarget>(
    backstack = BackStack(
        initialElement = plugins.filterIsInstance<WebhookTriggersEntryPoint.Params>().first().initialTarget.toNavTarget(),
        savedStateMap = buildContext.savedStateMap,
    ),
    buildContext = buildContext,
    plugins = plugins,
) {
    sealed interface NavTarget : Parcelable {
        @Parcelize
        data class List(val mode: WebhookTriggerListMode) : NavTarget

        @Parcelize
        data class Edit(val mode: WebhookTriggerEditMode) : NavTarget
    }

    override fun resolve(navTarget: NavTarget, buildContext: BuildContext): Node {
        return TemporaryWebhookTriggersNode(buildContext)
    }

    @Composable
    override fun View(modifier: Modifier) {
        BackstackView(modifier)
    }
}

private fun WebhookTriggersEntryPoint.InitialTarget.toNavTarget(): WebhookTriggersFlowNode.NavTarget = when (this) {
    WebhookTriggersEntryPoint.InitialTarget.Global -> WebhookTriggersFlowNode.NavTarget.List(WebhookTriggerListMode.Global)
    is WebhookTriggersEntryPoint.InitialTarget.Room -> WebhookTriggersFlowNode.NavTarget.List(
        WebhookTriggerListMode.Room(roomId, roomName)
    )
    is WebhookTriggersEntryPoint.InitialTarget.Edit -> WebhookTriggersFlowNode.NavTarget.Edit(mode)
}

private class TemporaryWebhookTriggersNode(
    buildContext: BuildContext,
) : Node(buildContext, plugins = emptyList()) {
    @Composable
    override fun View(modifier: Modifier) {
        Text("Webhook Triggers")
    }
}

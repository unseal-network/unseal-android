/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.webhooks.impl

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import com.bumble.appyx.navmodel.backstack.BackStack
import com.bumble.appyx.navmodel.backstack.operation.pop
import com.bumble.appyx.navmodel.backstack.operation.push
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.features.webhooks.api.WebhookTriggerEditMode
import io.element.android.features.webhooks.api.WebhookTriggersEntryPoint
import io.element.android.features.webhooks.impl.edit.WebhookTriggerEditNode
import io.element.android.features.webhooks.impl.list.WebhookTriggerListMode
import io.element.android.features.webhooks.impl.list.WebhookTriggerListNode
import io.element.android.libraries.architecture.BackstackView
import io.element.android.libraries.architecture.BaseFlowNode
import io.element.android.libraries.architecture.appyx.canPop
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.architecture.createNode
import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotWebhookTrigger
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.coroutines.flow.MutableSharedFlow
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

    private val callback: WebhookTriggersEntryPoint.Callback = callback()
    private val reloadRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun resolve(navTarget: NavTarget, buildContext: BuildContext): Node {
        return when (navTarget) {
            is NavTarget.List -> createNode<WebhookTriggerListNode>(
                buildContext = buildContext,
                plugins = listOf(
                    WebhookTriggerListNode.Inputs(
                        mode = navTarget.mode,
                        reloadRequests = reloadRequests,
                    ),
                    listCallback,
                ),
            )
            is NavTarget.Edit -> createNode<WebhookTriggerEditNode>(
                buildContext = buildContext,
                plugins = listOf(
                    WebhookTriggerEditNode.Inputs(mode = navTarget.mode),
                    editCallback,
                ),
            )
        }
    }

    @Composable
    override fun View(modifier: Modifier) {
        BackstackView(modifier)
    }

    private fun closeOrPop() {
        if (backstack.canPop()) {
            backstack.pop()
        } else {
            callback.onDone()
        }
    }

    private fun notifyTriggersChanged() {
        callback.onTriggersChanged()
        reloadRequests.tryEmit(Unit)
    }

    private val listCallback = object : WebhookTriggerListNode.Callback {
        override fun onDone() = closeOrPop()

        override fun onCreateTrigger(prefilledRoomId: RoomId?) {
            backstack.push(NavTarget.Edit(WebhookTriggerEditMode.Create(prefilledRoomId)))
        }

        override fun onEditTrigger(trigger: ChatbotWebhookTrigger) {
            backstack.push(NavTarget.Edit(WebhookTriggerEditMode.Edit(trigger)))
        }

        override fun onTriggersChanged() = notifyTriggersChanged()
    }

    private val editCallback = object : WebhookTriggerEditNode.Callback {
        override fun onSaved(trigger: ChatbotWebhookTrigger) {
            notifyTriggersChanged()
            closeOrPop()
        }

        override fun onCancelled() = closeOrPop()

        override fun onOpenConnectUrl(url: String) = callback.onOpenConnectUrl(url)
    }
}

private fun WebhookTriggersEntryPoint.InitialTarget.toNavTarget(): WebhookTriggersFlowNode.NavTarget = when (this) {
    WebhookTriggersEntryPoint.InitialTarget.Global -> WebhookTriggersFlowNode.NavTarget.List(WebhookTriggerListMode.Global)
    is WebhookTriggersEntryPoint.InitialTarget.Room -> WebhookTriggersFlowNode.NavTarget.List(
        WebhookTriggerListMode.Room(roomId, roomName)
    )
    is WebhookTriggersEntryPoint.InitialTarget.Edit -> WebhookTriggersFlowNode.NavTarget.Edit(mode)
}

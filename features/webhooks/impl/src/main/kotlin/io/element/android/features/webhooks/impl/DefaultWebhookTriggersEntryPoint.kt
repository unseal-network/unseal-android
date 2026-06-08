/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.webhooks.impl

import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.webhooks.api.WebhookTriggersEntryPoint
import io.element.android.libraries.architecture.createNode

@ContributesBinding(AppScope::class)
class DefaultWebhookTriggersEntryPoint : WebhookTriggersEntryPoint {
    override fun createNode(
        parentNode: Node,
        buildContext: BuildContext,
        params: WebhookTriggersEntryPoint.Params,
        callback: WebhookTriggersEntryPoint.Callback,
    ): Node {
        return parentNode.createNode<WebhookTriggersFlowNode>(
            buildContext = buildContext,
            plugins = listOf(params, callback),
        )
    }
}

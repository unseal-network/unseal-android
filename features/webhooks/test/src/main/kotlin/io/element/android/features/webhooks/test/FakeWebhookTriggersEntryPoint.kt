/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.webhooks.test

import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import io.element.android.features.webhooks.api.WebhookTriggersEntryPoint
import io.element.android.tests.testutils.lambda.lambdaError

class FakeWebhookTriggersEntryPoint(
    var createNodeResult: (Node, BuildContext, WebhookTriggersEntryPoint.Params, WebhookTriggersEntryPoint.Callback) -> Node = { _, _, _, _ -> lambdaError() },
) : WebhookTriggersEntryPoint {
    override fun createNode(
        parentNode: Node,
        buildContext: BuildContext,
        params: WebhookTriggersEntryPoint.Params,
        callback: WebhookTriggersEntryPoint.Callback,
    ): Node {
        return createNodeResult(parentNode, buildContext, params, callback)
    }
}

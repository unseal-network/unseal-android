/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.credits.test

import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import io.element.android.features.credits.api.CreditsEntryPoint
import io.element.android.tests.testutils.lambda.lambdaError

class FakeCreditsEntryPoint(
    var createNodeResult: (Node, BuildContext, CreditsEntryPoint.Params, CreditsEntryPoint.Callback) -> Node = { _, _, _, _ -> lambdaError() },
) : CreditsEntryPoint {
    override fun createNode(
        parentNode: Node,
        buildContext: BuildContext,
        params: CreditsEntryPoint.Params,
        callback: CreditsEntryPoint.Callback,
    ): Node {
        return createNodeResult(parentNode, buildContext, params, callback)
    }
}

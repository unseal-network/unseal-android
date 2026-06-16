/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.connectors.test

import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import io.element.android.features.connectors.api.ConnectorsEntryPoint
import io.element.android.tests.testutils.lambda.lambdaError

class FakeConnectorsEntryPoint(
    var createNodeResult: (Node, BuildContext, ConnectorsEntryPoint.Callback) -> Node = { _, _, _ -> lambdaError() },
) : ConnectorsEntryPoint {
    override fun createNode(
        parentNode: Node,
        buildContext: BuildContext,
        callback: ConnectorsEntryPoint.Callback,
    ): Node {
        return createNodeResult(parentNode, buildContext, callback)
    }
}

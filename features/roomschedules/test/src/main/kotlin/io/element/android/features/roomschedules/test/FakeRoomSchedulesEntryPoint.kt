/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.roomschedules.test

import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import io.element.android.features.roomschedules.api.RoomSchedulesEntryPoint
import io.element.android.tests.testutils.lambda.lambdaError

class FakeRoomSchedulesEntryPoint(
    var createNodeResult: (Node, BuildContext, RoomSchedulesEntryPoint.Params, RoomSchedulesEntryPoint.Callback) -> Node = { _, _, _, _ -> lambdaError() },
) : RoomSchedulesEntryPoint {
    override fun createNode(
        parentNode: Node,
        buildContext: BuildContext,
        params: RoomSchedulesEntryPoint.Params,
        callback: RoomSchedulesEntryPoint.Callback,
    ): Node {
        return createNodeResult(parentNode, buildContext, params, callback)
    }
}

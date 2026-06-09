/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.voicelibrary.test

import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import io.element.android.features.voicelibrary.api.VoiceLibraryEntryPoint
import io.element.android.tests.testutils.lambda.lambdaError

class FakeVoiceLibraryEntryPoint(
    var createNodeResult: (Node, BuildContext, VoiceLibraryEntryPoint.Callback) -> Node = { _, _, _ -> lambdaError() },
) : VoiceLibraryEntryPoint {
    override fun createNode(
        parentNode: Node,
        buildContext: BuildContext,
        callback: VoiceLibraryEntryPoint.Callback,
    ): Node {
        return createNodeResult(parentNode, buildContext, callback)
    }
}

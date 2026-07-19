/*
 * Copyright (c) 2026 Unseal
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.audience

import io.element.android.features.call.impl.audience.AudiencePlaybackController
import io.element.android.features.call.impl.audience.AudiencePlaybackState
import io.element.android.libraries.matrix.api.core.SessionId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAudiencePlaybackController(
    initialState: AudiencePlaybackState = AudiencePlaybackState.Connecting,
) : AudiencePlaybackController {
    val states = MutableStateFlow(initialState)
    var observeCount = 0
        private set

    override fun observe(sessionId: SessionId, broadcastId: String, audienceClientId: String): Flow<AudiencePlaybackState> {
        observeCount++
        return states
    }
}

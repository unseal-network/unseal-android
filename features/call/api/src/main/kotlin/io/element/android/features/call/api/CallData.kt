/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.api

import android.os.Parcelable
import io.element.android.libraries.architecture.NodeInputs
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId
import kotlinx.parcelize.Parcelize

@Parcelize
data class CallData(
    val sessionId: SessionId,
    val roomId: RoomId,
    val isAudioCall: Boolean,
    val audienceBroadcastId: String? = null,
) : NodeInputs, Parcelable {
    companion object {
        private val DIRECT_AUDIENCE_ROUTE_ROOM_ID = RoomId("!audience-route:keepsecret.io")

        /**
         * Opens a canonical audience link before its runtime has resolved the actual Matrix room.
         *
         * Audience widget authentication and runtime resolution are scoped by [audienceBroadcastId];
         * the synthetic room ID only satisfies the legacy participant-call activity input.
         */
        fun forDirectAudienceRoute(sessionId: SessionId, audienceBroadcastId: String): CallData = CallData(
            sessionId = sessionId,
            roomId = DIRECT_AUDIENCE_ROUTE_ROOM_ID,
            isAudioCall = false,
            audienceBroadcastId = audienceBroadcastId,
        )
    }
}

/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.roomkey

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.element.android.features.messages.impl.timeline.TimelineController
import io.element.android.libraries.di.RoomScope
import io.element.android.libraries.matrix.api.encryption.roomkey.RoomKeyRecoveryRequest
import io.element.android.libraries.matrix.api.timeline.MatrixTimelineItem
import io.element.android.libraries.matrix.api.timeline.item.event.UnableToDecryptContent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

fun interface RoomKeyDecryptionRetrier {
    suspend fun waitForRecovery(request: RoomKeyRecoveryRequest, timeout: Duration): Boolean
}

@SingleIn(RoomScope::class)
@ContributesBinding(RoomScope::class, binding = binding<RoomKeyDecryptionRetrier>())
@Inject
class TimelineRoomKeyDecryptionRetrier(
    private val timelineController: TimelineController,
) : RoomKeyDecryptionRetrier {
    private val parser = RoomKeyRecoveryRequestParser()
    private val retryInterval = 2.seconds

    override suspend fun waitForRecovery(request: RoomKeyRecoveryRequest, timeout: Duration): Boolean {
        val start = TimeSource.Monotonic.markNow()
        while (start.elapsedNow() < timeout) {
            timelineController.retryDecryption(listOf(request.sessionId))
            delay(retryInterval)
            if (!stillMissingRoomKey(request)) {
                return true
            }
        }
        timelineController.retryDecryption(listOf(request.sessionId))
        return !stillMissingRoomKey(request)
    }

    private suspend fun stillMissingRoomKey(request: RoomKeyRecoveryRequest): Boolean {
        return timelineController.timelineItems()
            .first()
            .any { item -> item.roomKeyRecoveryRequest(request)?.identityKey == request.identityKey }
    }

    private fun MatrixTimelineItem.roomKeyRecoveryRequest(request: RoomKeyRecoveryRequest): RoomKeyRecoveryRequest? {
        val event = (this as? MatrixTimelineItem.Event)?.event ?: return null
        val content = event.content as? UnableToDecryptContent ?: return null
        val data = content.data as? UnableToDecryptContent.Data.MegolmV1AesSha2
        return parser.parse(
            originalJson = event.timelineItemDebugInfoProvider().originalJson,
            fallbackRoomId = request.roomId,
            fallbackSenderId = event.sender,
            fallbackSessionId = data?.sessionId,
        )
    }
}

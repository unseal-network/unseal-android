/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.roomkey

import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.encryption.roomkey.RoomKeyRecoveryRequest
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

fun aRequest(
    roomId: RoomId = RoomId("!room:example.org"),
    senderUserId: UserId = UserId("@alice:example.org"),
    senderDeviceId: String? = "ALICEDEVICE",
    senderKey: String = "senderKey",
    sessionId: String = "session",
    ciphertext: String? = "ciphertext",
) = RoomKeyRecoveryRequest(
    roomId = roomId,
    senderUserId = senderUserId,
    senderDeviceId = senderDeviceId,
    senderKey = senderKey,
    sessionId = sessionId,
    ciphertext = ciphertext,
)

class MutableClock : Clock {
    private var instant = Instant.fromEpochMilliseconds(0)

    fun advanceBy(duration: Duration) {
        instant += duration
    }

    override fun now(): Instant = instant
}

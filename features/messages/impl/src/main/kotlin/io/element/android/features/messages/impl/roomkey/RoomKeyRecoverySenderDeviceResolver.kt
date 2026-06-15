/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.roomkey

import dev.zacsweers.metro.Inject
import io.element.android.libraries.matrix.api.core.UserId

fun interface RoomKeyRecoverySenderDeviceResolver {
    suspend fun latestSenderDeviceIds(
        senderUserId: UserId,
        roomMemberSignature: String,
    ): Set<String>?
}

@Inject
class DefaultRoomKeyRecoverySenderDeviceResolver : RoomKeyRecoverySenderDeviceResolver {
    override suspend fun latestSenderDeviceIds(
        senderUserId: UserId,
        roomMemberSignature: String,
    ): Set<String>? = null
}

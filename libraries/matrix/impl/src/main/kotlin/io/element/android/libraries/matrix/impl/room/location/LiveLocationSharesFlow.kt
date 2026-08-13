/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.room.location

import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.room.location.LastLocation
import io.element.android.libraries.matrix.api.room.location.LiveLocationShare
import io.element.android.libraries.matrix.impl.util.cancelAndDestroy
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import org.matrix.rustcomponents.sdk.LiveLocationShareListener
import org.matrix.rustcomponents.sdk.RoomInterface
import org.matrix.rustcomponents.sdk.LiveLocationShare as RustLiveLocationShare

fun RoomInterface.liveLocationSharesFlow(): Flow<List<LiveLocationShare>> {
    return callbackFlow {
        val taskHandle = subscribeToLiveLocationShares(object : LiveLocationShareListener {
            override fun call(liveLocationShares: List<RustLiveLocationShare>) {
                trySend(liveLocationShares.map { it.into() })
            }
        })
        awaitClose {
            taskHandle.cancelAndDestroy()
        }
    }.buffer(Channel.UNLIMITED)
}

private fun RustLiveLocationShare.into(): LiveLocationShare {
    val lastLocationTimestamp = lastLocation.ts.toLong()
    return LiveLocationShare(
        beaconId = EventId("\$live-location-$userId"),
        userId = UserId(userId),
        lastLocation = LastLocation(
            geoUri = lastLocation.location.geoUri,
            timestamp = lastLocationTimestamp,
            assetType = lastLocation.location.asset?.into() ?: io.element.android.libraries.matrix.api.room.location.AssetType.UNKNOWN,
        ),
        startTimestamp = lastLocationTimestamp,
        endTimestamp = if (isLive) Long.MAX_VALUE else lastLocationTimestamp,
    )
}

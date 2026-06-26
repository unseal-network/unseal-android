/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.room

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.max

internal const val TYPING_DISPLAY_TIMEOUT_MILLIS = 30_000L

internal fun Flow<List<String>>.expireTypingMembers(
    timeoutMillis: Long = TYPING_DISPLAY_TIMEOUT_MILLIS,
    nowMillis: () -> Long,
): Flow<List<String>> = channelFlow {
    val lastSeenByUserId = linkedMapOf<String, Long>()
    var expiryJob: Job? = null

    fun nonExpiredUserIds(now: Long): List<String> {
        val iterator = lastSeenByUserId.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value >= timeoutMillis) {
                iterator.remove()
            }
        }
        return lastSeenByUserId.entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Long>> { it.value }
                    .thenBy { it.key }
            )
            .map { it.key }
    }

    fun publish() {
        trySend(nonExpiredUserIds(nowMillis()))
    }

    fun scheduleExpiryCheck() {
        expiryJob?.cancel()
        if (lastSeenByUserId.isEmpty()) return

        val nextExpiryAt = lastSeenByUserId.values.min() + timeoutMillis
        val delayMillis = max(1, nextExpiryAt - nowMillis())
        expiryJob = launch {
            delay(delayMillis)
            publish()
            scheduleExpiryCheck()
        }
    }

    collectLatest { typingUserIds ->
        val now = nowMillis()
        val typingSet = typingUserIds.toSet()
        lastSeenByUserId.keys.retainAll(typingSet)
        typingUserIds.forEach { userId ->
            lastSeenByUserId[userId] = now
        }
        publish()
        scheduleExpiryCheck()
    }
}

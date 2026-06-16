/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.roomkey

import io.element.android.libraries.matrix.api.encryption.roomkey.RoomKeyRecoveryRequest
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class RoomKeyRecoveryPendingStore(
    private val clock: Clock = Clock.System,
    private val retryDelay: Duration = 30.minutes,
) {
    private val pendingUntilByIdentityKey = mutableMapOf<String, Instant>()

    fun markPendingIfNeeded(request: RoomKeyRecoveryRequest, force: Boolean = false): Boolean {
        pruneExpired()
        val identityKey = request.identityKey
        if (!force && pendingUntilByIdentityKey[identityKey]?.let { it > clock.now() } == true) {
            return false
        }
        pendingUntilByIdentityKey[identityKey] = clock.now() + retryDelay
        return true
    }

    fun removePending(request: RoomKeyRecoveryRequest) {
        pendingUntilByIdentityKey.remove(request.identityKey)
    }

    fun remainingInterval(request: RoomKeyRecoveryRequest): Duration? {
        val deadline = pendingUntilByIdentityKey[request.identityKey] ?: return null
        val remaining = deadline - clock.now()
        return remaining.takeIf { it.isPositive() }
    }

    fun retainOnly(requests: Collection<RoomKeyRecoveryRequest>) {
        val retainedIdentityKeys = requests.mapTo(mutableSetOf()) { it.identityKey }
        pendingUntilByIdentityKey.keys.retainAll(retainedIdentityKeys)
    }

    fun pruneExpired() {
        val now = clock.now()
        pendingUntilByIdentityKey.entries.removeAll { (_, deadline) -> deadline <= now }
    }
}

/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.roomkey

import io.element.android.libraries.matrix.api.encryption.roomkey.AgentRoomKeyRecoveryRequest
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class AgentRoomKeyRecoveryPendingStore(
    private val clock: Clock = Clock.System,
    private val retryDelay: Duration = 60.seconds,
) {
    private val pendingUntilByIdentityKey = mutableMapOf<String, Instant>()

    fun markPendingIfNeeded(request: AgentRoomKeyRecoveryRequest): Boolean {
        pruneExpired()
        val identityKey = request.identityKey
        if (pendingUntilByIdentityKey[identityKey]?.let { it > clock.now() } == true) {
            return false
        }
        pendingUntilByIdentityKey[identityKey] = clock.now() + retryDelay
        return true
    }

    fun removePending(request: AgentRoomKeyRecoveryRequest) {
        pendingUntilByIdentityKey.remove(request.identityKey)
    }

    fun retainOnly(requests: Collection<AgentRoomKeyRecoveryRequest>) {
        val retainedIdentityKeys = requests.mapTo(mutableSetOf()) { it.identityKey }
        pendingUntilByIdentityKey.keys.retainAll(retainedIdentityKeys)
    }

    private fun pruneExpired() {
        val now = clock.now()
        pendingUntilByIdentityKey.entries.removeAll { (_, deadline) -> deadline <= now }
    }
}

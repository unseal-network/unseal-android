/*
 * Copyright (c) 2026 Unseal
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.broadcastusage.impl

import java.math.BigInteger

enum class BroadcastUsageSessionState {
    Open,
    Live,
    ClosedSyncing,
    Finalized,
    Failed,
}

data class BroadcastUsageSession(
    val sessionId: String,
    val broadcastId: String,
    val roomId: String,
    val meetingInstanceId: String,
    val state: BroadcastUsageSessionState,
    val confirmedBytes: BigInteger,
    val allocatedBytes: BigInteger,
    val unallocatedBytes: BigInteger,
    val pendingAllocationBytes: BigInteger,
    val syncedThrough: String?,
    val openedAt: String,
    val startedAt: String?,
    val closedAt: String?,
    val finalizedAt: String?,
    val stopReason: String?,
    val displayName: String? = null,
) {
    val isTerminal get() = state == BroadcastUsageSessionState.Finalized || state == BroadcastUsageSessionState.Failed
    val showsRuntime get() = state == BroadcastUsageSessionState.Open || state == BroadcastUsageSessionState.Live
}

data class BroadcastUsageDashboard(
    val availableTrafficBytes: BigInteger,
    val pendingAllocationBytes: BigInteger,
    val unallocatedTrafficBytes: BigInteger,
    val calculatedAt: String,
    val sessionCount: Int,
    val sessions: List<BroadcastUsageSession>,
    val nextCursor: String?,
)

data class BroadcastTrafficActivity(
    val id: String,
    val type: String,
    val amountBytes: BigInteger,
    val occurredAt: String,
    val reasonCode: String,
    val note: String?,
    val sourceReference: String,
    val broadcastId: String?,
)

data class BroadcastActivityPage(val items: List<BroadcastTrafficActivity>, val nextCursor: String?)

data class BroadcastTrafficGrant(
    val id: String,
    val source: String,
    val sourceReference: String,
    val status: String,
    val originalBytes: BigInteger,
    val consumedBytes: BigInteger,
    val remainingBytes: BigInteger,
    val validFrom: String,
    val expiresAt: String,
    val reasonCode: String,
    val note: String?,
    val revokedAt: String?,
    val revocationReasonCode: String?,
    val createdAt: String,
    val updatedAt: String,
)

data class BroadcastGrantList(val availableTrafficBytes: BigInteger, val items: List<BroadcastTrafficGrant>)

data class BroadcastRuntimeStatus(
    val phase: String,
    val playable: Boolean,
    val participantCount: Int,
    val listenerCount: Int,
    val presentationTotal: Int,
    val presentationHealthy: Int,
    val pollAfterMs: Long,
)

internal fun formatTraffic(bytes: BigInteger, signed: Boolean = false): String {
    val units = listOf("B", "KB", "MB", "GB", "TB", "PB", "EB")
    var divisor = BigInteger.ONE
    var unit = 0
    val thousand = BigInteger.valueOf(1_000)
    while (unit < units.lastIndex && bytes.abs() >= divisor * thousand) {
        divisor *= thousand
        unit++
    }
    val sign = when {
        bytes.signum() < 0 -> "-"
        signed && bytes.signum() > 0 -> "+"
        else -> ""
    }
    val absolute = bytes.abs()
    if (unit == 0) return "$sign$absolute B"
    val whole = absolute / divisor
    val decimal = (absolute % divisor) * BigInteger.TEN / divisor
    val readable = if (decimal == BigInteger.ZERO) "$sign$whole ${units[unit]}" else "$sign$whole.$decimal ${units[unit]}"
    return "$readable · $sign$absolute B"
}

internal fun formatTrafficCompact(bytes: BigInteger, signed: Boolean = false): String =
    formatTraffic(bytes, signed).substringBefore(" · ")

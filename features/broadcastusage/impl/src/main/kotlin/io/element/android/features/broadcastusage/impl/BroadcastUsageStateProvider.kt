/*
 * Copyright (c) 2026 Unseal
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.broadcastusage.impl

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import java.math.BigInteger

open class BroadcastUsageStateProvider : PreviewParameterProvider<BroadcastUsageState> {
    override val values: Sequence<BroadcastUsageState>
        get() = sequenceOf(
            BroadcastUsageState(dashboard = dashboard, history = listOf(history)),
            BroadcastUsageState(
                dashboard = dashboard,
                selectedBroadcastId = history.broadcastId,
                selectedHistory = history,
            ),
            BroadcastUsageState(loading = true),
            BroadcastUsageState(dashboardError = "Unable to connect to the server."),
            BroadcastUsageState(
                tab = BroadcastUsageTab.Activity,
                dashboard = dashboard,
                activity = listOf(activity),
            ),
            BroadcastUsageState(
                tab = BroadcastUsageTab.Grants,
                dashboard = dashboard,
                grants = BroadcastGrantList(BigInteger("5000000000"), listOf(grant)),
            ),
        )

    private val activity = BroadcastTrafficActivity(
        id = "activity-preview",
        type = "broadcast_usage",
        amountBytes = BigInteger("-1500000000"),
        occurredAt = "2026-08-05T10:00:00.000Z",
        reasonCode = "broadcast_distribution_usage",
        note = null,
        sourceReference = "broadcast-preview",
        broadcastId = "broadcast-preview",
    )

    private val grant = BroadcastTrafficGrant(
        id = "grant-preview",
        source = "promotion",
        sourceReference = "welcome",
        status = "available",
        originalBytes = BigInteger("5000000000"),
        consumedBytes = BigInteger.ZERO,
        remainingBytes = BigInteger("5000000000"),
        validFrom = "2026-08-01T00:00:00.000Z",
        expiresAt = "2026-09-01T00:00:00.000Z",
        reasonCode = "grant_issued",
        note = null,
        revokedAt = null,
        revocationReasonCode = null,
        createdAt = "2026-08-01T00:00:00.000Z",
        updatedAt = "2026-08-01T00:00:00.000Z",
    )

    private val history = BroadcastHistoryItem(
        sessionId = "session-preview",
        broadcastId = "broadcast-preview",
        roomId = "!room:example.org",
        openedAt = "2026-08-05T09:00:00.000Z",
        closedAt = "2026-08-05T10:00:00.000Z",
        finalizedAt = "2026-08-05T10:01:00.000Z",
        traffic = BroadcastHistoryTraffic(
            confirmedBytes = BigInteger("1500000000"),
            grantCoveredBytes = BigInteger("1000000000"),
            balanceCoveredBytes = BigInteger("500000000"),
            pendingAllocationBytes = BigInteger.ZERO,
        ),
        audience = BroadcastHistoryAudience(
            uniqueViewerCount = BigInteger.valueOf(8),
            viewerSessionCount = BigInteger.TEN,
            peakConcurrentViewers = BigInteger.valueOf(4),
            finalizedAt = "2026-08-05T10:01:00.000Z",
        ),
        billing = BroadcastHistoryBilling(
            costMicros = BigInteger.valueOf(35_000),
            pricePerBytePicos = BigInteger.valueOf(70),
            chargedAt = "2026-08-05T10:02:00.000Z",
        ),
        displayName = "Weekly broadcast",
    )

    private val dashboard = BroadcastUsageDashboard(
        availableTrafficBytes = BigInteger("5000000000"),
        funding = BroadcastUsageFunding(
            grantBytes = BigInteger("5000000000"),
            balanceMicros = BigInteger("1250000"),
            effectiveBalanceMicros = BigInteger("1250000"),
            pendingBroadcastMicros = BigInteger.ZERO,
            pendingOtherUsageMicros = BigInteger.ZERO,
            balanceEquivalentBytes = BigInteger.ZERO,
            pricePerBytePicos = BigInteger.valueOf(70),
            pricePerGbMicros = BigInteger.valueOf(70_000),
            bytesPerGb = BigInteger("1000000000"),
        ),
        pendingAllocationBytes = BigInteger.ZERO,
        unallocatedTrafficBytes = BigInteger.ZERO,
        calculatedAt = "2026-08-05T10:00:00.000Z",
        sessionCount = 0,
        sessions = emptyList(),
        nextCursor = null,
    )
}

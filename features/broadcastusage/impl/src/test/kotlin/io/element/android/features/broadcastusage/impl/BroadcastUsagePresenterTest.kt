/*
 * Copyright (c) 2026 Unseal
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.broadcastusage.impl

import app.cash.turbine.TurbineTestContext
import com.google.common.truth.Truth.assertThat
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.test
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.math.BigInteger

class BroadcastUsagePresenterTest {
    @get:Rule val warmUpRule = WarmUpRule()

    @Test
    fun `foreground loads dashboard once and stable id opens detail`() = runTest {
        val service = mockk<BroadcastUsageService>()
        coEvery { service.dashboard(any(), any()) } returns DASHBOARD
        coEvery { service.session("bcast_one") } returns SESSION
        val presenter = BroadcastUsagePresenter(service, FakeMatrixClient())

        presenter.test {
            awaitItem().eventSink(BroadcastUsageEvent.Foreground)
            val loaded = awaitStateWhere { it.dashboard != null }
            assertThat(loaded.dashboard?.availableTrafficBytes).isEqualTo(BigInteger.TEN)
            coVerify(exactly = 1) { service.dashboard(any(), any()) }

            loaded.eventSink(BroadcastUsageEvent.OpenSession("bcast_one"))
            val detail = awaitStateWhere { it.selectedSession?.stopReason == "host_ended" }
            assertThat(detail.selectedSession?.roomId).isEqualTo("!room:unseal.test")
            coVerify(exactly = 1) { service.session("bcast_one") }
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun TurbineTestContext<BroadcastUsageState>.awaitStateWhere(
        predicate: (BroadcastUsageState) -> Boolean,
    ): BroadcastUsageState {
        while (true) {
            val state = awaitItem()
            if (predicate(state)) return state
        }
    }

    private companion object {
        val SESSION = BroadcastUsageSession(
            sessionId = "11111111-1111-4111-8111-111111111111", broadcastId = "bcast_one", roomId = "!room:unseal.test",
            meetingInstanceId = "22222222-2222-4222-8222-222222222222", state = BroadcastUsageSessionState.Finalized,
            confirmedBytes = BigInteger.ONE, allocatedBytes = BigInteger.ONE, unallocatedBytes = BigInteger.ZERO,
            pendingAllocationBytes = BigInteger.ZERO, syncedThrough = "2026-08-05T10:00:00Z", openedAt = "2026-08-05T09:00:00Z",
            startedAt = "2026-08-05T09:01:00Z", closedAt = "2026-08-05T09:30:00Z", finalizedAt = "2026-08-05T10:00:00Z",
            stopReason = "host_ended",
        )
        val DASHBOARD = BroadcastUsageDashboard(
            availableTrafficBytes = BigInteger.TEN, pendingAllocationBytes = BigInteger.ZERO, unallocatedTrafficBytes = BigInteger.ZERO,
            calculatedAt = "2026-08-05T10:00:00Z", sessionCount = 1, sessions = listOf(SESSION.copy(stopReason = null)), nextCursor = null,
        )
    }
}

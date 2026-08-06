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
import io.element.android.services.toolbox.test.strings.FakeStringProvider
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
        coEvery { service.history(any(), any()) } returns BroadcastHistoryPage(emptyList(), null)
        coEvery { service.session("bcast_one") } returnsMany listOf(
            SESSION,
            SESSION.copy(confirmedBytes = BigInteger.TWO),
        )
        val presenter = BroadcastUsagePresenter(service, FakeMatrixClient(), FakeStringProvider())

        presenter.test {
            awaitItem().eventSink(BroadcastUsageEvent.Foreground)
            val loaded = awaitStateWhere { it.dashboard != null }
            assertThat(loaded.dashboard?.availableTrafficBytes).isEqualTo(BigInteger.TEN)
            coVerify(exactly = 1) { service.dashboard(any(), any()) }

            loaded.eventSink(BroadcastUsageEvent.OpenSession("bcast_one"))
            val detail = awaitStateWhere { it.selectedSession?.stopReason == "host_ended" }
            assertThat(detail.selectedBroadcastId).isEqualTo("bcast_one")
            assertThat(detail.selectedSession?.roomId).isEqualTo("!room:unseal.test")
            coVerify(exactly = 1) { service.session("bcast_one") }

            detail.eventSink(BroadcastUsageEvent.Refresh)
            val refreshed = awaitStateWhere { it.selectedSession?.confirmedBytes == BigInteger.TWO }
            assertThat(refreshed.sessionError).isNull()
            coVerify(exactly = 2) { service.session("bcast_one") }
            coVerify(exactly = 1) { service.dashboard(any(), any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `activity failure remains visible when grants succeeds`() = runTest {
        val service = mockk<BroadcastUsageService>()
        coEvery { service.dashboard(any(), any()) } returns DASHBOARD
        coEvery { service.history(any(), any()) } returns BroadcastHistoryPage(emptyList(), null)
        coEvery { service.activity(any(), any()) } throws IllegalStateException("activity failed")
        coEvery { service.grants() } returns GRANTS
        val presenter = BroadcastUsagePresenter(service, FakeMatrixClient(), FakeStringProvider(defaultResult = LOCALIZED_ERROR))

        presenter.test {
            awaitItem().eventSink(BroadcastUsageEvent.Foreground)
            val loaded = awaitStateWhere { it.dashboard != null }
            loaded.eventSink(BroadcastUsageEvent.SelectTab(BroadcastUsageTab.Activity))
            val activityFailed = awaitStateWhere { it.activityError == LOCALIZED_ERROR }
            activityFailed.eventSink(BroadcastUsageEvent.SelectTab(BroadcastUsageTab.Grants))
            val grantsLoaded = awaitStateWhere { it.grants != null }

            assertThat(grantsLoaded.activityError).isEqualTo(LOCALIZED_ERROR)
            assertThat(grantsLoaded.grantsError).isNull()
            assertThat(grantsLoaded.dashboardError).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `history paginates without duplicates and opens refreshed detail`() = runTest {
        val service = mockk<BroadcastUsageService>()
        coEvery { service.dashboard(any(), any()) } returns DASHBOARD
        coEvery { service.history(any(), null) } returns BroadcastHistoryPage(listOf(HISTORY), "next")
        coEvery { service.history(any(), "next") } returns BroadcastHistoryPage(listOf(HISTORY, HISTORY.copy(sessionId = "session-two", broadcastId = "bcast_two")), null)
        coEvery { service.historyDetail("bcast_history") } returns HISTORY.copy(
            billing = HISTORY.billing.copy(costMicros = BigInteger.TWO),
        )
        val presenter = BroadcastUsagePresenter(service, FakeMatrixClient(), FakeStringProvider())

        presenter.test {
            awaitItem().eventSink(BroadcastUsageEvent.Foreground)
            val firstPage = awaitStateWhere { it.history.size == 1 && it.historyNextCursor == "next" }
            firstPage.eventSink(BroadcastUsageEvent.LoadMoreHistory)
            val secondPage = awaitStateWhere { it.history.size == 2 && it.historyNextCursor == null }
            secondPage.eventSink(BroadcastUsageEvent.OpenHistory("bcast_history"))
            val detail = awaitStateWhere { it.selectedHistory?.billing?.costMicros == BigInteger.TWO }

            assertThat(detail.selectedBroadcastId).isEqualTo("bcast_history")
            coVerify(exactly = 1) { service.history(any(), "next") }
            coVerify(exactly = 1) { service.historyDetail("bcast_history") }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `history detail failure remains retryable`() = runTest {
        val service = mockk<BroadcastUsageService>()
        coEvery { service.dashboard(any(), any()) } returns DASHBOARD
        coEvery { service.history(any(), any()) } returns BroadcastHistoryPage(listOf(HISTORY), null)
        coEvery { service.historyDetail("bcast_history") } throws IllegalStateException("detail failed")
        val presenter = BroadcastUsagePresenter(service, FakeMatrixClient(), FakeStringProvider(defaultResult = LOCALIZED_ERROR))

        presenter.test {
            awaitItem().eventSink(BroadcastUsageEvent.Foreground)
            val loaded = awaitStateWhere { it.history.isNotEmpty() }
            loaded.eventSink(BroadcastUsageEvent.OpenHistory("bcast_history"))
            val failed = awaitStateWhere { it.selectedBroadcastId == "bcast_history" && it.historyError == LOCALIZED_ERROR }

            failed.eventSink(BroadcastUsageEvent.Refresh)
            coVerify(atLeast = 2) { service.historyDetail("bcast_history") }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `traffic formatting appends exact bytes and preserves signs`() {
        val bytes = BigInteger("9223372036854775808")

        assertThat(formatTraffic(bytes)).isEqualTo("9.2 EB · 9223372036854775808 B")
        assertThat(formatTraffic(bytes, signed = true)).isEqualTo("+9.2 EB · +9223372036854775808 B")
        assertThat(formatTraffic(bytes.negate(), signed = true)).isEqualTo("-9.2 EB · -9223372036854775808 B")
        assertThat(formatTrafficCompact(bytes)).isEqualTo("9.2 EB")
        assertThat(formatUsdMicros(BigInteger.ONE)).isEqualTo("\$0.000001")
        assertThat(formatUsdMicros(BigInteger("70000"))).isEqualTo("\$0.07")
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
        const val LOCALIZED_ERROR = "Localized broadcast usage error"
        val SESSION = BroadcastUsageSession(
            sessionId = "11111111-1111-4111-8111-111111111111", broadcastId = "bcast_one", roomId = "!room:unseal.test",
            meetingInstanceId = "22222222-2222-4222-8222-222222222222", state = BroadcastUsageSessionState.Finalized,
            confirmedBytes = BigInteger.ONE, allocatedBytes = BigInteger.ONE, unallocatedBytes = BigInteger.ZERO,
            pendingAllocationBytes = BigInteger.ZERO, syncedThrough = "2026-08-05T10:00:00Z", openedAt = "2026-08-05T09:00:00Z",
            startedAt = "2026-08-05T09:01:00Z", closedAt = "2026-08-05T09:30:00Z", finalizedAt = "2026-08-05T10:00:00Z",
            stopReason = "host_ended",
        )
        val DASHBOARD = BroadcastUsageDashboard(
            availableTrafficBytes = BigInteger.TEN,
            funding = BroadcastUsageFunding(BigInteger.TEN, BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO),
            pendingAllocationBytes = BigInteger.ZERO, unallocatedTrafficBytes = BigInteger.ZERO,
            calculatedAt = "2026-08-05T10:00:00Z", sessionCount = 1, sessions = listOf(SESSION.copy(stopReason = null)), nextCursor = null,
        )
        val GRANTS = BroadcastGrantList(
            availableTrafficBytes = BigInteger.TEN,
            items = emptyList(),
        )
        val HISTORY = BroadcastHistoryItem(
            sessionId = "session-history",
            broadcastId = "bcast_history",
            roomId = "!room:unseal.test",
            openedAt = "2026-08-05T09:00:00Z",
            closedAt = "2026-08-05T10:00:00Z",
            finalizedAt = "2026-08-05T10:01:00Z",
            traffic = BroadcastHistoryTraffic(BigInteger.TEN, BigInteger.ONE, BigInteger.valueOf(9), BigInteger.ZERO),
            audience = BroadcastHistoryAudience(BigInteger.ONE, BigInteger.TWO, BigInteger.ONE, "2026-08-05T10:01:00Z"),
            billing = BroadcastHistoryBilling(BigInteger.ONE, BigInteger.valueOf(70), "2026-08-05T10:02:00Z"),
        )
    }
}

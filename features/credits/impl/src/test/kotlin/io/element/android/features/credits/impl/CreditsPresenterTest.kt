/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.credits.impl

import app.cash.turbine.TurbineTestContext
import com.google.common.truth.Truth.assertThat
import io.element.android.features.credits.api.CreditsEntryPoint
import io.element.android.features.credits.impl.model.CreditsPeriod
import io.element.android.features.credits.impl.model.DailyUsageRange
import io.element.android.features.credits.impl.model.UsageRankingTab
import io.element.android.libraries.chatbot.api.ChatbotApiService
import io.element.android.libraries.chatbot.api.ChatbotApiServiceFactory
import io.element.android.libraries.chatbot.api.model.analytics.AnalyticsAgentSummary
import io.element.android.libraries.chatbot.api.model.analytics.AnalyticsTokensResponse
import io.element.android.libraries.chatbot.api.model.credits.CreditBalance
import io.element.android.libraries.chatbot.api.model.credits.CreditDailyBucket
import io.element.android.libraries.chatbot.api.model.credits.CreditDailyUsageResponse
import io.element.android.libraries.chatbot.api.model.credits.CreditLedgerItem
import io.element.android.libraries.chatbot.api.model.credits.CreditLedgerResponse
import io.element.android.libraries.chatbot.test.FakeChatbotApiService
import io.element.android.libraries.chatbot.test.FakeChatbotApiServiceFactory
import io.element.android.libraries.chatbot.test.aCreditBalance
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class CreditsPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `present - balance tab on appear loads balance ledger and daily usage`() = runTest {
        val service = FakeChatbotApiService().apply {
            getBalanceResult = { Result.success(aCreditBalance(balanceMicros = "2500000").copy(balanceUsd = "2.50")) }
            getLedgerResult = { limit, cursor ->
                assertThat(limit).isEqualTo(10)
                assertThat(cursor).isNull()
                Result.success(CreditLedgerResponse(items = listOf(ledger("one")), nextCursor = "cursor-2"))
            }
            getDailyUsageResult = { start, end ->
                Result.success(CreditDailyUsageResponse(start = start, end = end, daily = listOf(bucket(start, "100000")), totalUsageMicros = "100000"))
            }
            getAnalyticsTokensResult = { period ->
                Result.success(analytics(period))
            }
        }
        val presenter = createPresenter(service = service)

        presenter.test {
            val initial = awaitItem()
            assertThat(initial.selectedTab).isEqualTo(CreditsEntryPoint.CreditsTab.Balance)
            assertThat(initial.dailyUsageRange).isEqualTo(DailyUsageRange.SevenDays)
            assertThat(initial.analyticsPeriod).isEqualTo(CreditsPeriod.ThirtyDays)
            initial.eventSink(CreditsEvents.OnAppear)

            val loaded = awaitStateWhere {
                it.balance?.balanceMicros == "2500000" &&
                    it.transactions.singleOrNull()?.id == "one" &&
                    it.dailyUsage?.daily?.size == 1 &&
                    !it.isBalanceLoading &&
                    !it.isLedgerLoading &&
                    !it.isDailyUsageLoading &&
                    !it.isAnalyticsLoading
            }
            assertThat(loaded.transactionsCursor).isEqualTo("cursor-2")
            assertThat(loaded.hasMoreTransactions).isTrue()
            assertThat(loaded.analytics).isNull()
            assertThat(loaded.analyticsError).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - appear only loads once`() = runTest {
        var balanceCalls = 0
        var ledgerCalls = 0
        var dailyUsageCalls = 0
        var analyticsCalls = 0
        val service = FakeChatbotApiService().apply {
            getBalanceResult = {
                balanceCalls++
                Result.success(aCreditBalance())
            }
            getLedgerResult = { _, _ ->
                ledgerCalls++
                Result.success(CreditLedgerResponse())
            }
            getDailyUsageResult = { start, end ->
                dailyUsageCalls++
                Result.success(CreditDailyUsageResponse(start = start, end = end, totalUsageMicros = "0"))
            }
            getAnalyticsTokensResult = {
                analyticsCalls++
                Result.success(analytics(it))
            }
        }
        val presenter = createPresenter(service = service)

        presenter.test {
            awaitItem().eventSink(CreditsEvents.OnAppear)
            val loaded = awaitStateWhere { !it.isBalanceLoading && !it.isLedgerLoading && !it.isDailyUsageLoading && !it.isAnalyticsLoading }
            loaded.eventSink(CreditsEvents.OnAppear)
            assertThat(balanceCalls).isEqualTo(1)
            assertThat(ledgerCalls).isEqualTo(1)
            assertThat(dailyUsageCalls).isEqualTo(1)
            assertThat(analyticsCalls).isEqualTo(0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - balance tab does not eagerly load usage analytics`() = runTest {
        var analyticsCalls = 0
        val service = FakeChatbotApiService().apply {
            getAnalyticsTokensResult = {
                analyticsCalls++
                Result.failure(RuntimeException("analytics down"))
            }
        }
        val presenter = createPresenter(service = service, initialTab = CreditsEntryPoint.CreditsTab.Balance)

        presenter.test {
            awaitItem().eventSink(CreditsEvents.OnAppear)
            val loaded = awaitStateWhere { !it.isBalanceLoading && !it.isLedgerLoading && !it.isDailyUsageLoading }
            assertThat(analyticsCalls).isEqualTo(0)
            assertThat(loaded.analyticsError).isNull()
            loaded.eventSink(CreditsEvents.SelectTab(CreditsEntryPoint.CreditsTab.Usage))
            val failedUsage = awaitStateWhere { !it.isAnalyticsLoading && it.analyticsError?.contains("analytics down") == true }
            assertThat(failedUsage.selectedTab).isEqualTo(CreditsEntryPoint.CreditsTab.Usage)
            assertThat(analyticsCalls).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - changing daily range reloads daily usage`() = runTest {
        val requestedRanges = mutableListOf<IntRange>()
        val service = FakeChatbotApiService().apply {
            getDailyUsageResult = { start, end ->
                requestedRanges += start until end
                Result.success(CreditDailyUsageResponse(start = start, end = end, daily = listOf(bucket(start, "1")), totalUsageMicros = "1"))
            }
        }
        val presenter = createPresenter(service = service, initialTab = CreditsEntryPoint.CreditsTab.DailyUsage)

        presenter.test {
            awaitItem().eventSink(CreditsEvents.OnAppear)
            val loaded = awaitStateWhere { it.dailyUsage != null && !it.isDailyUsageLoading }
            loaded.eventSink(CreditsEvents.SelectDailyUsageRange(DailyUsageRange.ThirtyDays))
            val reloaded = awaitStateWhere { it.dailyUsageRange == DailyUsageRange.ThirtyDays && requestedRanges.size == 2 && !it.isDailyUsageLoading }
            assertThat(reloaded.dailyUsage?.daily).hasSize(1)
            assertThat(requestedRanges).hasSize(2)
            val second = requestedRanges[1]
            assertThat(second.last - second.first + 1).isEqualTo(30 * 24 * 60 * 60)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - changing analytics period reloads analytics with iOS lowercase values`() = runTest {
        val periods = mutableListOf<String>()
        val service = FakeChatbotApiService().apply {
            getAnalyticsTokensResult = { period ->
                periods += period
                Result.success(analytics(period))
            }
        }
        val presenter = createPresenter(service = service, initialTab = CreditsEntryPoint.CreditsTab.Usage)

        presenter.test {
            awaitItem().eventSink(CreditsEvents.OnAppear)
            val loaded = awaitStateWhere { it.analytics?.period == "thirtydays" && !it.isAnalyticsLoading }
            loaded.eventSink(CreditsEvents.SelectAnalyticsPeriod(CreditsPeriod.SevenDays))
            awaitStateWhere { it.analyticsPeriod == CreditsPeriod.SevenDays && it.analytics?.period == "sevendays" && !it.isAnalyticsLoading }
            assertThat(periods).containsExactly("thirtydays", "sevendays").inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - analytics loads from homeserver client`() = runTest {
        var homeserverAnalyticsCalls = 0
        var unsealAnalyticsCalls = 0
        val homeserverService = FakeChatbotApiService().apply {
            getAnalyticsTokensResult = {
                homeserverAnalyticsCalls++
                Result.success(analytics(it))
            }
        }
        val unsealService = FakeChatbotApiService().apply {
            getAnalyticsTokensResult = {
                unsealAnalyticsCalls++
                Result.failure(RuntimeException("wrong client"))
            }
        }
        val factory = FakeChatbotApiServiceFactory().apply {
            createForHomeserverResult = homeserverService
            createForUnsealApiResult = unsealService
        }
        val presenter = createPresenter(
            chatbotApiServiceFactory = factory,
            initialTab = CreditsEntryPoint.CreditsTab.Usage,
        )

        presenter.test {
            awaitItem().eventSink(CreditsEvents.OnAppear)
            awaitStateWhere { it.analytics?.period == "thirtydays" && !it.isAnalyticsLoading }
            assertThat(homeserverAnalyticsCalls).isEqualTo(1)
            assertThat(unsealAnalyticsCalls).isEqualTo(0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - failed daily range reload clears stale data`() = runTest {
        var failDailyUsage = false
        val service = FakeChatbotApiService().apply {
            getDailyUsageResult = { start, end ->
                if (failDailyUsage) {
                    Result.failure(RuntimeException("daily down"))
                } else {
                    Result.success(CreditDailyUsageResponse(start = start, end = end, daily = listOf(bucket(start, "123")), totalUsageMicros = "123"))
                }
            }
        }
        val presenter = createPresenter(service = service, initialTab = CreditsEntryPoint.CreditsTab.DailyUsage)

        presenter.test {
            awaitItem().eventSink(CreditsEvents.OnAppear)
            val loaded = awaitStateWhere { it.dailyUsage?.totalUsageMicros == "123" && !it.isDailyUsageLoading }
            failDailyUsage = true
            loaded.eventSink(CreditsEvents.SelectDailyUsageRange(DailyUsageRange.ThirtyDays))
            val failed = awaitStateWhere { it.dailyUsageError?.contains("daily down") == true && !it.isDailyUsageLoading }
            assertThat(failed.dailyUsage).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - failed analytics period reload clears stale data`() = runTest {
        var failAnalytics = false
        val service = FakeChatbotApiService().apply {
            getAnalyticsTokensResult = {
                if (failAnalytics) {
                    Result.failure(RuntimeException("analytics down"))
                } else {
                    Result.success(analytics(it))
                }
            }
        }
        val presenter = createPresenter(service = service, initialTab = CreditsEntryPoint.CreditsTab.Usage)

        presenter.test {
            awaitItem().eventSink(CreditsEvents.OnAppear)
            val loaded = awaitStateWhere { it.analytics?.period == "thirtydays" && !it.isAnalyticsLoading }
            failAnalytics = true
            loaded.eventSink(CreditsEvents.SelectAnalyticsPeriod(CreditsPeriod.SevenDays))
            val failed = awaitStateWhere { it.analyticsError?.contains("analytics down") == true && !it.isAnalyticsLoading }
            assertThat(failed.analytics).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - load more appends ledger results`() = runTest {
        val requestedCursors = mutableListOf<String?>()
        val service = FakeChatbotApiService().apply {
            getLedgerResult = { _, cursor ->
                requestedCursors += cursor
                if (cursor == null) {
                    Result.success(CreditLedgerResponse(items = listOf(ledger("one")), nextCursor = "cursor-2"))
                } else {
                    Result.success(CreditLedgerResponse(items = listOf(ledger("two")), nextCursor = null))
                }
            }
        }
        val presenter = createPresenter(service = service)

        presenter.test {
            awaitItem().eventSink(CreditsEvents.OnAppear)
            val loaded = awaitStateWhere { it.transactions.singleOrNull()?.id == "one" && it.hasMoreTransactions }
            loaded.eventSink(CreditsEvents.LoadMoreTransactions)
            val appended = awaitStateWhere { it.transactions.size == 2 && !it.isLoadingMoreTransactions }
            assertThat(appended.transactions.map { it.id }).containsExactly("one", "two").inOrder()
            assertThat(appended.transactionsCursor).isNull()
            assertThat(appended.hasMoreTransactions).isFalse()
            assertThat(requestedCursors).containsExactly(null, "cursor-2").inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - load more handles client creation failure`() = runTest {
        var failClient = false
        val service = FakeChatbotApiService().apply {
            getLedgerResult = { _, cursor ->
                if (cursor == null) {
                    Result.success(CreditLedgerResponse(items = listOf(ledger("one")), nextCursor = "cursor-2"))
                } else {
                    Result.success(CreditLedgerResponse(items = listOf(ledger("two")), nextCursor = null))
                }
            }
        }
        val factory = object : ChatbotApiServiceFactory {
            override suspend fun createForAiStream(matrixClient: MatrixClient): ChatbotApiService = service
            override suspend fun createForHomeserver(matrixClient: MatrixClient): ChatbotApiService = service
            override suspend fun createForUnsealApi(matrixClient: MatrixClient): ChatbotApiService {
                if (failClient) throw RuntimeException("client down")
                return service
            }
            override fun createForBaseUrl(baseUrl: String, matrixClient: MatrixClient): ChatbotApiService = service
        }
        val presenter = createPresenter(chatbotApiServiceFactory = factory)

        presenter.test {
            awaitItem().eventSink(CreditsEvents.OnAppear)
            val loaded = awaitStateWhere { it.transactions.singleOrNull()?.id == "one" && it.hasMoreTransactions }
            failClient = true
            loaded.eventSink(CreditsEvents.LoadMoreTransactions)
            val failed = awaitStateWhere { it.transactionsError?.contains("client down") == true && !it.isLoadingMoreTransactions }
            assertThat(failed.transactions).hasSize(1)
            assertThat(failed.hasMoreTransactions).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - load more is ignored without next cursor`() = runTest {
        var ledgerCalls = 0
        val service = FakeChatbotApiService().apply {
            getLedgerResult = { _, _ ->
                ledgerCalls++
                Result.success(CreditLedgerResponse(items = listOf(ledger("one")), nextCursor = null))
            }
        }
        val presenter = createPresenter(service = service)

        presenter.test {
            awaitItem().eventSink(CreditsEvents.OnAppear)
            val loaded = awaitStateWhere { it.transactions.singleOrNull()?.id == "one" && !it.hasMoreTransactions }
            loaded.eventSink(CreditsEvents.LoadMoreTransactions)
            assertThat(ledgerCalls).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - api failures preserve existing loaded content`() = runTest {
        var failDailyUsage = false
        val service = FakeChatbotApiService().apply {
            getDailyUsageResult = { start, end ->
                if (failDailyUsage) {
                    Result.failure(RuntimeException("network"))
                } else {
                    Result.success(CreditDailyUsageResponse(start = start, end = end, daily = listOf(bucket(start, "123")), totalUsageMicros = "123"))
                }
            }
        }
        val presenter = createPresenter(service = service)

        presenter.test {
            awaitItem().eventSink(CreditsEvents.OnAppear)
            val loaded = awaitStateWhere { it.dailyUsage?.totalUsageMicros == "123" && !it.isDailyUsageLoading }
            failDailyUsage = true
            loaded.eventSink(CreditsEvents.SelectDailyUsageRange(DailyUsageRange.ThirtyDays))
            val failed = awaitStateWhere { it.dailyUsageError?.contains("network") == true && !it.isDailyUsageLoading }
            assertThat(failed.dailyUsage).isNull()
            failed.eventSink(CreditsEvents.ClearError)
            val cleared = awaitStateWhere { it.dailyUsageError == null }
            assertThat(cleared.dailyUsage).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - top up passes current balance to navigator`() = runTest {
        val balance = aCreditBalance(balanceMicros = "9000000").copy(balanceUsd = "9.00")
        val service = FakeChatbotApiService().apply {
            getBalanceResult = { Result.success(balance) }
        }
        val navigator = FakeCreditsNavigator()
        val presenter = createPresenter(service = service, navigator = navigator)

        presenter.test {
            awaitItem().eventSink(CreditsEvents.OnAppear)
            val loaded = awaitStateWhere { it.balance == balance && !it.isBalanceLoading }
            loaded.eventSink(CreditsEvents.RequestTopUp)
            assertThat(navigator.topUpBalances).containsExactly(balance)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createPresenter(
        service: FakeChatbotApiService = FakeChatbotApiService(),
        navigator: FakeCreditsNavigator = FakeCreditsNavigator(),
        initialTab: CreditsEntryPoint.CreditsTab = CreditsEntryPoint.CreditsTab.Balance,
        chatbotApiServiceFactory: ChatbotApiServiceFactory = FakeChatbotApiServiceFactory(service),
    ): CreditsPresenter {
        return CreditsPresenter(
            initialTab = initialTab,
            navigator = navigator,
            matrixClient = FakeMatrixClient(),
            chatbotApiServiceFactory = chatbotApiServiceFactory,
        )
    }
}

private class FakeCreditsNavigator : CreditsNavigator {
    var doneCalls = 0
    val topUpBalances = mutableListOf<CreditBalance?>()

    override fun onDone() {
        doneCalls++
    }

    override fun onTopUpRequested(balance: CreditBalance?) {
        topUpBalances += balance
    }
}

private suspend fun TurbineTestContext<CreditsState>.awaitStateWhere(
    predicate: (CreditsState) -> Boolean,
): CreditsState {
    while (true) {
        val state = awaitItem()
        if (predicate(state)) {
            return state
        }
    }
}

private fun ledger(id: String) = CreditLedgerItem(
    id = id,
    deltaMicros = "100000",
    balanceAfterMicros = "1000000",
    source = "topup",
    category = "credits",
    title = "Transaction $id",
    ts = "2026-06-09T00:00:00Z",
)

private fun bucket(start: Int, micros: String) = CreditDailyBucket(
    start = start,
    usageMicros = micros,
)

private fun analytics(period: String) = AnalyticsTokensResponse(
    period = period,
    summaryByAgent = listOf(
        AnalyticsAgentSummary(
            agentId = "agent",
            inputTokens = 10,
            outputTokens = 20,
            callCount = 1,
            pct = 100.0,
        )
    ),
)

/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalTestApi::class)

package io.element.android.features.credits.impl

import androidx.activity.ComponentActivity
import android.content.Context
import androidx.compose.ui.test.AndroidComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.element.android.features.credits.api.CreditsEntryPoint
import io.element.android.features.credits.impl.model.CreditsPeriod
import io.element.android.features.credits.impl.model.DailyUsageRange
import io.element.android.features.credits.impl.model.UsageRankingTab
import io.element.android.libraries.chatbot.api.model.analytics.AnalyticsAgentSummary
import io.element.android.libraries.chatbot.api.model.analytics.AnalyticsModelSummary
import io.element.android.libraries.chatbot.api.model.analytics.AnalyticsTokensResponse
import io.element.android.libraries.chatbot.api.model.credits.CreditBalance
import io.element.android.libraries.chatbot.api.model.credits.CreditDailyBucket
import io.element.android.libraries.chatbot.api.model.credits.CreditDailyUsageResponse
import io.element.android.libraries.chatbot.api.model.credits.CreditLedgerItem
import io.element.android.tests.testutils.EventsRecorder
import kotlinx.collections.immutable.toImmutableList
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class CreditsViewTest {
    @Test
    fun `view - shows title and top level tabs`() = runAndroidComposeUiTest {
        setView()

        onNodeWithText(testString(R.string.credits_title)).assertIsDisplayed()
        onNodeWithText(testString(R.string.credits_tab_balance)).assertIsDisplayed()
        onNodeWithText(testString(R.string.credits_tab_daily_usage)).assertIsDisplayed()
        onNodeWithText(testString(R.string.credits_tab_usage)).assertIsDisplayed()
    }

    @Test
    fun `balance tab - displays balance user id and transactions`() = runAndroidComposeUiTest {
        setView(
            state = aCreditsState(
                balance = CreditBalance(
                    userId = "@alice:server.org",
                    balanceMicros = "12500000",
                    balanceUsd = "12.50",
                ),
                transactions = listOf(ledger("txn-1", title = "Credit purchase", deltaMicros = "5000000")),
            )
        )

        onNodeWithText("$12.50").assertIsDisplayed()
        onNodeWithText("@alice:server.org").assertIsDisplayed()
        onNodeWithText("Credit purchase").assertIsDisplayed()
        onNodeWithText("+$5.00").assertIsDisplayed()
    }

    @Config(qualifiers = "h1800dp")
    @Test
    fun `balance tab - displays empty state and load more`() = runAndroidComposeUiTest {
        setView(
            state = aCreditsState(
                transactions = emptyList(),
                transactionsCursor = "cursor-2",
            )
        )

        onNodeWithText(testString(R.string.credits_no_transactions)).performScrollTo().assertIsDisplayed()
        onNodeWithText(testString(R.string.credits_load_more)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `daily usage tab - displays range controls and total spent`() = runAndroidComposeUiTest {
        setView(
            state = aCreditsState(
                selectedTab = CreditsEntryPoint.CreditsTab.DailyUsage,
                dailyUsage = CreditDailyUsageResponse(
                    start = 1,
                    end = 2,
                    daily = listOf(CreditDailyBucket(start = 1, usageMicros = "250000")),
                    totalUsageMicros = "250000",
                )
            )
        )

        onNodeWithText(testString(R.string.credits_seven_days)).assertIsDisplayed()
        onNodeWithText(testString(R.string.credits_thirty_days)).assertIsDisplayed()
        onNodeWithText(testString(R.string.credits_total_spend)).assertIsDisplayed()
        onAllNodesWithText("$0.25").assertCountEquals(1)
    }

    @Config(qualifiers = "h1800dp")
    @Test
    fun `usage tab - displays ranking and period controls`() = runAndroidComposeUiTest {
        setView(
            state = aCreditsState(
                selectedTab = CreditsEntryPoint.CreditsTab.Usage,
                analytics = analytics(),
            )
        )

        onNodeWithText("Agent").performScrollTo().assertIsDisplayed()
        onNodeWithText("Model").performScrollTo().assertIsDisplayed()
        onNodeWithText(testString(R.string.credits_seven_days)).performScrollTo().assertIsDisplayed()
        onNodeWithText(testString(R.string.credits_thirty_days)).performScrollTo().assertIsDisplayed()
        onNodeWithText(testString(R.string.credits_all)).performScrollTo().assertIsDisplayed()
        onNodeWithText("Alice Bot").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `recharge click emits top up event`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<CreditsEvents>()
        setView(state = aCreditsState(eventSink = eventsRecorder))
        waitForIdle()
        eventsRecorder.clear()

        onNodeWithText(testString(R.string.credits_top_up)).performClick()

        eventsRecorder.assertSingle(CreditsEvents.RequestTopUp)
    }

    @Test
    fun `view - scoped error does not expose raw exception text`() = runAndroidComposeUiTest {
        setView(
            state = aCreditsState(
                balance = null,
                balanceError = "HTTP 500 internal service error",
            )
        )

        onNodeWithText(testString(R.string.credits_error_balance_unavailable)).assertIsDisplayed()
        onNodeWithText("HTTP 500 internal service error").assertDoesNotExist()
    }
}

private fun testString(resId: Int): String {
    return ApplicationProvider.getApplicationContext<Context>().getString(resId)
}

private fun AndroidComposeUiTest<ComponentActivity>.setView(
    state: CreditsState = aCreditsState(),
) {
    setContent {
        CreditsView(state = state)
    }
}

private fun aCreditsState(
    selectedTab: CreditsEntryPoint.CreditsTab = CreditsEntryPoint.CreditsTab.Balance,
    balance: CreditBalance? = CreditBalance(
        userId = "@alice:server.org",
        balanceMicros = "0",
        balanceUsd = "0.00",
    ),
    transactions: List<CreditLedgerItem> = emptyList(),
    transactionsCursor: String? = null,
    dailyUsage: CreditDailyUsageResponse? = null,
    analytics: AnalyticsTokensResponse? = null,
    dailyUsageRange: DailyUsageRange = DailyUsageRange.SevenDays,
    usageRankingTab: UsageRankingTab = UsageRankingTab.Agent,
    analyticsPeriod: CreditsPeriod = CreditsPeriod.ThirtyDays,
    balanceError: String? = null,
    transactionsError: String? = null,
    dailyUsageError: String? = null,
    analyticsError: String? = null,
    eventSink: (CreditsEvents) -> Unit = EventsRecorder(),
) = CreditsState(
    selectedTab = selectedTab,
    balance = balance,
    transactions = transactions.toImmutableList(),
    transactionsCursor = transactionsCursor,
    dailyUsage = dailyUsage,
    analytics = analytics,
    dailyUsageRange = dailyUsageRange,
    usageRankingTab = usageRankingTab,
    analyticsPeriod = analyticsPeriod,
    isBalanceLoading = false,
    isLedgerLoading = false,
    isDailyUsageLoading = false,
    isAnalyticsLoading = false,
    isLoadingMoreTransactions = false,
    balanceError = balanceError,
    transactionsError = transactionsError,
    dailyUsageError = dailyUsageError,
    analyticsError = analyticsError,
    eventSink = eventSink,
)

private fun ledger(
    id: String,
    title: String = "Transaction $id",
    deltaMicros: String = "100000",
) = CreditLedgerItem(
    id = id,
    deltaMicros = deltaMicros,
    balanceAfterMicros = "1000000",
    source = "topup",
    category = "credits",
    title = title,
    ts = "2026-06-09T00:00:00Z",
)

private fun analytics() = AnalyticsTokensResponse(
    period = "30d",
    summaryByAgent = listOf(
        AnalyticsAgentSummary(
            agentId = "alice-bot",
            displayName = "Alice Bot",
            inputTokens = 10,
            outputTokens = 20,
            callCount = 1,
            pct = 100.0,
        )
    ),
    summaryByModel = listOf(
        AnalyticsModelSummary(
            model = "gpt-4.1",
            inputTokens = 30,
            outputTokens = 40,
            pct = 100.0,
        )
    ),
)

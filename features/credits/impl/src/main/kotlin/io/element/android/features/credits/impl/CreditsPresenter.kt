/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.credits.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.features.credits.api.CreditsEntryPoint
import io.element.android.features.credits.impl.model.CreditsPeriod
import io.element.android.features.credits.impl.model.DailyUsageRange
import io.element.android.features.credits.impl.model.UsageRankingTab
import io.element.android.features.credits.impl.model.localDayRangeEpochSeconds
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.chatbot.api.ChatbotApiService
import io.element.android.libraries.chatbot.api.ChatbotApiServiceFactory
import io.element.android.libraries.chatbot.api.model.analytics.AnalyticsTokensResponse
import io.element.android.libraries.chatbot.api.model.credits.CreditBalance
import io.element.android.libraries.chatbot.api.model.credits.CreditDailyUsageResponse
import io.element.android.libraries.chatbot.api.model.credits.CreditLedgerItem
import io.element.android.libraries.matrix.api.MatrixClient
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

interface CreditsNavigator {
    fun onDone()
    fun onTopUpRequested(balance: CreditBalance?)
}

@AssistedInject
class CreditsPresenter(
    @Assisted private val initialTab: CreditsEntryPoint.CreditsTab,
    @Assisted private val navigator: CreditsNavigator,
    private val matrixClient: MatrixClient,
    private val chatbotApiServiceFactory: ChatbotApiServiceFactory,
) : Presenter<CreditsState> {
    @AssistedFactory
    interface Factory {
        fun create(
            initialTab: CreditsEntryPoint.CreditsTab,
            navigator: CreditsNavigator,
        ): CreditsPresenter
    }

    @Composable
    override fun present(): CreditsState {
        val coroutineScope = rememberCoroutineScope()
        var hasAppeared by remember { mutableStateOf(false) }
        var selectedTab by remember { mutableStateOf(initialTab) }
        var balance by remember { mutableStateOf<CreditBalance?>(null) }
        var transactions by remember { mutableStateOf(emptyList<CreditLedgerItem>()) }
        var transactionsCursor by remember { mutableStateOf<String?>(null) }
        var dailyUsage by remember { mutableStateOf<CreditDailyUsageResponse?>(null) }
        var analytics by remember { mutableStateOf<AnalyticsTokensResponse?>(null) }
        var dailyUsageRange by remember { mutableStateOf(DailyUsageRange.SevenDays) }
        var usageRankingTab by remember { mutableStateOf(UsageRankingTab.Agent) }
        var analyticsPeriod by remember { mutableStateOf(CreditsPeriod.ThirtyDays) }
        var isBalanceLoading by remember { mutableStateOf(false) }
        var isLedgerLoading by remember { mutableStateOf(false) }
        var isDailyUsageLoading by remember { mutableStateOf(false) }
        var isAnalyticsLoading by remember { mutableStateOf(false) }
        var isLoadingMoreTransactions by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }

        fun errorMessage(throwable: Throwable): String {
            return throwable.message ?: throwable::class.simpleName ?: throwable.toString()
        }

        // Credits (balance/ledger/daily-usage) live on the agent-api (/api/credits/*); analytics
        // tokens live on the homeserver (/chatbot/v1/analytics/tokens) — mirror iOS, which uses two
        // separate clients. Using the agent-api client for analytics 404s.
        suspend fun api() = chatbotApiServiceFactory.createForUnsealApi(matrixClient)
        suspend fun homeserverApi() = chatbotApiServiceFactory.createForHomeserver(matrixClient)

        suspend fun loadBalance(service: ChatbotApiService) {
            isBalanceLoading = true
            service.getBalance()
                .onSuccess {
                    balance = it
                    error = null
                }
                .onFailure {
                    error = errorMessage(it)
                }
            isBalanceLoading = false
        }

        suspend fun loadLedger(service: ChatbotApiService) {
            isLedgerLoading = true
            service.getLedger(limit = 10, cursor = null)
                .onSuccess {
                    transactions = it.items
                    transactionsCursor = it.nextCursor
                    error = null
                }
                .onFailure {
                    error = errorMessage(it)
                }
            isLedgerLoading = false
        }

        suspend fun loadDailyUsage(service: ChatbotApiService, range: DailyUsageRange) {
            isDailyUsageLoading = true
            val dayRange = localDayRangeEpochSeconds(range)
            service.getDailyUsage(start = dayRange.start, end = dayRange.end)
                .onSuccess {
                    dailyUsage = it
                    error = null
                }
                .onFailure {
                    error = errorMessage(it)
                }
            isDailyUsageLoading = false
        }

        suspend fun loadAnalytics(service: ChatbotApiService, period: CreditsPeriod) {
            isAnalyticsLoading = true
            service.getAnalyticsTokens(period.apiValue)
                .onSuccess {
                    analytics = it
                    error = null
                }
                .onFailure {
                    error = errorMessage(it)
                }
            isAnalyticsLoading = false
        }

        fun loadAll() = coroutineScope.launch {
            isBalanceLoading = true
            isLedgerLoading = true
            isDailyUsageLoading = true
            isAnalyticsLoading = true
            val service = runCatching { api() }
                .onFailure {
                    error = errorMessage(it)
                    isBalanceLoading = false
                    isLedgerLoading = false
                    isDailyUsageLoading = false
                }
                .getOrNull() ?: return@launch
            coroutineScope.launch { loadBalance(service) }
            coroutineScope.launch { loadLedger(service) }
            coroutineScope.launch { loadDailyUsage(service, dailyUsageRange) }
            coroutineScope.launch {
                val homeserver = runCatching { homeserverApi() }
                    .onFailure {
                        error = errorMessage(it)
                        isAnalyticsLoading = false
                    }
                    .getOrNull() ?: return@launch
                loadAnalytics(homeserver, analyticsPeriod)
            }
        }

        fun loadMoreTransactions() = coroutineScope.launch {
            val cursor = transactionsCursor ?: return@launch
            if (isLoadingMoreTransactions) return@launch
            isLoadingMoreTransactions = true
            api().getLedger(limit = 10, cursor = cursor)
                .onSuccess {
                    transactions = transactions + it.items
                    transactionsCursor = it.nextCursor
                    error = null
                }
                .onFailure {
                    error = errorMessage(it)
                }
            isLoadingMoreTransactions = false
        }

        fun handleEvent(event: CreditsEvents) {
            when (event) {
                CreditsEvents.OnAppear -> if (!hasAppeared) {
                    hasAppeared = true
                    loadAll()
                }
                is CreditsEvents.SelectTab -> selectedTab = event.tab
                is CreditsEvents.SelectDailyUsageRange -> {
                    if (dailyUsageRange != event.range) {
                        dailyUsageRange = event.range
                        isDailyUsageLoading = true
                        coroutineScope.launch { loadDailyUsage(api(), event.range) }
                    }
                }
                is CreditsEvents.SelectUsageRankingTab -> usageRankingTab = event.tab
                is CreditsEvents.SelectAnalyticsPeriod -> {
                    if (analyticsPeriod != event.period) {
                        analyticsPeriod = event.period
                        isAnalyticsLoading = true
                        coroutineScope.launch { loadAnalytics(homeserverApi(), event.period) }
                    }
                }
                CreditsEvents.LoadMoreTransactions -> loadMoreTransactions()
                CreditsEvents.ClearError -> error = null
                CreditsEvents.Dismiss -> navigator.onDone()
                CreditsEvents.RequestTopUp -> navigator.onTopUpRequested(balance)
            }
        }

        return CreditsState(
            selectedTab = selectedTab,
            balance = balance,
            transactions = transactions.toImmutableList(),
            transactionsCursor = transactionsCursor,
            dailyUsage = dailyUsage,
            analytics = analytics,
            dailyUsageRange = dailyUsageRange,
            usageRankingTab = usageRankingTab,
            analyticsPeriod = analyticsPeriod,
            isBalanceLoading = isBalanceLoading,
            isLedgerLoading = isLedgerLoading,
            isDailyUsageLoading = isDailyUsageLoading,
            isAnalyticsLoading = isAnalyticsLoading,
            isLoadingMoreTransactions = isLoadingMoreTransactions,
            error = error,
            eventSink = ::handleEvent,
        )
    }
}

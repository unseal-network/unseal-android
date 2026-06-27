/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.credits.impl

import io.element.android.features.credits.api.CreditsEntryPoint
import io.element.android.features.credits.impl.model.CreditsPeriod
import io.element.android.features.credits.impl.model.DailyUsageRange
import io.element.android.features.credits.impl.model.UsageRankingTab
import io.element.android.libraries.chatbot.api.model.analytics.AnalyticsTokensResponse
import io.element.android.libraries.chatbot.api.model.credits.CreditBalance
import io.element.android.libraries.chatbot.api.model.credits.CreditDailyUsageResponse
import io.element.android.libraries.chatbot.api.model.credits.CreditLedgerItem
import kotlinx.collections.immutable.ImmutableList

data class CreditsState(
    val selectedTab: CreditsEntryPoint.CreditsTab,
    val balance: CreditBalance?,
    val transactions: ImmutableList<CreditLedgerItem>,
    val transactionsCursor: String?,
    val dailyUsage: CreditDailyUsageResponse?,
    val analytics: AnalyticsTokensResponse?,
    val dailyUsageRange: DailyUsageRange,
    val usageRankingTab: UsageRankingTab,
    val analyticsPeriod: CreditsPeriod,
    val isBalanceLoading: Boolean,
    val isLedgerLoading: Boolean,
    val isDailyUsageLoading: Boolean,
    val isAnalyticsLoading: Boolean,
    val isLoadingMoreTransactions: Boolean,
    val balanceError: String?,
    val transactionsError: String?,
    val dailyUsageError: String?,
    val analyticsError: String?,
    val eventSink: (CreditsEvents) -> Unit,
) {
    val hasMoreTransactions: Boolean = transactionsCursor != null
}

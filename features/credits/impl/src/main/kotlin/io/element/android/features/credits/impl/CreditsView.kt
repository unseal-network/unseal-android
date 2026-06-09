/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.credits.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.element.android.features.credits.api.CreditsEntryPoint
import io.element.android.features.credits.impl.model.CreditsPeriod
import io.element.android.features.credits.impl.model.DailyUsageRange
import io.element.android.features.credits.impl.model.UsageRankingTab
import io.element.android.features.credits.impl.model.formatMicrosDelta
import io.element.android.features.credits.impl.model.formatMicrosUsd
import io.element.android.features.credits.impl.model.prefixedDollar
import io.element.android.libraries.chatbot.api.model.analytics.AnalyticsAgentSummary
import io.element.android.libraries.chatbot.api.model.analytics.AnalyticsModelSummary
import io.element.android.libraries.chatbot.api.model.credits.CreditDailyBucket
import io.element.android.libraries.chatbot.api.model.credits.CreditLedgerItem

@Composable
fun CreditsView(
    state: CreditsState,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        state.eventSink(CreditsEvents.OnAppear)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Header(state)
        TopLevelTabs(state)
        state.error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        when (state.selectedTab) {
            CreditsEntryPoint.CreditsTab.Balance -> BalanceTab(state)
            CreditsEntryPoint.CreditsTab.DailyUsage -> DailyUsageTab(state)
            CreditsEntryPoint.CreditsTab.Usage -> UsageTab(state)
        }
    }
}

@Composable
private fun Header(state: CreditsState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = { state.eventSink(CreditsEvents.Dismiss) }) {
            Text("Done")
        }
        Text(
            text = "Credits & Billing",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun TopLevelTabs(state: CreditsState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = state.selectedTab == CreditsEntryPoint.CreditsTab.Balance,
            onClick = { state.eventSink(CreditsEvents.SelectTab(CreditsEntryPoint.CreditsTab.Balance)) },
            label = { Text("Balance") },
        )
        FilterChip(
            selected = state.selectedTab == CreditsEntryPoint.CreditsTab.DailyUsage,
            onClick = { state.eventSink(CreditsEvents.SelectTab(CreditsEntryPoint.CreditsTab.DailyUsage)) },
            label = { Text("Daily Usage") },
        )
        FilterChip(
            selected = state.selectedTab == CreditsEntryPoint.CreditsTab.Usage,
            onClick = { state.eventSink(CreditsEvents.SelectTab(CreditsEntryPoint.CreditsTab.Usage)) },
            label = { Text("Usage") },
        )
    }
}

@Composable
private fun BalanceTab(state: CreditsState) {
    Section(title = "Current balance") {
        if (state.isBalanceLoading) {
            CircularProgressIndicator()
        }
        Text(
            text = state.balance?.balanceUsd.orEmpty().prefixedDollar(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        state.balance?.userId?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(onClick = { state.eventSink(CreditsEvents.RequestTopUp) }) {
            Text("Recharge")
        }
    }

    Section(title = "Transactions") {
        if (state.isLedgerLoading) {
            CircularProgressIndicator()
        }
        if (!state.isLedgerLoading && state.transactions.isEmpty()) {
            Text(
                text = "No transactions yet",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        state.transactions.forEachIndexed { index, transaction ->
            TransactionRow(transaction)
            if (index < state.transactions.lastIndex) {
                HorizontalDivider()
            }
        }
        if (state.hasMoreTransactions) {
            OutlinedButton(
                onClick = { state.eventSink(CreditsEvents.LoadMoreTransactions) },
                enabled = !state.isLoadingMoreTransactions,
            ) {
                Text(if (state.isLoadingMoreTransactions) "Loading more" else "Load more")
            }
        }
    }
}

@Composable
private fun TransactionRow(transaction: CreditLedgerItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transaction.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            transaction.description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = formatMicrosDelta(transaction.deltaMicros),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun DailyUsageTab(state: CreditsState) {
    Section(title = "Daily Usage") {
        DailyUsageRangeSelector(state)
        if (state.isDailyUsageLoading) {
            CircularProgressIndicator()
        }
        val dailyUsage = state.dailyUsage
        if (!state.isDailyUsageLoading && dailyUsage == null) {
            Text(
                text = "No usage yet",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        dailyUsage?.daily.orEmpty().take(8).forEach { bucket ->
            DailyUsageRow(bucket)
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Total spent",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = formatMicrosUsd(dailyUsage?.totalUsageMicros.orEmpty()),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun DailyUsageRangeSelector(state: CreditsState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = state.dailyUsageRange == DailyUsageRange.SevenDays,
            onClick = { state.eventSink(CreditsEvents.SelectDailyUsageRange(DailyUsageRange.SevenDays)) },
            label = { Text("7 days") },
        )
        FilterChip(
            selected = state.dailyUsageRange == DailyUsageRange.ThirtyDays,
            onClick = { state.eventSink(CreditsEvents.SelectDailyUsageRange(DailyUsageRange.ThirtyDays)) },
            label = { Text("30 days") },
        )
    }
}

@Composable
private fun DailyUsageRow(bucket: CreditDailyBucket) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Day ${bucket.start}",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = formatMicrosUsd(bucket.usageMicros),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun UsageTab(state: CreditsState) {
    Section(title = "Usage") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RankingTabSelector(state)
            Spacer(modifier = Modifier.weight(1f))
            AnalyticsPeriodSelector(state)
        }
        if (state.isAnalyticsLoading) {
            CircularProgressIndicator()
        }
        val analytics = state.analytics
        if (!state.isAnalyticsLoading && analytics == null) {
            Text(
                text = "No usage yet",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        when (state.usageRankingTab) {
            UsageRankingTab.Agent -> analytics?.summaryByAgent.orEmpty().take(5).forEach { AgentRow(it) }
            UsageRankingTab.Model -> analytics?.summaryByModel.orEmpty().take(5).forEach { ModelRow(it) }
        }
    }
}

@Composable
private fun RankingTabSelector(state: CreditsState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = state.usageRankingTab == UsageRankingTab.Agent,
            onClick = { state.eventSink(CreditsEvents.SelectUsageRankingTab(UsageRankingTab.Agent)) },
            label = { Text("Agent") },
        )
        FilterChip(
            selected = state.usageRankingTab == UsageRankingTab.Model,
            onClick = { state.eventSink(CreditsEvents.SelectUsageRankingTab(UsageRankingTab.Model)) },
            label = { Text("Model") },
        )
    }
}

@Composable
private fun AnalyticsPeriodSelector(state: CreditsState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = state.analyticsPeriod == CreditsPeriod.SevenDays,
            onClick = { state.eventSink(CreditsEvents.SelectAnalyticsPeriod(CreditsPeriod.SevenDays)) },
            label = { Text("7 days") },
        )
        FilterChip(
            selected = state.analyticsPeriod == CreditsPeriod.ThirtyDays,
            onClick = { state.eventSink(CreditsEvents.SelectAnalyticsPeriod(CreditsPeriod.ThirtyDays)) },
            label = { Text("30 days") },
        )
        FilterChip(
            selected = state.analyticsPeriod == CreditsPeriod.All,
            onClick = { state.eventSink(CreditsEvents.SelectAnalyticsPeriod(CreditsPeriod.All)) },
            label = { Text("All") },
        )
    }
}

@Composable
private fun AgentRow(agent: AnalyticsAgentSummary) {
    RankingRow(
        title = agent.displayName ?: agent.agentId,
        subtitle = "${agent.inputTokens} in / ${agent.outputTokens} out / ${agent.callCount} calls",
        percent = agent.pct,
    )
}

@Composable
private fun ModelRow(model: AnalyticsModelSummary) {
    RankingRow(
        title = model.model,
        subtitle = "${model.inputTokens} in / ${model.outputTokens} out",
        percent = model.pct,
    )
}

@Composable
private fun RankingRow(
    title: String,
    subtitle: String,
    percent: Double,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = String.format("%.1f%%", percent),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun Section(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        content()
    }
}

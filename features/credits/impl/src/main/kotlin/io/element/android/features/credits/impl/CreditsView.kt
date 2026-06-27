/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package io.element.android.features.credits.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.credits.api.CreditsEntryPoint
import io.element.android.features.credits.impl.model.CreditsPeriod
import io.element.android.features.credits.impl.model.DailyUsageRange
import kotlinx.collections.immutable.toImmutableList
import io.element.android.features.credits.impl.model.UsageRankingTab
import io.element.android.features.credits.impl.model.formatMicrosDelta
import io.element.android.features.credits.impl.model.formatMicrosUsd
import io.element.android.features.credits.impl.model.isLowBalance
import io.element.android.features.credits.impl.model.prefixedDollar
import io.element.android.libraries.chatbot.api.model.analytics.AnalyticsAgentSummary
import io.element.android.libraries.chatbot.api.model.analytics.AnalyticsModelSummary
import io.element.android.libraries.chatbot.api.model.analytics.AnalyticsTokensResponse
import io.element.android.libraries.chatbot.api.model.credits.CreditBalance
import io.element.android.libraries.chatbot.api.model.credits.CreditDailyBucket
import io.element.android.libraries.chatbot.api.model.credits.CreditDailyUsageResponse
import io.element.android.libraries.chatbot.api.model.credits.CreditLedgerItem
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.SegmentedButton
import io.element.android.libraries.ui.strings.CommonStrings

private val RankingDotColors = listOf(
    Color(0xFF2E7D32),
    Color(0xFF1565C0),
    Color(0xFF6A1B9A),
    Color(0xFFEF6C00),
    Color(0xFFAD1457),
)

@Composable
fun CreditsView(
    state: CreditsState,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        state.eventSink(CreditsEvents.OnAppear)
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.credits_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { state.eventSink(CreditsEvents.Dismiss) }) {
                        Icon(CompoundIcons.ChevronLeft(), contentDescription = stringResource(CommonStrings.action_done))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TopLevelTabs(state)
            when (state.selectedTab) {
                CreditsEntryPoint.CreditsTab.Balance -> BalanceCard(state)
                CreditsEntryPoint.CreditsTab.DailyUsage -> DailyUsageCard(state)
                CreditsEntryPoint.CreditsTab.Usage -> UsageRankingCard(state)
            }
            if (state.selectedTab == CreditsEntryPoint.CreditsTab.Balance) {
                TransactionsCard(state)
            }
        }
    }
}

@Composable
private fun TopLevelTabs(state: CreditsState) {
    val tabs = listOf(
        CreditsEntryPoint.CreditsTab.Balance to stringResource(R.string.credits_tab_balance),
        CreditsEntryPoint.CreditsTab.DailyUsage to stringResource(R.string.credits_tab_daily_usage),
        CreditsEntryPoint.CreditsTab.Usage to stringResource(R.string.credits_tab_usage),
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        tabs.forEachIndexed { index, (tab, label) ->
            SegmentedButton(
                index = index,
                count = tabs.size,
                selected = state.selectedTab == tab,
                onClick = { state.eventSink(CreditsEvents.SelectTab(tab)) },
                text = label,
            )
        }
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun SectionError(
    title: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = CompoundIcons.Error(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onRetry) {
            Text(stringResource(CommonStrings.action_retry))
        }
    }
}

// MARK: - Balance

@Composable
private fun BalanceCard(state: CreditsState) {
    Card {
        Text(
            text = stringResource(R.string.credits_available_balance),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!state.isBalanceLoading && state.balance == null && state.balanceError != null) {
            SectionError(
                title = stringResource(R.string.credits_error_balance_unavailable),
                onRetry = { state.eventSink(CreditsEvents.SelectTab(CreditsEntryPoint.CreditsTab.Balance)) },
            )
            return@Card
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "USD",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 6.dp),
            )
            if (state.isBalanceLoading) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
            } else {
                val low = isLowBalance(state.balance?.balanceMicros.orEmpty())
                Text(
                    text = state.balance?.balanceUsd.orEmpty().prefixedDollar(),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.weight(1f))
            BalanceSparkline(
                values = state.dailyUsage?.daily.orEmpty().takeLast(14).map { microsToUsd(it.usageMicros) },
                modifier = Modifier
                    .size(width = 100.dp, height = 40.dp),
            )
        }
        state.balance?.userId?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { state.eventSink(CreditsEvents.RequestTopUp) },
        ) {
            Icon(CompoundIcons.Plus(), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.credits_top_up))
            Spacer(Modifier.size(8.dp))
            Icon(CompoundIcons.PopOut(), contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun TransactionsCard(state: CreditsState) {
    Card {
        Text(
            text = stringResource(R.string.credits_recent_transactions),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        when {
            state.isLedgerLoading -> CircularProgressIndicator(modifier = Modifier.size(28.dp))
            state.transactionsError != null && state.transactions.isEmpty() -> SectionError(
                title = stringResource(R.string.credits_error_transactions_unavailable),
                onRetry = { state.eventSink(CreditsEvents.SelectTab(CreditsEntryPoint.CreditsTab.Balance)) },
            )
            state.transactions.isEmpty() -> Text(
                text = stringResource(R.string.credits_no_transactions),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            else -> state.transactions.forEachIndexed { index, transaction ->
                TransactionRow(transaction)
                if (index < state.transactions.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(start = 44.dp))
                }
            }
        }
        if (state.hasMoreTransactions) {
            TextButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { state.eventSink(CreditsEvents.LoadMoreTransactions) },
                enabled = !state.isLoadingMoreTransactions,
            ) {
                Text(if (state.isLoadingMoreTransactions) stringResource(R.string.credits_loading) else stringResource(R.string.credits_load_more))
                if (!state.isLoadingMoreTransactions) {
                    Spacer(Modifier.size(6.dp))
                    Icon(CompoundIcons.ChevronDown(), contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun TransactionRow(transaction: CreditLedgerItem) {
    val isCredit = (transaction.deltaMicros.toLongOrNull() ?: 0L) >= 0L
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TransactionIcon(transaction.source)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transaction.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatLedgerTimestamp(transaction.ts),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = formatMicrosDelta(transaction.deltaMicros),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (isCredit) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun TransactionIcon(source: String) {
    // Mirrors iOS: topup=card/blue, grant=gift/purple, usage=cpu/gray.
    val (icon, color) = when (source) {
        "topup" -> CompoundIcons.Download() to Color(0xFF1565C0)
        "grant" -> CompoundIcons.Favourite() to Color(0xFF6A1B9A)
        else -> CompoundIcons.Computer() to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp),
        )
    }
}

// MARK: - Daily Usage

@Composable
private fun DailyUsageCard(state: CreditsState) {
    Card {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.credits_tab_daily_usage),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            DailyUsageRangeSelector(state)
        }
        val dailyUsage = state.dailyUsage
        val buckets = dailyUsage?.daily.orEmpty()
        when {
            state.isDailyUsageLoading -> Box(
                modifier = Modifier.fillMaxWidth().height(180.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(modifier = Modifier.size(28.dp)) }
            state.dailyUsageError != null && dailyUsage == null -> Box(
                modifier = Modifier.fillMaxWidth().height(180.dp),
                contentAlignment = Alignment.Center,
            ) {
                SectionError(
                    title = stringResource(R.string.credits_error_daily_usage_unavailable),
                    onRetry = { state.eventSink(CreditsEvents.SelectTab(CreditsEntryPoint.CreditsTab.DailyUsage)) },
                )
            }
            buckets.isEmpty() -> Box(
                modifier = Modifier.fillMaxWidth().height(180.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.credits_no_data),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            else -> DailyUsageBarChart(
                buckets = buckets.map { it.start to microsToUsd(it.usageMicros) },
                sevenDayRange = state.dailyUsageRange == DailyUsageRange.SevenDays,
                modifier = Modifier.fillMaxWidth().height(180.dp),
            )
        }
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.credits_total_spend),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatMicrosUsd(dailyUsage?.totalUsageMicros.orEmpty()),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun DailyUsageRangeSelector(state: CreditsState) {
    CreditPillPicker(
        options = listOf(
            DailyUsageRange.SevenDays to stringResource(R.string.credits_seven_days),
            DailyUsageRange.ThirtyDays to stringResource(R.string.credits_thirty_days),
        ),
        selected = state.dailyUsageRange,
        onSelect = { state.eventSink(CreditsEvents.SelectDailyUsageRange(it)) },
        compact = true,
    )
}

// MARK: - Usage Ranking

@Composable
private fun UsageRankingCard(state: CreditsState) {
    Card {
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
            CircularProgressIndicator(modifier = Modifier.size(28.dp))
        }
        val analytics = state.analytics
        if (!state.isAnalyticsLoading && analytics == null && state.analyticsError != null) {
            SectionError(
                title = stringResource(R.string.credits_error_usage_unavailable),
                onRetry = { state.eventSink(CreditsEvents.SelectTab(CreditsEntryPoint.CreditsTab.Usage)) },
            )
            return@Card
        }
        if (!state.isAnalyticsLoading && analytics == null) {
            Text(
                text = stringResource(R.string.credits_no_data),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (analytics != null && !state.isAnalyticsLoading) {
            TokenMiniBarChart(
                values = analytics.daily.map { day -> day.models.sumOf { (it.inputTokens + it.outputTokens).toDouble() } },
                modifier = Modifier.fillMaxWidth().height(60.dp),
            )
        }
        when (state.usageRankingTab) {
            UsageRankingTab.Agent -> {
                val items = analytics?.summaryByAgent.orEmpty().take(5)
                items.forEachIndexed { index, agent ->
                    AgentRow(agent, RankingDotColors[index % RankingDotColors.size])
                    if (index < items.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(start = 22.dp))
                    }
                }
            }
            UsageRankingTab.Model -> {
                val items = analytics?.summaryByModel.orEmpty().take(5)
                items.forEachIndexed { index, model ->
                    ModelRow(model, RankingDotColors[index % RankingDotColors.size])
                    if (index < items.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(start = 22.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RankingTabSelector(state: CreditsState) {
    // iOS keeps "Agent"/"Model" untranslated in zh-Hans (credits_ranking_tab_agent/model).
    CreditPillPicker(
        options = listOf(
            UsageRankingTab.Agent to stringResource(R.string.credits_ranking_tab_agent),
            UsageRankingTab.Model to stringResource(R.string.credits_ranking_tab_model),
        ),
        selected = state.usageRankingTab,
        onSelect = { state.eventSink(CreditsEvents.SelectUsageRankingTab(it)) },
    )
}

@Composable
private fun AnalyticsPeriodSelector(state: CreditsState) {
    CreditPillPicker(
        options = listOf(
            CreditsPeriod.SevenDays to stringResource(R.string.credits_seven_days),
            CreditsPeriod.ThirtyDays to stringResource(R.string.credits_thirty_days),
            CreditsPeriod.All to stringResource(R.string.credits_all),
        ),
        selected = state.analyticsPeriod,
        onSelect = { state.eventSink(CreditsEvents.SelectAnalyticsPeriod(it)) },
        compact = true,
    )
}

@Composable
private fun AgentRow(agent: AnalyticsAgentSummary, dotColor: Color) {
    RankingRow(
        dotColor = dotColor,
        title = agent.displayName ?: agent.agentId,
        subtitle = stringResource(R.string.credits_agent_usage_subtitle, agent.inputTokens, agent.outputTokens, agent.callCount),
        percent = agent.pct,
    )
}

@Composable
private fun ModelRow(model: AnalyticsModelSummary, dotColor: Color) {
    RankingRow(
        dotColor = dotColor,
        title = model.model,
        subtitle = stringResource(R.string.credits_model_usage_subtitle, model.inputTokens, model.outputTokens),
        percent = model.pct,
    )
}

@Composable
private fun RankingRow(
    dotColor: Color,
    title: String,
    subtitle: String,
    percent: Double,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(dotColor, CircleShape),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
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
            text = formatPercent(percent),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun formatPercent(percent: Double): String {
    val scaled = kotlin.math.round(percent * 10).toLong()
    return "${scaled / 10}.${scaled % 10}%"
}

// MARK: - Previews

internal class CreditsStateProvider : PreviewParameterProvider<CreditsState> {
    override val values: Sequence<CreditsState>
        get() = sequenceOf(
            aCreditsState(),
            aCreditsState(selectedTab = CreditsEntryPoint.CreditsTab.DailyUsage),
            aCreditsState(selectedTab = CreditsEntryPoint.CreditsTab.Usage),
            aCreditsState(transactions = emptyList(), transactionsCursor = null),
        )
}

private fun aCreditsState(
    selectedTab: CreditsEntryPoint.CreditsTab = CreditsEntryPoint.CreditsTab.Balance,
    transactions: List<CreditLedgerItem> = sampleTransactions(),
    transactionsCursor: String? = "cursor-2",
) = CreditsState(
    selectedTab = selectedTab,
    balance = CreditBalance(
        userId = "@alice:unseal.network",
        balanceMicros = "12500000",
        balanceUsd = "12.50",
    ),
    transactions = transactions.toImmutableList(),
    transactionsCursor = transactionsCursor,
    dailyUsage = CreditDailyUsageResponse(
        start = 1,
        end = 8,
        daily = listOf(
            CreditDailyBucket(start = 1, usageMicros = "250000"),
            CreditDailyBucket(start = 2, usageMicros = "1200000"),
            CreditDailyBucket(start = 3, usageMicros = "800000"),
        ),
        totalUsageMicros = "2250000",
    ),
    analytics = AnalyticsTokensResponse(
        period = "thirtydays",
        summaryByAgent = listOf(
            AnalyticsAgentSummary(agentId = "alice-bot", displayName = "Alice Bot", inputTokens = 1200, outputTokens = 3400, callCount = 18, pct = 62.5),
            AnalyticsAgentSummary(agentId = "ops-bot", displayName = "Ops Bot", inputTokens = 800, outputTokens = 1100, callCount = 7, pct = 37.5),
        ),
        summaryByModel = listOf(
            AnalyticsModelSummary(model = "gpt-4o", inputTokens = 1500, outputTokens = 3000, pct = 70.0),
            AnalyticsModelSummary(model = "claude-3", inputTokens = 500, outputTokens = 1500, pct = 30.0),
        ),
    ),
    dailyUsageRange = DailyUsageRange.SevenDays,
    usageRankingTab = UsageRankingTab.Agent,
    analyticsPeriod = CreditsPeriod.ThirtyDays,
    isBalanceLoading = false,
    isLedgerLoading = false,
    isDailyUsageLoading = false,
    isAnalyticsLoading = false,
    isLoadingMoreTransactions = false,
    balanceError = null,
    transactionsError = null,
    dailyUsageError = null,
    analyticsError = null,
    eventSink = {},
)

private fun sampleTransactions() = listOf(
    CreditLedgerItem(
        id = "txn-1",
        deltaMicros = "5000000",
        balanceAfterMicros = "12500000",
        source = "topup",
        category = "credits",
        title = "Top up",
        description = "Card payment",
        ts = "2026-06-09T10:00:00Z",
    ),
    CreditLedgerItem(
        id = "txn-2",
        deltaMicros = "-250000",
        balanceAfterMicros = "7500000",
        source = "usage",
        category = "credits",
        title = "Assistant usage",
        description = "gpt-4o",
        ts = "2026-06-08T18:30:00Z",
    ),
)

@PreviewsDayNight
@Composable
internal fun CreditsViewPreview(@PreviewParameter(CreditsStateProvider::class) state: CreditsState) = ElementPreview {
    CreditsView(state = state)
}

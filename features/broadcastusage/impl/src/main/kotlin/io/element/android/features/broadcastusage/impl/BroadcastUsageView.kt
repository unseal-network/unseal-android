/*
 * Copyright (c) 2026 Unseal
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.element.android.features.broadcastusage.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
internal fun BroadcastUsageView(state: BroadcastUsageState, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val detail = state.selectedHistory
    val detailId = state.selectedBroadcastId
    val initialError = if (state.loading) {
        null
    } else {
        when {
            detailId != null -> state.historyError
            detailId == null && state.dashboard == null && state.history.isEmpty() -> state.dashboardError ?: state.historyError
            else -> null
        }
    }
    val showInitialLoading = initialError == null &&
        ((detailId == null && state.dashboard == null && state.history.isEmpty()) || (detailId != null && detail == null))
    val scrollState = rememberScrollState()
    LaunchedEffect(state.tab, detailId, scrollState.value, scrollState.maxValue, state.historyNextCursor, state.loadingMore) {
        if (
            state.tab == BroadcastUsageTab.Overview && detailId == null &&
            scrollState.value >= scrollState.maxValue - 240 && state.historyNextCursor != null && !state.loadingMore
        ) {
            state.eventSink(BroadcastUsageEvent.LoadMoreHistory)
        }
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(when {
                            detailId != null -> R.string.screen_broadcast_usage_session_details_title
                            state.tab == BroadcastUsageTab.Activity -> R.string.screen_broadcast_usage_activity_title
                            state.tab == BroadcastUsageTab.Grants -> R.string.screen_broadcast_usage_grants_title
                            else -> R.string.screen_broadcast_usage_title
                        }),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = when {
                        detailId != null -> ({ state.eventSink(BroadcastUsageEvent.CloseSession) })
                        state.tab != BroadcastUsageTab.Overview -> ({ state.eventSink(BroadcastUsageEvent.SelectTab(BroadcastUsageTab.Overview)) })
                        else -> onDone
                    }) {
                        Icon(CompoundIcons.ChevronLeft(), contentDescription = stringResource(CommonStrings.action_done))
                    }
                },
                actions = {
                    IconButton(onClick = { state.eventSink(BroadcastUsageEvent.Refresh) }) {
                        Icon(CompoundIcons.Restart(), contentDescription = stringResource(CommonStrings.action_retry))
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                showInitialLoading -> FullScreenLoading()
                initialError != null -> FullScreenError(initialError) { state.eventSink(BroadcastUsageEvent.Refresh) }
                else -> Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (detailId != null) {
                        if (detail != null) HistoryDetailContent(detail)
                    } else {
                        when (state.tab) {
                            BroadcastUsageTab.Overview -> state.dashboardError ?: state.historyError
                            BroadcastUsageTab.Activity -> state.activityError
                            BroadcastUsageTab.Grants -> state.grantsError
                        }?.let { ErrorCard(it) { state.eventSink(BroadcastUsageEvent.Refresh) } }
                        when (state.tab) {
                            BroadcastUsageTab.Overview -> OverviewContent(state)
                            BroadcastUsageTab.Activity -> ActivityContent(state)
                            BroadcastUsageTab.Grants -> GrantsContent(state)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FullScreenLoading() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.screen_broadcast_usage_loading),
            modifier = Modifier.padding(top = 16.dp),
            style = ElementTheme.typography.fontBodyLgRegular,
            color = ElementTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FullScreenError(message: String, retry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = CompoundIcons.Offline(),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = ElementTheme.colors.textSecondary,
        )
        Text(
            text = stringResource(R.string.screen_broadcast_usage_load_failed),
            modifier = Modifier.padding(top = 20.dp),
            style = ElementTheme.typography.fontHeadingMdBold,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = message,
            modifier = Modifier.padding(top = 8.dp),
            style = ElementTheme.typography.fontBodyLgRegular,
            color = ElementTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Button(onClick = retry, modifier = Modifier.padding(top = 24.dp)) {
            Text(stringResource(CommonStrings.action_retry))
        }
    }
}

@Composable
private fun ColumnScope.OverviewContent(state: BroadcastUsageState) {
    val dashboard = state.dashboard
    if (dashboard != null) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard(stringResource(R.string.screen_broadcast_usage_grant_traffic), formatTrafficCompact(dashboard.funding.grantBytes), Modifier.weight(1f))
        MetricCard(stringResource(R.string.screen_broadcast_usage_balance), formatUsdMicros(dashboard.funding.balanceMicros), Modifier.weight(1f))
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { state.eventSink(BroadcastUsageEvent.SelectTab(BroadcastUsageTab.Activity)) }, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.screen_broadcast_usage_activity_title))
        }
        TextButton(onClick = { state.eventSink(BroadcastUsageEvent.SelectTab(BroadcastUsageTab.Grants)) }, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.screen_broadcast_usage_grants_title))
        }
    }
    val activeSessions = dashboard?.sessions?.filter { it.showsRuntime }.orEmpty()
    if (activeSessions.isNotEmpty()) Card {
        Text(stringResource(R.string.screen_broadcast_usage_active_sessions), style = ElementTheme.typography.fontBodyLgMedium, fontWeight = FontWeight.SemiBold)
        activeSessions.forEach { session ->
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                val startTime = session.startedAt ?: session.openedAt
                Text(session.displayName ?: startTime.displayTime(), fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(startTime.displayTime(), style = ElementTheme.typography.fontBodySmRegular, color = ElementTheme.colors.textSecondary)
            }
        }
    }
    Card {
        Text(stringResource(R.string.screen_broadcast_usage_previous_sessions), style = ElementTheme.typography.fontBodyLgMedium, fontWeight = FontWeight.SemiBold)
        if (state.history.isEmpty() && !state.loading) Text(stringResource(R.string.screen_broadcast_usage_empty_sessions), color = ElementTheme.colors.textSecondary)
        state.history.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider()
            HistoryRow(item) { state.eventSink(BroadcastUsageEvent.OpenHistory(item.broadcastId)) }
        }
        if (state.loadingMore) CircularProgressIndicator(modifier = Modifier.size(24.dp).align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = ElementTheme.colors.bgSubtleSecondary, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = ElementTheme.typography.fontBodySmRegular, color = ElementTheme.colors.textSecondary)
            Text(value, style = ElementTheme.typography.fontHeadingMdBold, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun HistoryRow(item: BroadcastHistoryItem, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.displayName ?: item.openedAt.displayTime(), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    Text("${item.openedAt.displayTime()} – ${item.closedAt.displayTime()}", style = ElementTheme.typography.fontBodySmRegular, color = ElementTheme.colors.textSecondary)
                }
                Icon(CompoundIcons.ChevronRight(), contentDescription = null)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                HistoryMetric(stringResource(R.string.screen_broadcast_usage_traffic), formatTrafficCompact(item.traffic.confirmedBytes))
                item.audience.viewerSessionCount?.let { HistoryMetric(stringResource(R.string.screen_broadcast_usage_viewer_sessions), it.toString()) }
                item.billing.costMicros?.let { HistoryMetric(stringResource(R.string.screen_broadcast_usage_cost), formatUsdMicros(it)) }
            }
        }
    }
}

@Composable
private fun HistoryMetric(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = ElementTheme.typography.fontBodyXsRegular, color = ElementTheme.colors.textSecondary)
        Text(value, style = ElementTheme.typography.fontBodyMdRegular, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun HistoryDetailContent(item: BroadcastHistoryItem) {
    Card {
        Text(item.displayName ?: item.openedAt.displayTime(), style = ElementTheme.typography.fontBodyLgMedium, fontWeight = FontWeight.SemiBold)
        if (item.displayName != null) Text(item.roomId, style = ElementTheme.typography.fontBodySmRegular, color = ElementTheme.colors.textSecondary)
        KeyValue(stringResource(R.string.screen_broadcast_usage_start_time), item.openedAt.displayTime())
        KeyValue(stringResource(R.string.screen_broadcast_usage_end_time), item.closedAt.displayTime())
    }
    Card {
        Text(stringResource(R.string.screen_broadcast_usage_traffic), style = ElementTheme.typography.fontBodyLgMedium, fontWeight = FontWeight.SemiBold)
        KeyValue(stringResource(R.string.screen_broadcast_usage_confirmed_traffic), formatTraffic(item.traffic.confirmedBytes))
        KeyValue(stringResource(R.string.screen_broadcast_usage_grant_covered_traffic), formatTraffic(item.traffic.grantCoveredBytes))
        KeyValue(stringResource(R.string.screen_broadcast_usage_balance_covered_traffic), formatTraffic(item.traffic.balanceCoveredBytes))
    }
    if (item.audience.viewerSessionCount != null || item.audience.uniqueViewerCount != null || item.audience.peakConcurrentViewers != null) Card {
        Text(stringResource(R.string.screen_broadcast_usage_audience), style = ElementTheme.typography.fontBodyLgMedium, fontWeight = FontWeight.SemiBold)
        item.audience.viewerSessionCount?.let { KeyValue(stringResource(R.string.screen_broadcast_usage_viewer_sessions), it.toString()) }
        item.audience.uniqueViewerCount?.let { KeyValue(stringResource(R.string.screen_broadcast_usage_unique_viewers), it.toString()) }
        item.audience.peakConcurrentViewers?.let { KeyValue(stringResource(R.string.screen_broadcast_usage_peak_concurrent_viewers), it.toString()) }
    }
    item.billing.costMicros?.let { cost ->
        Card {
            Text(stringResource(R.string.screen_broadcast_usage_billing), style = ElementTheme.typography.fontBodyLgMedium, fontWeight = FontWeight.SemiBold)
            KeyValue(stringResource(R.string.screen_broadcast_usage_cost), formatUsdMicros(cost))
            item.billing.chargedAt?.let { KeyValue(stringResource(R.string.screen_broadcast_usage_charged_at), it.displayTime()) }
        }
    }
}

@Composable
private fun ColumnScope.ActivityContent(state: BroadcastUsageState) {
    Card {
        Text(stringResource(R.string.screen_broadcast_usage_activity_title), style = ElementTheme.typography.fontBodyLgMedium, fontWeight = FontWeight.SemiBold)
        if (state.activity.isEmpty() && !state.loading) Text(stringResource(R.string.screen_broadcast_usage_empty_activity), color = ElementTheme.colors.textSecondary)
        state.activity.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(item.type.localizedReason(), fontWeight = FontWeight.Medium)
                    Text(item.note ?: item.occurredAt.displayTime(), style = ElementTheme.typography.fontBodySmRegular, color = ElementTheme.colors.textSecondary)
                    Text(item.sourceReference, style = ElementTheme.typography.fontBodySmRegular, color = ElementTheme.colors.textSecondary)
                    item.broadcastId?.let { Text(stringResource(R.string.screen_broadcast_usage_broadcast_reference, it), style = ElementTheme.typography.fontBodySmRegular, color = ElementTheme.colors.textSecondary) }
                }
                Text(
                    text = formatTraffic(item.amountBytes, signed = true),
                    color = if (item.amountBytes.signum() >= 0) ElementTheme.colors.textActionPrimary else ElementTheme.colors.textCriticalPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (state.activityNextCursor != null) TextButton(onClick = { state.eventSink(BroadcastUsageEvent.LoadMoreActivity) }) {
            Text(stringResource(R.string.screen_broadcast_usage_load_more))
        }
    }
}

@Composable
private fun ColumnScope.GrantsContent(state: BroadcastUsageState) {
    val grants = state.grants
    if (grants != null) MetricCard(stringResource(R.string.screen_broadcast_usage_current_available_total), formatTraffic(grants.availableTrafficBytes), Modifier.fillMaxWidth())
    Card {
        Text(stringResource(R.string.screen_broadcast_usage_grants_title), style = ElementTheme.typography.fontBodyLgMedium, fontWeight = FontWeight.SemiBold)
        if (grants?.items.isNullOrEmpty() && !state.loading) Text(stringResource(R.string.screen_broadcast_usage_empty_grants), color = ElementTheme.colors.textSecondary)
        grants?.items?.forEachIndexed { index, grant ->
            if (index > 0) HorizontalDivider()
            Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(grant.reasonCode.localizedReason(), fontWeight = FontWeight.Medium)
                    Text(formatTraffic(grant.remainingBytes), fontWeight = FontWeight.SemiBold)
                }
                Text(stringResource(R.string.screen_broadcast_usage_grant_period, grant.status.localizedLabel(), grant.validFrom.displayTime(), grant.expiresAt.displayTime()), style = ElementTheme.typography.fontBodySmRegular, color = ElementTheme.colors.textSecondary)
                Text(stringResource(R.string.screen_broadcast_usage_grant_usage, formatTraffic(grant.originalBytes), formatTraffic(grant.consumedBytes)), style = ElementTheme.typography.fontBodySmRegular)
            }
        }
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), color = ElementTheme.colors.bgSubtleSecondary, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun KeyValue(key: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key, color = ElementTheme.colors.textSecondary)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ErrorCard(message: String, retry: () -> Unit) {
    Surface(color = ElementTheme.colors.bgCriticalSubtle, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, Modifier.weight(1f), color = ElementTheme.colors.textCriticalPrimary)
            Button(onClick = retry) { Text(stringResource(CommonStrings.action_retry)) }
        }
    }
}

private fun String.displayTime(): String = runCatching {
    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        .withZone(java.time.ZoneId.systemDefault())
        .format(java.time.Instant.parse(this))
}.getOrElse { this }

private fun BroadcastUsageSession.durationLabel(): String? {
    val start = startedAt ?: openedAt
    val end = closedAt ?: finalizedAt ?: return null
    return runCatching {
        val seconds = java.time.Duration.between(java.time.Instant.parse(start), java.time.Instant.parse(end)).seconds.coerceAtLeast(0)
        "%d:%02d:%02d".format(seconds / 3_600, (seconds % 3_600) / 60, seconds % 60)
    }.getOrNull()
}

@Composable
private fun BroadcastUsageSessionState.localizedLabel(): String = stringResource(
    when (this) {
        BroadcastUsageSessionState.Open -> R.string.screen_broadcast_usage_session_state_open
        BroadcastUsageSessionState.Live -> R.string.screen_broadcast_usage_session_state_live
        BroadcastUsageSessionState.ClosedSyncing -> R.string.screen_broadcast_usage_session_state_closed_syncing
        BroadcastUsageSessionState.Finalized -> R.string.screen_broadcast_usage_session_state_finalized
        BroadcastUsageSessionState.Failed -> R.string.screen_broadcast_usage_session_state_failed
    },
)

@Composable
private fun String.localizedLabel(): String = when (this) {
    "scheduled" -> stringResource(R.string.screen_broadcast_usage_grant_state_scheduled)
    "available" -> stringResource(R.string.screen_broadcast_usage_grant_state_available)
    "exhausted" -> stringResource(R.string.screen_broadcast_usage_grant_state_exhausted)
    "expired" -> stringResource(R.string.screen_broadcast_usage_grant_state_expired)
    "revoked" -> stringResource(R.string.screen_broadcast_usage_grant_state_revoked)
    else -> this
}

@Composable
private fun String.localizedReason(): String = when (this) {
    "grant_issued" -> stringResource(R.string.screen_broadcast_usage_reason_grant_issued)
    "grant_adjusted" -> stringResource(R.string.screen_broadcast_usage_reason_grant_adjusted)
    "grant_expired" -> stringResource(R.string.screen_broadcast_usage_reason_grant_expired)
    "broadcast_usage" -> stringResource(R.string.screen_broadcast_usage_reason_broadcast_usage)
    else -> this
}

@PreviewsDayNight
@Composable
internal fun BroadcastUsageViewPreview(@PreviewParameter(BroadcastUsageStateProvider::class) state: BroadcastUsageState) = ElementPreview {
    BroadcastUsageView(state, {})
}

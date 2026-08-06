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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
internal fun BroadcastUsageView(state: BroadcastUsageState, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val detail = state.selectedSession
    val detailId = state.selectedBroadcastId
    val initialError = when {
        detailId != null && detail == null -> state.sessionError
        detailId == null && state.dashboard == null -> state.dashboardError
        else -> null
    }
    val showInitialLoading = initialError == null &&
        ((detailId == null && state.dashboard == null) || (detailId != null && detail == null))
    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (detailId == null) R.string.screen_broadcast_usage_title else R.string.screen_broadcast_usage_session_details_title,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = if (detailId == null) onDone else ({ state.eventSink(BroadcastUsageEvent.CloseSession) })) {
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
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (detailId != null) {
                        if (detail != null) DetailContent(detail, state.runtime, state.runtimeError)
                    } else {
                        when (state.tab) {
                            BroadcastUsageTab.Overview -> state.dashboardError
                            BroadcastUsageTab.Activity -> state.activityError
                            BroadcastUsageTab.Grants -> state.grantsError
                        }?.let { ErrorCard(it) { state.eventSink(BroadcastUsageEvent.Refresh) } }
                        Tabs(state.tab) { state.eventSink(BroadcastUsageEvent.SelectTab(it)) }
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
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.screen_broadcast_usage_load_failed),
            modifier = Modifier.padding(top = 20.dp),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = message,
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = retry, modifier = Modifier.padding(top = 24.dp)) {
            Text(stringResource(CommonStrings.action_retry))
        }
    }
}

@Composable
private fun Tabs(selected: BroadcastUsageTab, select: (BroadcastUsageTab) -> Unit) {
    val tabs = listOf(
        BroadcastUsageTab.Overview to stringResource(R.string.screen_broadcast_usage_sessions_tab),
        BroadcastUsageTab.Activity to stringResource(R.string.screen_broadcast_usage_activity_tab),
        BroadcastUsageTab.Grants to stringResource(R.string.screen_broadcast_usage_grants_tab),
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        tabs.forEach { (tab, label) ->
            TextButton(onClick = { select(tab) }) {
                Text(label, fontWeight = if (tab == selected) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun ColumnScope.OverviewContent(state: BroadcastUsageState) {
    val dashboard = state.dashboard ?: return
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard(stringResource(R.string.screen_broadcast_usage_available_traffic), formatTrafficCompact(dashboard.availableTrafficBytes), Modifier.weight(1f))
        MetricCard(stringResource(R.string.screen_broadcast_usage_unallocated_traffic), formatTrafficCompact(dashboard.unallocatedTrafficBytes), Modifier.weight(1f))
    }
    Card {
        Text(stringResource(R.string.screen_broadcast_usage_sessions_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        val active = dashboard.sessions.count { it.showsRuntime }
        val syncing = dashboard.sessions.count { it.state == BroadcastUsageSessionState.ClosedSyncing }
        Text(
            stringResource(R.string.screen_broadcast_usage_sessions_summary, active, syncing, dashboard.sessionCount - active - syncing),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            stringResource(R.string.screen_broadcast_usage_sync_delay),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (dashboard.sessions.isEmpty()) Text(stringResource(R.string.screen_broadcast_usage_empty_sessions), color = MaterialTheme.colorScheme.onSurfaceVariant)
        val activeSessions = dashboard.sessions.filter { it.showsRuntime }
        val syncingSessions = dashboard.sessions.filter { it.state == BroadcastUsageSessionState.ClosedSyncing }
        val history = dashboard.sessions.filter { it.isTerminal }
        if (activeSessions.isNotEmpty()) Text(stringResource(R.string.screen_broadcast_usage_active_sessions), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        activeSessions.forEach { session -> SessionRow(session) { state.eventSink(BroadcastUsageEvent.OpenSession(session.broadcastId)) } }
        if (syncingSessions.isNotEmpty()) Text(stringResource(R.string.screen_broadcast_usage_syncing_sessions), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        syncingSessions.forEach { session -> SessionRow(session) { state.eventSink(BroadcastUsageEvent.OpenSession(session.broadcastId)) } }
        if (history.isNotEmpty()) Text(stringResource(R.string.screen_broadcast_usage_previous_sessions), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        history.forEach { session -> SessionRow(session) { state.eventSink(BroadcastUsageEvent.OpenSession(session.broadcastId)) } }
        if (dashboard.nextCursor != null) TextButton(onClick = { state.eventSink(BroadcastUsageEvent.LoadMoreSessions) }) {
            Text(stringResource(R.string.screen_broadcast_usage_load_more))
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SessionRow(session: BroadcastUsageSession, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(session.displayName ?: session.roomId, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                Text("${session.state.localizedLabel()} · ${session.openedAt.displayTime()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(formatTrafficCompact(session.confirmedBytes), fontWeight = FontWeight.SemiBold, maxLines = 1)
            Icon(CompoundIcons.ChevronRight(), contentDescription = null)
        }
    }
}

@Composable
private fun DetailContent(session: BroadcastUsageSession, runtime: BroadcastRuntimeStatus?, runtimeError: String?) {
    Card {
        Text(session.displayName ?: session.roomId, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (session.displayName != null) Text(session.roomId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        KeyValue(stringResource(R.string.screen_broadcast_usage_status), session.state.localizedLabel())
        KeyValue(stringResource(R.string.screen_broadcast_usage_confirmed_traffic), formatTraffic(session.confirmedBytes))
        KeyValue(stringResource(R.string.screen_broadcast_usage_start_time), (session.startedAt ?: session.openedAt).displayTime())
        session.closedAt?.let { KeyValue(stringResource(R.string.screen_broadcast_usage_end_time), it.displayTime()) }
        session.durationLabel()?.let { KeyValue(stringResource(R.string.screen_broadcast_usage_duration), it) }
        session.syncedThrough?.let { KeyValue(stringResource(R.string.screen_broadcast_usage_synced_through), it.displayTime()) }
        session.stopReason?.let { KeyValue(stringResource(R.string.screen_broadcast_usage_end_reason), it) }
        if (!session.isTerminal) Text(stringResource(R.string.screen_broadcast_usage_cloudflare_sync_delay), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (session.showsRuntime) Card {
        Text(stringResource(R.string.screen_broadcast_usage_live_status), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (runtime != null) {
            KeyValue(stringResource(R.string.screen_broadcast_usage_phase), runtime.phase)
            KeyValue(stringResource(R.string.screen_broadcast_usage_listeners), runtime.listenerCount.toString())
            KeyValue(stringResource(R.string.screen_broadcast_usage_participants), runtime.participantCount.toString())
            KeyValue(stringResource(R.string.screen_broadcast_usage_presentation_health), "${runtime.presentationHealthy}/${runtime.presentationTotal}")
            KeyValue(stringResource(R.string.screen_broadcast_usage_playback_status), stringResource(if (runtime.playable) R.string.screen_broadcast_usage_playable else R.string.screen_broadcast_usage_recovering))
        } else Text(runtimeError ?: stringResource(R.string.screen_broadcast_usage_loading_live_status), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (session.unallocatedBytes.signum() > 0) Card { KeyValue(stringResource(R.string.screen_broadcast_usage_unallocated_traffic), formatTraffic(session.unallocatedBytes)) }
}

@Composable
private fun ColumnScope.ActivityContent(state: BroadcastUsageState) {
    Card {
        Text(stringResource(R.string.screen_broadcast_usage_activity_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (state.activity.isEmpty() && !state.loading) Text(stringResource(R.string.screen_broadcast_usage_empty_activity), color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.activity.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(item.reasonCode, fontWeight = FontWeight.Medium)
                    Text(item.note ?: item.occurredAt.displayTime(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(item.sourceReference, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    item.broadcastId?.let { Text(stringResource(R.string.screen_broadcast_usage_broadcast_reference, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Text(
                    text = formatTraffic(item.amountBytes, signed = true),
                    color = if (item.amountBytes.signum() >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
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
        Text(stringResource(R.string.screen_broadcast_usage_grants_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (grants?.items.isNullOrEmpty() && !state.loading) Text(stringResource(R.string.screen_broadcast_usage_empty_grants), color = MaterialTheme.colorScheme.onSurfaceVariant)
        grants?.items?.forEachIndexed { index, grant ->
            if (index > 0) HorizontalDivider()
            Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(grant.reasonCode, fontWeight = FontWeight.Medium)
                    Text(formatTraffic(grant.remainingBytes), fontWeight = FontWeight.SemiBold)
                }
                Text(stringResource(R.string.screen_broadcast_usage_grant_period, grant.status.localizedLabel(), grant.validFrom.displayTime(), grant.expiresAt.displayTime()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.screen_broadcast_usage_grant_usage, formatTraffic(grant.originalBytes), formatTraffic(grant.consumedBytes)), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun KeyValue(key: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ErrorCard(message: String, retry: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
            Button(onClick = retry) { Text(stringResource(CommonStrings.action_retry)) }
        }
    }
}

private fun String.displayTime(): String = replace('T', ' ').substringBefore('.')

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

@PreviewsDayNight
@Composable
internal fun BroadcastUsageViewPreview() = ElementPreview {
    BroadcastUsageView(BroadcastUsageState(dashboard = BroadcastUsageDashboard(java.math.BigInteger.TEN, java.math.BigInteger.ZERO, java.math.BigInteger.ZERO, "", 0, emptyList(), null)), {})
}

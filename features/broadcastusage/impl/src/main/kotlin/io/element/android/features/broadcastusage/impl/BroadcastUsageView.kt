/*
 * Copyright (c) 2026 Unseal
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.element.android.features.broadcastusage.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.ui.strings.CommonStrings
import androidx.compose.ui.res.stringResource

@Composable
internal fun BroadcastUsageView(state: BroadcastUsageState, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val detail = state.selectedSession
    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (detail == null) "直播流量" else "场次详情", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = if (detail == null) onDone else ({ state.eventSink(BroadcastUsageEvent.CloseSession) })) {
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
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.loading && state.dashboard == null && detail == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            state.error?.let { ErrorCard(it) { state.eventSink(BroadcastUsageEvent.Refresh) } }
            if (detail != null) {
                DetailContent(detail, state.runtime, state.runtimeError)
            } else {
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

@Composable
private fun Tabs(selected: BroadcastUsageTab, select: (BroadcastUsageTab) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        listOf(BroadcastUsageTab.Overview to "场次", BroadcastUsageTab.Activity to "明细", BroadcastUsageTab.Grants to "可用流量").forEach { (tab, label) ->
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
        MetricCard("可用流量", formatTraffic(dashboard.availableTrafficBytes), Modifier.weight(1f))
        MetricCard("未分配流量", formatTraffic(dashboard.unallocatedTrafficBytes), Modifier.weight(1f))
    }
    Card {
        Text("直播场次", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        val active = dashboard.sessions.count { it.showsRuntime }
        val syncing = dashboard.sessions.count { it.state == BroadcastUsageSessionState.ClosedSyncing }
        Text("正在直播 $active 场 · 统计中 $syncing 场 · 历史 ${dashboard.sessionCount - active - syncing} 场", style = MaterialTheme.typography.bodyMedium)
        Text("流量数据可能延迟 2–3 分钟", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (dashboard.sessions.isEmpty()) Text("还没有直播场次", color = MaterialTheme.colorScheme.onSurfaceVariant)
        val activeSessions = dashboard.sessions.filter { it.showsRuntime }
        val syncingSessions = dashboard.sessions.filter { it.state == BroadcastUsageSessionState.ClosedSyncing }
        val history = dashboard.sessions.filter { it.isTerminal }
        if (activeSessions.isNotEmpty()) Text("进行中", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        activeSessions.forEach { session -> SessionRow(session) { state.eventSink(BroadcastUsageEvent.OpenSession(session.broadcastId)) } }
        if (syncingSessions.isNotEmpty()) Text("流量统计中", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        syncingSessions.forEach { session -> SessionRow(session) { state.eventSink(BroadcastUsageEvent.OpenSession(session.broadcastId)) } }
        if (history.isNotEmpty()) Text("历史场次", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        history.forEach { session -> SessionRow(session) { state.eventSink(BroadcastUsageEvent.OpenSession(session.broadcastId)) } }
        if (dashboard.nextCursor != null) TextButton(onClick = { state.eventSink(BroadcastUsageEvent.LoadMoreSessions) }) { Text("加载更多") }
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
                Text("${session.state.label} · ${session.openedAt.displayTime()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(formatTraffic(session.confirmedBytes), fontWeight = FontWeight.SemiBold)
            Icon(CompoundIcons.ChevronRight(), contentDescription = null)
        }
    }
}

@Composable
private fun DetailContent(session: BroadcastUsageSession, runtime: BroadcastRuntimeStatus?, runtimeError: String?) {
    Card {
        Text(session.displayName ?: session.roomId, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (session.displayName != null) Text(session.roomId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        KeyValue("状态", session.state.label)
        KeyValue("已确认流量", formatTraffic(session.confirmedBytes))
        KeyValue("开始时间", (session.startedAt ?: session.openedAt).displayTime())
        session.closedAt?.let { KeyValue("结束时间", it.displayTime()) }
        session.durationLabel()?.let { KeyValue("持续时间", it) }
        session.syncedThrough?.let { KeyValue("同步至", it.displayTime()) }
        session.stopReason?.let { KeyValue("结束原因", it) }
        if (!session.isTerminal) Text("Cloudflare 统计可能延迟 2–3 分钟", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (session.showsRuntime) Card {
        Text("实时状态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (runtime != null) {
            KeyValue("阶段", runtime.phase)
            KeyValue("听众", runtime.listenerCount.toString())
            KeyValue("参与者", runtime.participantCount.toString())
            KeyValue("画面健康", "${runtime.presentationHealthy}/${runtime.presentationTotal}")
            KeyValue("播放状态", if (runtime.playable) "可播放" else "恢复中")
        } else Text(runtimeError ?: "正在读取实时状态…", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (session.unallocatedBytes.signum() > 0) Card { KeyValue("未分配流量", formatTraffic(session.unallocatedBytes)) }
}

@Composable
private fun ColumnScope.ActivityContent(state: BroadcastUsageState) {
    Card {
        Text("流量明细", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (state.activity.isEmpty() && !state.loading) Text("暂无流量变动", color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.activity.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(item.reasonCode, fontWeight = FontWeight.Medium)
                    Text(item.note ?: item.occurredAt.displayTime(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(item.sourceReference, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    item.broadcastId?.let { Text("直播 $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Text(
                    text = (if (item.amountBytes.signum() > 0) "+" else "") + formatTraffic(item.amountBytes),
                    color = if (item.amountBytes.signum() >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (state.activityNextCursor != null) TextButton(onClick = { state.eventSink(BroadcastUsageEvent.LoadMoreActivity) }) { Text("加载更多") }
    }
}

@Composable
private fun ColumnScope.GrantsContent(state: BroadcastUsageState) {
    val grants = state.grants
    if (grants != null) MetricCard("当前可用总量", formatTraffic(grants.availableTrafficBytes), Modifier.fillMaxWidth())
    Card {
        Text("可用流量记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (grants?.items.isNullOrEmpty() && !state.loading) Text("暂无可用流量记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
        grants?.items?.forEachIndexed { index, grant ->
            if (index > 0) HorizontalDivider()
            Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(grant.reasonCode, fontWeight = FontWeight.Medium)
                    Text(formatTraffic(grant.remainingBytes), fontWeight = FontWeight.SemiBold)
                }
                Text("${grant.status.label()} · ${grant.validFrom.displayTime()} 至 ${grant.expiresAt.displayTime()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("原始 ${formatTraffic(grant.originalBytes)} · 已用 ${formatTraffic(grant.consumedBytes)}", style = MaterialTheme.typography.bodySmall)
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
            Button(onClick = retry) { Text("重试") }
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

private fun String.label(): String = when (this) {
    "scheduled" -> "待生效"
    "available" -> "可用"
    "exhausted" -> "已用完"
    "expired" -> "已过期"
    "revoked" -> "已撤销"
    else -> this
}

@PreviewsDayNight
@Composable
internal fun BroadcastUsageViewPreview() = ElementPreview {
    BroadcastUsageView(BroadcastUsageState(dashboard = BroadcastUsageDashboard(java.math.BigInteger.TEN, java.math.BigInteger.ZERO, java.math.BigInteger.ZERO, "", 0, emptyList(), null)), {})
}

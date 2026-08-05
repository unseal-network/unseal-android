/*
 * Copyright (c) 2026 Unseal
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.broadcastusage.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Inject
class BroadcastUsagePresenter(
    private val service: BroadcastUsageService,
    private val matrixClient: MatrixClient,
) : Presenter<BroadcastUsageState> {
    @Composable
    override fun present(): BroadcastUsageState {
        val scope = rememberCoroutineScope()
        var tab by remember { mutableStateOf(BroadcastUsageTab.Overview) }
        var dashboard by remember { mutableStateOf<BroadcastUsageDashboard?>(null) }
        var selected by remember { mutableStateOf<BroadcastUsageSession?>(null) }
        var runtime by remember { mutableStateOf<BroadcastRuntimeStatus?>(null) }
        var activity by remember { mutableStateOf(emptyList<BroadcastTrafficActivity>()) }
        var activityNextCursor by remember { mutableStateOf<String?>(null) }
        var grants by remember { mutableStateOf<BroadcastGrantList?>(null) }
        var loading by remember { mutableStateOf(false) }
        var loadingMore by remember { mutableStateOf(false) }
        var foreground by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        var runtimeError by remember { mutableStateOf<String?>(null) }

        fun message(failure: Throwable): String = when ((failure as? BroadcastUsageHttpException)?.statusCode) {
            0 -> "无法连接网络，请检查连接后重试"
            401 -> "登录已过期，请重新登录"
            404 -> "找不到这条直播场次"
            else -> failure.message ?: "暂时无法读取直播流量"
        }

        suspend fun BroadcastUsageSession.withLocalRoomName(): BroadcastUsageSession {
            val name = runCatching { matrixClient.getRoom(RoomId(roomId))?.info()?.name }.getOrNull()?.takeIf(String::isNotBlank)
            return copy(displayName = name)
        }

        suspend fun loadDashboard(cursor: String? = null) {
            runCatching { service.dashboard(cursor = cursor) }
                .map { result -> result.copy(sessions = result.sessions.map { it.withLocalRoomName() }) }
                .onSuccess { result ->
                    dashboard = if (cursor == null || dashboard == null) result else result.copy(
                        sessions = (dashboard!!.sessions + result.sessions).distinctBy { it.sessionId }
                    )
                    error = null
                }
                .onFailure { error = message(it) }
        }

        suspend fun loadActivity(cursor: String? = null) {
            runCatching { service.activity(cursor = cursor) }
                .onSuccess { page ->
                    activity = ((if (cursor == null) page.items else activity + page.items).associateBy { it.id }).values
                        .sortedByDescending { it.occurredAt }
                    activityNextCursor = page.nextCursor
                    error = null
                }
                .onFailure { error = message(it) }
        }

        suspend fun loadGrants() {
            runCatching { service.grants() }.onSuccess { grants = it; error = null }.onFailure { error = message(it) }
        }

        fun refresh() = scope.launch {
            loading = true
            when (tab) {
                BroadcastUsageTab.Overview -> loadDashboard()
                BroadcastUsageTab.Activity -> loadActivity()
                BroadcastUsageTab.Grants -> loadGrants()
            }
            loading = false
        }

        LaunchedEffect(foreground) {
            if (!foreground) return@LaunchedEffect
            do {
                loadDashboard()
                delay(60_000)
            } while (foreground)
        }

        LaunchedEffect(foreground, selected?.broadcastId) {
            val broadcastId = selected?.broadcastId ?: return@LaunchedEffect
            if (!foreground) return@LaunchedEffect
            do {
                runCatching { service.session(broadcastId) }
                    .map { it.withLocalRoomName() }
                    .onSuccess { selected = it; error = null }
                    .onFailure { error = message(it) }
                if (selected?.isTerminal == true) break
                delay(60_000)
            } while (foreground && selected?.broadcastId == broadcastId)
        }

        LaunchedEffect(foreground, selected?.broadcastId, selected?.showsRuntime) {
            val broadcastId = selected?.broadcastId ?: return@LaunchedEffect
            if (!foreground || selected?.showsRuntime != true) {
                runtime = null
                runtimeError = null
                return@LaunchedEffect
            }
            do {
                val wait = runCatching { service.runtime(broadcastId) }
                    .onSuccess { runtime = it; runtimeError = null }
                    .onFailure { runtimeError = message(it) }
                    .getOrNull()?.pollAfterMs ?: 5_000
                delay(wait)
            } while (foreground && selected?.broadcastId == broadcastId && selected?.showsRuntime == true)
        }

        fun handle(event: BroadcastUsageEvent) {
            when (event) {
                BroadcastUsageEvent.Foreground -> {
                    foreground = true
                }
                BroadcastUsageEvent.Background -> foreground = false
                BroadcastUsageEvent.Refresh -> refresh()
                is BroadcastUsageEvent.SelectTab -> {
                    tab = event.tab
                    if (event.tab == BroadcastUsageTab.Activity && activity.isEmpty()) scope.launch { loading = true; loadActivity(); loading = false }
                    if (event.tab == BroadcastUsageTab.Grants && grants == null) scope.launch { loading = true; loadGrants(); loading = false }
                }
                is BroadcastUsageEvent.OpenSession -> selected = dashboard?.sessions?.firstOrNull { it.broadcastId == event.broadcastId }
                BroadcastUsageEvent.CloseSession -> {
                    selected = null
                    runtime = null
                    runtimeError = null
                }
                BroadcastUsageEvent.LoadMoreSessions -> dashboard?.nextCursor?.let { cursor ->
                    scope.launch { loadingMore = true; loadDashboard(cursor); loadingMore = false }
                }
                BroadcastUsageEvent.LoadMoreActivity -> activityNextCursor?.let { cursor ->
                    scope.launch { loadingMore = true; loadActivity(cursor); loadingMore = false }
                }
            }
        }

        return BroadcastUsageState(
            tab = tab, dashboard = dashboard, selectedSession = selected, runtime = runtime, activity = activity,
            activityNextCursor = activityNextCursor, grants = grants, loading = loading, loadingMore = loadingMore,
            error = error, runtimeError = runtimeError, eventSink = ::handle,
        )
    }
}

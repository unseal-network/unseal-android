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
import androidx.compose.runtime.saveable.rememberSaveable
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.services.toolbox.api.strings.StringProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Inject
class BroadcastUsagePresenter(
    private val service: BroadcastUsageService,
    private val matrixClient: MatrixClient,
    private val stringProvider: StringProvider,
) : Presenter<BroadcastUsageState> {
    @Composable
    override fun present(): BroadcastUsageState {
        val scope = rememberCoroutineScope()
        var tab by remember { mutableStateOf(BroadcastUsageTab.Overview) }
        var dashboard by remember { mutableStateOf<BroadcastUsageDashboard?>(null) }
        var selectedBroadcastId by rememberSaveable { mutableStateOf<String?>(null) }
        var selectedSession by remember { mutableStateOf<BroadcastUsageSession?>(null) }
        var selectedHistory by remember { mutableStateOf<BroadcastHistoryItem?>(null) }
        var runtime by remember { mutableStateOf<BroadcastRuntimeStatus?>(null) }
        var history by remember { mutableStateOf(emptyList<BroadcastHistoryItem>()) }
        var historyNextCursor by remember { mutableStateOf<String?>(null) }
        var activity by remember { mutableStateOf(emptyList<BroadcastTrafficActivity>()) }
        var activityNextCursor by remember { mutableStateOf<String?>(null) }
        var grants by remember { mutableStateOf<BroadcastGrantList?>(null) }
        var loading by remember { mutableStateOf(false) }
        var loadingMore by remember { mutableStateOf(false) }
        var foreground by remember { mutableStateOf(false) }
        var dashboardError by remember { mutableStateOf<String?>(null) }
        var sessionError by remember { mutableStateOf<String?>(null) }
        var historyError by remember { mutableStateOf<String?>(null) }
        var activityError by remember { mutableStateOf<String?>(null) }
        var grantsError by remember { mutableStateOf<String?>(null) }
        var runtimeError by remember { mutableStateOf<String?>(null) }

        fun message(failure: Throwable): String {
            val statusCode = (failure as? BroadcastUsageHttpException)?.statusCode
            return when {
                statusCode == 0 -> stringProvider.getString(R.string.error_broadcast_usage_network)
                statusCode == 400 -> stringProvider.getString(R.string.error_broadcast_usage_bad_request)
                statusCode == 401 -> stringProvider.getString(R.string.error_broadcast_usage_unauthorized)
                statusCode == 404 -> stringProvider.getString(R.string.error_broadcast_usage_not_found)
                statusCode != null && statusCode in 500..599 -> stringProvider.getString(R.string.error_broadcast_usage_service_unavailable)
                else -> stringProvider.getString(R.string.error_broadcast_usage_generic)
            }
        }

        suspend fun BroadcastUsageSession.withLocalRoomName(): BroadcastUsageSession {
            val name = runCatching { matrixClient.getRoom(RoomId(roomId))?.info()?.name }.getOrNull()?.takeIf(String::isNotBlank)
            return copy(displayName = name)
        }

        suspend fun BroadcastHistoryItem.withLocalRoomName(): BroadcastHistoryItem {
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
                    dashboardError = null
                }
                .onFailure { dashboardError = message(it) }
        }

        suspend fun loadSession(broadcastId: String) {
            runCatching { service.session(broadcastId) }
                .map { it.withLocalRoomName() }
                .onSuccess {
                    selectedSession = it
                    sessionError = null
                }
                .onFailure { sessionError = message(it) }
        }

        suspend fun loadHistory(cursor: String? = null) {
            runCatching { service.history(cursor = cursor) }
                .map { page -> page.copy(items = page.items.map { it.withLocalRoomName() }) }
                .onSuccess { page ->
                    history = if (cursor == null) page.items else (history + page.items).distinctBy { it.sessionId }
                    historyNextCursor = page.nextCursor
                    historyError = null
                }
                .onFailure { historyError = message(it) }
        }

        suspend fun loadHistoryDetail(broadcastId: String) {
            runCatching { service.historyDetail(broadcastId) }
                .map { it.withLocalRoomName() }
                .onSuccess {
                    selectedHistory = it
                    historyError = null
                }
                .onFailure { historyError = message(it) }
        }

        suspend fun loadActivity(cursor: String? = null) {
            runCatching { service.activity(cursor = cursor) }
                .onSuccess { page ->
                    activity = ((if (cursor == null) page.items else activity + page.items).associateBy { it.id }).values
                        .sortedByDescending { it.occurredAt }
                    activityNextCursor = page.nextCursor
                    activityError = null
                }
                .onFailure { activityError = message(it) }
        }

        suspend fun loadGrants() {
            runCatching { service.grants() }
                .onSuccess {
                    grants = it
                    grantsError = null
                }
                .onFailure { grantsError = message(it) }
        }

        fun refresh() = scope.launch {
            loading = true
            val detailId = selectedBroadcastId
            if (detailId != null) {
                if (selectedHistory != null) loadHistoryDetail(detailId) else loadSession(detailId)
            } else {
                when (tab) {
                    BroadcastUsageTab.Overview -> {
                        loadDashboard()
                        loadHistory()
                    }
                    BroadcastUsageTab.Activity -> loadActivity()
                    BroadcastUsageTab.Grants -> loadGrants()
                }
            }
            loading = false
        }

        LaunchedEffect(foreground) {
            if (!foreground) return@LaunchedEffect
            do {
                loadDashboard()
                loadHistory()
                delay(60_000)
            } while (foreground)
        }

        LaunchedEffect(foreground, selectedBroadcastId) {
            val broadcastId = selectedBroadcastId ?: return@LaunchedEffect
            if (selectedHistory != null) return@LaunchedEffect
            if (!foreground) return@LaunchedEffect
            do {
                loadSession(broadcastId)
                if (selectedSession?.isTerminal == true) break
                delay(60_000)
            } while (foreground && selectedBroadcastId == broadcastId)
        }

        LaunchedEffect(foreground, selectedBroadcastId, selectedSession?.showsRuntime) {
            val broadcastId = selectedBroadcastId ?: return@LaunchedEffect
            if (!foreground || selectedSession?.showsRuntime != true) {
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
            } while (foreground && selectedBroadcastId == broadcastId && selectedSession?.showsRuntime == true)
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
                is BroadcastUsageEvent.OpenSession -> {
                    selectedBroadcastId = event.broadcastId
                    selectedSession = dashboard?.sessions?.firstOrNull { it.broadcastId == event.broadcastId }
                    sessionError = null
                }
                is BroadcastUsageEvent.OpenHistory -> {
                    selectedBroadcastId = event.broadcastId
                    selectedHistory = history.firstOrNull { it.broadcastId == event.broadcastId }
                    historyError = null
                    scope.launch { loadHistoryDetail(event.broadcastId) }
                }
                BroadcastUsageEvent.CloseSession -> {
                    selectedBroadcastId = null
                    selectedSession = null
                    selectedHistory = null
                    runtime = null
                    sessionError = null
                    runtimeError = null
                }
                BroadcastUsageEvent.LoadMoreSessions -> dashboard?.nextCursor?.let { cursor ->
                    scope.launch { loadingMore = true; loadDashboard(cursor); loadingMore = false }
                }
                BroadcastUsageEvent.LoadMoreHistory -> historyNextCursor?.let { cursor ->
                    scope.launch { loadingMore = true; loadHistory(cursor); loadingMore = false }
                }
                BroadcastUsageEvent.LoadMoreActivity -> activityNextCursor?.let { cursor ->
                    scope.launch { loadingMore = true; loadActivity(cursor); loadingMore = false }
                }
            }
        }

        return BroadcastUsageState(
            tab = tab, dashboard = dashboard, selectedBroadcastId = selectedBroadcastId, selectedSession = selectedSession,
            selectedHistory = selectedHistory, runtime = runtime, activity = activity, history = history, historyNextCursor = historyNextCursor,
            activityNextCursor = activityNextCursor, grants = grants, loading = loading, loadingMore = loadingMore,
            dashboardError = dashboardError, sessionError = sessionError, historyError = historyError, activityError = activityError,
            grantsError = grantsError, runtimeError = runtimeError, eventSink = ::handle,
        )
    }
}

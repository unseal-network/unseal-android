/*
 * Copyright (c) 2026 Unseal
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.broadcastusage.impl

enum class BroadcastUsageTab { Overview, Activity, Grants }

sealed interface BroadcastUsageEvent {
    data object Foreground : BroadcastUsageEvent
    data object Background : BroadcastUsageEvent
    data object Refresh : BroadcastUsageEvent
    data class SelectTab(val tab: BroadcastUsageTab) : BroadcastUsageEvent
    data class OpenSession(val broadcastId: String) : BroadcastUsageEvent
    data class OpenHistory(val broadcastId: String) : BroadcastUsageEvent
    data object CloseSession : BroadcastUsageEvent
    data object LoadMoreSessions : BroadcastUsageEvent
    data object LoadMoreHistory : BroadcastUsageEvent
    data object LoadMoreActivity : BroadcastUsageEvent
}

data class BroadcastUsageState(
    val tab: BroadcastUsageTab = BroadcastUsageTab.Overview,
    val dashboard: BroadcastUsageDashboard? = null,
    val selectedBroadcastId: String? = null,
    val selectedSession: BroadcastUsageSession? = null,
    val selectedHistory: BroadcastHistoryItem? = null,
    val runtime: BroadcastRuntimeStatus? = null,
    val activity: List<BroadcastTrafficActivity> = emptyList(),
    val history: List<BroadcastHistoryItem> = emptyList(),
    val historyNextCursor: String? = null,
    val activityNextCursor: String? = null,
    val grants: BroadcastGrantList? = null,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val dashboardError: String? = null,
    val sessionError: String? = null,
    val historyError: String? = null,
    val activityError: String? = null,
    val grantsError: String? = null,
    val runtimeError: String? = null,
    val eventSink: (BroadcastUsageEvent) -> Unit = {},
)

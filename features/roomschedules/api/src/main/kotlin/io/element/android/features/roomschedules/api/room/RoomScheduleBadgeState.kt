/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.roomschedules.api.room

data class RoomScheduleBadgeState(
    val isLoading: Boolean = false,
    val isVisible: Boolean = false,
    val activeScheduleCount: Int = 0,
    val error: String? = null,
    val eventSink: (RoomScheduleBadgeEvents) -> Unit,
)

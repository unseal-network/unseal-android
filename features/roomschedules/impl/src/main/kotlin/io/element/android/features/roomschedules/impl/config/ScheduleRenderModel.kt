/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.roomschedules.impl.config

import io.element.android.features.roomschedules.impl.cron.CronParser
import io.element.android.features.roomschedules.impl.model.isEnabled
import io.element.android.features.roomschedules.impl.model.stableId
import io.element.android.libraries.chatbot.api.model.schedules.ChatbotSchedule
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

data class ScheduleRenderModel(
    val id: String,
    val title: String,
    val agentLabel: String,
    val cronLabel: String,
    val actionPreview: String,
    val isEnabled: Boolean,
    val isOwner: Boolean,
    val statusLabel: String,
    val toggleLabel: String,
    val source: ChatbotSchedule,
)

fun ChatbotSchedule.toScheduleRenderModel(currentUserId: String): ScheduleRenderModel {
    val enabled = isEnabled()
    return ScheduleRenderModel(
        id = stableId(),
        title = name.ifBlank { stableId() },
        agentLabel = agentId.ifBlank { "Unknown agent" },
        cronLabel = CronParser.toReadable(cron),
        actionPreview = action,
        isEnabled = enabled,
        isOwner = creatorId == currentUserId,
        statusLabel = if (enabled) "Enabled" else "Disabled",
        toggleLabel = if (enabled) "Disable" else "Enable",
        source = this,
    )
}

fun List<ChatbotSchedule>.toScheduleRenderModels(
    currentUserId: String,
    showOnlyMine: Boolean,
): ImmutableList<ScheduleRenderModel> {
    return filter { it.isEnabled() || it.creatorId == currentUserId }
        .filter { !showOnlyMine || it.creatorId == currentUserId }
        .sortedWith(
            compareByDescending<ChatbotSchedule> { it.isEnabled() }
                .thenBy { it.name.lowercase() }
                .thenBy { it.stableId() }
        )
        .map { it.toScheduleRenderModel(currentUserId) }
        .toImmutableList()
}

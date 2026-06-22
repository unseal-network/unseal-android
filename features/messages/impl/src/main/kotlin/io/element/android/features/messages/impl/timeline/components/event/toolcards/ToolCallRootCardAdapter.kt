/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event.toolcards

import io.element.android.features.messages.impl.timeline.components.event.toRenderableToolParts
import io.element.android.features.messages.impl.timeline.components.event.toToolCardEntries
import io.element.android.features.messages.impl.timeline.model.event.AiToolCardEntry
import io.element.android.features.messages.impl.timeline.model.event.AiToolStreamPart
import io.element.android.features.messages.impl.timeline.model.event.ToolCallRootRenderModel
import kotlinx.collections.immutable.toImmutableList
import org.json.JSONObject

internal object ToolCallRootCardAdapter {
    fun toolCallEntries(parts: List<AiToolStreamPart>): List<AiToolCardEntry> {
        return parts.flatMap { it.toToolCardEntries() }
    }

    fun renderableToolParts(parts: List<AiToolStreamPart>): List<AiToolStreamPart> {
        return parts.flatMap { it.toRenderableToolParts() }
    }

    fun rootModel(entries: List<AiToolCardEntry>): ToolCallRootRenderModel? {
        if (entries.isEmpty()) return null
        val doneCount = entries.count { it.state == "done" }
        val errorCount = entries.count { it.state == "error" }
        val callingCount = entries.size - doneCount - errorCount
        val allFinished = callingCount == 0
        val displayEntries = entries.deduplicatedForTabs()
        val selectedOriginal = entries.last()
        val selectedIndex = displayEntries.indexOfLast {
            it.name == selectedOriginal.name &&
                it.cardType == selectedOriginal.cardType &&
                it.state == selectedOriginal.state
        }.takeIf { it >= 0 } ?: displayEntries.lastIndex
        val isSingleTool = displayEntries.size == 1
        val selectedTitle = displayEntries.getOrNull(selectedIndex)
            ?.name
            ?.takeIf { it.isNotBlank() && !it.equals("Tool Calls", ignoreCase = true) }
        return ToolCallRootRenderModel(
            id = entries.first().id.substringBefore('_'),
            title = selectedTitle ?: "Tool Calls",
            entries = displayEntries.toImmutableList(),
            selectedIndex = selectedIndex,
            doneCount = doneCount,
            errorCount = errorCount,
            callingCount = callingCount,
            allFinished = allFinished,
            isSingleTool = isSingleTool,
            expandedByDefault = !allFinished,
        )
    }
}

private fun List<AiToolCardEntry>.deduplicatedForTabs(): List<AiToolCardEntry> {
    val order = mutableListOf<Triple<String, String, String>>()
    val merged = linkedMapOf<Triple<String, String, String>, AiToolCardEntry>()
    for (entry in this) {
        val key = Triple(entry.name, entry.cardType, entry.state)
        if (key !in merged) {
            order += key
        }
        merged[key] = when {
            entry.state == "done" -> entry
            merged[key]?.state == "done" -> merged.getValue(key)
            entry.state == "error" -> entry
            else -> entry
        }
    }
    return order.mapNotNull { merged[it] }
}

internal fun errorProps(cardType: String, message: String?): JSONObject {
    return JSONObject()
        .put("_cardType", cardType)
        .put("errorText", message?.takeIf { it.isNotBlank() } ?: "Tool call failed")
}

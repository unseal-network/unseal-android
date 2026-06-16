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
        val isSingleTool = entries.size == 1
        val selectedIndex = entries.lastIndex
        return ToolCallRootRenderModel(
            id = entries.joinToString(separator = "|") { it.id },
            title = if (isSingleTool) entries.single().name else "Tool Calls",
            entries = entries.toImmutableList(),
            selectedIndex = selectedIndex,
            doneCount = doneCount,
            errorCount = errorCount,
            callingCount = callingCount,
            allFinished = allFinished,
            isSingleTool = isSingleTool,
            expandedByDefault = true,
        )
    }
}

internal fun errorProps(cardType: String, message: String?): JSONObject {
    return JSONObject()
        .put("_cardType", cardType)
        .put("errorText", message?.takeIf { it.isNotBlank() } ?: "Tool call failed")
}

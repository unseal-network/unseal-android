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
import org.json.JSONObject

internal object ToolCallRootCardAdapter {
    fun toolCallEntries(parts: List<AiToolStreamPart>): List<AiToolCardEntry> {
        return parts.flatMap { it.toToolCardEntries() }
    }

    fun renderableToolParts(parts: List<AiToolStreamPart>): List<AiToolStreamPart> {
        return parts.flatMap { it.toRenderableToolParts() }
    }
}

internal fun errorProps(cardType: String, message: String?): JSONObject {
    return JSONObject()
        .put("_cardType", cardType)
        .put("errorText", message?.takeIf { it.isNotBlank() } ?: "Tool call failed")
}

/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.timeline.model.event.AiToolStreamPart
import org.junit.Test

class AiToolCardLogicTest {
    @Test
    fun `registered tools do not allow raw payload fallback`() {
        val part = toolPart(toolName = "GMAIL_FETCH_EMAILS")

        assertThat(part.allowsRawPayloadFallback()).isFalse()
    }

    @Test
    fun `unregistered tools allow raw payload fallback`() {
        val part = toolPart(toolName = "weather")

        assertThat(part.allowsRawPayloadFallback()).isTrue()
    }

    private fun toolPart(toolName: String): AiToolStreamPart {
        return AiToolStreamPart(
            id = "tool-1",
            state = "output-available",
            toolName = toolName,
            title = null,
            input = null,
            output = """{"debug":"raw"}""",
            errorText = null,
        )
    }
}

/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class JsonSpecRenderTest {
    @Test
    fun `JsonRenderElement isVisible follows boolean visible prop`() {
        val spec = """
            {
              "root": "root",
              "elements": {
                "root": { "type": "stack", "children": ["shown", "hidden"] },
                "shown": { "type": "text", "props": { "text": "Shown", "visible": true } },
                "hidden": { "type": "text", "props": { "text": "Hidden", "visible": false } }
              }
            }
        """.trimIndent().toJsonRenderSpec()!!

        assertThat(spec.elements.getValue("shown").isVisible(spec)).isTrue()
        assertThat(spec.elements.getValue("hidden").isVisible(spec)).isFalse()
    }

    @Test
    fun `JsonRenderElement isVisible resolves state reference`() {
        val spec = """
            {
              "root": "root",
              "state": {
                "flags": {
                  "show": true,
                  "hide": false
                }
              },
              "elements": {
                "root": { "type": "stack", "children": ["shown", "hidden"] },
                "shown": { "type": "text", "props": { "text": "Shown", "visible": { "${"$"}state": "/flags/show" } } },
                "hidden": { "type": "text", "props": { "text": "Hidden", "visible": { "${"$"}state": "/flags/hide" } } }
              }
            }
        """.trimIndent().toJsonRenderSpec()!!

        assertThat(spec.elements.getValue("shown").isVisible(spec)).isTrue()
        assertThat(spec.elements.getValue("hidden").isVisible(spec)).isFalse()
    }
}

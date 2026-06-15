/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MarkdownBodyTest {
    @Test
    fun `spec code blocks render as json spec`() {
        val patchStream = """
            {"op":"add","path":"/root","value":"main"}
            {"op":"add","path":"/elements/main","value":{"type":"Text","props":{"text":"Hello spec"},"children":[]}}
        """.trimIndent()

        assertThat(markdownCodeBlockRenderMode(language = "spec", code = patchStream))
            .isEqualTo(MarkdownCodeBlockRenderMode.JsonSpec)
    }

    @Test
    fun `json code blocks render as json spec only when the payload is renderable`() {
        val renderableJson = """
            {
              "root": "main",
              "elements": {
                "main": {
                  "type": "Text",
                  "props": { "text": "Hello JSON" },
                  "children": []
                }
              }
            }
        """.trimIndent()
        val ordinaryJson = """{"message":"debug payload","count":3}"""

        assertThat(markdownCodeBlockRenderMode(language = "json", code = renderableJson))
            .isEqualTo(MarkdownCodeBlockRenderMode.JsonSpec)
        assertThat(markdownCodeBlockRenderMode(language = "json", code = ordinaryJson))
            .isEqualTo(MarkdownCodeBlockRenderMode.Code)
    }

    @Test
    fun `jsonl code blocks render as json spec when patches build elements`() {
        val jsonl = """
            {"op":"add","path":"/root","value":"main"}
            {"op":"add","path":"/elements/main","value":{"type":"Text","props":{"text":{"${"$"}state":"/weather/temp"}},"children":[]}}
            {"op":"add","path":"/state/weather","value":{"temp":"21°C"}}
        """.trimIndent()

        assertThat(markdownCodeBlockRenderMode(language = "jsonl", code = jsonl))
            .isEqualTo(MarkdownCodeBlockRenderMode.JsonSpec)
    }

    @Test
    fun `non spec languages stay as code blocks`() {
        assertThat(markdownCodeBlockRenderMode(language = "kotlin", code = "fun main() = Unit"))
            .isEqualTo(MarkdownCodeBlockRenderMode.Code)
    }
}

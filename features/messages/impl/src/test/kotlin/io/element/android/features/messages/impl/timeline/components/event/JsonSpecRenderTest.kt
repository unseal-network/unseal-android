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

    @Test
    fun `JsonRenderElement renderChildren repeats children with local item state`() {
        val spec = """
            {
              "root": "root",
              "state": {
                "items": [
                  { "title": "First" },
                  { "title": "Second" }
                ]
              },
              "elements": {
                "root": { "type": "stack", "props": { "repeat": { "${"$"}state": "/items" } }, "children": ["row"] },
                "row": { "type": "text", "props": { "text": { "${"$"}state": "/title" } } }
              }
            }
        """.trimIndent().toJsonRenderSpec()!!

        val renderChildren = spec.elements.getValue("root").renderChildren(spec)

        assertThat(renderChildren.map { it.id }).containsExactly("row", "row").inOrder()
        assertThat(renderChildren.map { spec.elements.getValue(it.id).props.firstString(it.state, "text") })
            .containsExactly("First", "Second")
            .inOrder()
    }

    @Test
    fun `JsonRenderElement renderChildren repeats template with local item state`() {
        val spec = """
            {
              "root": "root",
              "state": {
                "items": [
                  { "title": "First", "enabled": true },
                  { "title": "Second", "enabled": false }
                ]
              },
              "elements": {
                "root": {
                  "type": "stack",
                  "props": {
                    "repeat": { "${"$"}state": "/items" },
                    "${"$"}template": {
                      "type": "checkbox",
                      "props": {
                        "label": { "${"$"}state": "/title" },
                        "checked": { "${"$"}state": "/enabled" }
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent().toJsonRenderSpec()!!

        val renderChildren = spec.elements.getValue("root").renderChildren(spec)

        assertThat(renderChildren).hasSize(2)
        assertThat(renderChildren.map { it.element?.type }).containsExactly("checkbox", "checkbox").inOrder()
        assertThat(renderChildren.map { it.element?.props?.firstString(it.state, "label") })
            .containsExactly("First", "Second")
            .inOrder()
        assertThat(renderChildren.map { it.element?.isVisible(spec, it.state) })
            .containsExactly(true, true)
            .inOrder()
    }

    @Test
    fun `canRenderAsJsonSpec accepts form control typed data`() {
        val payload = """
            {
              "type": "select",
              "data": {
                "label": "Choose",
                "options": ["A", "B"],
                "value": "B"
              }
            }
        """.trimIndent()

        assertThat(payload.canRenderAsJsonSpec()).isTrue()
        assertThat(payload.toJsonRenderSpec()?.elements?.getValue("root")?.type).isEqualTo("select")
    }

    @Test
    fun `canRenderAsJsonSpec accepts spacer typed data`() {
        val payload = """
            {
              "type": "spacer",
              "data": {
                "height": 16
              }
            }
        """.trimIndent()

        val spec = payload.toJsonRenderSpec()

        assertThat(payload.canRenderAsJsonSpec()).isTrue()
        assertThat(spec?.elements?.getValue("root")?.type).isEqualTo("spacer")
        assertThat(spec?.elements?.getValue("root")?.props?.optDouble("height")).isEqualTo(16.0)
    }

    @Test
    fun `canRenderAsJsonSpec accepts scroll container`() {
        val payload = """
            {
              "root": "root",
              "elements": {
                "root": { "type": "scroll", "props": { "maxHeight": 180 }, "children": ["title"] },
                "title": { "type": "text", "props": { "text": "Scrollable content" } }
              }
            }
        """.trimIndent()

        val spec = payload.toJsonRenderSpec()

        assertThat(payload.canRenderAsJsonSpec()).isTrue()
        assertThat(spec?.elements?.getValue("root")?.type).isEqualTo("scroll")
        assertThat(spec?.elements?.getValue("root")?.renderChildren(spec).orEmpty().map { it.id })
            .containsExactly("title")
    }

    @Test
    fun `toJsonRenderSpec accepts ios flat payload wrapper`() {
        val payload = """
            {
              "type": "flat",
              "spec": {
                "root": "root",
                "elements": {
                  "root": { "type": "card", "children": ["title"] },
                  "title": { "type": "heading", "props": { "text": "Flat title" } }
                }
              }
            }
        """.trimIndent()

        val spec = payload.toJsonRenderSpec()

        assertThat(payload.canRenderAsJsonSpec()).isTrue()
        assertThat(spec?.root).isEqualTo("root")
        assertThat(spec?.elements?.getValue("title")?.props?.optString("text")).isEqualTo("Flat title")
    }

    @Test
    fun `toJsonRenderSpec flattens ios nested payload wrapper`() {
        val payload = """
            {
              "type": "nested",
              "spec": {
                "type": "card",
                "props": { "title": "Nested card" },
                "children": [
                  { "type": "text", "props": { "text": "First child" } },
                  { "id": "custom", "type": "text", "props": { "text": "Second child" } }
                ]
              }
            }
        """.trimIndent()

        val spec = payload.toJsonRenderSpec()

        assertThat(payload.canRenderAsJsonSpec()).isTrue()
        assertThat(spec?.root).isEqualTo("root")
        assertThat(spec?.elements?.getValue("root")?.children).containsExactly("root_child_0", "custom").inOrder()
        assertThat(spec?.elements?.getValue("root_child_0")?.props?.optString("text")).isEqualTo("First child")
        assertThat(spec?.elements?.getValue("custom")?.props?.optString("text")).isEqualTo("Second child")
    }

    @Test
    fun `JsonRenderElement keeps top level visibility when props are present`() {
        val spec = """
            {
              "root": "root",
              "elements": {
                "root": { "type": "stack", "children": ["hidden"] },
                "hidden": {
                  "type": "text",
                  "visible": false,
                  "props": { "text": "Hidden" }
                }
              }
            }
        """.trimIndent().toJsonRenderSpec()!!

        assertThat(spec.elements.getValue("hidden").isVisible(spec)).isFalse()
    }

    @Test
    fun `clickActionLabel resolves non url action metadata`() {
        val spec = """
            {
              "type": "button",
              "data": {
                "on": {
                  "click": {
                    "action": "approve_vault_access"
                  }
                }
              }
            }
        """.trimIndent().toJsonRenderSpec()!!

        val element = spec.elements.getValue("root")

        assertThat(element.props.clickActionLabel(spec.state)).isEqualTo("approve vault access")
    }
}

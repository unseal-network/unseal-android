/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event.toolcards

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class ToolCardRegistryTest {
    private val manifest = Json.parseToJsonElement(
        javaClass.classLoader!!.getResource("toolcards/ios_tool_card_manifest.json")!!.readText()
    ).jsonObject

    @Test
    fun `android registry card types match ios manifest mappings`() {
        val mappings = manifest.getValue("registryMappings").jsonObject
        val names = mappings.keys

        assertThat(TOOL_CARD_REGISTRY_WITH_DISPLAY.keys).containsAtLeastElementsIn(names)
        names.forEach { name ->
            val expected = mappings.getValue(name).jsonObject
            val actual = TOOL_CARD_REGISTRY_WITH_DISPLAY.getValue(name)
            assertThat(actual.cardType).isEqualTo(expected.string("cardType"))
            assertThat(actual.displayName).isEqualTo(expected.string("displayName"))
        }
    }

    @Test
    fun `root and suspended card sets match ios manifest`() {
        assertThat(ROOT_DISPATCH_CARD_TYPES).containsAtLeastElementsIn(manifest.array("rootDispatchCardTypes").toStringSet())
        assertThat(STANDALONE_SUSPENDED_CARD_TYPES).containsAtLeastElementsIn(manifest.array("standaloneSuspendedCardTypes").toStringSet())
    }

    @Test
    fun `meta and ignored tool sets match ios manifest`() {
        assertThat(META_TOOL_NAMES).containsAtLeastElementsIn(manifest.array("metaTools").toStringSet())
        assertThat(IGNORED_TOOL_NAMES).containsAtLeastElementsIn(manifest.array("ignoredTools").toStringSet())
    }
}

private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

private fun JsonObject.array(key: String): JsonArray = getValue(key).jsonArray

private fun JsonArray.toStringSet(): Set<String> =
    map { it.jsonPrimitive.content }.toSet()

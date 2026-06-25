/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.skills.impl.shared

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.chatbot.api.model.skills.ChatbotSkillFacetsResponse
import io.element.android.libraries.chatbot.api.model.skills.ChatbotSkillSource
import io.element.android.libraries.chatbot.api.model.skills.ChatbotSkillTagMode
import io.element.android.libraries.chatbot.api.model.skills.ChatbotUserSkill
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.junit.Test

class SkillMetadataModelsTest {
    @Test
    fun `metadata helpers expose top-level fields`() {
        val skill = ChatbotUserSkill(
            id = "browser",
            name = "browser-qa",
            description = "Runs rendered web checks",
            category = "Testing",
            tags = listOf("browser", "qa"),
            source = ChatbotSkillSource(label = "GitHub", repository = "vercel-labs/skills"),
        )

        assertThat(skill.skillCategory()).isEqualTo("Testing")
        assertThat(skill.skillTags()).containsExactly("browser", "qa").inOrder()
        assertThat(skill.skillSourceLabel()).isEqualTo("GitHub")
    }

    @Test
    fun `metadata helpers fall back to legacy metadata fields`() {
        val skill = ChatbotUserSkill(
            id = "legacy",
            name = "legacy",
            metadata = buildJsonObject {
                put("category", JsonPrimitive("Testing"))
                put("tags", buildJsonArray {
                    add(JsonPrimitive("browser"))
                    add(JsonPrimitive("qa"))
                })
                put("source", JsonPrimitive("GitHub"))
            },
        )

        assertThat(skill.skillCategory()).isEqualTo("Testing")
        assertThat(skill.skillTags()).containsExactly("browser", "qa").inOrder()
        assertThat(skill.skillSourceLabel()).isEqualTo("GitHub")
    }

    @Test
    fun `source label precedence is label repository id type`() {
        assertThat(ChatbotSkillSource(label = "Label", repository = "repo", id = "id", type = "type").displayLabel()).isEqualTo("Label")
        assertThat(ChatbotSkillSource(repository = "repo", id = "id", type = "type").displayLabel()).isEqualTo("repo")
        assertThat(ChatbotSkillSource(id = "id", type = "type").displayLabel()).isEqualTo("id")
        assertThat(ChatbotSkillSource(type = "type").displayLabel()).isEqualTo("type")
    }

    @Test
    fun `legacy primitive source is treated as type after sibling repository and id`() {
        val skill = ChatbotUserSkill(
            id = "legacy",
            name = "legacy",
            metadata = buildJsonObject {
                put("source", JsonPrimitive("builtin"))
                put("source_id", JsonPrimitive("server-id"))
                put("repository", JsonPrimitive("org/skills"))
            },
        )

        assertThat(skill.skillSourceLabel()).isEqualTo("org/skills")
    }

    @Test
    fun `source serializer accepts string and object values`() {
        val json = Json { ignoreUnknownKeys = true }

        val stringSkill = json.decodeFromString<ChatbotUserSkill>("""{"id":"s","name":"Skill","source":"GitHub"}""")
        val objectSkill = json.decodeFromString<ChatbotUserSkill>(
            """{"id":"o","name":"Skill","source":{"type":"git","id":"github","repository":"vercel-labs/skills","trust_tier":"trusted"}}"""
        )

        assertThat(stringSkill.skillSourceLabel()).isEqualTo("GitHub")
        assertThat(objectSkill.skillSourceLabel()).isEqualTo("vercel-labs/skills")
        assertThat(objectSkill.source?.trustTier).isEqualTo("trusted")
    }

    @Test
    fun `filter state matches search text and structured filters`() {
        val skill = ChatbotUserSkill(
            id = "browser",
            name = "browser-qa",
            description = "Runs rendered web checks",
            category = "Testing",
            tags = listOf("browser", "qa"),
            source = ChatbotSkillSource(label = "GitHub"),
        )

        val state = SkillFilterState(
            searchQuery = "rendered",
            category = "Testing",
            source = "GitHub",
            tags = listOf("browser"),
        )

        assertThat(state.matches(skill)).isTrue()
        assertThat(state.copy(category = "Documents").matches(skill)).isFalse()
        assertThat(state.copy(tags = listOf("browser", "mobile"), tagMode = SkillTagMode.All).matches(skill)).isFalse()
        assertThat(state.copy(searchQuery = "github").matches(skill)).isTrue()
    }

    @Test
    fun `filter state maps to API filters`() {
        val filters = SkillFilterState(
            searchQuery = " browser ",
            category = "Testing",
            source = "GitHub",
            tags = listOf("qa", "browser"),
            tagMode = SkillTagMode.All,
        ).toApiFilters()

        assertThat(filters.search).isEqualTo(" browser ")
        assertThat(filters.category).isEqualTo("Testing")
        assertThat(filters.source).isEqualTo("GitHub")
        assertThat(filters.tags).containsExactly("qa", "browser").inOrder()
        assertThat(filters.tagMode).isEqualTo(ChatbotSkillTagMode.All)
    }

    @Test
    fun `local facets are derived from loaded skills`() {
        val facets = deriveSkillFacets(
            listOf(
                ChatbotUserSkill(
                    id = "one",
                    name = "One",
                    category = "Testing",
                    tags = listOf("browser", "qa"),
                    source = ChatbotSkillSource(label = "GitHub"),
                ),
                ChatbotUserSkill(
                    id = "two",
                    name = "Two",
                    category = "Testing",
                    tags = listOf("browser"),
                    source = ChatbotSkillSource(repository = "vercel-labs/skills"),
                ),
            )
        )

        assertThat(facets.categories.map { it.value to it.count }).containsExactly("Testing" to 2)
        assertThat(facets.tags.map { it.value to it.count }).containsExactly("browser" to 2, "qa" to 1).inOrder()
        assertThat(facets.sources.map { it.value to it.count }).containsExactly("GitHub" to 1, "vercel-labs/skills" to 1).inOrder()
        assertThat(ChatbotSkillFacetsResponse().hasAnyFacet()).isFalse()
        assertThat(facets.hasAnyFacet()).isTrue()
    }

    @Test
    fun `discovery metadata is present only when metadata exists`() {
        assertThat(ChatbotUserSkill(id = "empty", name = "empty").hasDiscoveryMetadata()).isFalse()
        assertThat(
            ChatbotUserSkill(
                id = "rich",
                name = "rich",
                category = "Testing",
                tags = listOf("browser"),
                source = ChatbotSkillSource(label = "GitHub"),
            ).hasDiscoveryMetadata()
        ).isTrue()
    }
}

/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.skills.impl.shared

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.chatbot.api.model.skills.ChatbotListPublicSkillsResponse
import io.element.android.libraries.chatbot.api.model.skills.ChatbotSkillFacetsResponse
import io.element.android.libraries.chatbot.api.model.skills.ChatbotSkillListFilters
import io.element.android.libraries.chatbot.api.model.skills.ChatbotSkillNamedFacet
import io.element.android.libraries.chatbot.api.model.skills.ChatbotSkillSource
import io.element.android.libraries.chatbot.api.model.skills.ChatbotSkillTagMode
import io.element.android.libraries.chatbot.api.model.skills.ChatbotUserSkill
import kotlinx.serialization.json.Json
import org.junit.Test

class SkillMetadataModelsTest {
    private val category = ChatbotSkillNamedFacet(id = 11, name = "Testing", slug = "testing")
    private val browserTag = ChatbotSkillNamedFacet(id = 21, name = "browser", slug = "browser")
    private val qaTag = ChatbotSkillNamedFacet(id = 22, name = "qa", slug = "qa")
    private val githubSource = ChatbotSkillSource(id = 31, name = "GitHub", slug = "github", sourceType = "github_repo")

    @Test
    fun `public skill response decodes server taxonomy objects`() {
        val json = Json { ignoreUnknownKeys = true }

        val response = json.decodeFromString<ChatbotListPublicSkillsResponse>(
            """
            {
              "skills": [
                {
                  "id": "browser",
                  "name": "Browser QA",
                  "category": {"id": 11, "name": "Testing", "slug": "testing"},
                  "tags": [
                    {"id": 21, "name": "browser", "slug": "browser"},
                    {"id": 22, "name": "qa", "slug": "qa"}
                  ],
                  "source": {"id": 31, "name": "GitHub", "slug": "github", "sourceType": "github_repo"}
                }
              ],
              "total": 1,
              "page": 1,
              "pageSize": 20
            }
            """.trimIndent()
        )

        val skill = response.skills.single()
        assertThat(skill.category).isEqualTo(category)
        assertThat(skill.tags).containsExactly(browserTag, qaTag).inOrder()
        assertThat(skill.source).isEqualTo(githubSource)
        assertThat(skill.skillCategory()).isEqualTo("Testing")
        assertThat(skill.skillTags()).containsExactly("browser", "qa").inOrder()
        assertThat(skill.skillSourceLabel()).isEqualTo("GitHub")
    }

    @Test
    fun `filter state matches name and slug values from taxonomy objects`() {
        val skill = ChatbotUserSkill(
            id = "browser",
            name = "browser-qa",
            description = "Runs rendered web checks",
            category = category,
            tags = listOf(browserTag, qaTag),
            source = githubSource,
        )

        val state = SkillFilterState(
            searchQuery = "github",
            category = "Testing",
            source = "github",
            tags = listOf("browser"),
        )

        assertThat(state.matches(skill)).isTrue()
        assertThat(state.copy(category = "Documents").matches(skill)).isFalse()
        assertThat(state.copy(tags = listOf("browser", "mobile"), tagMode = SkillTagMode.All).matches(skill)).isFalse()
    }

    @Test
    fun `filter state maps to API filters`() {
        val filters = SkillFilterState(
            searchQuery = " browser ",
            category = "Testing",
            source = "github",
            tags = listOf("qa", "browser"),
            tagMode = SkillTagMode.All,
        ).toApiFilters()

        assertThat(filters).isEqualTo(
            ChatbotSkillListFilters(
                search = " browser ",
                categorySlug = "Testing",
                sourceSlug = "github",
                tagSlugs = listOf("qa", "browser"),
                tagMode = ChatbotSkillTagMode.All,
            )
        )
    }

    @Test
    fun `local facets are derived from loaded taxonomy objects`() {
        val facets = deriveSkillFacets(
            listOf(
                ChatbotUserSkill(
                    id = "one",
                    name = "One",
                    category = category,
                    tags = listOf(browserTag, qaTag),
                    source = githubSource,
                ),
                ChatbotUserSkill(
                    id = "two",
                    name = "Two",
                    category = category,
                    tags = listOf(browserTag),
                    source = ChatbotSkillSource(id = 32, name = "Internal", slug = "internal", sourceType = "marketplace"),
                ),
            )
        )

        assertThat(facets.categories.map { it.value to it.count }).containsExactly("Testing" to 2)
        assertThat(facets.tags.map { it.value to it.count }).containsExactly("browser" to 2, "qa" to 1).inOrder()
        assertThat(facets.sources.map { it.value to it.count }).containsExactly("GitHub" to 1, "Internal" to 1).inOrder()
        assertThat(ChatbotSkillFacetsResponse().hasAnyFacet()).isFalse()
        assertThat(facets.hasAnyFacet()).isTrue()
    }

    @Test
    fun `discovery metadata is present only when taxonomy exists`() {
        assertThat(ChatbotUserSkill(id = "empty", name = "empty").hasDiscoveryMetadata()).isFalse()
        assertThat(
            ChatbotUserSkill(
                id = "rich",
                name = "rich",
                category = category,
                tags = listOf(browserTag),
                source = githubSource,
            ).hasDiscoveryMetadata()
        ).isTrue()
    }
}

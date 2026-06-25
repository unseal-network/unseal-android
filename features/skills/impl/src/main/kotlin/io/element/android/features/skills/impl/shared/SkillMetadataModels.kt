/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.skills.impl.shared

import io.element.android.libraries.chatbot.api.model.skills.ChatbotSkillFacetValue
import io.element.android.libraries.chatbot.api.model.skills.ChatbotSkillFacetsResponse
import io.element.android.libraries.chatbot.api.model.skills.ChatbotSkillListFilters
import io.element.android.libraries.chatbot.api.model.skills.ChatbotSkillSource
import io.element.android.libraries.chatbot.api.model.skills.ChatbotSkillTagMode
import io.element.android.libraries.chatbot.api.model.skills.ChatbotUserSkill
import io.element.android.libraries.chatbot.api.model.skills.toChatbotSkillSource
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

enum class SkillTagMode {
    Any,
    All,
}

sealed interface SkillFilterToken {
    val value: String

    data class Category(override val value: String) : SkillFilterToken
    data class Tag(override val value: String) : SkillFilterToken
    data class Source(override val value: String) : SkillFilterToken
}

data class SkillFilterState(
    val searchQuery: String = "",
    val category: String? = null,
    val source: String? = null,
    val tags: List<String> = emptyList(),
    val tagMode: SkillTagMode = SkillTagMode.Any,
) {
    val activeTokenCount: Int = activeTokens.size

    val activeTokens: List<SkillFilterToken>
        get() = buildList {
            category?.let { add(SkillFilterToken.Category(it)) }
            tags.forEach { add(SkillFilterToken.Tag(it)) }
            source?.let { add(SkillFilterToken.Source(it)) }
        }

    fun matches(skill: ChatbotUserSkill): Boolean {
        if (category != null && !skill.skillCategory().equalsNormalized(category)) return false
        if (source != null && !skill.skillSourceLabel().equalsNormalized(source)) return false
        if (tags.isNotEmpty()) {
            val skillTags = skill.skillTags().map { it.lowercase() }.toSet()
            val selectedTags = tags.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            val tagMatches = if (tagMode == SkillTagMode.All) {
                selectedTags.all(skillTags::contains)
            } else {
                selectedTags.any(skillTags::contains)
            }
            if (!tagMatches) return false
        }

        val query = searchQuery.trim()
        if (query.isEmpty()) return true
        return listOfNotNull(skill.name, skill.description, skill.skillCategory(), skill.skillSourceLabel())
            .plus(skill.skillTags())
            .joinToString(" ")
            .contains(query, ignoreCase = true)
    }

    fun apply(token: SkillFilterToken): SkillFilterState = when (token) {
        is SkillFilterToken.Category -> copy(category = token.value.trim().takeIf { it.isNotEmpty() })
        is SkillFilterToken.Source -> copy(source = token.value.trim().takeIf { it.isNotEmpty() })
        is SkillFilterToken.Tag -> {
            val value = token.value.trim()
            if (value.isEmpty() || tags.any { it.equals(value, ignoreCase = true) }) this else copy(tags = tags + value)
        }
    }

    fun remove(token: SkillFilterToken): SkillFilterState = when (token) {
        is SkillFilterToken.Category -> copy(category = null)
        is SkillFilterToken.Source -> copy(source = null)
        is SkillFilterToken.Tag -> copy(tags = tags.filterNot { it.equals(token.value, ignoreCase = true) })
    }
}

fun ChatbotUserSkill.skillCategory(): String? = category.clean()
    ?: metadataString("category")
    ?: metadataString("skill_category")

fun ChatbotUserSkill.skillTags(): List<String> {
    val sourceTags = tags.takeIf { it.isNotEmpty() }
        ?: metadataStringList("tags") + metadataStringList("skill_tags")
    return sourceTags.normalizedDistinct()
}

fun ChatbotUserSkill.skillSourceLabel(): String? = source?.displayLabel()
    ?: metadataSource()?.displayLabel()
    ?: metadataString("repository")
    ?: metadataString("source_id")
    ?: metadataString("sourceId")
    ?: metadataString("source")

fun ChatbotSkillSource.displayLabel(): String? =
    listOf(label, repository, id, type).firstNotNullOfOrNull { it.clean() }

fun ChatbotUserSkill.hasDiscoveryMetadata(): Boolean {
    return skillCategory() != null ||
        skillTags().isNotEmpty() ||
        skillSourceLabel() != null ||
        source?.repository.clean() != null ||
        source?.path.clean() != null ||
        source?.ref.clean() != null ||
        source?.trustTier.clean() != null ||
        source?.installRef.clean() != null
}

fun SkillFilterState.toApiFilters(): ChatbotSkillListFilters =
    ChatbotSkillListFilters(
        search = searchQuery,
        category = category,
        source = source,
        tags = tags,
        tagMode = if (tagMode == SkillTagMode.All) ChatbotSkillTagMode.All else ChatbotSkillTagMode.Any,
    )

fun ChatbotSkillFacetsResponse.hasAnyFacet(): Boolean = categories.isNotEmpty() || tags.isNotEmpty() || sources.isNotEmpty()

fun deriveSkillFacets(skills: List<ChatbotUserSkill>): ChatbotSkillFacetsResponse =
    ChatbotSkillFacetsResponse(
        categories = skills.countFacetValues { listOfNotNull(it.skillCategory()) },
        tags = skills.countFacetValues { it.skillTags() },
        sources = skills.countFacetValues { listOfNotNull(it.skillSourceLabel()) },
    )

private fun List<ChatbotUserSkill>.countFacetValues(values: (ChatbotUserSkill) -> List<String>): List<ChatbotSkillFacetValue> {
    return flatMap(values)
        .mapNotNull { it.clean() }
        .groupingBy { it }
        .eachCount()
        .map { (value, count) -> ChatbotSkillFacetValue(value = value, count = count) }
        .sortedWith(compareByDescending<ChatbotSkillFacetValue> { it.count }.thenBy { it.value.lowercase() })
}

private fun ChatbotUserSkill.metadataSource(): ChatbotSkillSource? {
    val element = metadata?.get("source") ?: return null
    if (element is JsonPrimitive) return null
    return element.toChatbotSkillSource()
}

private fun ChatbotUserSkill.metadataString(key: String): String? {
    return (metadata?.get(key) as? JsonPrimitive)?.contentOrNull.clean()
}

private fun ChatbotUserSkill.metadataStringList(key: String): List<String> {
    val element = metadata?.get(key) ?: return emptyList()
    return when (element) {
        is JsonArray -> element.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        is JsonPrimitive -> element.contentOrNull.orEmpty().split(',')
        is JsonObject -> emptyList()
    }
}

private fun List<String>.normalizedDistinct(): List<String> {
    val seen = linkedSetOf<String>()
    forEach { value ->
        val clean = value.clean()
        if (clean != null && seen.none { it.equals(clean, ignoreCase = true) }) {
            seen += clean
        }
    }
    return seen.toList()
}

private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

private fun String?.equalsNormalized(value: String): Boolean = clean()?.equals(value.trim(), ignoreCase = true) == true

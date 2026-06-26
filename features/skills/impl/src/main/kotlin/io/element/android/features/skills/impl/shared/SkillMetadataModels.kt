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
import io.element.android.libraries.chatbot.api.model.skills.ChatbotPublicSkillCategory
import io.element.android.libraries.chatbot.api.model.skills.ChatbotPublicSkillTag

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
        if (category != null && skill.skillCategoryValues().none { it.equalsNormalized(category) }) return false
        if (source != null && !skill.skillSourceValues().any { it.equalsNormalized(source) }) return false
        if (tags.isNotEmpty()) {
            val skillTags = skill.skillTagValues().map { it.lowercase() }.toSet()
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
            .plus(skill.skillSourceValues())
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

fun ChatbotUserSkill.skillCategory(): String? = category?.name.clean()

fun ChatbotUserSkill.skillCategoryValues(): List<String> =
    listOfNotNull(category?.name, category?.slug, category?.id?.toString()).normalizedDistinct()

fun ChatbotUserSkill.skillTags(): List<String> {
    return tags.map { it.name }.normalizedDistinct()
}

fun ChatbotUserSkill.skillTagValues(): List<String> =
    tags.flatMap { listOf(it.name, it.slug, it.id.toString()) }.normalizedDistinct()

fun ChatbotUserSkill.skillSourceLabel(): String? = source?.displayLabel()

fun ChatbotSkillSource.displayLabel(): String? =
    listOf(name, repository, slug, id.toString(), sourceType).firstNotNullOfOrNull { it.clean() }

fun ChatbotUserSkill.skillSourceValues(): List<String> =
    listOfNotNull(
        source?.name,
        source?.slug,
        source?.id?.toString(),
        source?.sourceType,
        source?.repository,
        source?.path,
        source?.ref,
        source?.installRef,
    ).normalizedDistinct()

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
        categorySlug = category,
        sourceSlug = source,
        tagSlugs = tags,
        tagMode = if (tagMode == SkillTagMode.All) ChatbotSkillTagMode.All else ChatbotSkillTagMode.Any,
    )

fun ChatbotSkillFacetsResponse.hasAnyFacet(): Boolean = categories.isNotEmpty() || tags.isNotEmpty() || sources.isNotEmpty()

fun deriveSkillFacets(skills: List<ChatbotUserSkill>): ChatbotSkillFacetsResponse =
    ChatbotSkillFacetsResponse(
        categories = skills.countFacetValues { skill ->
            skill.category?.let { listOf(FacetEntry(value = it.name, slug = it.slug)) } ?: emptyList()
        },
        tags = skills.countFacetValues { skill ->
            skill.tags.map { FacetEntry(value = it.name, slug = it.slug) }
        },
        sources = skills.countFacetValues { skill ->
            skill.source?.displayLabel()?.let { label -> listOf(FacetEntry(value = label, slug = skill.source?.slug)) } ?: emptyList()
        },
    )

fun publicTaxonomyFacets(
    categories: List<ChatbotPublicSkillCategory>,
    tags: List<ChatbotPublicSkillTag>,
    sourceFallback: List<ChatbotSkillFacetValue>,
): ChatbotSkillFacetsResponse =
    ChatbotSkillFacetsResponse(
        categories = categories.sortedWith(compareBy<ChatbotPublicSkillCategory> { it.sortOrder }.thenBy { it.name.lowercase() })
            .map { ChatbotSkillFacetValue(value = it.name, count = 0, slug = it.slug) },
        tags = tags.sortedWith(compareBy<ChatbotPublicSkillTag> { it.sortOrder }.thenBy { it.name.lowercase() })
            .map { ChatbotSkillFacetValue(value = it.name, count = 0, slug = it.slug) },
        sources = sourceFallback,
    )

private data class FacetEntry(val value: String, val slug: String?)

private fun List<ChatbotUserSkill>.countFacetValues(values: (ChatbotUserSkill) -> List<FacetEntry>): List<ChatbotSkillFacetValue> {
    return flatMap(values)
        .mapNotNull { entry -> entry.value.clean()?.let { clean -> FacetEntry(value = clean, slug = entry.slug.clean()) } }
        .groupingBy { it.slug ?: it.value.lowercase() }
        .eachCount()
        .map { (key, count) ->
            val entry = firstNotNullOf { skill ->
                values(skill).firstOrNull { (it.slug ?: it.value.lowercase()) == key }
            }
            ChatbotSkillFacetValue(value = entry.value, count = count, slug = entry.slug)
        }
        .sortedWith(compareByDescending<ChatbotSkillFacetValue> { it.count }.thenBy { it.value.lowercase() })
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

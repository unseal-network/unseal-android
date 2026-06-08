/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.skills.impl.shared

import io.element.android.libraries.chatbot.api.model.skills.ChatbotSkillVisibility
import io.element.android.libraries.chatbot.api.model.skills.ChatbotUserSkill
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun ChatbotUserSkill.matchesSkillQuery(query: String): Boolean {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return true
    return name.contains(trimmed, ignoreCase = true) ||
        description.orEmpty().contains(trimmed, ignoreCase = true)
}

fun ChatbotSkillVisibility.displayName(): String = when (this) {
    ChatbotSkillVisibility.Private -> "Private"
    ChatbotSkillVisibility.Public -> "Public"
    ChatbotSkillVisibility.Shared -> "Shared"
}

fun ChatbotSkillVisibility.apiValue(): String = when (this) {
    ChatbotSkillVisibility.Private -> "private"
    ChatbotSkillVisibility.Public -> "public"
    ChatbotSkillVisibility.Shared -> "shared"
}

fun ChatbotUserSkill.createdDateLabel(): String? {
    val raw = createdAt ?: return null
    val instant = runCatching { Instant.parse(raw) }
        .recoverCatching { Instant.ofEpochSecond(raw.toLong()) }
        .getOrNull() ?: return null
    return SkillDateFormatter.format(instant)
}

fun List<ChatbotUserSkill>.sortedBySkillName(): List<ChatbotUserSkill> = sortedBy { it.name }

private object SkillDateFormatter {
    private val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
        .withZone(ZoneId.systemDefault())

    fun format(instant: Instant): String = formatter.format(instant)
}

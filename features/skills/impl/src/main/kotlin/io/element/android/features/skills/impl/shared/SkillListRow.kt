/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.skills.impl.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.chatbot.api.model.skills.ChatbotSkillVisibility
import io.element.android.libraries.chatbot.api.model.skills.ChatbotUserSkill
import io.element.android.libraries.designsystem.components.management.ManagementListRow

@Composable
fun SkillListRow(
    skill: ChatbotUserSkill,
    showVisibility: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ManagementListRow(
        modifier = modifier
            .padding(vertical = 4.dp),
        title = skill.name,
        description = skill.description,
        meta = skill.createdDateLabel()?.let { "创建时间：$it" },
        onClick = onClick,
        leadingContent = {
            SkillIconTile()
        },
        titleTrailingContent = {
            if (showVisibility) {
                skill.visibility?.let { SkillVisibilityBadge(it) }
            }
        },
        trailingContent = {
            Icon(
                imageVector = CompoundIcons.ChevronRight(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        },
    )
}

@Composable
fun SkillIconTile(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(48.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = CompoundIcons.ListBulleted(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
fun SkillVisibilityBadge(visibility: ChatbotSkillVisibility) {
    val (bg, fg) = when (visibility) {
        ChatbotSkillVisibility.Public -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        ChatbotSkillVisibility.Shared -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        ChatbotSkillVisibility.Private -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        modifier = Modifier
            .background(bg, RoundedCornerShape(percent = 50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        text = visibility.displayName(),
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

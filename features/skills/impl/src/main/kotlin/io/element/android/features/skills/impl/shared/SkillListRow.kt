/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.skills.impl.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.chatbot.api.model.skills.ChatbotSkillVisibility
import io.element.android.libraries.chatbot.api.model.skills.ChatbotUserSkill

@Composable
fun SkillListRow(
    skill: ChatbotUserSkill,
    showVisibility: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFilterSelected: ((SkillFilterToken) -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkillIconTile()
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        modifier = Modifier.weight(1f, fill = false),
                        text = skill.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (showVisibility) {
                        skill.visibility?.let { SkillVisibilityBadge(it) }
                    }
                }
                skill.description?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                SkillMetadataChips(skill = skill, onFilterSelected = onFilterSelected)
                skill.createdDateLabel()?.let {
                    Text(
                        text = "创建时间：$it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                imageVector = CompoundIcons.ChevronRight(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
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

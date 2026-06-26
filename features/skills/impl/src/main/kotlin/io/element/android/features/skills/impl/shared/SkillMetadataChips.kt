/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.skills.impl.shared

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.chatbot.api.model.skills.ChatbotUserSkill

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SkillMetadataChips(
    skill: ChatbotUserSkill,
    modifier: Modifier = Modifier,
    maxTags: Int = 2,
    onFilterSelected: ((SkillFilterToken) -> Unit)? = null,
) {
    val category = skill.skillCategory()
    val tags = skill.skillTags()
    val source = skill.skillSourceLabel()
    if (category == null && tags.isEmpty() && source == null) return
    var isExpanded by remember(skill.id, tags) { mutableStateOf(false) }
    val visibleTags = if (isExpanded) tags else tags.take(maxTags)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (category != null || source != null) {
            MetadataLine(
                category = category,
                source = source,
                onClick = category?.let { { onFilterSelected?.invoke(SkillFilterToken.Category(it)) } }
                    ?: source?.let { { onFilterSelected?.invoke(SkillFilterToken.Source(it)) } },
            )
        }

        if (tags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                visibleTags.forEach { tag ->
                    TagLabel(
                        label = "#$tag",
                        onClick = { onFilterSelected?.invoke(SkillFilterToken.Tag(tag)) },
                    )
                }
                if (!isExpanded && tags.size > maxTags) {
                    TagLabel(
                        label = "+${tags.size - maxTags}",
                        isMore = true,
                        onClick = { isExpanded = true },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SkillFilterTokensRow(
    filterState: SkillFilterState,
    onRemove: (SkillFilterToken) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (filterState.activeTokens.isEmpty()) return
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        filterState.activeTokens.forEach { token ->
            InputChip(
                selected = false,
                onClick = { onRemove(token) },
                label = {
                    Text(
                        text = token.label(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                trailingIcon = {
                    Icon(
                        imageVector = CompoundIcons.Close(),
                        contentDescription = "移除筛选",
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
        }
        TextButton(onClick = onClear) {
            Text("清除筛选")
        }
    }
}

@Composable
private fun MetadataLine(
    category: String?,
    source: String?,
    onClick: (() -> Unit)?,
) {
    val label = listOfNotNull(category, source).joinToString(" · ")
    Text(
        modifier = Modifier
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 1.dp),
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun TagLabel(
    label: String,
    isMore: Boolean = false,
    onClick: (() -> Unit)?,
) {
    Text(
        modifier = Modifier
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 1.dp),
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = if (isMore) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = if (isMore) FontWeight.SemiBold else FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun SkillFilterToken.label(): String = when (this) {
    is SkillFilterToken.Category -> "分类: $value"
    is SkillFilterToken.Tag -> "标签: $value"
    is SkillFilterToken.Source -> "来源: $value"
}

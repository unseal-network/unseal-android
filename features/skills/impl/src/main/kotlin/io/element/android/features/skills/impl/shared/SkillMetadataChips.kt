/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.skills.impl.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        category?.let {
            MetadataChip(label = it, onClick = { onFilterSelected?.invoke(SkillFilterToken.Category(it)) })
        }
        tags.take(maxTags).forEach { tag ->
            MetadataChip(label = "#$tag", onClick = { onFilterSelected?.invoke(SkillFilterToken.Tag(tag)) })
        }
        if (tags.size > maxTags) {
            MetadataChip(label = "+${tags.size - maxTags}", onClick = null)
        }
        source?.let {
            MetadataChip(label = it, onClick = { onFilterSelected?.invoke(SkillFilterToken.Source(it)) })
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
private fun MetadataChip(
    label: String,
    onClick: (() -> Unit)?,
) {
    AssistChip(
        onClick = { onClick?.invoke() },
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

private fun SkillFilterToken.label(): String = when (this) {
    is SkillFilterToken.Category -> "分类: $value"
    is SkillFilterToken.Tag -> "标签: $value"
    is SkillFilterToken.Source -> "来源: $value"
}

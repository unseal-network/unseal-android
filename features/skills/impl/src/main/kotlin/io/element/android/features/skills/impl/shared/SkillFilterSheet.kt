/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.skills.impl.shared

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.element.android.libraries.chatbot.api.model.skills.ChatbotSkillFacetValue
import io.element.android.libraries.chatbot.api.model.skills.ChatbotSkillFacetsResponse

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillFilterSheet(
    facets: ChatbotSkillFacetsResponse,
    filterState: SkillFilterState,
    onApplyToken: (SkillFilterToken) -> Unit,
    onTagModeChanged: (SkillTagMode) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedField by remember { mutableStateOf<SkillFilterField?>(null) }
    var query by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            val field = selectedField
            if (field == null) {
                Text("添加筛选", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                SkillFilterField.entries.forEach { candidate ->
                    ListItem(
                        headlineContent = { Text(candidate.label) },
                        supportingContent = { Text("${candidate.values(facets).size} 个选项") },
                        modifier = Modifier.clickable(enabled = candidate.values(facets).isNotEmpty()) {
                            selectedField = candidate
                            query = ""
                        },
                    )
                }
            } else {
                TextButton(onClick = { selectedField = null; query = "" }) {
                    Text("返回")
                }
                Text(field.label, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("搜索${field.label}") },
                    singleLine = true,
                )
                if (field == SkillFilterField.Tag) {
                    Row(modifier = Modifier.padding(top = 8.dp)) {
                        FilterChip(
                            selected = filterState.tagMode == SkillTagMode.Any,
                            onClick = { onTagModeChanged(SkillTagMode.Any) },
                            label = { Text("任一标签") },
                        )
                        Spacer(Modifier.width(8.dp))
                        FilterChip(
                            selected = filterState.tagMode == SkillTagMode.All,
                            onClick = { onTagModeChanged(SkillTagMode.All) },
                            label = { Text("全部标签") },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                field.values(facets)
                    .filter { query.isBlank() || it.value.contains(query.trim(), ignoreCase = true) }
                    .forEach { facet ->
                        ListItem(
                            headlineContent = { Text(facet.value) },
                            supportingContent = { Text("${facet.count} 个技能") },
                            modifier = Modifier.clickable {
                                onApplyToken(field.token(facet.filterValue()))
                                if (field != SkillFilterField.Tag) {
                                    onDismiss()
                                }
                            },
                        )
                        HorizontalDivider()
                    }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private enum class SkillFilterField(val label: String) {
    Category("分类"),
    Tag("标签"),
    Source("来源");

    fun values(facets: ChatbotSkillFacetsResponse): List<ChatbotSkillFacetValue> = when (this) {
        Category -> facets.categories
        Tag -> facets.tags
        Source -> facets.sources
    }

    fun token(value: String): SkillFilterToken = when (this) {
        Category -> SkillFilterToken.Category(value)
        Tag -> SkillFilterToken.Tag(value)
        Source -> SkillFilterToken.Source(value)
    }
}

private fun ChatbotSkillFacetValue.filterValue(): String = slug ?: value

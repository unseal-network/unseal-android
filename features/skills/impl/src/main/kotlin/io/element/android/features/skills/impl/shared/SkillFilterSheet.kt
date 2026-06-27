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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.element.android.features.skills.impl.R
import io.element.android.libraries.chatbot.api.model.skills.ChatbotSkillFacetValue
import io.element.android.libraries.chatbot.api.model.skills.ChatbotSkillFacetsResponse
import io.element.android.libraries.ui.strings.CommonStrings

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
                Text(stringResource(R.string.skills_filter_add), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                SkillFilterField.entries.forEach { candidate ->
                    ListItem(
                        headlineContent = { Text(candidate.label()) },
                        supportingContent = { Text(stringResource(R.string.skills_filter_options_count, candidate.values(facets).size)) },
                        modifier = Modifier.clickable(enabled = candidate.values(facets).isNotEmpty()) {
                            selectedField = candidate
                            query = ""
                        },
                    )
                }
            } else {
                TextButton(onClick = { selectedField = null; query = "" }) {
                    Text(stringResource(CommonStrings.action_go_back))
                }
                Text(field.label(), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.skills_filter_search_field, field.label())) },
                    singleLine = true,
                )
                if (field == SkillFilterField.Tag) {
                    Row(modifier = Modifier.padding(top = 8.dp)) {
                        FilterChip(
                            selected = filterState.tagMode == SkillTagMode.Any,
                            onClick = { onTagModeChanged(SkillTagMode.Any) },
                            label = { Text(stringResource(R.string.skills_filter_any_tag)) },
                        )
                        Spacer(Modifier.width(8.dp))
                        FilterChip(
                            selected = filterState.tagMode == SkillTagMode.All,
                            onClick = { onTagModeChanged(SkillTagMode.All) },
                            label = { Text(stringResource(R.string.skills_filter_all_tags)) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                field.values(facets)
                    .filter { query.isBlank() || it.value.contains(query.trim(), ignoreCase = true) }
                    .forEach { facet ->
                        ListItem(
                            headlineContent = { Text(facet.value) },
                            supportingContent = { Text(stringResource(R.string.skills_filter_skills_count, facet.count)) },
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

private enum class SkillFilterField {
    Category,
    Tag,
    Source;

    @Composable
    fun label(): String = when (this) {
        Category -> stringResource(R.string.skills_filter_category)
        Tag -> stringResource(R.string.skills_filter_tag)
        Source -> stringResource(R.string.skills_filter_source)
    }

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

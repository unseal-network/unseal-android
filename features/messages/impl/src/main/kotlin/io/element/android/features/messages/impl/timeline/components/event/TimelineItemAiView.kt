/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.element.android.features.messages.impl.timeline.model.event.AiSource
import io.element.android.features.messages.impl.timeline.model.event.AiThinkingStep
import io.element.android.features.messages.impl.timeline.model.event.AiToolCall
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemAiContent

/**
 * Native (degraded) renderer for [TimelineItemAiContent]. Composes the AI stream sub-parts that
 * iOS renders with plain SwiftUI (thinking process, tool calls, sources). The markdown body is
 * rendered as plain text — rich markdown rendering depends on external component libraries that
 * are out of scope for this first version. Quick actions are shown as static chips (the iOS
 * "send action" wiring is not migrated).
 */
@Composable
fun TimelineItemAiView(
    content: TimelineItemAiContent,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (content.thinkingSteps.isNotEmpty()) {
            ThinkingSection(content.thinkingSteps)
        }
        content.toolCalls.forEach { ToolCallCard(it) }
        if (content.body.isNotBlank()) {
            Text(
                text = content.body,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (content.isStreaming) {
            Text(
                text = "…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (content.sources.isNotEmpty()) {
            SourcesSection(content.sources)
        }
        if (content.quickActions.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                content.quickActions.forEach { action ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            text = action.label,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThinkingSection(steps: List<AiThinkingStep>) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = (if (expanded) "▾ " else "▸ ") + "Thinking (${steps.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
            )
            if (expanded) {
                steps.forEach { step ->
                    Column {
                        Text(text = step.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        if (step.description.isNotEmpty()) {
                            Text(
                                text = step.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolCallCard(toolCall: AiToolCall) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "🔧 ${toolCall.displayName} · ${toolCall.state}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            toolCall.output?.takeIf { it.isNotBlank() }?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
            toolCall.error?.takeIf { it.isNotBlank() }?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun SourcesSection(sources: List<AiSource>) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HorizontalDivider()
        Text(text = "Sources", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        sources.forEachIndexed { index, source ->
            Column {
                Text(text = "${index + 1}. ${source.title}", style = MaterialTheme.typography.bodyMedium)
                source.url?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

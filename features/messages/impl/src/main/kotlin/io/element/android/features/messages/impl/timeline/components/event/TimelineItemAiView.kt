/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.messages.impl.timeline.model.event.AiCustomStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiDataStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiErrorStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiFileStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiReasoningStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiSource
import io.element.android.features.messages.impl.timeline.model.event.AiSourceStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiThinkingStep
import io.element.android.features.messages.impl.timeline.model.event.AiTextStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiToolCall
import io.element.android.features.messages.impl.timeline.model.event.AiToolStreamPart
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemAiContent
import io.element.android.libraries.androidutils.text.LinkifyHelper
import io.element.android.libraries.textcomposer.ElementRichTextEditorStyle
import io.element.android.wysiwyg.compose.EditorStyledText
import io.element.android.wysiwyg.link.Link
import org.json.JSONArray
import org.json.JSONObject

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
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (content.parts.isNotEmpty()) {
            AiStreamPartsView(
                parts = content.parts,
                isStreaming = content.isStreaming,
                onLinkClick = onLinkClick,
                onLinkLongClick = onLinkLongClick,
            )
        } else {
            if (content.thinkingSteps.isNotEmpty()) {
                ThinkingSection(content.thinkingSteps)
            }
            content.toolCalls.forEach { ToolCallCard(it) }
            if (content.body.isNotBlank()) {
                LinkifiedAiText(
                    text = content.body,
                    onLinkClick = onLinkClick,
                    onLinkLongClick = onLinkLongClick,
                )
            }
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
private fun AiStreamPartsView(
    parts: List<AiStreamPart>,
    isStreaming: Boolean,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
) {
    val (toolParts, nonToolParts) = remember(parts) {
        val registeredToolParts = parts.filterIsInstance<AiToolStreamPart>()
            .filter { it.toolName.isRegisteredToolName }
        registeredToolParts.flatMap { it.toRenderableToolParts() } to parts.filterNot { part ->
            part is AiToolStreamPart && part.toolName.isRegisteredToolName
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (toolParts.isNotEmpty()) {
            ToolCallRootCard(
                parts = toolParts,
                isStreaming = isStreaming,
                onLinkClick = onLinkClick,
                onLinkLongClick = onLinkLongClick,
            )
        }
        nonToolParts.forEach { part ->
            when (part) {
                is AiTextStreamPart -> TextPart(
                    part = part,
                    onLinkClick = onLinkClick,
                    onLinkLongClick = onLinkLongClick,
                )
                is AiReasoningStreamPart -> ReasoningPart(part)
                is AiToolStreamPart -> GenericToolPart(
                    part = part,
                    onLinkClick = onLinkClick,
                    onLinkLongClick = onLinkLongClick,
                )
                is AiSourceStreamPart -> SourcePart(
                    part = part,
                    onLinkClick = onLinkClick,
                    onLinkLongClick = onLinkLongClick,
                )
                is AiFileStreamPart -> FilePart(
                    part = part,
                    onLinkClick = onLinkClick,
                    onLinkLongClick = onLinkLongClick,
                )
                is AiErrorStreamPart -> ErrorPart(part)
                is AiDataStreamPart -> DataPart(
                    part = part,
                    onLinkClick = onLinkClick,
                    onLinkLongClick = onLinkLongClick,
                    toolCardInserted = toolParts.isNotEmpty(),
                )
                is AiCustomStreamPart -> Unit
            }
        }
    }
}

@Composable
private fun GenericToolPart(
    part: AiToolStreamPart,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolStateDot(part.state)
                Text(
                    text = part.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = toolStateLabel(part.state),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            part.errorText?.takeIf { it.isNotBlank() }?.let { errorText ->
                Text(
                    text = errorText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            GenericToolPayloadSection("Output", part.output, onLinkClick, onLinkLongClick)
            GenericToolPayloadSection("Input", part.input, onLinkClick, onLinkLongClick)
            if (part.rawInput != part.input) {
                GenericToolPayloadSection("Raw input", part.rawInput, onLinkClick, onLinkLongClick)
            }
            if (part.output.isNullOrBlank() && part.input.isNullOrBlank() && part.rawInput.isNullOrBlank() && part.errorText.isNullOrBlank()) {
                Text(
                    text = if (part.isDone) "Completed" else "Waiting for tool output.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun GenericToolPayloadSection(
    label: String,
    payload: String?,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
) {
    if (payload.isNullOrBlank()) return
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinkifiedAiText(
                text = payload.take(MAX_VALUE_CHARS),
                onLinkClick = onLinkClick,
                onLinkLongClick = onLinkLongClick,
            )
        }
    }
}

@Composable
private fun TextPart(
    part: AiTextStreamPart,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
) {
    if (part.text.isNotBlank()) {
        LinkifiedAiText(
            text = part.text,
            onLinkClick = onLinkClick,
            onLinkLongClick = onLinkLongClick,
        )
    }
}

@Composable
private fun ReasoningPart(part: AiReasoningStreamPart) {
    if (part.text.isBlank()) return
    val isStreaming = part.state == "streaming"
    var expanded by remember(part.id) { mutableStateOf(isStreaming) }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = (if (expanded) "▾ " else "▸ ") + if (isStreaming) "Thinking..." else "Thought",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isStreaming) { expanded = !expanded },
            )
            if (expanded) {
                Text(
                    text = part.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ToolCallRootCard(
    parts: List<AiToolStreamPart>,
    isStreaming: Boolean,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
) {
    if (parts.isEmpty()) return
    var selectedIndex by remember(parts.joinToString(separator = "|") { it.id }) { mutableStateOf(parts.lastIndex) }
    var expanded by remember(parts.first().id) { mutableStateOf(true) }
    if (selectedIndex !in parts.indices) selectedIndex = parts.lastIndex
    val selectedPart = parts[selectedIndex]
    val doneCount = parts.count { it.isDone }
    val errorCount = parts.count { it.isError }
    val callingCount = if (isStreaming) parts.size - doneCount - errorCount else 0
    val allFinished = callingCount == 0

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ToolProgressIndicator(
                    total = parts.size,
                    doneCount = doneCount,
                    errorCount = errorCount,
                    isCalling = callingCount > 0,
                )
                Text(
                    text = if (parts.size == 1) selectedPart.displayName else "Tool Calls",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (doneCount > 0) {
                    CountPill(label = "✓", count = doneCount, color = Color(0xFF2E7D32))
                }
                if (errorCount > 0) {
                    CountPill(label = "!", count = errorCount, color = MaterialTheme.colorScheme.error)
                }
                Text(
                    text = if (expanded) "⌄" else "›",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expanded) {
                if (parts.size > 1) {
                    ToolSelectionTabs(
                        parts = parts,
                        selectedIndex = selectedIndex,
                        onSelected = { selectedIndex = it },
                    )
                }
                ToolPartContent(
                    part = selectedPart,
                    showTitle = parts.size == 1,
                    isStreaming = isStreaming,
                    allFinished = allFinished,
                    onLinkClick = onLinkClick,
                    onLinkLongClick = onLinkLongClick,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun ToolSelectionTabs(
    parts: List<AiToolStreamPart>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        parts.forEachIndexed { index, part ->
            val isSelected = index == selectedIndex
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                modifier = Modifier.clickable { onSelected(index) },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ToolStateDot(part.state)
                    Text(
                        text = part.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolPartContent(
    part: AiToolStreamPart,
    showTitle: Boolean,
    isStreaming: Boolean,
    allFinished: Boolean,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showTitle) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolStateDot(part.state)
                Text(
                    text = part.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = toolStateLabel(part.state),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Text(
                text = toolStateLabel(part.state),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            part.isCalling && isStreaming -> ToolCallingContent(part, allFinished, onLinkClick, onLinkLongClick)
            part.isError -> ToolErrorContent(part)
            part.output?.isNotBlank() == true -> ToolPayloadCard(part, payload = part.output, onLinkClick, onLinkLongClick)
            part.input?.isNotBlank() == true -> ToolPayloadCard(part, payload = part.input, onLinkClick, onLinkLongClick)
            else -> Text(
                text = if (part.isDone) "Completed" else "Waiting for tool output.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ToolCallingContent(
    part: AiToolStreamPart,
    allFinished: Boolean,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
) {
    if (part.input?.isNotBlank() == true) {
        ToolPayloadCard(part, payload = part.input, onLinkClick, onLinkLongClick)
    } else {
        Text(
            text = if (allFinished) "Waiting for tool output." else "Running tool…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ToolErrorContent(part: AiToolStreamPart) {
    Text(
        text = part.errorText?.takeIf { it.isNotBlank() } ?: "Tool call failed.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun ToolPayloadCard(
    part: AiToolStreamPart,
    payload: String?,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
) {
    val model = remember(part.toolName, payload) {
        payload.toToolCardModel(part.toolName)
    } ?: return
    if (model.items.isEmpty()) return
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 260.dp),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            model.items.take(MAX_RENDERED_ITEMS).forEach { item ->
                ToolCardItem(item, onLinkClick, onLinkLongClick)
            }
            if (model.moreCount > 0) {
                Text(
                    text = "+${model.moreCount} more",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ToolCardItem(
    item: ToolRenderableItem,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        LinkifiedAiText(
            text = item.title,
            onLinkClick = onLinkClick,
            onLinkLongClick = onLinkLongClick,
        )
        item.subtitle?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item.url?.takeIf { it.isNotBlank() }?.let {
            LinkifiedAiText(
                text = it,
                onLinkClick = onLinkClick,
                onLinkLongClick = onLinkLongClick,
            )
        }
    }
}

@Composable
private fun DataPart(
    part: AiDataStreamPart,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
    toolCardInserted: Boolean,
) {
    when (part.type) {
        "data-error" -> ErrorPart(AiErrorStreamPart(id = part.id, state = part.state, errorText = part.payload.errorTextFromJson() ?: "Stream error"))
        "data-error-card" -> ErrorCard(part.payload)
        "data-tool-call-suspended" -> SuspendedToolCard(part.payload)
        "data-ui-spec", "data-json-render", "data-spec" -> {
            if (!toolCardInserted) {
                part.payload.toToolCardModel(part.type)?.let { model ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            model.items.take(MAX_RENDERED_ITEMS).forEach { item ->
                                ToolCardItem(item, onLinkClick, onLinkLongClick)
                            }
                        }
                    }
                }
            }
        }
        else -> Unit
    }
}

@Composable
private fun ErrorCard(payload: String) {
    val json = payload.jsonObjectOrNull()
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = json?.optString("title")?.takeIf { it.isNotBlank() } ?: "Something went wrong",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = json?.optString("message")?.takeIf { it.isNotBlank() } ?: payload.errorTextFromJson().orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun SuspendedToolCard(payload: String) {
    val json = payload.jsonObjectOrNull()
    val action = json?.optString("action")?.takeIf { it.isNotBlank() }
    val title = when (action) {
        "chooseRequest" -> json.optString("title").takeIf { it.isNotBlank() } ?: "Choose an option"
        "requestVaultAuthorization" -> "Authorization required"
        "moltbookRegister" -> "Moltbook registration"
        "deleteSchedule" -> "Delete schedule"
        "setSandboxMode" -> "Sandbox mode"
        else -> "Action required"
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            json?.optString("reason")?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            json?.optJSONArray("list")?.let { list ->
                (0 until list.length()).asSequence()
                    .mapNotNull { list.optJSONObject(it) }
                    .take(4)
                    .forEach { item ->
                        Text(
                            text = item.optString("label").takeIf { it.isNotBlank() } ?: item.optString("id"),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
            }
        }
    }
}

private fun String?.toToolCardModel(toolName: String): ToolCardModel? {
    val payload = this?.trim().orEmpty()
    if (payload.isBlank()) return null
    val normalizedToolName = toolName.removePrefix("tool-")
    if (META_TOOL_NAMES.contains(normalizedToolName)) return null
    val cardType = TOOL_CARD_REGISTRY[normalizedToolName] ?: when {
        normalizedToolName.startsWith("agent-") -> "subAgent"
        normalizedToolName == "data-spec" || normalizedToolName == "data-ui-spec" || normalizedToolName == "data-json-render" -> "jsonSpec"
        else -> return null
    }
    return runCatching {
        when {
            payload.startsWith("[") -> JSONArray(payload).toToolCardModel(cardType)
            payload.startsWith("{") -> JSONObject(payload).toToolCardModel(cardType)
            else -> ToolCardModel(listOf(ToolRenderableItem(title = payload.take(MAX_VALUE_CHARS))), moreCount = 0)
        }
    }.getOrNull()
}

private fun JSONObject.toToolCardModel(cardType: String): ToolCardModel {
    val source = firstArrayOrSelf()
    return source.toToolCardModel(cardType)
}

private fun JSONArray.toToolCardModel(cardType: String): ToolCardModel {
    val items = (0 until length())
        .asSequence()
        .mapNotNull { index -> optJSONObject(index)?.toRenderableItem(cardType) }
        .toList()
    if (items.isNotEmpty()) {
        return ToolCardModel(items = items, moreCount = (length() - items.size).coerceAtLeast(0))
    }
    return ToolCardModel(
        items = (0 until length())
            .asSequence()
            .mapNotNull { index -> optString(index).takeIf { it.isNotBlank() } }
            .take(MAX_RENDERED_ITEMS)
            .map { ToolRenderableItem(title = it.take(MAX_VALUE_CHARS)) }
            .toList(),
        moreCount = (length() - MAX_RENDERED_ITEMS).coerceAtLeast(0),
    )
}

private fun JSONObject.firstArrayOrSelf(): JSONArray {
    KNOWN_LIST_KEYS.forEach { key ->
        optJSONArray(key)?.let { return it }
    }
    val data = opt("data")
    when (data) {
        is JSONArray -> return data
        is JSONObject -> return data.firstArrayOrSelf()
    }
    return JSONArray().put(this)
}

private fun AiToolStreamPart.toRenderableToolParts(): List<AiToolStreamPart> {
    val normalizedToolName = toolName.removePrefix("tool-")
    return when {
        IGNORED_TOOL_NAMES.contains(normalizedToolName) -> emptyList()
        META_TOOL_NAMES.contains(normalizedToolName) -> expandMultiExecute()
        normalizedToolName.startsWith("agent-") -> expandSubAgent()
        TOOL_CARD_REGISTRY.containsKey(normalizedToolName) -> listOf(this)
        else -> emptyList()
    }
}

private fun AiToolStreamPart.expandMultiExecute(): List<AiToolStreamPart> {
    val outputJson = output?.jsonObjectOrNull()
    val results = outputJson
        ?.optJSONObject("data")
        ?.optJSONArray("results")
        ?: outputJson?.optJSONArray("results")
    if ((isDone || isError) && results != null) {
        return results.toSyntheticToolParts(idPrefix = id, fallbackState = state)
    }

    val tools = input
        ?.jsonObjectOrNull()
        ?.optJSONArray("tools")
        ?: return emptyList()
    val seen = mutableSetOf<String>()
    return (0 until tools.length())
        .asSequence()
        .mapNotNull { index -> tools.optJSONObject(index)?.firstString(listOf("tool_slug", "toolSlug", "toolName", "tool_name")) }
        .filter { TOOL_CARD_REGISTRY.containsKey(it) && seen.add(it) }
        .map { slug ->
            copy(
                id = "${id}_$slug",
                toolName = slug,
                title = slug.toDisplayLabel(),
                input = null,
                output = null,
                errorText = null,
            )
        }
        .toList()
}

private fun AiToolStreamPart.expandSubAgent(): List<AiToolStreamPart> {
    if (!isDone && !isError) {
        return listOf(
            copy(
                title = toolName
                    .removePrefix("tool-")
                    .removePrefix("agent-")
                    .removeSuffix("Agent")
                    .toDisplayLabel()
                    .ifBlank { "Agent" },
                input = null,
                output = null,
                errorText = null,
            )
        )
    }
    val toolResults = output
        ?.jsonObjectOrNull()
        ?.optJSONArray("subAgentToolResults")
        ?: return emptyList()
    return (0 until toolResults.length())
        .asSequence()
        .mapNotNull { toolResults.optJSONObject(it) }
        .flatMap { result ->
            val resultToolName = result.firstString(listOf("toolName", "tool_name", "tool_slug", "toolSlug")) ?: return@flatMap emptySequence()
            val normalizedResultToolName = resultToolName.removePrefix("tool-")
            if (IGNORED_TOOL_NAMES.contains(normalizedResultToolName)) return@flatMap emptySequence()
            val resultPayload = result.optJSONObject("result")?.toString()
                ?: result.opt("result")?.toString()
                ?: result.toString()
            if (META_TOOL_NAMES.contains(normalizedResultToolName)) {
                copy(
                    id = "${id}_$normalizedResultToolName",
                    toolName = normalizedResultToolName,
                    title = normalizedResultToolName.toDisplayLabel(),
                    input = null,
                    output = resultPayload,
                    errorText = null,
                ).expandMultiExecute().asSequence()
            } else if (TOOL_CARD_REGISTRY.containsKey(normalizedResultToolName)) {
                sequenceOf(
                    copy(
                        id = "${id}_$normalizedResultToolName",
                        toolName = normalizedResultToolName,
                        title = normalizedResultToolName.toDisplayLabel(),
                        input = result.opt("args")?.toString(),
                        output = resultPayload,
                        errorText = null,
                    )
                )
            } else {
                emptySequence()
            }
        }
        .toList()
}

private fun JSONArray.toSyntheticToolParts(
    idPrefix: String,
    fallbackState: String,
): List<AiToolStreamPart> {
    return (0 until length())
        .asSequence()
        .mapNotNull { index -> optJSONObject(index) }
        .mapNotNull { result ->
            val slug = result.firstString(listOf("tool_slug", "toolSlug", "toolName", "tool_name", "name")) ?: return@mapNotNull null
            val normalizedSlug = slug.removePrefix("tool-")
            if (!TOOL_CARD_REGISTRY.containsKey(normalizedSlug)) return@mapNotNull null
            val successful = when (val value = result.opt("successful")) {
                is Boolean -> value
                else -> true
            }
            AiToolStreamPart(
                id = "${idPrefix}_$normalizedSlug",
                state = if (successful) "output-available" else "output-error",
                toolName = normalizedSlug,
                title = normalizedSlug.toDisplayLabel(),
                input = null,
                output = result.toString(),
                errorText = result.firstString(listOf("error", "message")).takeIf { !successful } ?: if (fallbackState == "output-error") "Tool call failed." else null,
            )
        }
        .toList()
}

private fun JSONObject.toRenderableItem(cardType: String): ToolRenderableItem {
    val titleKeys = when (cardType) {
        "composeEmail" -> listOf("subject", "title", "snippet", "from", "to")
        "fileAttachment" -> listOf("name", "filename", "title", "mimeType")
        "githubIssue", "githubIssuesList", "linearIssue", "linearIssuesList" -> listOf("title", "name", "number", "id")
        "finance" -> listOf("symbol", "ticker", "name", "title")
        "imageGrid" -> listOf("title", "alt", "description", "url")
        else -> listOf("title", "name", "subject", "headline", "query", "url", "text", "message")
    }
    val subtitleKeys = when (cardType) {
        "composeEmail" -> listOf("from", "to", "date", "snippet", "body")
        "finance" -> listOf("price", "change", "currency", "marketCap")
        "githubIssue", "githubIssuesList", "linearIssue", "linearIssuesList" -> listOf("state", "status", "repository", "assignee", "body")
        else -> listOf("description", "snippet", "summary", "content", "body", "status")
    }
    return ToolRenderableItem(
        title = firstString(titleKeys) ?: cardType.toDisplayLabel(),
        subtitle = firstString(subtitleKeys),
        url = firstString(listOf("url", "html_url", "web_url", "link", "href")),
    )
}

private fun JSONObject.firstString(keys: List<String>): String? {
    keys.forEach { key ->
        val value = opt(key) ?: return@forEach
        when (value) {
            is String -> value.takeIf { it.isNotBlank() }?.let { return it.take(MAX_VALUE_CHARS) }
            is Number, is Boolean -> return value.toString()
        }
    }
    return null
}

private fun String.jsonObjectOrNull(): JSONObject? =
    runCatching { JSONObject(this) }.getOrNull()

private fun String.errorTextFromJson(): String? {
    val json = jsonObjectOrNull() ?: return takeIf { it.isNotBlank() && !it.looksLikeRawJson() }
    return json.optString("errorText").takeIf { it.isNotBlank() }
        ?: json.optString("message").takeIf { it.isNotBlank() }
        ?: json.optString("title").takeIf { it.isNotBlank() }
}

private data class ToolCardModel(
    val items: List<ToolRenderableItem>,
    val moreCount: Int,
)

private data class ToolRenderableItem(
    val title: String,
    val subtitle: String? = null,
    val url: String? = null,
)

private val TOOL_CARD_REGISTRY = mapOf(
    "COMPOSIO_SEARCH_FLIGHTS" to "flightAlert",
    "COMPOSIO_SEARCH_HOTELS" to "hotelBooking",
    "COMPOSIO_SEARCH_NEWS" to "headlineList",
    "COMPOSIO_SEARCH_WEB" to "headlineList",
    "COMPOSIO_SEARCH_TAVILY" to "headlineList",
    "COMPOSIO_SEARCH_SCHOLAR" to "headlineList",
    "COMPOSIO_SEARCH_IMAGE" to "imageGrid",
    "COMPOSIO_SEARCH_SHOPPING" to "productList",
    "COMPOSIO_SEARCH_AMAZON" to "productList",
    "COMPOSIO_SEARCH_WALMART" to "productList",
    "COMPOSIO_SEARCH_FINANCE" to "finance",
    "COMPOSIO_SEARCH_EVENT" to "eventList",
    "COMPOSIO_SEARCH_GOOGLE_MAPS" to "placeList",
    "COMPOSIO_SEARCH_FETCH_URL_CONTENT" to "urlContent",
    "GITHUB_LIST_REPOSITORY_ISSUES" to "githubIssuesList",
    "GITHUB_SEARCH_ISSUES_AND_PULL_REQUESTS" to "githubIssuesList",
    "GITHUB_LIST_PULL_REQUESTS" to "githubIssuesList",
    "GITHUB_CREATE_AN_ISSUE" to "githubIssue",
    "GITHUB_GET_AN_ISSUE" to "githubIssue",
    "GITHUB_LIST_CHECK_RUNS_FOR_A_REF" to "checkRuns",
    "GITHUB_COMPARE_TWO_COMMITS" to "commitComparison",
    "GITHUB_LIST_REPOSITORY_CONTRIBUTORS" to "contributors",
    "GITHUB_LIST_DEPLOYMENTS" to "deployments",
    "GITHUB_LIST_NOTIFICATIONS" to "notifications",
    "GITHUB_LIST_ORGANIZATIONS_FOR_A_USER" to "orgsList",
    "GITHUB_LIST_ORGANIZATIONS_FOR_THE_AUTHENTICATED_USER" to "orgsList",
    "GITHUB_CREATE_A_RELEASE" to "release",
    "GITHUB_FIND_REPOSITORIES" to "repoList",
    "GITHUB_SEARCH_REPOSITORIES" to "repoList",
    "GITHUB_LIST_REPOSITORIES_STARRED_BY_THE_AUTHENTICATED_USER" to "repoList",
    "GITHUB_LIST_SECRET_SCANNING_ALERTS_FOR_A_REPOSITORY" to "secretAlerts",
    "GITHUB_LIST_REPOSITORY_WORKFLOWS" to "workflows",
    "GITHUB_CREATE_AN_ISSUE_COMMENT" to "commentThread",
    "GITHUB_LIST_REVIEW_COMMENTS_ON_A_PULL_REQUEST" to "commentThread",
    "GMAIL_FETCH_MESSAGE_BY_MESSAGE_ID" to "composeEmail",
    "GMAIL_FETCH_EMAILS" to "composeEmail",
    "GMAIL_CREATE_EMAIL_DRAFT" to "composeEmail",
    "GOOGLEDRIVE_FIND_FILE" to "fileAttachment",
    "GOOGLEDRIVE_GET_FILE_METADATA" to "fileAttachment",
    "LINEAR_CREATE_LINEAR_ISSUE" to "linearIssue",
    "LINEAR_LIST_LINEAR_ISSUES" to "linearIssuesList",
    "TWITTER_USER_HOME_TIMELINE_BY_USER_ID" to "socialPostFeed",
    "TWITTER_FULL_ARCHIVE_SEARCH" to "socialPostFeed",
    "TWITTER_POST_LOOKUP_BY_POST_ID" to "socialPostFeed",
    "createSchedule" to "createSchedule",
    "updateSchedule" to "updateSchedule",
    "updateScheduleStatus" to "updateScheduleStatus",
)

private val IGNORED_TOOL_NAMES = setOf("COMPOSIO_SEARCH_TOOLS")
private val META_TOOL_NAMES = setOf("COMPOSIO_MULTI_EXECUTE_TOOL")
private val KNOWN_LIST_KEYS = listOf("items", "results", "data", "files", "issues", "repositories", "messages", "posts", "events")

private val String.isRegisteredToolName: Boolean
    get() {
        val name = removePrefix("tool-")
        return TOOL_CARD_REGISTRY.containsKey(name) || META_TOOL_NAMES.contains(name) || IGNORED_TOOL_NAMES.contains(name) || name.startsWith("agent-")
    }

private val String.isIgnoredToolName: Boolean
    get() = IGNORED_TOOL_NAMES.contains(removePrefix("tool-"))

@Composable
private fun ToolProgressIndicator(
    total: Int,
    doneCount: Int,
    errorCount: Int,
    isCalling: Boolean,
) {
    Box(modifier = Modifier.size(22.dp)) {
        if (isCalling) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.size(22.dp),
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Surface(
                shape = CircleShape,
                color = if (errorCount > 0) MaterialTheme.colorScheme.errorContainer else Color(0xFFE3F6E8),
                modifier = Modifier.size(22.dp),
            ) {
                Text(
                    text = if (errorCount > 0) "!" else "✓",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (errorCount > 0) MaterialTheme.colorScheme.onErrorContainer else Color(0xFF2E7D32),
                    modifier = Modifier.padding(4.dp),
                )
            }
        }
    }
}

@Composable
private fun CountPill(label: String, count: Int, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = color)
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ToolStateDot(state: String) {
    Box(
        modifier = Modifier
            .padding(top = 5.dp)
            .size(8.dp)
            .background(color = toolStateColor(state), shape = CircleShape),
    )
}

@Composable
private fun SourcePart(
    part: AiSourceStreamPart,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = if (part.sourceType == "document") "Document" else "Source",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = part.filename ?: part.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            part.url?.takeIf { it.isNotBlank() }?.let {
                LinkifiedAiText(
                    text = it,
                    onLinkClick = onLinkClick,
                    onLinkLongClick = onLinkLongClick,
                )
            }
        }
    }
}

@Composable
private fun FilePart(
    part: AiFileStreamPart,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = listOfNotNull(part.filename, part.mediaType).joinToString(" · ").ifBlank { "File · ${part.state}" },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            part.url?.takeIf { it.isNotBlank() }?.let {
                LinkifiedAiText(
                    text = it,
                    onLinkClick = onLinkClick,
                    onLinkLongClick = onLinkLongClick,
                )
            }
        }
    }
}

@Composable
private fun ErrorPart(part: AiErrorStreamPart) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = part.errorText.ifBlank { "Stream error" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun toolStateColor(state: String): Color {
    return when (state) {
        "output-available", "approval-responded" -> Color(0xFF2E7D32)
        "output-error", "output-denied" -> MaterialTheme.colorScheme.error
        "input-available", "approval-requested" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

private fun toolStateLabel(state: String): String =
    when (state) {
        "input-streaming" -> "Streaming input"
        "input-available" -> "Input ready"
        "output-available" -> "Completed"
        "output-error" -> "Failed"
        "approval-requested" -> "Approval requested"
        "approval-responded" -> "Approval responded"
        "output-denied" -> "Denied"
        else -> state
    }

private val AiToolStreamPart.displayName: String
    get() = title ?: toolName.removePrefix("tool-").replace("_", " ").ifBlank { id }

private val AiToolStreamPart.isDone: Boolean
    get() = state == "output-available" || state == "approval-responded"

private val AiToolStreamPart.isError: Boolean
    get() = state == "output-error" || state == "output-denied"

private val AiToolStreamPart.isCalling: Boolean
    get() = !isDone && !isError

@Composable
private fun LinkifiedAiText(
    text: String,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(
        LocalContentColor provides ElementTheme.colors.textPrimary,
        LocalTextStyle provides ElementTheme.typography.fontBodyMdRegular,
    ) {
        EditorStyledText(
            text = LinkifyHelper.linkify(text),
            onLinkClickedListener = onLinkClick,
            onLinkLongClickedListener = onLinkLongClick,
            style = ElementRichTextEditorStyle.textStyle(),
            releaseOnDetach = false,
            modifier = modifier,
        )
    }
}

private fun String.looksLikeRawJson(): Boolean =
    (startsWith("{") && endsWith("}")) || (startsWith("[") && endsWith("]"))

private fun String.toDisplayLabel(): String =
    replace("_", " ")
        .replace("-", " ")
        .split(" ")
        .filter { it.isNotBlank() }
        .joinToString(" ") { word -> word.replaceFirstChar { it.uppercaseChar() } }

private const val MAX_RENDERED_ITEMS = 5
private const val MAX_VALUE_CHARS = 240

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
                text = "${toolCall.displayName} · ${toolCall.state}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            toolCall.output?.takeIf { it.isNotBlank() }?.let {
                if (!it.looksLikeRawJson()) {
                    Text(text = it, style = MaterialTheme.typography.bodySmall)
                }
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

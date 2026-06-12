/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import io.element.android.features.messages.impl.timeline.model.event.AiCustomStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiDataStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiToolCardEntry
import io.element.android.features.messages.impl.timeline.model.event.AiToolStreamPart
import io.element.android.features.messages.impl.timeline.components.event.toolcards.CardTransforms
import io.element.android.features.messages.impl.timeline.components.event.toolcards.IGNORED_TOOL_NAMES
import io.element.android.features.messages.impl.timeline.components.event.toolcards.KNOWN_LIST_KEYS
import io.element.android.features.messages.impl.timeline.components.event.toolcards.META_TOOL_NAMES
import io.element.android.features.messages.impl.timeline.components.event.toolcards.TOOL_CARD_REGISTRY
import io.element.android.features.messages.impl.timeline.components.event.toolcards.TOOL_CARD_REGISTRY_WITH_DISPLAY
import io.element.android.features.messages.impl.timeline.components.event.toolcards.errorProps
import org.json.JSONArray
import org.json.JSONObject

/**
 * Plain-Kotlin parsing / registry / expansion logic for AI tool-call stream parts.
 *
 * Mirrors iOS `ToolCallRootCardAdapter`: this parses tool parts off the Compose thread (the
 * reducer precomputes the renderable lists) so the view only renders a precomputed model.
 * These helpers are `internal` so the reducer (in a different package, same module) can call
 * them and the composables in [TimelineItemAiView] (same package) can reference them too.
 */

internal fun String?.toToolCardModel(toolName: String): ToolCardModel? {
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

internal fun JSONObject.toToolCardModel(cardType: String): ToolCardModel {
    val source = firstArrayOrSelf()
    return source.toToolCardModel(cardType)
}

internal fun JSONArray.toToolCardModel(cardType: String): ToolCardModel {
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

internal fun JSONObject.firstArrayOrSelf(): JSONArray {
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

internal fun AiToolStreamPart.toRenderableToolParts(): List<AiToolStreamPart> {
    val normalizedToolName = toolName.removePrefix("tool-")
    return when {
        IGNORED_TOOL_NAMES.contains(normalizedToolName) -> emptyList()
        META_TOOL_NAMES.contains(normalizedToolName) -> expandMultiExecute()
        normalizedToolName.startsWith("agent-") -> expandSubAgent()
        TOOL_CARD_REGISTRY.containsKey(normalizedToolName) -> listOf(this)
        else -> emptyList()
    }
}

/** Mirrors iOS `toolCallEntries(from:)`: ToolUIPart[] -> ToolCallEntry[]. */
internal fun AiToolStreamPart.toToolCardEntries(): List<AiToolCardEntry> {
    val normalizedToolName = toolName.removePrefix("tool-")
    if (IGNORED_TOOL_NAMES.contains(normalizedToolName)) return emptyList()
    if (META_TOOL_NAMES.contains(normalizedToolName)) return expandMultiExecuteEntries()
    if (normalizedToolName.startsWith("agent-")) return expandSubAgentEntries()
    val registry = TOOL_CARD_REGISTRY_WITH_DISPLAY[normalizedToolName] ?: return emptyList()
    val cardState = state.toCardToolState()
    val props = when {
        cardState == CARD_STATE_ERROR -> errorProps(registry.cardType, errorText)
        registry.cardType.isScheduleCardType() -> scheduleProps(cardType = registry.cardType, input = input)
        else -> {
            JSONObject().put("_cardType", registry.cardType).also { props ->
                if (cardState == CARD_STATE_DONE) {
                    val raw = extractProps(output)
                    val transformed = CardTransforms.transform(raw, registry.cardType)
                    transformed.copyInto(props)
                }
            }
        }
    }
    return listOf(
        AiToolCardEntry(
            id = id,
            name = registry.displayName,
            state = cardState,
            props = props.toString(),
        )
    )
}

internal fun AiToolStreamPart.expandMultiExecute(): List<AiToolStreamPart> {
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

private fun AiToolStreamPart.expandMultiExecuteEntries(): List<AiToolCardEntry> {
    val cardState = state.toCardToolState()
    val outputJson = output?.jsonObjectOrNull()
    val results = outputJson
        ?.optJSONObject("data")
        ?.optJSONArray("results")
        ?: outputJson?.optJSONArray("results")
    if ((cardState == CARD_STATE_DONE || cardState == CARD_STATE_ERROR) && results != null) {
        return entriesFromMultiExecuteResults(results, idPrefix = id)
    }

    val tools = input
        ?.jsonObjectOrNull()
        ?.optJSONArray("tools")
        ?: return emptyList()
    val seen = mutableSetOf<String>()
    return (0 until tools.length())
        .asSequence()
        .mapNotNull { index -> tools.optJSONObject(index)?.firstString(listOf("tool_slug", "toolSlug", "toolName", "tool_name")) }
        .filter { seen.add(it) }
        .mapNotNull { slug ->
            val registry = TOOL_CARD_REGISTRY_WITH_DISPLAY[slug] ?: return@mapNotNull null
            AiToolCardEntry(
                id = "${id}_$slug",
                name = registry.displayName,
                state = CARD_STATE_CALLING,
                props = JSONObject().put("_cardType", registry.cardType).toString(),
            )
        }
        .toList()
}

internal fun AiToolStreamPart.expandSubAgent(): List<AiToolStreamPart> {
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

private fun AiToolStreamPart.expandSubAgentEntries(): List<AiToolCardEntry> {
    val cardState = state.toCardToolState()
    if (cardState != CARD_STATE_DONE && cardState != CARD_STATE_ERROR) {
        val agentName = toolName
            .removePrefix("tool-")
            .removePrefix("agent-")
            .removeSuffix("Agent")
            .toDisplayLabel()
            .ifBlank { "Agent" } + " Agent"
        return listOf(
            AiToolCardEntry(
                id = id,
                name = agentName,
                state = CARD_STATE_CALLING,
                props = JSONObject().put("_cardType", "generic").toString(),
            )
        )
    }

    val toolResults = output
        ?.jsonObjectOrNull()
        ?.optJSONArray("subAgentToolResults")
        ?: return emptyList()
    val entries = mutableListOf<AiToolCardEntry>()
    for (index in 0 until toolResults.length()) {
        val toolResult = toolResults.optJSONObject(index) ?: continue
        val resultToolName = toolResult.firstString(listOf("toolName", "tool_name", "tool_slug", "toolSlug")) ?: continue
        val normalizedResultToolName = resultToolName.removePrefix("tool-")
        if (IGNORED_TOOL_NAMES.contains(normalizedResultToolName)) continue
        val result = toolResult.optJSONObject("result")
        if (META_TOOL_NAMES.contains(normalizedResultToolName)) {
            val nestedResults = result?.optJSONObject("data")?.optJSONArray("results")
                ?: result?.optJSONArray("results")
                ?: continue
            entries += entriesFromMultiExecuteResults(nestedResults, idPrefix = id)
            continue
        }
        val registry = TOOL_CARD_REGISTRY_WITH_DISPLAY[normalizedResultToolName] ?: continue
        val successful = result?.optBooleanOrNull("successful")
            ?: result?.optJSONObject("data")?.optBooleanOrNull("successful")
            ?: (result != null)
        val toolState = if (successful) CARD_STATE_DONE else CARD_STATE_ERROR
        val props = if (registry.cardType.isScheduleCardType()) {
            scheduleProps(cardType = registry.cardType, input = toolResult.opt("args")?.toString())
        } else {
            JSONObject().put("_cardType", registry.cardType).also { props ->
                if (successful) {
                    val data = result?.optJSONObject("data") ?: result ?: JSONObject()
                    CardTransforms.transform(data, registry.cardType).copyInto(props)
                }
            }
        }
        entries += AiToolCardEntry(
            id = "${id}_$normalizedResultToolName",
            name = registry.displayName,
            state = toolState,
            props = props.toString(),
        )
    }
    return entries
}

private fun entriesFromMultiExecuteResults(
    results: JSONArray,
    idPrefix: String,
): List<AiToolCardEntry> {
    val slugOrder = mutableListOf<String>()
    val grouped = linkedMapOf<String, MutableList<JSONObject>>()
    for (index in 0 until results.length()) {
        val item = results.optJSONObject(index) ?: continue
        val slug = item.firstString(listOf("tool_slug", "toolSlug", "toolName", "tool_name", "name")) ?: continue
        if (!grouped.containsKey(slug)) slugOrder += slug
        val response = item.optJSONObject("response") ?: item
        grouped.getOrPut(slug) { mutableListOf() } += response
    }
    return slugOrder.mapNotNull { slug ->
        val registry = TOOL_CARD_REGISTRY_WITH_DISPLAY[slug] ?: return@mapNotNull null
        val responses = grouped[slug].orEmpty()
        val allSuccessful = responses.all { it.optBooleanOrNull("successful") ?: false }
        val state = if (allSuccessful) CARD_STATE_DONE else CARD_STATE_ERROR
        val props = JSONObject().put("_cardType", registry.cardType)
        if (allSuccessful) {
            val mergedData = JSONObject()
            responses.forEach { response ->
                val data = response.optJSONObject("data") ?: return@forEach
                val keys = data.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = data.opt(key)
                    val existing = mergedData.opt(key)
                    if (value is JSONArray && existing is JSONArray) {
                        for (i in 0 until value.length()) existing.put(value.opt(i))
                    } else if (!mergedData.has(key)) {
                        mergedData.put(key, value)
                    }
                }
            }
            CardTransforms.transform(mergedData, registry.cardType).copyInto(props)
        }
        AiToolCardEntry(
            id = "${idPrefix}_$slug",
            name = registry.displayName,
            state = state,
            props = props.toString(),
        )
    }
}

internal fun JSONArray.toSyntheticToolParts(
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

internal fun JSONObject.toRenderableItem(cardType: String): ToolRenderableItem {
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

internal fun JSONObject.firstString(keys: List<String>): String? {
    keys.forEach { key ->
        val value = opt(key) ?: return@forEach
        when (value) {
            is String -> value.takeIf { it.isNotBlank() }?.let { return it.take(MAX_VALUE_CHARS) }
            is Number, is Boolean -> return value.toString()
        }
    }
    return null
}

internal fun String.jsonObjectOrNull(): JSONObject? =
    runCatching { JSONObject(this) }.getOrNull()

private fun extractProps(output: String?): JSONObject {
    val dict = output?.jsonObjectOrNull() ?: return JSONObject()
    dict.optJSONObject("data")?.let { return it }
    return JSONObject(dict.toString()).apply {
        remove("error")
        remove("successful")
        remove("logId")
    }
}

private fun JSONObject.copyInto(target: JSONObject) {
    val keys = keys()
    while (keys.hasNext()) {
        val key = keys.next()
        target.put(key, opt(key))
    }
}

private fun String.toCardToolState(): String = when (this) {
    "input-streaming", "input-available" -> CARD_STATE_CALLING
    "output-available", "approval-requested", "approval-responded" -> CARD_STATE_DONE
    "output-error", "output-denied" -> CARD_STATE_ERROR
    else -> CARD_STATE_CALLING
}

private fun String.isScheduleCardType(): Boolean = this in SCHEDULE_CARD_TYPES

private fun scheduleProps(cardType: String, input: String?): JSONObject {
    val schedule = input?.jsonObjectOrNull() ?: JSONObject()
    val props = JSONObject().put("_cardType", cardType)
    when (cardType) {
        "createSchedule" -> {
            val cron = schedule.str("cron")
            props.put("name", schedule.str("name")?.takeIf { it.isNotBlank() } ?: "Unnamed schedule")
            props.put("cadence", cron ?: "No schedule rule")
            props.put("cadenceProvided", cron != null)
            props.put("timezone", schedule.str("timezone") ?: "UTC")
            props.put("action", schedule.str("action") ?: "")
        }
        "updateSchedule" -> {
            val cron = schedule.str("cron")
            props.put("name", schedule.str("name")?.takeIf { it.isNotBlank() } ?: "Unnamed schedule")
            props.put("cadence", cron ?: "Schedule updated")
            props.put("cadenceChanged", cron != null)
            props.put("timezone", schedule.str("timezone") ?: "")
            props.put("action", schedule.str("action") ?: "")
        }
        "updateScheduleStatus" -> {
            val names = JSONArray()
            schedule.optJSONArray("names")?.let { arr ->
                for (i in 0 until arr.length()) names.put(arr.optString(i))
            } ?: schedule.str("name")?.let { names.put(it) }
            props.put("summary", if (names.length() == 0) "schedule" else names.optString(0))
            props.put("names", names)
            props.put("isEnable", schedule.str("status") == "enabled" || schedule.optBoolean("isEnable", true))
        }
    }
    return props
}

/** First non-blank string value for [key] (mirrors the CardTransforms `str` helper). */
private fun org.json.JSONObject.str(key: String): String? =
    if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null

/** Boolean value for [key], or null when absent/null (coerces "true"/"false" strings). */
private fun org.json.JSONObject.optBooleanOrNull(key: String): Boolean? = when {
    !has(key) || isNull(key) -> null
    else -> when (val v = opt(key)) {
        is Boolean -> v
        is String -> v.toBooleanStrictOrNull()
        is Number -> v.toInt() != 0
        else -> null
    }
}

internal fun String.errorTextFromJson(): String? {
    val json = jsonObjectOrNull() ?: return takeIf { it.isNotBlank() && !it.looksLikeRawJson() }
    return json.optString("errorText").takeIf { it.isNotBlank() }
        ?: json.optString("message").takeIf { it.isNotBlank() }
        ?: json.optString("title").takeIf { it.isNotBlank() }
}

internal data class ToolCardModel(
    val items: List<ToolRenderableItem>,
    val moreCount: Int,
)

internal data class ToolRenderableItem(
    val title: String,
    val subtitle: String? = null,
    val url: String? = null,
)

// Ported from iOS HideToolNames: internal tool/data/step parts that are never rendered in the UI.
internal val HIDDEN_PART_NAMES = setOf(
    "tool-information", "tool-requestDeviceAction", "tool-sendMessage", "tool-setTyping",
    "tool-uploadFile", "tool-getMessages", "tool-getSchedule", "tool-unsealRpcCall",
    "tool-updateWorkingMemory", "tool-requestVaultAuthorization", "tool-chooseRequest",
    "tool-deleteSchedule", "tool-listSchedules", "tool-getMessagesTool", "tool-executeCommand",
    "tool-mastra_workspace_execute_command", "step-start",
    "data-om-observation-start", "data-om-observation-end", "data-om-status",
    "data-sandbox-stderr", "data-sandbox-exit", "data-workspace-metadata",
    "tool-listVaultGrants", "tool-fetch", "tool-getCurrentTime",
    "tool-mastra_workspace_list_files", "tool-mastra_workspace_write_file",
    "tool-mastra_workspace_edit_file", "tool-pagePeek", "tool-downloadMessage",
    "tool-unsealCli", "tool-addToVault", "tool-readRoomMemory", "tool-writeRoomMemory",
    "tool-skill_read", "tool-skill", "data-evaluation", "data-plan",
)

/** Mirrors iOS ToolGroupUtils.isHiddenPart — checks the part's wire type against [HIDDEN_PART_NAMES]. */
internal val AiStreamPart.isHiddenStreamPart: Boolean
    get() = when (this) {
        is AiToolStreamPart -> {
            val bare = toolName.removePrefix("tool-")
            HIDDEN_PART_NAMES.contains(toolName) || HIDDEN_PART_NAMES.contains("tool-$bare")
        }
        is AiDataStreamPart -> HIDDEN_PART_NAMES.contains(type)
        is AiCustomStreamPart -> HIDDEN_PART_NAMES.contains(type)
        else -> false
    }

internal fun toolStateLabel(state: String): String =
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

internal val AiToolStreamPart.displayName: String
    get() {
        val raw = title?.takeIf { it.isNotBlank() } ?: toolName.removePrefix("tool-")
        // Humanise ALL-CAPS / snake_case tool slugs; leave already-readable titles untouched.
        return (if (raw == raw.uppercase() || raw.contains('_')) raw.toDisplayLabel() else raw).ifBlank { id }
    }

internal val AiToolStreamPart.isDone: Boolean
    // Mirrors iOS mapState: output-available + approval-requested + approval-responded are "done".
    get() = state == "output-available" || state == "approval-requested" || state == "approval-responded"

internal val AiToolStreamPart.isError: Boolean
    get() = state == "output-error" || state == "output-denied"

internal val AiToolStreamPart.isCalling: Boolean
    get() = !isDone && !isError

internal fun String.looksLikeRawJson(): Boolean =
    (startsWith("{") && endsWith("}")) || (startsWith("[") && endsWith("]"))

internal fun String.toDisplayLabel(): String =
    replace("_", " ")
        .replace("-", " ")
        .split(" ")
        .filter { it.isNotBlank() }
        // Title-case each word (lowercase the rest) so SCREAMING_SNAKE tool slugs like
        // GITHUB_FIND_REPOSITORIES render as "Github Find Repositories", not "GITHUB FIND ...".
        .joinToString(" ") { word -> word.lowercase().replaceFirstChar { it.uppercaseChar() } }

internal const val MAX_RENDERED_ITEMS = 5
internal const val MAX_VALUE_CHARS = 240
internal const val MAX_RAW_PAYLOAD_CHARS = 4000
private const val CARD_STATE_CALLING = "calling"
private const val CARD_STATE_DONE = "done"
private const val CARD_STATE_ERROR = "error"
private val SCHEDULE_CARD_TYPES = setOf("createSchedule", "updateSchedule", "updateScheduleStatus")

/** Pretty-prints a tool payload (JSON if possible) so the full result is readable on expand. */
internal fun String.prettyPayload(maxChars: Int = MAX_RAW_PAYLOAD_CHARS): String {
    val trimmed = trim()
    val pretty = runCatching {
        when {
            trimmed.startsWith("{") -> JSONObject(trimmed).toString(2)
            trimmed.startsWith("[") -> JSONArray(trimmed).toString(2)
            else -> trimmed
        }
    }.getOrDefault(trimmed)
    return if (pretty.length > maxChars) pretty.take(maxChars) + "\n…" else pretty
}

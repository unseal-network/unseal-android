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
import io.element.android.features.messages.impl.timeline.model.event.AiToolStreamPart
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

internal val TOOL_CARD_REGISTRY = mapOf(
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

internal val IGNORED_TOOL_NAMES = setOf("COMPOSIO_SEARCH_TOOLS")
internal val META_TOOL_NAMES = setOf("COMPOSIO_MULTI_EXECUTE_TOOL")
internal val KNOWN_LIST_KEYS = listOf("items", "results", "data", "files", "issues", "repositories", "messages", "posts", "events")

internal val String.isRegisteredToolName: Boolean
    get() {
        val name = removePrefix("tool-")
        return TOOL_CARD_REGISTRY.containsKey(name) || META_TOOL_NAMES.contains(name) || IGNORED_TOOL_NAMES.contains(name) || name.startsWith("agent-")
    }

internal val String.isIgnoredToolName: Boolean
    get() = IGNORED_TOOL_NAMES.contains(removePrefix("tool-"))

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
    get() = title ?: toolName.removePrefix("tool-").replace("_", " ").ifBlank { id }

internal val AiToolStreamPart.isDone: Boolean
    get() = state == "output-available" || state == "approval-responded"

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
        .joinToString(" ") { word -> word.replaceFirstChar { it.uppercaseChar() } }

internal const val MAX_RENDERED_ITEMS = 5
internal const val MAX_VALUE_CHARS = 240

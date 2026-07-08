/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event.toolcards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.element.android.features.messages.impl.R
import io.element.android.features.messages.impl.timeline.components.event.PptGenerationWorkflowCard
import io.element.android.features.messages.impl.timeline.components.event.PptGenerationWorkflowData
import org.json.JSONArray
import org.json.JSONObject

internal val TOOL_CARD_DISPATCHER_CARD_TYPES = ROOT_DISPATCH_CARD_TYPES + STANDALONE_SUSPENDED_CARD_TYPES

/**
 * Dispatches a parsed tool payload to a specific Material 3 card by [cardType], mirroring the iOS
 * ToolCallRootCardAdapter `_cardType` dispatch. Returns true if a card rendered; false lets the
 * caller fall back to the raw payload. Specific per-type cards (Composio/GitHub/Gmail/Linear/…)
 * are added per migration task; until a type has a bespoke card it uses [GenericListCard] when the
 * payload looks like a list, otherwise the caller's raw fallback.
 */
@Composable
internal fun ToolCard(
    cardType: String,
    rawData: JSONObject,
    onLinkClick: () -> Unit = {},
): Boolean {
    // Map the raw tool response into the props each card expects (iOS CardTransforms parity).
    // Cache the transform: it allocates a whole JSONObject tree (measured 2-5ms per card in real
    // rooms) and rawData is a stable instance, so without remember it re-runs on every recomposition
    // (notably during streaming, which recomposes this subtree every chunk).
    val data = remember(rawData, cardType) { CardTransforms.transform(rawData, cardType) }
    return ToolCardFinalProps(cardType = cardType, data = data, onLinkClick = onLinkClick)
}

@Composable
internal fun ToolCardFinalProps(
    cardType: String,
    data: JSONObject,
    onLinkClick: () -> Unit = {},
): Boolean {
    // If the transform produced no renderable content, let the caller fall back to the raw payload
    // instead of a card claiming success while drawing nothing.
    if (!data.hasRenderableContent() || !data.hasCardContentFor(cardType)) return false
    // Bespoke per-category cards first (iOS ToolCardsIOS parity); generic list renderer last.
    if (pptGenerationWorkflowCard(cardType, data)) return true
    if (composioSearchCard(cardType, data, onLinkClick)) return true
    if (gitHubPrimaryCard(cardType, data, onLinkClick)) return true
    if (gitHubActivityCard(cardType, data, onLinkClick)) return true
    if (gmailDriveCard(cardType, data, onLinkClick)) return true
    if (linearTwitterCard(cardType, data, onLinkClick)) return true
    if (scheduleMoltbookCard(cardType, data, onLinkClick)) return true
    return GenericListCard(data)
}

/** True if the (possibly transformed) payload has any non-empty array or a title/summary field. */
private fun JSONObject.hasRenderableContent(): Boolean {
    val keys = keys()
    while (keys.hasNext()) {
        when (val v = opt(keys.next())) {
            is JSONArray -> if (v.length() > 0) return true
            is JSONObject -> if (v.length() > 0) return true
            is String -> if (v.isNotBlank()) return true
            is Number, is Boolean -> return true
        }
    }
    return false
}

private val LIST_KEYS = arrayOf(
    "headlines", "items", "results", "products", "issues", "repositories", "repos",
    "messages", "posts", "events", "places", "files", "flights", "hotels", "notifications",
    "data",
)

internal fun JSONObject.hasCardContentFor(cardType: String): Boolean {
    return when (cardType) {
        "composeEmail" -> hasSingleEmailContent() || cardObjects("messages", "items").isNotEmpty()
        "fileAttachment" -> cardObjects("files", "items").isNotEmpty() ||
            cardString("name", "filename", "title", "mimeType", "mime_type", "url", "webViewLink", "web_view_link").isNullOrBlank().not()
        "githubIssue" -> cardString("title", "number").isNullOrBlank().not()
        "linearIssue" -> cardString("title", "identifier").isNullOrBlank().not()
        "githubIssuesList", "linearIssuesList" -> cardObjects("items", "issues", "pull_requests").isNotEmpty()
        "repoList" -> cardObjects("repositories", "repos", "items").isNotEmpty()
        "release" -> cardString("name", "tagName", "tag_name", "title").isNullOrBlank().not()
        "orgsList" -> cardObjects("organizations", "orgs", "items").isNotEmpty()
        "contributors" -> cardObjects("contributors", "items").isNotEmpty()
        "checkRuns" -> cardObjects("checkRuns", "check_runs", "items").isNotEmpty()
        "commentThread" -> cardObjects("comments", "items").isNotEmpty() ||
            cardString("body", "comment", "author", "createdAt", "created_at").isNullOrBlank().not() ||
            optJSONObject("user") != null
        "commitComparison" -> cardObjects("commits", "files").isNotEmpty() || cardString("status").isNullOrBlank().not()
        "deployments" -> cardObjects("deployments", "items").isNotEmpty()
        "notifications" -> cardObjects("notifications", "items").isNotEmpty()
        "secretAlerts" -> cardObjects("alerts", "secretAlerts", "secret_alerts", "items").isNotEmpty()
        "workflows" -> cardObjects("workflows", "items").isNotEmpty()
        "flightAlert" -> cardObjects("flights").isNotEmpty()
        "hotelBooking" -> cardObjects("hotels").isNotEmpty()
        "headlineList" -> cardObjects("headlines", "items").isNotEmpty()
        "breakingNews" -> cardString("headline", "title").isNullOrBlank().not() ||
            cardObjects("headlines", "items").firstOrNull()?.cardString("headline", "title").isNullOrBlank().not()
        "imageGrid" -> cardObjects("images").isNotEmpty()
        "productList" -> cardObjects("products").isNotEmpty()
        "finance" -> optJSONObject("quote") != null ||
            cardObjects("markets", "keyEvents", "news", "graph", "financials").isNotEmpty() ||
            cardStrings("stats").isNotEmpty()
        "weather" -> optJSONObject("current") != null ||
            cardObjects("forecast").isNotEmpty()
        "eventList" -> cardObjects("events").isNotEmpty()
        "placeList" -> cardObjects("places").isNotEmpty()
        "urlContent" -> cardObjects("articles", "results", "items", "data").isNotEmpty()
        "socialPostFeed" -> cardObjects("posts", "tweets", "items", "data").isNotEmpty() ||
            cardString("text", "full_text", "body", "content", "id").isNullOrBlank().not()
        "createSchedule", "updateSchedule", "updateScheduleStatus" -> cardString("name", "title", "scheduleId", "schedule_id", "status").isNullOrBlank().not()
        "moltbookRegister" -> cardString("title", "name", "status").isNullOrBlank().not()
        "pptGenerationWorkflow" -> cardString("task_id").isNullOrBlank().not()
        else -> true
    }
}

private fun JSONObject.hasSingleEmailContent(): Boolean {
    return cardString("subject", "body", "date", "snippet").isNullOrBlank().not() ||
        optJSONObject("from") != null ||
        cardObjects("to", "cc", "bcc").isNotEmpty()
}

/** Renders a payload's primary array as a divided list of title/subtitle/source/thumbnail rows. */
@Composable
internal fun GenericListCard(data: JSONObject): Boolean {
    val items = data.cardObjects(*LIST_KEYS)
    if (items.isEmpty()) return false
    val shown = items.take(MAX_CARD_ITEMS)
    ToolCardSurface {
        DividedList(shown) { item -> GenericListRow(item) }
        if (items.size > shown.size) {
            Text(
                text = stringResource(R.string.screen_room_timeline_tool_card_more_count, items.size - shown.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    return true
}

@Composable
private fun GenericListRow(item: JSONObject) {
    val title = item.cardString("title", "name", "subject", "headline", "summary", "query", "text")
        ?: stringResource(R.string.screen_room_timeline_tool_card_untitled)
    val subtitle = item.cardString("snippet", "description", "body", "content", "status", "state")
    val source = item.cardString("source", "label", "author", "from", "repository")
    val meta = item.cardString("publishedAt", "date", "time", "meta", "price", "priceFormatted")?.compactToolCardMetaDate()
    val url = item.cardString("url", "html_url", "web_url", "link", "href")
    val imageUrl = item.cardString("imageUrl", "image", "thumbnail", "thumbnailUrl", "avatarUrl")
    val uriHandler = LocalUriHandler.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (url != null) Modifier.clickable { runCatching { uriHandler.openUri(url) } } else Modifier)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!source.isNullOrBlank() || !meta.isNullOrBlank() || url != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    source?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                    }
                    meta?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    url?.let {
                        Text(domainOf(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (!imageUrl.isNullOrBlank()) {
            Spacer(Modifier.width(0.dp))
            CardRemoteImage(url = imageUrl, modifier = Modifier.size(width = 56.dp, height = 42.dp))
        }
    }
}

// The root ToolCall card owns the fixed-height viewport and vertical scrolling, matching iOS.
// Keep only a high safety cap here so card renderers do not truncate ordinary tool output before
// the inner card scroll can take over.
internal const val MAX_CARD_ITEMS = 100

@Composable
internal fun pptGenerationWorkflowCard(cardType: String, data: JSONObject): Boolean {
    if (cardType != "pptGenerationWorkflow") return false
    val workflowData = remember(data) { PptGenerationWorkflowData.fromJson(data) } ?: return false
    PptGenerationWorkflowCard(data = workflowData)
    return true
}

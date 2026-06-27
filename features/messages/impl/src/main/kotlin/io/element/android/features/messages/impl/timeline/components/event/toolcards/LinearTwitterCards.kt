/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event.toolcards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.element.android.features.messages.impl.R
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import org.json.JSONObject
import java.util.Locale

/**
 * Native Material 3 renderers for the Linear and Twitter (social) tool cards, ported
 * field-for-field from the iOS `ToolCardsIOS/Linear` and `ToolCardsIOS/Twitter` SwiftUI
 * cards. Each card mirrors the iOS field set and layout, expressed with the shared
 * [ToolCardKit] helpers. Lists are capped at [MAX_CARD_ITEMS].
 *
 * Wiring into [ToolCard]/ToolCardDispatcher is done separately by the caller.
 */
@Composable
internal fun linearTwitterCard(
    cardType: String,
    data: JSONObject,
    onLinkClick: () -> Unit,
): Boolean {
    return when (cardType) {
        "linearIssue" -> { LinearIssueCardView(data, onLinkClick); true }
        "linearIssuesList" -> { LinearIssuesListCardView(data, onLinkClick); true }
        "socialPostFeed" -> { SocialPostFeedCardView(data); true }
        else -> false
    }
}

// MARK: - Shared helpers (local to this file, mirroring GitHubCardsPrimary conventions)

@Composable
private fun ltLinkOpener(onLinkClick: () -> Unit): (String?) -> Unit {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    return { url ->
        if (!url.isNullOrBlank()) {
            runCatching { uriHandler.openUri(url) }
            onLinkClick()
        }
    }
}

@Composable
private fun LtMetaText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Round avatar mirroring iOS AvatarView/initial-circle; falls back to a tinted initial. */
@Composable
private fun LtAvatar(name: String, avatarUrl: String?, size: Int = 20) {
    if (!avatarUrl.isNullOrBlank()) {
        CardRemoteImage(url = avatarUrl, modifier = Modifier.size(size.dp), corner = size / 2)
    } else {
        Box(
            modifier = Modifier
                .size(size.dp)
                .clip(CircleShape)
                .background(LinearIndigo.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name.take(1).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = LinearIndigo,
            )
        }
    }
}

// MARK: - Linear status / priority styling (mirror iOS statusStyles / priorityStyles)

private val LinearIndigo = Color(0xFF5E5CE6)
private val LinearGray = Color(0xFF8E8E93)
private val LinearTodoGray = Color(0xFF8E8E93).copy(alpha = 0.6f)
private val LinearYellow = Color(0xFFE6B800)

private data class LinearStatusStyle(val icon: ImageVector, val color: Color)

private fun normalizeLinearStatus(status: String?): String {
    val normalized = status.orEmpty()
        .trim()
        .lowercase(Locale.US)
        .replace("-", "_")
        .replace(" ", "_")
    return when (normalized) {
        "backlog" -> "backlog"
        "in_progress", "inprogress", "started", "active" -> "in_progress"
        "done", "completed", "complete", "closed", "resolved" -> "done"
        "cancelled", "canceled" -> "cancelled"
        "todo", "to_do", "open" -> "todo"
        else -> "todo"
    }
}

private fun linearStatusLabelRes(status: String): Int = when (status) {
    "backlog" -> R.string.screen_room_timeline_tool_card_linear_status_backlog
    "in_progress" -> R.string.screen_room_timeline_tool_card_linear_status_in_progress
    "done" -> R.string.screen_room_timeline_tool_card_linear_status_done
    "cancelled" -> R.string.screen_room_timeline_tool_card_linear_status_cancelled
    else -> R.string.screen_room_timeline_tool_card_linear_status_todo
}

private fun normalizeLinearPriority(priority: String?): String {
    val normalized = priority.orEmpty().trim().lowercase(Locale.US)
    return when (normalized) {
        "1", "urgent", "critical" -> "urgent"
        "2", "high" -> "high"
        "3", "medium", "normal" -> "medium"
        "4", "low" -> "low"
        else -> "none"
    }
}

private fun linearStatusStyle(status: String): LinearStatusStyle = when (status) {
    "backlog" -> LinearStatusStyle(Icons.Outlined.Circle, LinearGray)
    "in_progress" -> LinearStatusStyle(Icons.Outlined.RadioButtonUnchecked, LinearYellow)
    "done" -> LinearStatusStyle(Icons.Filled.CheckCircle, LinearIndigo)
    "cancelled" -> LinearStatusStyle(Icons.Outlined.Cancel, LinearGray)
    else -> LinearStatusStyle(Icons.Outlined.Circle, LinearTodoGray)
}

private data class LinearPriorityStyle(val text: String, val color: Color)

private fun linearPriorityStyle(priority: String): LinearPriorityStyle = when (priority) {
    "urgent" -> LinearPriorityStyle("!!!", Color(0xFFFF3B30))
    "high" -> LinearPriorityStyle("!!", Color(0xFFFF9500))
    "medium" -> LinearPriorityStyle("!", LinearYellow)
    "low" -> LinearPriorityStyle("—", Color(0xFF007AFF))
    else -> LinearPriorityStyle("···", LinearGray)
}

/** Number of active priority bars (mirror iOS PriorityBars.activeBars). */
private fun priorityActiveBars(priority: String): Int = when (priority) {
    "urgent" -> 4
    "high" -> 3
    "medium" -> 2
    "low" -> 1
    else -> 0
}

private fun priorityBarColor(priority: String): Color = when (priority) {
    "urgent" -> Color(0xFFFF3B30)
    "high" -> Color(0xFFFF9500)
    "medium" -> LinearYellow
    "low" -> Color(0xFF007AFF)
    else -> Color.Transparent
}

/** Four ascending bars filled per priority (mirror iOS PriorityBars). */
@Composable
private fun PriorityBars(priority: String) {
    val active = priorityActiveBars(priority)
    if (active == 0) return
    val color = priorityBarColor(priority)
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        for (i in 1..4) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((4 + i * 2).dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (i <= active) color else LinearGray.copy(alpha = 0.2f)),
            )
        }
    }
}

// MARK: - linearIssue (single issue)

@Composable
private fun LinearIssueCardView(data: JSONObject, onLinkClick: () -> Unit) {
    val title = data.cardString("title", "name")
    val identifier = data.cardString("identifier", "id")
    if (title.isNullOrBlank() && identifier == null) return

    val description = data.cardString("description")
    val url = data.cardString("url")
    val status = normalizeLinearStatus(data.cardString("status", "state"))
    val statusLabel = data.cardString("statusLabel", "stateLabel") ?: stringResource(linearStatusLabelRes(status))
    val priority = normalizeLinearPriority(data.cardString("priority", "priorityLabel"))
    val assignee = data.opt("assignee") as? JSONObject
    val team = data.cardString("team")
    val project = data.cardString("project")
    val labels = data.cardStrings("labels")
    val dueDate = data.cardString("dueDate")
    val statusStyle = linearStatusStyle(status)
    val open = ltLinkOpener(onLinkClick)

    ToolCardSurface {
        // Header: "Issue" title + status badge
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.screen_room_timeline_tool_card_issue),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Icon(statusStyle.icon, contentDescription = null, tint = statusStyle.color, modifier = Modifier.size(12.dp))
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = statusStyle.color,
                )
            }
        }

        // Title row: status icon + title/identifier + meta line, with priority bars trailing
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (url != null) Modifier.clickable { open(url) } else Modifier),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                statusStyle.icon,
                contentDescription = null,
                tint = statusStyle.color,
                modifier = Modifier.size(16.dp),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = title.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (identifier != null) {
                        Text(
                            text = identifier,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (!team.isNullOrBlank() || !project.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (!team.isNullOrBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Icon(Icons.Outlined.Group, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(10.dp))
                                LtMetaText(team)
                            }
                        }
                        if (!project.isNullOrBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color(0xFFAF52DE).copy(alpha = 0.6f)),
                                )
                                LtMetaText(project)
                            }
                        }
                    }
                }
            }
            PriorityBars(priority)
        }

        // Description (indented to align under title)
        if (!description.isNullOrBlank()) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 24.dp),
            )
        }

        // Labels
        if (labels.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.padding(start = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                labels.forEach { CardChip(it) }
            }
        }

        // Footer: due date + assignee
        val hasFooter = dueDate != null || assignee != null
        if (hasFooter) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (dueDate != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(Icons.Outlined.AccessTime, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(10.dp))
                        LtMetaText(dueDate)
                    }
                }
                Box(Modifier.weight(1f))
                if (assignee != null) {
                    val name = assignee.cardString("name") ?: ""
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        LtAvatar(name = name.ifEmpty { "?" }, avatarUrl = assignee.cardString("avatarUrl"), size = 16)
                        LtMetaText(name)
                    }
                }
            }
        }
    }
}

// MARK: - linearIssuesList

@Composable
private fun LinearIssuesListCardView(data: JSONObject, onLinkClick: () -> Unit) {
    val items = data.cardObjects("items", "issues", "pull_requests")
    if (items.isEmpty()) return
    val shown = items.take(MAX_CARD_ITEMS)
    val open = ltLinkOpener(onLinkClick)

    ToolCardSurface {
        ToolCardHeader(title = stringResource(R.string.screen_room_timeline_tool_card_issues), count = items.size)
        DividedList(shown) { issue -> LinearIssueRow(issue, open) }
        if (items.size > shown.size) {
            LtMetaText(stringResource(R.string.screen_room_timeline_tool_card_more_count, items.size - shown.size))
        }
    }
}

@Composable
private fun LinearIssueRow(item: JSONObject, open: (String?) -> Unit) {
    val identifier = item.cardString("identifier", "id") ?: ""
    val title = item.cardString("title", "name") ?: stringResource(R.string.screen_room_timeline_tool_card_untitled)
    val status = normalizeLinearStatus(item.cardString("status", "state"))
    val priority = normalizeLinearPriority(item.cardString("priority", "priorityLabel"))
    val url = item.cardString("url")
    val dueDate = item.cardString("dueDate")
    val assignee = item.opt("assignee") as? JSONObject
    val statusStyle = linearStatusStyle(status)
    val priorityStyle = linearPriorityStyle(priority)

    ToolCardRowSurface(
        modifier = Modifier
            .then(if (url != null) Modifier.clickable { open(url) } else Modifier)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = priorityStyle.text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = priorityStyle.color,
                modifier = Modifier.width(18.dp),
            )
            Icon(statusStyle.icon, contentDescription = null, tint = statusStyle.color, modifier = Modifier.size(16.dp))
            if (identifier.isNotBlank()) {
                Text(
                    text = identifier,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (!dueDate.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(Icons.Outlined.AccessTime, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(10.dp))
                    LtMetaText(dueDate)
                }
            }
            if (assignee != null) {
                val name = assignee.cardString("name") ?: "?"
                LtAvatar(name = name, avatarUrl = assignee.cardString("avatarUrl"), size = 20)
            }
        }
    }
}

// MARK: - socialPostFeed (Twitter)

@Composable
private fun SocialPostFeedCardView(data: JSONObject) {
    val posts = data.cardObjects("posts", "tweets", "items", "data")
    if (posts.isEmpty()) return
    val shown = posts.take(MAX_CARD_ITEMS)

    ToolCardSurface {
        ToolCardHeader(title = stringResource(R.string.screen_room_timeline_tool_card_posts), count = posts.size)
        DividedList(shown) { post -> PostRow(post) }
        if (posts.size > shown.size) {
            LtMetaText(stringResource(R.string.screen_room_timeline_tool_card_more_count, posts.size - shown.size))
        }
    }
}

private data class EngagementStat(val icon: ImageVector, val value: Int)

/** Engagement stats in iOS order: replies, reposts, likes, views. */
private fun engagementStats(post: JSONObject): List<EngagementStat> {
    val stats = mutableListOf<EngagementStat>()
    post.cardInt("replies")?.let { stats.add(EngagementStat(Icons.Outlined.ChatBubbleOutline, it)) }
    post.cardInt("reposts")?.let { stats.add(EngagementStat(Icons.Outlined.Repeat, it)) }
    post.cardInt("likes")?.let { stats.add(EngagementStat(Icons.Outlined.FavoriteBorder, it)) }
    post.cardInt("views")?.let { stats.add(EngagementStat(Icons.Outlined.BarChart, it)) }
    return stats
}

/** Mirror iOS formatCount: 1.2M / 5.6k / raw. */
private fun formatSocialCount(n: Int): String = when {
    n >= 1_000_000 -> String.format("%.1fM", n / 1_000_000.0)
    n >= 1000 -> String.format("%.1fk", n / 1000.0)
    else -> n.toString()
}

@Composable
private fun PostRow(post: JSONObject) {
    val authorObject = post.optJSONObject("author") ?: post.optJSONObject("user")
    val author = post.cardString("author") ?: authorObject?.cardString("name", "username", "screen_name") ?: stringResource(R.string.screen_room_timeline_tool_card_unknown)
    val handle = post.cardString("handle") ?: authorObject?.cardString("handle", "username", "screen_name")?.let { "@${it.removePrefix("@")}" }
    val avatarUrl = post.cardString("avatarUrl", "avatar_url", "profile_image_url") ?: authorObject?.cardString("avatarUrl", "avatar_url", "profile_image_url")
    val body = post.cardString("body", "text", "full_text", "content") ?: ""
    val createdAt = post.cardString("createdAt", "created_at")
    val verified = post.cardBool("verified") ?: false
    val stats = engagementStats(post)

    ToolCardRowSurface(
        modifier = Modifier
            .fillMaxWidth(),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LtAvatar(name = author, avatarUrl = avatarUrl, size = 36)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = author,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (verified) {
                        Icon(Icons.Filled.Verified, contentDescription = null, tint = Color(0xFF1D9BF0), modifier = Modifier.size(13.dp))
                    }
                    if (handle != null) {
                        LtMetaText(handle)
                    }
                    if (createdAt != null) {
                        LtMetaText("·")
                        LtMetaText(createdAt)
                    }
                }
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Engagement stats
        if (stats.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(start = 46.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                stats.forEach { stat ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(stat.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
                        LtMetaText(formatSocialCount(stat.value))
                    }
                }
            }
        }
    }
}

// MARK: - Previews

@PreviewsDayNight
@Composable
internal fun LinearIssuesListCardPreview() = ElementPreview {
    LinearIssuesListCardView(
        data = JSONObject(
            """
            {
              "items": [
                { "identifier": "ENG-142", "title": "Add SSO support for enterprise accounts", "status": "in_progress",
                  "priority": "high", "url": "https://linear.app/unseal/issue/ENG-142",
                  "assignee": { "name": "Alice Chen", "avatarUrl": "https://avatar.vercel.sh/alice" }, "dueDate": "Jun 5" },
                { "identifier": "ENG-138", "title": "Fix audio clipping in meeting recordings", "status": "todo",
                  "priority": "urgent", "assignee": { "name": "Bob Martinez" } },
                { "identifier": "ENG-135", "title": "Migrate user preferences to new schema", "status": "done",
                  "priority": "medium", "assignee": { "name": "Carol Wu", "avatarUrl": "https://avatar.vercel.sh/carol" } },
                { "identifier": "ENG-130", "title": "Update onboarding flow copy", "status": "backlog", "priority": "low",
                  "url": "https://linear.app/unseal/issue/ENG-130" },
                { "identifier": "ENG-127", "title": "Remove deprecated v1 API endpoints", "status": "cancelled",
                  "priority": "none", "assignee": { "name": "Dan Kim" } }
              ]
            }
            """.trimIndent()
        ),
        onLinkClick = {},
    )
}

@PreviewsDayNight
@Composable
internal fun SocialPostFeedCardPreview() = ElementPreview {
    SocialPostFeedCardView(
        data = JSONObject(
            """
            {
              "posts": [
                { "author": "Unseal AI", "handle": "@unseal_ai", "verified": true, "createdAt": "2h",
                  "body": "Introducing our new AI meeting agent - joins your calls, takes notes, and follows up automatically. The future of meetings is here.",
                  "likes": 1420, "reposts": 312, "replies": 87, "views": 52300 },
                { "author": "Sarah Chen", "handle": "@sarahchen_dev", "createdAt": "5h",
                  "body": "Just tried @unseal_ai in our standup and it actually captured every action item correctly. Impressed.",
                  "likes": 234, "reposts": 18, "replies": 12, "views": 8900 },
                { "author": "TechCrunch", "handle": "@TechCrunch", "verified": true, "createdAt": "1d",
                  "body": "AI meeting assistants are having a moment. Here's why enterprises are paying attention.",
                  "likes": 5600, "reposts": 890, "replies": 203, "views": 284000 }
              ]
            }
            """.trimIndent()
        ),
    )
}

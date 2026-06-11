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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import org.json.JSONObject

/**
 * Native Material 3 renderers for the "primary" set of GitHub tool cards, ported field-for-field
 * from the iOS `ToolCardsIOS/GitHub` SwiftUI cards. Each card mirrors the iOS field set and
 * layout, expressed with the shared [ToolCardKit] helpers. Lists are capped at [MAX_CARD_ITEMS].
 *
 * Wiring into [ToolCard]/ToolCardDispatcher is done separately by the caller.
 */
@Composable
internal fun gitHubPrimaryCard(
    cardType: String,
    data: JSONObject,
    onLinkClick: () -> Unit,
): Boolean {
    return when (cardType) {
        "githubIssue" -> { GitHubIssueCardView(data, onLinkClick); true }
        "githubIssuesList" -> { GitHubIssuesListCardView(data, onLinkClick); true }
        "repoList" -> { GitHubRepoListCardView(data, onLinkClick); true }
        "release" -> { GitHubReleaseCardView(data); true }
        "orgsList" -> { GitHubOrgsListCardView(data, onLinkClick); true }
        "contributors" -> { GitHubContributorsCardView(data, onLinkClick); true }
        else -> false
    }
}

// MARK: - Shared helpers

/** Opens [url] via the platform handler and notifies the caller; no-op when [url] is blank/null. */
@Composable
private fun rememberLinkOpener(onLinkClick: () -> Unit): (String?) -> Unit {
    val uriHandler = LocalUriHandler.current
    return { url ->
        if (!url.isNullOrBlank()) {
            runCatching { uriHandler.openUri(url) }
            onLinkClick()
        }
    }
}

private fun JSONObject.issueUrl(): String? = cardString("url", "htmlUrl", "html_url")

/** Parses a `#rrggbb` (or `rrggbb`) hex string into a Compose [Color], or null if malformed. */
private fun parseHexColor(hex: String?): Color? {
    val h = hex?.removePrefix("#") ?: return null
    if (h.length != 6) return null
    val r = h.substring(0, 2).toIntOrNull(16) ?: return null
    val g = h.substring(2, 4).toIntOrNull(16) ?: return null
    val b = h.substring(4, 6).toIntOrNull(16) ?: return null
    return Color(red = r / 255f, green = g / 255f, blue = b / 255f)
}

/** GitHub label chip: tinted background derived from the label hex, luma-aware foreground. */
@Composable
private fun LabelBadge(label: JSONObject) {
    val name = label.cardString("name") ?: return
    val rgb = parseHexColor(label.cardString("color"))
    val bg = rgb?.copy(alpha = 0.25f) ?: MaterialTheme.colorScheme.secondaryContainer
    val fg = if (rgb != null) {
        val luma = rgb.red * 0.299f + rgb.green * 0.587f + rgb.blue * 0.114f
        if (luma > 0.6f) {
            Color(rgb.red * 0.5f, rgb.green * 0.5f, rgb.blue * 0.5f)
        } else {
            Color(minOf(rgb.red * 1.3f, 1f), minOf(rgb.green * 1.3f, 1f), minOf(rgb.blue * 1.3f, 1f))
        }
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    Text(
        text = name,
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        modifier = Modifier
            .background(bg, RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun MetaText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Avatar thumbnail mirroring iOS AvatarView; falls back to a tinted placeholder box. */
@Composable
private fun Avatar(url: String?, size: Int = 28) {
    if (url.isNullOrBlank()) {
        Box(
            modifier = Modifier
                .size(size.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp)),
        )
    } else {
        CardRemoteImage(url = url, modifier = Modifier.size(size.dp), corner = 6)
    }
}

// MARK: - githubIssue (single issue)

@Composable
private fun GitHubIssueCardView(data: JSONObject, onLinkClick: () -> Unit) {
    val title = data.cardString("title")
    val number = data.cardInt("number")
    if (title.isNullOrBlank() && number == null) return

    val state = data.cardString("state") ?: "open"
    val isClosed = state.lowercase() == "closed"
    val url = data.cardString("url")
    val author = data.cardString("author")
    val createdAt = data.cardString("createdAt")
    val body = data.cardString("body")
    val labels = data.cardObjects("labels")
    val assignee = (data.opt("assignee") as? JSONObject)
    val milestone = data.cardString("milestone")
    val commentCount = data.cardInt("commentCount")
    val open = rememberLinkOpener(onLinkClick)

    ToolCardSurface {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Issue",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            CardChip(
                text = if (isClosed) "Closed" else "Open",
                color = if (isClosed) Color(0xFF8250DF).copy(alpha = 0.12f) else Color(0xFF1A7F37).copy(alpha = 0.12f),
                contentColor = if (isClosed) Color(0xFF8250DF) else Color(0xFF1A7F37),
            )
        }

        // Issue row: title + #number, then Opened/Closed · createdAt · author
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (url != null) Modifier.clickable { open(url) } else Modifier)
                .padding(vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (number != null) {
                    Text(
                        text = "#$number",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetaText(if (isClosed) "Closed" else "Opened")
                createdAt?.let { MetaText(it) }
                author?.let { MetaText(it) }
            }
        }

        if (!body.isNullOrBlank()) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (labels.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                labels.forEach { LabelBadge(it) }
            }
        }

        val hasFooter = assignee != null || !milestone.isNullOrBlank() || (commentCount ?: 0) > 0
        if (hasFooter) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (assignee != null) {
                    val name = assignee.cardString("name") ?: "?"
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Avatar(assignee.cardString("avatarUrl"), size = 20)
                        MetaText(name)
                    }
                }
                Box(Modifier.weight(1f))
                if (!milestone.isNullOrBlank()) MetaText("⚑ $milestone")
                if ((commentCount ?: 0) > 0) MetaText("💬 $commentCount")
            }
        }
    }
}

// MARK: - githubIssuesList

@Composable
private fun GitHubIssuesListCardView(data: JSONObject, onLinkClick: () -> Unit) {
    val items = data.cardObjects("items")
    if (items.isEmpty()) return
    val shown = items.take(MAX_CARD_ITEMS)
    val open = rememberLinkOpener(onLinkClick)

    ToolCardSurface {
        ToolCardHeader(title = "Issues", count = items.size)
        DividedList(shown) { issue -> IssueRow(issue, open) }
        if (items.size > shown.size) {
            MetaText("+${items.size - shown.size} more")
        }
    }
}

@Composable
private fun IssueRow(issue: JSONObject, open: (String?) -> Unit) {
    val title = issue.cardString("title") ?: "Untitled"
    val number = issue.cardInt("number")
    val author = issue.cardString("author")
    val updatedAt = issue.cardString("updatedAt")
    val commentCount = issue.cardInt("commentCount")
    val labels = issue.cardObjects("labels")
    val url = issue.issueUrl()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (url != null) Modifier.clickable { open(url) } else Modifier)
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (labels.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                labels.take(4).forEach { LabelBadge(it) }
                if (labels.size > 4) MetaText("+${labels.size - 4}")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            number?.let { MetaText("#$it") }
            author?.let { MetaText(it) }
            updatedAt?.let { MetaText(it) }
            Box(Modifier.weight(1f))
            if ((commentCount ?: 0) > 0) MetaText("💬 $commentCount")
        }
    }
}

// MARK: - repoList

@Composable
private fun GitHubRepoListCardView(data: JSONObject, onLinkClick: () -> Unit) {
    val repos = data.cardObjects("repositories")
    if (repos.isEmpty()) return
    val shown = repos.take(MAX_CARD_ITEMS)
    val open = rememberLinkOpener(onLinkClick)

    ToolCardSurface {
        ToolCardHeader(title = "Repositories", count = repos.size)
        DividedList(shown) { repo -> RepoRow(repo, open) }
        if (repos.size > shown.size) {
            MetaText("+${repos.size - shown.size} more")
        }
    }
}

@Composable
private fun RepoRow(repo: JSONObject, open: (String?) -> Unit) {
    val name = repo.cardString("fullName", "name") ?: ""
    val desc = repo.cardString("description")
    val lang = repo.cardString("language")
    val stars = repo.cardInt("stargazersCount")
    val url = repo.cardString("htmlUrl", "html_url", "url")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (url != null) Modifier.clickable { open(url) } else Modifier)
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!desc.isNullOrBlank()) {
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!lang.isNullOrBlank() || stars != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!lang.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(langColor(lang)),
                        )
                        MetaText(lang)
                    }
                }
                if (stars != null) MetaText("★ ${formatCount(stars)}")
            }
        }
    }
}

private fun formatCount(n: Int): String {
    if (n < 1000) return n.toString()
    val k = n / 1000.0
    if (n >= 10_000) return "${k.toInt()}k"
    val formatted = String.format("%.1f", k)
    return if (formatted.endsWith(".0")) "${k.toInt()}k" else "${formatted}k"
}

private fun langColor(lang: String): Color = when (lang) {
    "TypeScript" -> Color(0xFF3178C6)
    "JavaScript" -> Color(0xFFF1E05A)
    "Swift" -> Color(0xFFF05138)
    "Python" -> Color(0xFF3572A5)
    "Go" -> Color(0xFF00ADD8)
    "Rust" -> Color(0xFFDEA584)
    else -> Color(0xFF9E9E9E)
}

// MARK: - release

@Composable
private fun GitHubReleaseCardView(data: JSONObject) {
    val name = data.cardString("name") ?: ""
    val tagName = data.cardString("tagName") ?: ""
    if (name.isBlank() && tagName.isBlank()) return

    val body = data.cardString("body")
    val draft = data.cardBool("draft") ?: false
    val prerelease = data.cardBool("prerelease") ?: false
    val assetCount = data.cardObjects("assets").size

    ToolCardSurface {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Release",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (tagName.isNotBlank()) {
                Text(
                    text = tagName,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = name.ifBlank { tagName },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (draft) Text("Draft", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = Color(0xFFD29922))
            if (prerelease) Text("Pre", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = Color(0xFFBF8700))
        }
        if (!body.isNullOrBlank()) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (assetCount > 0) {
            MetaText("$assetCount asset${if (assetCount == 1) "" else "s"}")
        }
    }
}

// MARK: - orgsList

@Composable
private fun GitHubOrgsListCardView(data: JSONObject, onLinkClick: () -> Unit) {
    val orgs = data.cardObjects("organizations")
    if (orgs.isEmpty()) return
    val shown = orgs.take(MAX_CARD_ITEMS)
    val open = rememberLinkOpener(onLinkClick)

    ToolCardSurface {
        ToolCardHeader(title = "Organizations", count = orgs.size)
        DividedList(shown) { org -> OrgRow(org, open) }
        if (orgs.size > shown.size) {
            MetaText("+${orgs.size - shown.size} more")
        }
    }
}

@Composable
private fun OrgRow(org: JSONObject, open: (String?) -> Unit) {
    val login = org.cardString("login") ?: "unknown"
    val description = org.cardString("description")
    val avatarUrl = org.cardString("avatarUrl")
    val url = org.cardString("url")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (url != null) Modifier.clickable { open(url) } else Modifier)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(avatarUrl, size = 30)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = login,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// MARK: - contributors

@Composable
private fun GitHubContributorsCardView(data: JSONObject, onLinkClick: () -> Unit) {
    val contributors = data.cardObjects("contributors")
        .sortedByDescending { it.cardInt("contributions") ?: 0 }
    if (contributors.isEmpty()) return
    val shown = contributors.take(MAX_CARD_ITEMS)
    val open = rememberLinkOpener(onLinkClick)

    ToolCardSurface {
        ToolCardHeader(title = "Contributors", count = contributors.size)
        DividedList(shown) { c -> ContributorRow(c, open) }
        if (contributors.size > shown.size) {
            MetaText("+${contributors.size - shown.size} more")
        }
    }
}

@Composable
private fun ContributorRow(c: JSONObject, open: (String?) -> Unit) {
    val login = c.cardString("login") ?: "unknown"
    val contributions = c.cardInt("contributions")
    val avatarUrl = c.cardString("avatarUrl")
    val url = c.cardString("htmlUrl", "html_url", "url")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (url != null) Modifier.clickable { open(url) } else Modifier)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(avatarUrl, size = 28)
        Text(
            text = login,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (contributions != null) {
            Text(
                text = contributions.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// MARK: - Previews

@PreviewsDayNight
@Composable
internal fun GitHubIssueCardPreview() = ElementPreview {
    GitHubIssueCardView(
        data = JSONObject(
            """
            {
              "title": "Audio dropout when E2EE key rotation happens during active speech",
              "number": 142,
              "state": "open",
              "url": "https://github.com/unseal-network/unseal-agents/issues/142",
              "author": "jelf",
              "createdAt": "2 days ago",
              "body": "When a participant joins mid-meeting and triggers SFrame key rotation, there's a ~200ms audio gap.",
              "labels": [
                { "name": "bug", "color": "d73a4a" },
                { "name": "audio-pipeline", "color": "0075ca" },
                { "name": "P1", "color": "e4e669" }
              ],
              "assignee": { "name": "jelf", "avatarUrl": "https://github.com/jelf.png" },
              "milestone": "v2.8.0",
              "commentCount": 7
            }
            """.trimIndent()
        ),
        onLinkClick = {},
    )
}

@PreviewsDayNight
@Composable
internal fun GitHubIssuesListCardPreview() = ElementPreview {
    GitHubIssuesListCardView(
        data = JSONObject(
            """
            {
              "items": [
                { "number": 142, "title": "Audio dropout when switching E2EE keys", "state": "open", "author": "jelf",
                  "labels": [{ "name": "bug", "color": "d73a4a" }, { "name": "audio", "color": "0075ca" }],
                  "commentCount": 5, "updatedAt": "2d ago", "url": "https://github.com/x/y/issues/142" },
                { "number": 139, "title": "Support multiple CORS origins in agent-server", "state": "closed", "author": "dependabot",
                  "labels": [{ "name": "enhancement", "color": "a2eeef" }], "commentCount": 2, "updatedAt": "5d ago" },
                { "number": 137, "title": "Gemini reconnect storm under high concurrency", "state": "open", "author": "jelf",
                  "labels": [{ "name": "bug", "color": "d73a4a" }, { "name": "P1", "color": "b60205" }], "commentCount": 12, "updatedAt": "1w ago" }
              ]
            }
            """.trimIndent()
        ),
        onLinkClick = {},
    )
}

@PreviewsDayNight
@Composable
internal fun GitHubRepoListCardPreview() = ElementPreview {
    GitHubRepoListCardView(
        data = JSONObject(
            """
            {
              "repositories": [
                { "fullName": "unseal-network/unseal-agent", "description": "AI meeting agents", "language": "TypeScript", "stargazersCount": 12800, "htmlUrl": "https://github.com/unseal-network/unseal-agent" },
                { "fullName": "unseal-network/unseal-ios", "description": "iOS client", "language": "Swift", "stargazersCount": 428 }
              ]
            }
            """.trimIndent()
        ),
        onLinkClick = {},
    )
}

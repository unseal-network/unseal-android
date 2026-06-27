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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.element.android.features.messages.impl.R
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import org.json.JSONObject

// MARK: - Semantic status colors (mirror iOS GitHub card RGB values)

private val StatusSuccess = Color(red = 0.20f, green = 0.78f, blue = 0.35f)
private val StatusFailure = Color(red = 0.95f, green = 0.26f, blue = 0.21f)
private val StatusPending = Color(red = 0.98f, green = 0.74f, blue = 0.18f)

/**
 * Renders a GitHub tool card by [cardType], mirroring the iOS ToolCardsIOS GitHub cards in
 * Material 3. Returns true if a matching card rendered; false lets the dispatcher fall back.
 *
 * List keys follow the iOS prop names: checkRuns / deployments / notifications / alerts /
 * workflows / comments / commits / files. Lists are capped at [MAX_CARD_ITEMS].
 */
@Composable
internal fun gitHubActivityCard(cardType: String, data: JSONObject, onLinkClick: () -> Unit): Boolean {
    return when (cardType) {
        "checkRuns" -> CheckRunsCard(data, onLinkClick)
        "commitComparison" -> CommitComparisonCard(data)
        "deployments" -> DeploymentsCard(data)
        "notifications" -> NotificationsCard(data, onLinkClick)
        "secretAlerts" -> SecretAlertsCard(data, onLinkClick)
        "workflows" -> WorkflowsCard(data, onLinkClick)
        "commentThread" -> CommentThreadCard(data)
        else -> false
    }
}

// MARK: - Check Runs

@Composable
private fun CheckRunsCard(data: JSONObject, onLinkClick: () -> Unit): Boolean {
    val runs = data.cardObjects("checkRuns", "check_runs", "items")
    if (runs.isEmpty()) return false
    val summary = checkRunsSummary(runs)
    ToolCardSurface {
        ToolCardHeader(title = stringResource(R.string.screen_room_timeline_tool_card_check_runs))
        CardChip(text = summary.first, color = summary.second.copy(alpha = 0.12f), contentColor = summary.second)
        DividedList(runs.take(MAX_CARD_ITEMS)) { run -> CheckRunRow(run, onLinkClick) }
        MoreRow(runs.size)
    }
    return true
}

private fun checkRunsSummary(runs: List<JSONObject>): Pair<String, Color> {
    var completed = 0
    var failed = 0
    var passed = 0
    for (run in runs) {
        if (run.cardString("status") == "completed") {
            completed++
            when (run.cardString("conclusion")) {
                "failure" -> failed++
                "success" -> passed++
            }
        }
    }
    val pending = runs.size - completed
    return when {
        failed > 0 -> "$failed failing" to StatusFailure
        pending > 0 -> "$pending pending" to StatusPending
        passed == runs.size -> "All passed" to StatusSuccess
        else -> "$completed/${runs.size}" to StatusPending
    }
}

@Composable
private fun CheckRunRow(run: JSONObject, onLinkClick: () -> Unit) {
    val name = run.cardString("name") ?: stringResource(R.string.screen_room_timeline_tool_card_unknown)
    val appName = run.optJSONObject("app")?.cardString("name")
    val status = run.cardString("status")
    val conclusion = run.cardString("conclusion")
    val url = run.cardString("htmlUrl", "html_url", "url")
    val dotColor = when {
        status == "queued" || status == "in_progress" -> StatusPending
        conclusion == "success" -> StatusSuccess
        conclusion == "failure" -> StatusFailure
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    LinkableRow(url = url, onLinkClick = onLinkClick) {
        StatusDot(dotColor)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            RowTitle(name)
            appName?.let { Subtle(it) }
        }
    }
}

// MARK: - Commit Comparison

@Composable
private fun CommitComparisonCard(data: JSONObject): Boolean {
    val status = data.cardString("status")
    val aheadBy = data.cardInt("aheadBy") ?: 0
    val behindBy = data.cardInt("behindBy") ?: 0
    val commits = data.cardObjects("commits")
    val files = data.cardObjects("files")
    if (status == null && commits.isEmpty() && files.isEmpty()) return false

    val statusColor = when (status) {
        "ahead" -> StatusSuccess
        "behind" -> StatusFailure
        "diverged" -> StatusPending
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    ToolCardSurface {
        ToolCardHeader(title = stringResource(R.string.screen_room_timeline_tool_card_commit_comparison))
        status?.let { CardChip(text = it.replaceFirstChar { c -> c.uppercase() }, color = statusColor.copy(alpha = 0.12f), contentColor = statusColor) }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (aheadBy > 0) Text(stringResource(R.string.screen_room_timeline_tool_card_ahead, aheadBy), style = MaterialTheme.typography.labelSmall, color = StatusSuccess)
            if (behindBy > 0) Text(stringResource(R.string.screen_room_timeline_tool_card_behind, behindBy), style = MaterialTheme.typography.labelSmall, color = StatusFailure)
        }
        if (commits.isNotEmpty()) {
            SectionLabel(stringResource(R.string.screen_room_timeline_tool_card_commits).uppercase())
            commits.take(MAX_CARD_ITEMS).forEach { CommitRow(it) }
        }
        if (files.isNotEmpty()) {
            SectionLabel(stringResource(R.string.screen_room_timeline_tool_card_files_changed).uppercase())
            files.take(5).forEach { FileRow(it) }
            if (files.size > 5) Subtle(stringResource(R.string.screen_room_timeline_tool_card_more_count, files.size - 5))
        }
    }
    return true
}

@Composable
private fun CommitRow(commit: JSONObject) {
    val sha = commit.cardString("sha")?.take(7) ?: ""
    val message = (commit.cardString("message") ?: "").substringBefore("\n")
    val login = commit.optJSONObject("author")?.cardString("login") ?: ""
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(sha, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(modifier = Modifier.weight(1f)) {
            RowTitle(message)
            if (login.isNotEmpty()) Subtle(login)
        }
    }
}

@Composable
private fun FileRow(file: JSONObject) {
    val name = file.cardString("filename") ?: ""
    val adds = file.cardInt("additions") ?: 0
    val dels = file.cardInt("deletions") ?: 0
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (adds > 0) Text("+$adds", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = StatusSuccess)
        if (dels > 0) Text("-$dels", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = StatusFailure)
    }
}

// MARK: - Deployments

@Composable
private fun DeploymentsCard(data: JSONObject): Boolean {
    val deployments = data.cardObjects("deployments", "items")
    if (deployments.isEmpty()) return false
    ToolCardSurface {
        ToolCardHeader(title = stringResource(R.string.screen_room_timeline_tool_card_deployments), count = deployments.size)
        DividedList(deployments.take(MAX_CARD_ITEMS)) { DeploymentRow(it) }
        MoreRow(deployments.size)
    }
    return true
}

@Composable
private fun DeploymentRow(dep: JSONObject) {
    val env = dep.cardString("environment") ?: stringResource(R.string.screen_room_timeline_tool_card_unknown_user)
    val ref = dep.cardString("ref")
    val login = dep.optJSONObject("creator")?.cardString("login")
    val isProd = dep.cardBool("productionEnvironment") ?: false
    ToolCardRowSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusDot(if (isProd) StatusSuccess else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    RowTitle(env)
                    ref?.let { MonoTag(it) }
                }
                login?.let { Subtle(it) }
            }
        }
    }
}

// MARK: - Notifications

@Composable
private fun NotificationsCard(data: JSONObject, onLinkClick: () -> Unit): Boolean {
    val notifications = data.cardObjects("notifications", "items")
    if (notifications.isEmpty()) return false
    ToolCardSurface {
        ToolCardHeader(title = stringResource(R.string.screen_room_timeline_tool_card_notifications), count = notifications.size)
        DividedList(notifications.take(MAX_CARD_ITEMS)) { NotificationRow(it, onLinkClick) }
        MoreRow(notifications.size)
    }
    return true
}

@Composable
private fun NotificationRow(n: JSONObject, onLinkClick: () -> Unit) {
    val subject = n.optJSONObject("subject")
    val repository = n.optJSONObject("repository")
    val title = subject?.cardString("title") ?: ""
    val type = subject?.cardString("type") ?: ""
    val repoName = repository?.cardString("fullName") ?: ""
    val repoUrl = repository?.cardString("htmlUrl", "html_url")
    val unread = n.cardBool("unread") ?: false
    LinkableRow(url = repoUrl, onLinkClick = onLinkClick) {
        StatusDot(if (unread) MaterialTheme.colorScheme.primary else Color.Transparent)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            RowTitle(title)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (type.isNotEmpty()) Subtle(type)
                if (repoName.isNotEmpty()) Subtle(repoName)
            }
        }
    }
}

// MARK: - Secret Alerts

@Composable
private fun SecretAlertsCard(data: JSONObject, onLinkClick: () -> Unit): Boolean {
    val alerts = data.cardObjects("alerts", "secretAlerts", "secret_alerts", "items")
    if (alerts.isEmpty()) return false
    ToolCardSurface {
        ToolCardHeader(title = stringResource(R.string.screen_room_timeline_tool_card_secret_alerts), count = alerts.size)
        DividedList(alerts.take(MAX_CARD_ITEMS)) { SecretAlertRow(it, onLinkClick) }
        MoreRow(alerts.size)
    }
    return true
}

@Composable
private fun SecretAlertRow(alert: JSONObject, onLinkClick: () -> Unit) {
    val state = alert.cardString("state") ?: "open"
    val url = alert.cardString("htmlUrl", "html_url")
    val secretType = alert.cardString("secretTypeDisplayName") ?: alert.cardString("secretType") ?: ""
    val leaked = alert.cardBool("publiclyLeaked") ?: false
    val resolved = state == "resolved"
    val color = if (resolved) StatusSuccess else StatusFailure
    LinkableRow(url = url, onLinkClick = onLinkClick) {
        StatusDot(color)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            RowTitle(secretType)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(state.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall, color = color)
                if (leaked) Text(stringResource(R.string.screen_room_timeline_tool_card_leaked), style = MaterialTheme.typography.labelSmall, color = StatusFailure)
            }
        }
    }
}

// MARK: - Workflows

@Composable
private fun WorkflowsCard(data: JSONObject, onLinkClick: () -> Unit): Boolean {
    val workflows = data.cardObjects("workflows", "items")
    if (workflows.isEmpty()) return false
    ToolCardSurface {
        ToolCardHeader(title = stringResource(R.string.screen_room_timeline_tool_card_workflows), count = workflows.size)
        DividedList(workflows.take(MAX_CARD_ITEMS)) { WorkflowRow(it, onLinkClick) }
        MoreRow(workflows.size)
    }
    return true
}

@Composable
private fun WorkflowRow(wf: JSONObject, onLinkClick: () -> Unit) {
    val name = wf.cardString("name") ?: stringResource(R.string.screen_room_timeline_tool_card_untitled)
    val state = wf.cardString("state") ?: "active"
    val path = wf.cardString("path")
    val url = wf.cardString("htmlUrl", "html_url")
    val stateColor = when (state) {
        "active" -> StatusSuccess
        "deleted" -> StatusFailure
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    LinkableRow(url = url, onLinkClick = onLinkClick) {
        StatusDot(stateColor)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            RowTitle(name)
            path?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// MARK: - Comment Thread

@Composable
private fun CommentThreadCard(data: JSONObject): Boolean {
    val comments = data.cardObjects("comments", "items")
    if (comments.isEmpty()) return false
    val prTitle = data.cardString("prTitle")
    val prNumber = data.cardInt("prNumber")
    val repo = data.cardString("repo")
    ToolCardSurface {
        ToolCardHeader(title = stringResource(R.string.screen_room_timeline_tool_card_comments), count = comments.size)
        if (prTitle != null || prNumber != null) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                prTitle?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                prNumber?.let { Subtle("#$it") }
                Spacer(Modifier.weight(1f))
                repo?.let { Subtle(it) }
            }
        }
        DividedList(comments.take(MAX_CARD_ITEMS)) { CommentRow(it) }
        MoreRow(comments.size)
    }
    return true
}

@Composable
private fun CommentRow(comment: JSONObject) {
    val author = comment.cardString("author") ?: stringResource(R.string.screen_room_timeline_tool_card_unknown_user)
    val avatarUrl = comment.cardString("avatarUrl")
    val body = comment.cardString("body") ?: ""
    val createdAt = comment.cardString("createdAt")
    val association = comment.cardString("association")
    val reactions = comment.cardObjects("reactions")
    ToolCardRowSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!avatarUrl.isNullOrBlank()) {
                CardRemoteImage(url = avatarUrl, modifier = Modifier.size(28.dp).clip(CircleShape), corner = 14)
            } else {
                Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
                    Text(author.take(1).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(author, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                    if (!association.isNullOrBlank()) MonoTag(association)
                    Spacer(Modifier.weight(1f))
                    createdAt?.let { Subtle(it) }
                }
                if (body.isNotEmpty()) {
                    Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
                if (reactions.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        reactions.forEach { r ->
                            val emoji = r.cardString("emoji") ?: ""
                            val count = r.cardInt("count") ?: 0
                            if (emoji.isNotEmpty()) CardChip(text = "$emoji $count")
                        }
                    }
                }
            }
        }
    }
}

// MARK: - Shared row primitives

@Composable
private fun LinkableRow(url: String?, onLinkClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val uriHandler = LocalUriHandler.current
    ToolCardRowSurface(
        modifier = Modifier
            .then(
                if (url != null) {
                    Modifier.clickable {
                        onLinkClick()
                        runCatching { uriHandler.openUri(url) }
                    }
                } else {
                    Modifier
                }
            ),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
private fun StatusDot(color: Color) {
    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
}

@Composable
private fun RowTitle(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun Subtle(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun MonoTag(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

@Composable
private fun MoreRow(total: Int) {
    if (total > MAX_CARD_ITEMS) {
        Text(
            text = stringResource(R.string.screen_room_timeline_tool_card_more_count, total - MAX_CARD_ITEMS),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// MARK: - Previews

@PreviewsDayNight
@Composable
internal fun GitHubCheckRunsCardPreview() = ElementPreview {
    val data = JSONObject(
        """
        {"checkRuns":[
          {"name":"build / typecheck","status":"completed","conclusion":"success","app":{"name":"GitHub Actions"},"htmlUrl":"https://github.com/runs/1"},
          {"name":"test / unit-tests","status":"completed","conclusion":"failure","app":{"name":"GitHub Actions"},"htmlUrl":"https://github.com/runs/2"},
          {"name":"lint / ultracite","status":"completed","conclusion":"success","app":{"name":"GitHub Actions"}}
        ]}
        """.trimIndent()
    )
    gitHubActivityCard(cardType = "checkRuns", data = data, onLinkClick = {})
}

@PreviewsDayNight
@Composable
internal fun GitHubCommentThreadCardPreview() = ElementPreview {
    val data = JSONObject(
        """
        {"prTitle":"Fix token refresh race condition","prNumber":187,"repo":"unseal-network/agents","comments":[
          {"author":"octocat","avatarUrl":"https://avatars.githubusercontent.com/u/583231?v=4","body":"Looks great! Add error handling for the token-expiry edge case.","createdAt":"2h ago","association":"OWNER","reactions":[{"emoji":"👍","count":3}]},
          {"author":"hubot","body":"Good catch! Updated the retry logic in the latest commit.","createdAt":"1h ago","association":"COLLABORATOR"}
        ]}
        """.trimIndent()
    )
    gitHubActivityCard(cardType = "commentThread", data = data, onLinkClick = {})
}

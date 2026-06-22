/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import android.content.res.Configuration
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import io.element.android.features.messages.impl.timeline.model.event.AiDataStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiTextStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiToolCardEntry
import io.element.android.features.messages.impl.timeline.model.event.AiToolStreamPart
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemAiContent
import io.element.android.features.messages.impl.timeline.model.event.ToolCallRootRenderModel
import io.element.android.wysiwyg.link.Link
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.persistentListOf

@Composable
internal fun AgentStreamParityPreview(
    content: TimelineItemAiContent,
    modifier: Modifier = Modifier,
) {
    TimelineItemAiView(
        content = content,
        onLinkClick = { _: Link -> },
        onLinkLongClick = { _: Link -> },
        onLongClick = {},
        modifier = modifier.width(360.dp),
    )
}

@Preview(name = "Day", group = "Agent Stream Tool Cards", widthDp = 390, heightDp = 760)
@Preview(name = "Night", group = "Agent Stream Tool Cards", widthDp = 390, heightDp = 760, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun AgentStreamToolCardParityPreview(
    @PreviewParameter(AgentStreamParityContentProvider::class) content: TimelineItemAiContent,
) {
    AgentStreamParityPreview(content = content)
}

private class AgentStreamParityContentProvider : PreviewParameterProvider<TimelineItemAiContent> {
    override val values: Sequence<TimelineItemAiContent> = agentStreamParityPreviewContents().asSequence()
}

internal fun agentStreamParityPreviewContents(): List<TimelineItemAiContent> = buildList {
    previewGroups().forEach { group ->
        group.entries.indices.forEach { selectedIndex ->
            add(group.toContent(selectedIndex))
        }
    }
    addAll(previewDataParts())
}

internal fun agentStreamParityPreviewInteractiveContents(): List<TimelineItemAiContent> = buildList {
    previewGroups().forEach { group ->
        add(group.toContent(selectedIndex = 0))
    }
    addAll(previewDataParts())
}

internal fun agentStreamParityPreviewContent(index: Int): TimelineItemAiContent? {
    return agentStreamParityPreviewContents().getOrNull(index)
}

private data class PreviewGroup(
    val id: String,
    val title: String,
    val entries: List<AiToolCardEntry>,
    val doneCount: Int = entries.count { it.state == "done" },
    val errorCount: Int = entries.count { it.state == "error" },
    val callingCount: Int = entries.count { it.state == "calling" },
)

private fun PreviewGroup.toContent(selectedIndex: Int): TimelineItemAiContent {
    val selected = entries[selectedIndex]
    val visibleParts = listOf(
        AiTextStreamPart(
            id = "$id-text",
            state = "done",
            text = "Fixture: ${selected.cardType}",
        ),
        AiToolStreamPart(
            id = selected.id,
            state = when (selected.state) {
                "done" -> "output-available"
                "error" -> "output-error"
                else -> "input-available"
            },
            toolName = selected.name,
            title = selected.name,
            input = null,
            output = selected.props,
            errorText = null,
        ),
    )
    return TimelineItemAiContent(
        body = "",
        isEdited = false,
        isStreaming = false,
        isTerminal = true,
        streamId = id,
        streamStatus = "completed",
        thinkingSteps = persistentListOf(),
        toolCalls = persistentListOf(),
        sources = persistentListOf(),
        quickActions = persistentListOf(),
        toolCardEntries = entries.toImmutableList(),
        toolCallRoot = ToolCallRootRenderModel(
            id = "$id-root-$selectedIndex",
            title = title,
            entries = entries.toImmutableList(),
            selectedIndex = selectedIndex,
            doneCount = doneCount,
            errorCount = errorCount,
            callingCount = callingCount,
            allFinished = callingCount == 0,
            isSingleTool = entries.size == 1,
            expandedByDefault = true,
        ),
        visibleParts = visibleParts.toImmutableList(),
        firstToolPartIndex = 1,
    )
}

private fun previewGroups(): List<PreviewGroup> = listOf(
    PreviewGroup(
        id = "composio-search-cards",
        title = "Tool Calls",
        entries = listOf(
            entry("flight", "Flights", "flightAlert", """{"_cardType":"flightAlert","flights":[{"airline":"Air China","flightNumber":"CA 4304","departureAirport":"CAN","arrivalAirport":"TFU","departureTime":"19:30","arrivalTime":"22:00","duration":"2h 30m","price":"$331","cabin":"Economy","stops":"Direct"}]}"""),
            entry("hotel", "Hotels", "hotelBooking", """{"_cardType":"hotelBooking","hotels":[{"name":"Feltone Chengdu","price":"$50","totalPrice":"$199","rating":4.6,"reviews":63,"address":"Chengdu Tianfu Square","stars":5,"amenities":["Breakfast ($)","Free Wi-Fi","Free parking","Indoor pool","Hot tub"],"images":["https://picsum.photos/seed/unseal-hotel-pool/320/220","https://picsum.photos/seed/unseal-hotel-room/320/220","https://picsum.photos/seed/unseal-hotel-lobby/320/220"],"url":"https://example.com/hotel"}]}"""),
            entry("headline", "Web Search", "headlineList", """{"_cardType":"headlineList","headlines":[{"title":"AI Builders Shanghai opens registration","snippet":"A June 2026 conference for applied AI teams.","source":"Shanghai Tech News","publishedAt":"Jun 12, 2026","url":"https://example.com/ai-builders","imageUrl":"https://picsum.photos/seed/unseal-news/240/160"}]}"""),
            entry("breaking", "Breaking", "breakingNews", """{"_cardType":"breakingNews","headline":"Major AI conference expands Shanghai venue","source":"TechWire","publishedAt":"Jun 18, 2026","summary":"Organizers added a second venue after developer demand exceeded capacity.","category":"AI","url":"https://example.com/breaking"}"""),
            entry("image", "Images", "imageGrid", """{"_cardType":"imageGrid","images":[{"title":"Hotel pool","thumbnail":"https://picsum.photos/seed/unseal-pool-thumb/240/240","url":"https://picsum.photos/seed/unseal-pool/640/480"},{"title":"Hotel lobby","thumbnail":"https://picsum.photos/seed/unseal-lobby-thumb/240/240","url":"https://picsum.photos/seed/unseal-lobby/640/480"}]}"""),
            entry("product", "Shopping", "productList", """{"_cardType":"productList","products":[{"title":"Sony WH-CH720N","thumbnail":"https://picsum.photos/seed/unseal-headphones/240/160","price":"$99.99","rating":4.6,"reviews":15000,"source":"Amazon","url":"https://example.com/sony"}]}"""),
            entry("finance", "Finance", "finance", """{"_cardType":"finance","quote":{"name":"Apple Inc.","symbol":"AAPL","price":"214.5","currency":"USD","exchange":"NASDAQ"},"graph":[{"date":"09:30","price":211.0},{"date":"16:00","price":214.5}]}"""),
            entry("event", "Events", "eventList", """{"_cardType":"eventList","events":[{"title":"AI Builders Shanghai","when":"Jun 20, 2026, 2:30 PM","venue":"West Bund Center","address":"Shanghai, Xuhui","thumbnail":"https://picsum.photos/seed/unseal-event/240/160","url":"https://example.com/event"}]}"""),
            entry("place", "Places", "placeList", """{"_cardType":"placeList","places":[{"name":"Quiet Coffee","type":"Cafe","rating":4.7,"reviews":128,"address":"Jing'an Temple, Shanghai","thumbnail":"https://picsum.photos/seed/unseal-cafe-thumb/240/160","imageUrls":["https://picsum.photos/seed/unseal-cafe/640/480"],"url":"https://maps.google.com/?q=31.223,121.445"}]}"""),
            entry("url", "Web Content", "urlContent", """{"_cardType":"urlContent","articles":[{"title":"Newsroom - Apple","url":"https://www.apple.com/newsroom/","content":"Latest Apple announcements and company news."}]}"""),
        ),
    ),
    PreviewGroup(
        id = "gmail-drive-cards",
        title = "Tool Calls",
        entries = listOf(
            entry("email-list", "Emails", "composeEmail", """{"_cardType":"composeEmail","messages":[{"subject":"Q3 planning meeting notes","from":{"name":"Jelf Liang","email":"jelf@unseal.ai"},"snippet":"Attached are the notes from today's Q3 planning session.","date":"Jun 15, 2026","url":"https://mail.google.com/mail/u/0/#inbox/1","labels":["Work","Planning"],"starred":true,"hasAttachments":true},{"subject":"Agent stream card parity","from":{"name":"Ruihan","email":"ruihan@unseal.ai"},"snippet":"Let's compare iOS and Android tool cards from the same stream.","date":"Jun 14, 2026","url":"https://mail.google.com/mail/u/0/#inbox/2","labels":["Engineering"],"hasAttachments":false}]}"""),
            entry("email-draft", "Draft", "composeEmail", """{"_cardType":"composeEmail","subject":"Follow up on card parity","from":{"name":"Ruihan","email":"ruihan@unseal.ai"},"to":[{"name":"Mobile Team","email":"mobile@unseal.ai"}],"body":"Please compare Android and iOS screenshots from the fixture run.","date":"Jun 18, 2026","labels":["Draft"],"hasAttachments":false}"""),
            entry("drive-files", "Files", "fileAttachment", """{"_cardType":"fileAttachment","title":"Drive files","files":[{"name":"Agent Stream Parity Plan","mimeType":"application/vnd.google-apps.document","webViewLink":"https://drive.google.com/file/d/doc-1/view","modifiedTime":"2026-06-15T09:30:00Z","size":"18432","owner":"Ruihan"},{"name":"Tool Card Screenshots","mimeType":"application/vnd.google-apps.folder","webViewLink":"https://drive.google.com/drive/folders/folder-1","modifiedTime":"2026-06-14T18:15:00Z","shared":true},{"name":"Fixture Matrix.csv","mimeType":"text/csv","webViewLink":"https://drive.google.com/file/d/csv-1/view","modifiedTime":"2026-06-13T11:20:00Z","size":"4096"}]}"""),
        ),
    ),
    PreviewGroup(
        id = "github-primary-cards",
        title = "Tool Calls",
        entries = listOf(
            entry("github-issue", "Issue", "githubIssue", """{"_cardType":"githubIssue","number":128,"title":"Android tool card parity","state":"open","html_url":"https://github.com/unseal/mobile/issues/128","labels":[{"name":"android"},{"name":"ui"}],"user":{"login":"rayson","avatar_url":"https://github.com/github.png"}}"""),
            entry("github-issues", "Issues", "githubIssuesList", """{"_cardType":"githubIssuesList","issues":[{"number":128,"title":"Android tool card parity","state":"open","html_url":"https://github.com/unseal/mobile/issues/128"},{"number":129,"title":"Markdown streaming flicker","state":"open","html_url":"https://github.com/unseal/mobile/issues/129"}]}"""),
            entry("repo", "Repositories", "repoList", """{"_cardType":"repoList","repositories":[{"full_name":"unseal/unseal-android","description":"Android client","stargazers_count":42,"forks_count":7,"language":"Kotlin","html_url":"https://github.com/unseal/unseal-android"}]}"""),
            entry("release", "Release", "release", """{"_cardType":"release","tag_name":"v1.2.0","name":"Tool Card Parity","body":"Align Android and iOS cards","draft":false,"html_url":"https://github.com/unseal/unseal-android/releases/tag/v1.2.0"}"""),
        ),
    ),
    PreviewGroup(
        id = "github-activity-cards",
        title = "Tool Calls",
        entries = listOf(
            entry("orgs", "Organizations", "orgsList", """{"_cardType":"orgsList","organizations":[{"login":"unseal","description":"Private agent clients","avatar_url":"https://github.com/github.png","url":"https://github.com/unseal"}]}"""),
            entry("contributors", "Contributors", "contributors", """{"_cardType":"contributors","contributors":[{"login":"rayson","contributions":84,"avatar_url":"https://github.com/github.png","html_url":"https://github.com/rayson"}]}"""),
            entry("checks", "Check Runs", "checkRuns", """{"_cardType":"checkRuns","checkRuns":[{"name":"unit","status":"completed","conclusion":"success"},{"name":"android build","status":"completed","conclusion":"failure"},{"name":"screenshots","status":"queued"}]}"""),
            entry("compare", "Commits", "commitComparison", """{"_cardType":"commitComparison","status":"ahead","ahead_by":3,"behind_by":0,"commits":[{"sha":"abc1234","message":"Align tool cards"}],"files":[{"filename":"ToolCard.kt","status":"modified","changes":120}]}"""),
            entry("deployments", "Deployments", "deployments", """{"_cardType":"deployments","deployments":[{"environment":"staging","state":"success","created_at":"2026-06-18T11:00:00Z"},{"environment":"production","state":"pending","created_at":"2026-06-18T12:00:00Z"}]}"""),
            entry("notifications", "Notifications", "notifications", """{"_cardType":"notifications","notifications":[{"unread":true,"reason":"mention","subject":{"title":"Review Android card layout","type":"PullRequest"},"repository":{"full_name":"unseal/unseal-android"},"updated_at":"2026-06-18T11:00:00Z"}]}"""),
            entry("secrets", "Secret Alerts", "secretAlerts", """{"_cardType":"secretAlerts","alerts":[{"state":"open","secret_type":"api_token","created_at":"2026-06-18T11:00:00Z"},{"state":"resolved","secret_type":"private_key","resolution":"revoked"}]}"""),
            entry("workflows", "Workflows", "workflows", """{"_cardType":"workflows","workflows":[{"name":"Android CI","state":"active","path":".github/workflows/android.yml"},{"name":"Release","state":"disabled_manually","path":".github/workflows/release.yml"}]}"""),
            entry("comments", "PR Comments", "commentThread", """{"_cardType":"commentThread","comments":[{"body":"Tags should live on a separate row.","user":{"login":"rayson","avatar_url":"https://github.com/github.png"},"created_at":"2026-06-18T11:00:00Z"}]}"""),
        ),
    ),
    PreviewGroup(
        id = "linear-twitter-schedule-cards",
        title = "Tool Calls",
        entries = listOf(
            entry("linear", "Issue", "linearIssue", """{"_cardType":"linearIssue","identifier":"MOB-42","title":"Android tool card long press is inconsistent","state":"In Progress","priority":2,"team":"Mobile","url":"https://linear.app/unseal/issue/MOB-42"}"""),
            entry("linear-list", "Issues", "linearIssuesList", """{"_cardType":"linearIssuesList","issues":[{"identifier":"MOB-42","title":"Long press selection priority","state":"In Progress","priority":2},{"identifier":"MOB-43","title":"Weather card light mode contrast","state":"Done","priority":1}]}"""),
            entry("social", "Posts", "socialPostFeed", """{"_cardType":"socialPostFeed","posts":[{"body":"Streaming markdown needs stable incremental rendering.","author":"Android Dev","handle":"androiddev","createdAt":"2026-06-18T11:00:00Z","likes":18,"replies":2}]}"""),
            entry("schedule-create", "Create Schedule", "createSchedule", """{"_cardType":"createSchedule","name":"Review Android weather card","cadence":"Every day at 10:00","timezone":"Asia/Shanghai","action":"Review weather card screenshots"}"""),
            entry("schedule-update", "Update Schedule", "updateSchedule", """{"_cardType":"updateSchedule","name":"Review Android weather card","cadence":"Every Friday at 15:30","timezone":"Asia/Shanghai","action":"Move review to Friday afternoon"}"""),
            entry("schedule-status", "Schedule Status", "updateScheduleStatus", """{"_cardType":"updateScheduleStatus","summary":"Review Android weather card","names":["Review Android weather card"],"isEnable":false}"""),
        ),
    ),
    PreviewGroup(
        id = "meta-subagent-cards",
        title = "Tool Calls",
        entries = listOf(
            entry("meta-agent-calling", "Research Agent", "generic", """{"_cardType":"generic","items":[{"title":"Research Agent","subtitle":"Collecting nested tool results"}]}""", state = "calling"),
            entry("meta-weather", "Weather", "weather", """{"_cardType":"weather","city":"Shanghai","current":{"condition":"Sunny","temperature":29},"forecast":[{"day":"Fri","high":31,"low":24,"condition":"Cloudy"}]}"""),
            entry("meta-github-error", "Issues", "githubIssuesList", """{"_cardType":"githubIssuesList","title":"GitHub token missing"}""", state = "error"),
            entry("meta-schedule", "Create Schedule", "createSchedule", """{"_cardType":"createSchedule","name":"Review parity fixture screenshots","cadence":"Every day at 09:00","timezone":"Asia/Shanghai","action":"Compare Android and iOS cards"}"""),
        ),
        doneCount = 2,
        errorCount = 1,
        callingCount = 1,
    ),
    PreviewGroup(
        id = "fallback-cards",
        title = "Tool Calls",
        entries = listOf(
            entry("generic", "Results", "generic", """{"_cardType":"generic","items":[{"title":"Generic result row","snippet":"Fallback list render for unknown but structured payloads.","url":"https://example.com/result"},{"title":"Second result","source":"Fixture"}]}"""),
            entry("json-spec-root", "JSON Spec", "jsonSpec", """{"_cardType":"jsonSpec","items":[{"title":"Spec section","subtitle":"Root card fallback for data-json-render tool output"}]}"""),
        ),
    ),
)

private fun previewDataParts(): List<TimelineItemAiContent> = listOf(
    emptyStreamingContent(),
    dataContent(
        id = "moltbook-register",
        label = "Fixture: moltbookRegister",
        part = AiDataStreamPart(
            id = "suspend-moltbook",
            state = "done",
            type = "data-tool-call-suspended",
            payload = """{"toolCallId":"moltbook-register-1","toolName":"moltbookRegister","targetUserId":"@ruihan:unseal.ai","title":"Connect Moltbook","reason":"Enter your Moltbook credentials to register this agent.","suspendPayload":{"kind":"moltbookRegister","agentId":"agent-mail","moltyName":"Mail Agent","claimUrl":"https://moltbook.example/claim/abc","verificationCode":"842193"}}""",
        ),
    ),
    dataContent(
        id = "json-spec",
        label = "Fixture: jsonSpec",
        part = AiDataStreamPart(
            id = "data-json-render",
            state = "done",
            type = "data-json-render",
            payload = """{"title":"Generated UI spec","summary":"Render a compact status panel for card parity QA.","items":[{"label":"Platform","value":"Android"},{"label":"Mode","value":"Day/Night"},{"label":"Status","value":"Ready"}]}""",
        ),
    ),
    dataContent(
        id = "stream-error-card",
        label = "Fixture: data-error-card",
        part = AiDataStreamPart(
            id = "data-error-card",
            state = "done",
            type = "data-error-card",
            payload = """{"title":"Tool execution failed","message":"The connector returned a permission error."}""",
        ),
    ),
)

private fun emptyStreamingContent(): TimelineItemAiContent {
    return TimelineItemAiContent(
        body = "loading",
        isEdited = false,
        isStreaming = true,
        isTerminal = false,
        streamId = "empty-stream-loading",
        streamStatus = "loading",
        thinkingSteps = persistentListOf(),
        toolCalls = persistentListOf(),
        sources = persistentListOf(),
        quickActions = persistentListOf(),
        visibleParts = persistentListOf(),
    )
}

private fun dataContent(
    id: String,
    label: String,
    part: AiDataStreamPart,
): TimelineItemAiContent {
    val visibleParts = listOf(
        AiTextStreamPart(id = "$id-text", state = "done", text = label),
        part,
    )
    return TimelineItemAiContent(
        body = "",
        isEdited = false,
        isStreaming = false,
        isTerminal = true,
        streamId = id,
        streamStatus = "completed",
        thinkingSteps = persistentListOf(),
        toolCalls = persistentListOf(),
        sources = persistentListOf(),
        quickActions = persistentListOf(),
        visibleParts = visibleParts.toImmutableList(),
    )
}

private fun entry(
    id: String,
    name: String,
    cardType: String,
    props: String,
    state: String = "done",
): AiToolCardEntry = AiToolCardEntry(
    id = id,
    name = name,
    cardType = cardType,
    state = state,
    props = props,
)

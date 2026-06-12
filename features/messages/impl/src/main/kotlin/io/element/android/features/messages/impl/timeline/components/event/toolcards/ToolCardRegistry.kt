/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event.toolcards

internal data class ToolRegistryEntry(
    val cardType: String,
    val displayName: String,
)

internal val ROOT_DISPATCH_CARD_TYPES = setOf(
    "checkRuns",
    "commentThread",
    "commitComparison",
    "contributors",
    "deployments",
    "githubIssue",
    "notifications",
    "orgsList",
    "release",
    "repoList",
    "secretAlerts",
    "workflows",
    "githubIssuesList",
    "breakingNews",
    "flightAlert",
    "headlineList",
    "imageGrid",
    "productList",
    "finance",
    "eventList",
    "placeList",
    "urlContent",
    "hotelBooking",
    "linearIssue",
    "linearIssuesList",
    "composeEmail",
    "fileAttachment",
    "socialPostFeed",
    "createSchedule",
    "updateSchedule",
    "updateScheduleStatus",
    "generic",
)

internal val STANDALONE_SUSPENDED_CARD_TYPES = setOf("moltbookRegister")

internal val IGNORED_TOOL_NAMES = setOf("COMPOSIO_SEARCH_TOOLS")

internal val META_TOOL_NAMES = setOf("COMPOSIO_MULTI_EXECUTE_TOOL")

internal val KNOWN_LIST_KEYS = listOf("items", "results", "data", "files", "issues", "repositories", "messages", "posts", "events")

internal val TOOL_CARD_REGISTRY_WITH_DISPLAY = mapOf(
    "COMPOSIO_SEARCH_FLIGHTS" to ToolRegistryEntry("flightAlert", "Flights"),
    "COMPOSIO_SEARCH_HOTELS" to ToolRegistryEntry("hotelBooking", "Hotels"),
    "COMPOSIO_SEARCH_NEWS" to ToolRegistryEntry("headlineList", "News"),
    "COMPOSIO_SEARCH_WEB" to ToolRegistryEntry("headlineList", "Web Search"),
    "COMPOSIO_SEARCH_TAVILY" to ToolRegistryEntry("headlineList", "Search"),
    "COMPOSIO_SEARCH_SCHOLAR" to ToolRegistryEntry("headlineList", "Scholar"),
    "COMPOSIO_SEARCH_IMAGE" to ToolRegistryEntry("imageGrid", "Images"),
    "COMPOSIO_SEARCH_SHOPPING" to ToolRegistryEntry("productList", "Shopping"),
    "COMPOSIO_SEARCH_AMAZON" to ToolRegistryEntry("productList", "Amazon"),
    "COMPOSIO_SEARCH_WALMART" to ToolRegistryEntry("productList", "Walmart"),
    "COMPOSIO_SEARCH_FINANCE" to ToolRegistryEntry("finance", "Finance"),
    "COMPOSIO_SEARCH_EVENT" to ToolRegistryEntry("eventList", "Events"),
    "COMPOSIO_SEARCH_GOOGLE_MAPS" to ToolRegistryEntry("placeList", "Places"),
    "COMPOSIO_SEARCH_FETCH_URL_CONTENT" to ToolRegistryEntry("urlContent", "Web Content"),
    "GITHUB_LIST_REPOSITORY_ISSUES" to ToolRegistryEntry("githubIssuesList", "Issues"),
    "GITHUB_SEARCH_ISSUES_AND_PULL_REQUESTS" to ToolRegistryEntry("githubIssuesList", "Issues & PRs"),
    "GITHUB_LIST_PULL_REQUESTS" to ToolRegistryEntry("githubIssuesList", "Pull Requests"),
    "GITHUB_CREATE_AN_ISSUE" to ToolRegistryEntry("githubIssue", "Issue"),
    "GITHUB_GET_AN_ISSUE" to ToolRegistryEntry("githubIssue", "Issue"),
    "GITHUB_LIST_CHECK_RUNS_FOR_A_REF" to ToolRegistryEntry("checkRuns", "Check Runs"),
    "GITHUB_COMPARE_TWO_COMMITS" to ToolRegistryEntry("commitComparison", "Commits"),
    "GITHUB_LIST_REPOSITORY_CONTRIBUTORS" to ToolRegistryEntry("contributors", "Contributors"),
    "GITHUB_LIST_DEPLOYMENTS" to ToolRegistryEntry("deployments", "Deployments"),
    "GITHUB_LIST_NOTIFICATIONS" to ToolRegistryEntry("notifications", "Notifications"),
    "GITHUB_LIST_ORGANIZATIONS_FOR_A_USER" to ToolRegistryEntry("orgsList", "Organizations"),
    "GITHUB_LIST_ORGANIZATIONS_FOR_THE_AUTHENTICATED_USER" to ToolRegistryEntry("orgsList", "Organizations"),
    "GITHUB_CREATE_A_RELEASE" to ToolRegistryEntry("release", "Release"),
    "GITHUB_FIND_REPOSITORIES" to ToolRegistryEntry("repoList", "Repositories"),
    "GITHUB_SEARCH_REPOSITORIES" to ToolRegistryEntry("repoList", "Repositories"),
    "GITHUB_LIST_REPOSITORIES_STARRED_BY_THE_AUTHENTICATED_USER" to ToolRegistryEntry("repoList", "Starred"),
    "GITHUB_LIST_SECRET_SCANNING_ALERTS_FOR_A_REPOSITORY" to ToolRegistryEntry("secretAlerts", "Secret Alerts"),
    "GITHUB_LIST_REPOSITORY_WORKFLOWS" to ToolRegistryEntry("workflows", "Workflows"),
    "GITHUB_CREATE_AN_ISSUE_COMMENT" to ToolRegistryEntry("commentThread", "Comments"),
    "GITHUB_LIST_REVIEW_COMMENTS_ON_A_PULL_REQUEST" to ToolRegistryEntry("commentThread", "PR Comments"),
    "GMAIL_FETCH_MESSAGE_BY_MESSAGE_ID" to ToolRegistryEntry("composeEmail", "Email"),
    "GMAIL_FETCH_EMAILS" to ToolRegistryEntry("composeEmail", "Emails"),
    "GMAIL_CREATE_EMAIL_DRAFT" to ToolRegistryEntry("composeEmail", "Draft"),
    "GOOGLEDRIVE_FIND_FILE" to ToolRegistryEntry("fileAttachment", "Files"),
    "GOOGLEDRIVE_GET_FILE_METADATA" to ToolRegistryEntry("fileAttachment", "File"),
    "LINEAR_CREATE_LINEAR_ISSUE" to ToolRegistryEntry("linearIssue", "Issue"),
    "LINEAR_LIST_LINEAR_ISSUES" to ToolRegistryEntry("linearIssuesList", "Issues"),
    "TWITTER_USER_HOME_TIMELINE_BY_USER_ID" to ToolRegistryEntry("socialPostFeed", "Timeline"),
    "TWITTER_FULL_ARCHIVE_SEARCH" to ToolRegistryEntry("socialPostFeed", "Posts"),
    "TWITTER_POST_LOOKUP_BY_POST_ID" to ToolRegistryEntry("socialPostFeed", "Post"),
    "createSchedule" to ToolRegistryEntry("createSchedule", "Create Schedule"),
    "updateSchedule" to ToolRegistryEntry("updateSchedule", "Update Schedule"),
    "updateScheduleStatus" to ToolRegistryEntry("updateScheduleStatus", "Schedule Status"),
)

internal val TOOL_CARD_REGISTRY: Map<String, String> = TOOL_CARD_REGISTRY_WITH_DISPLAY.mapValues { it.value.cardType }

internal val String.isRegisteredToolName: Boolean
    get() {
        val name = removePrefix("tool-")
        return TOOL_CARD_REGISTRY_WITH_DISPLAY.containsKey(name) ||
            META_TOOL_NAMES.contains(name) ||
            IGNORED_TOOL_NAMES.contains(name) ||
            name.startsWith("agent-")
    }

internal val String.isIgnoredToolName: Boolean
    get() = IGNORED_TOOL_NAMES.contains(removePrefix("tool-"))

# AI SDK Stream Render Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build Android AI SDK stream rendering so Android consumes Stream SDK snapshots and renders the same parts, tool-card entries, states, and markdown semantics as iOS, while keeping Compose as a pure `UI=f(renderModel)` layer.

**Architecture:** Stream SDK owns stream fetch, SSE parsing, terminal state normalization, memory/store cache, and background execution. Android messages code owns only `StreamSnapshot -> TimelineItemAiContent` adaptation, iOS-parity tool card entry generation, markdown render models, and Compose rendering. A room-scoped stream binding layer owns SDK handles so list recycling cancels UI listeners without canceling background completion or store writes.

**Tech Stack:** Kotlin, Android Compose, kotlinx.serialization JSON, org.json for card payload transforms, Stream SDK module `libraries/agentstream`, Gradle unit tests, existing Element timeline/message architecture.

---

## Source Spec

Implement against:

- `docs/superpowers/specs/2026-06-12-ai-tool-card-parity-design.md`

Do not use iOS stream lifecycle implementation as Android fetch logic. Use iOS only for parts semantics, render orchestration, markdown behavior, and tool-card adapter behavior.

## Current Worktree Safety

Before starting implementation, inspect the worktree:

```bash
git status --short
```

There may already be unrelated or partially completed Kotlin edits. Do not revert files you did not change. If a task needs to touch a dirty file, read it first and preserve existing changes while applying the task.

## File Structure

### Stream SDK files

- Modify: `libraries/agentstream/src/main/kotlin/io/element/android/libraries/agentstream/api/StreamModels.kt`
  - Owns `StreamSnapshot`, `StreamStatus`, `StreamPart`, `TextPartState`, and `ToolPartState`.
  - Add missing AI SDK tool states and helpers used by SDK normalization.
- Modify: `libraries/agentstream/src/main/kotlin/io/element/android/libraries/agentstream/api/StreamSnapshotParser.kt`
  - Parses SDK JSON snapshots into `StreamSnapshot`.
  - Must preserve unknown wire state strings while mapping known states.
- Modify: `libraries/agentstream/src/main/kotlin/io/element/android/libraries/agentstream/api/DefaultAgentStreamClient.kt`
  - Owns lifecycle, dedupe, background task execution, storage reads/writes, and terminal snapshot publication.
  - Must normalize terminal parts before publishing/saving completed snapshots.
- Modify: `libraries/agentstream/src/test/kotlin/io/element/android/libraries/agentstream/api/DefaultAgentStreamClientTest.kt`
  - SDK readiness and lifecycle smoke tests.
- Modify: `libraries/agentstream/src/test/kotlin/io/element/android/libraries/agentstream/api/StreamSnapshotParserTest.kt`
  - Parser and terminal state tests.
- Modify: `libraries/agentstream/src/test/kotlin/io/element/android/libraries/agentstream/api/StreamModelsCompileTest.kt`
  - Compile/API contract tests for new enum states.

### Android messages data and adapter files

- Modify: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/model/event/TimelineItemAiContent.kt`
  - Render model consumed by Compose.
  - Preserve SDK metadata fields needed for incremental rendering.
- Modify: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/factories/event/AiSdkStreamReducer.kt`
  - Converts SDK `StreamSnapshot` to `TimelineItemAiContent`.
  - Must not mutate SDK part state or repair terminal states.
- Create: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/ToolCardRegistry.kt`
  - Single Android registry manifest reader/constants for toolName -> cardType/displayName and root/suspended card sets.
- Create: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/ToolCallRootCardAdapter.kt`
  - Pure Kotlin parity port of iOS `ToolCallRootCardAdapter`.
- Modify: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/AiToolCardLogic.kt`
  - Remove duplicated registry/adapter logic after extraction.
- Create: `features/messages/impl/src/test/resources/toolcards/ios_tool_card_manifest.json`
  - Checked-in golden manifest reviewed against iOS.
- Create: `features/messages/impl/src/test/resources/toolcards/fixtures/*.json`
  - Fixture matrix for root dispatch, registry-mapped, meta, sub-agent, schedule, Gmail, generic, error, denied, and suspended cards.
- Create: `features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/ToolCardRegistryTest.kt`
  - Manifest and coverage tests.
- Create: `features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/ToolCallRootCardAdapterTest.kt`
  - Data parity tests for direct, meta, sub-agent, schedule, error, denied, and suspended paths.
- Modify: `features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/timeline/factories/event/AiSdkStreamReducerTest.kt`
  - Reducer orchestration tests.

### Android UI files

- Modify: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/TimelineItemAiView.kt`
  - Compose rendering from `TimelineItemAiContent`.
  - Must not parse full stream JSON, fetch stream, or write store.
- Modify: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/MarkdownBody.kt`
  - Markdown display and link handling.
- Modify: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/ToolCardDispatcher.kt`
  - Dispatches `_cardType` to Android card UI and provides non-raw fallback.
- Modify grouped card files:
  - `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/GitHubCardsPrimary.kt`
  - `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/GitHubCardsActivity.kt`
  - `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/ComposioSearchCards.kt`
  - `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/GmailDriveCards.kt`
  - `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/LinearTwitterCards.kt`
  - `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/ScheduleMoltbookCards.kt`
  - `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/ToolCardKit.kt`
- Create: `features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/ToolCardDispatcherCoverageTest.kt`
  - Ensures each manifest root/suspended card has a non-raw render path or a deliberate display-only fallback.

### Stream handle ownership files

- Create: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/factories/event/AiStreamHandleStore.kt`
  - Room-scoped handle cache keyed by streamId.
  - Reuses SDK handles and subscriptions.
- Create: `features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/timeline/factories/event/AiStreamHandleStoreTest.kt`
  - Tests listener replay, dedupe, unbind behavior, refresh policy, and no offscreen cancellation.
- Modify: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/TimelineItemAiPresenter.kt`
  - Moves SDK handle ownership out of ad hoc `LaunchedEffect` stream collection and into `AiStreamHandleStore`.

## Gradle Commands

Use these commands throughout the plan:

```bash
./gradlew :libraries:agentstream:testDebugUnitTest
./gradlew :features:messages:impl:testDebugUnitTest
./gradlew :features:messages:impl:compileDebugKotlin
```

Expected passing output includes `BUILD SUCCESSFUL`.

---

### Task 1: Lock Stream SDK Tool State Contract

**Files:**
- Modify: `libraries/agentstream/src/main/kotlin/io/element/android/libraries/agentstream/api/StreamModels.kt`
- Modify: `libraries/agentstream/src/test/kotlin/io/element/android/libraries/agentstream/api/StreamModelsCompileTest.kt`
- Modify: `libraries/agentstream/src/test/kotlin/io/element/android/libraries/agentstream/api/StreamSnapshotParserTest.kt`

- [ ] **Step 1: Write the failing enum contract test**

Add this test to `StreamModelsCompileTest.kt`:

```kotlin
@Test
fun `tool part states include ai sdk approval and denied states`() {
    assertEquals(ToolPartState.InputStreaming, ToolPartState.fromWire("input-streaming"))
    assertEquals(ToolPartState.InputAvailable, ToolPartState.fromWire("input-available"))
    assertEquals(ToolPartState.OutputAvailable, ToolPartState.fromWire("output-available"))
    assertEquals(ToolPartState.ApprovalRequested, ToolPartState.fromWire("approval-requested"))
    assertEquals(ToolPartState.ApprovalResponded, ToolPartState.fromWire("approval-responded"))
    assertEquals(ToolPartState.OutputError, ToolPartState.fromWire("output-error"))
    assertEquals(ToolPartState.OutputDenied, ToolPartState.fromWire("output-denied"))
}
```

If `StreamModelsCompileTest.kt` does not import JUnit assertions yet, add:

```kotlin
import org.junit.Assert.assertEquals
import org.junit.Test
```

- [ ] **Step 2: Run the failing SDK model test**

Run:

```bash
./gradlew :libraries:agentstream:testDebugUnitTest --tests "io.element.android.libraries.agentstream.api.StreamModelsCompileTest"
```

Expected: FAIL because `ApprovalRequested`, `ApprovalResponded`, and `OutputDenied` are not defined.

- [ ] **Step 3: Add the missing SDK enum states**

Update `ToolPartState` in `StreamModels.kt` to:

```kotlin
enum class ToolPartState(val wireValue: String) {
    InputStreaming("input-streaming"),
    InputAvailable("input-available"),
    OutputAvailable("output-available"),
    ApprovalRequested("approval-requested"),
    ApprovalResponded("approval-responded"),
    OutputError("output-error"),
    OutputDenied("output-denied"),
    ;

    companion object {
        fun fromWire(value: String?): ToolPartState? = entries.firstOrNull { it.wireValue == value }
    }
}
```

- [ ] **Step 4: Add parser coverage for new states**

Add this test to `StreamSnapshotParserTest.kt`:

```kotlin
@Test
fun `parser preserves approval and denied tool states`() {
    val snapshot = StreamSnapshotParser().parseOrFailed(
        """
        {
          "streamId": "stream-approval",
          "status": "done",
          "parts": [
            {
              "type": "tool-mail",
              "id": "tool-approval",
              "state": "approval-requested",
              "input": { "subject": "Confirm" }
            },
            {
              "type": "tool-mail",
              "id": "tool-denied",
              "state": "output-denied",
              "errorText": "User denied"
            }
          ]
        }
        """.trimIndent()
    )

    val approval = snapshot.parts[0] as StreamPart.Tool
    val denied = snapshot.parts[1] as StreamPart.Tool
    assertEquals("approval-requested", approval.toolState)
    assertEquals(ToolPartState.ApprovalRequested, approval.toolPartState)
    assertEquals("output-denied", denied.toolState)
    assertEquals(ToolPartState.OutputDenied, denied.toolPartState)
}
```

Add imports if missing:

```kotlin
import org.junit.Assert.assertEquals
import org.junit.Test
```

- [ ] **Step 5: Run SDK model/parser tests**

Run:

```bash
./gradlew :libraries:agentstream:testDebugUnitTest --tests "io.element.android.libraries.agentstream.api.StreamModelsCompileTest" --tests "io.element.android.libraries.agentstream.api.StreamSnapshotParserTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add libraries/agentstream/src/main/kotlin/io/element/android/libraries/agentstream/api/StreamModels.kt \
  libraries/agentstream/src/test/kotlin/io/element/android/libraries/agentstream/api/StreamModelsCompileTest.kt \
  libraries/agentstream/src/test/kotlin/io/element/android/libraries/agentstream/api/StreamSnapshotParserTest.kt
git commit -m "feat: add ai sdk tool states to stream sdk"
```

---

### Task 2: Move Completed-State Normalization Into Stream SDK

**Files:**
- Modify: `libraries/agentstream/src/main/kotlin/io/element/android/libraries/agentstream/api/DefaultAgentStreamClient.kt`
- Modify: `libraries/agentstream/src/test/kotlin/io/element/android/libraries/agentstream/api/DefaultAgentStreamClientTest.kt`
- Modify: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/factories/event/AiSdkStreamReducer.kt`
- Modify: `features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/timeline/factories/event/AiSdkStreamReducerTest.kt`

- [ ] **Step 1: Write the failing SDK terminal normalization test**

Add this test to `DefaultAgentStreamClientTest.kt`:

```kotlin
@Test
fun `completed stream normalizes active text reasoning and tool states before save`() = runTest {
    val storage = FakeStreamStorageProvider()
    val http = FakeStreamHttpClient(
        chunks = listOf(
            """
            {
              "schemaVersion": 1,
              "streamId": "stream-1",
              "status": "streaming",
              "parts": [
                { "type": "text", "id": "text-1", "state": "streaming", "text": "hello" },
                { "type": "reasoning", "id": "reason-1", "state": "streaming", "text": "thinking" },
                { "type": "tool-GMAIL_FETCH_EMAILS", "id": "tool-1", "state": "input-available", "input": { "query": "from:alice" } }
              ]
            }
            """.trimIndent()
        ),
    )
    val client = createClient(storage = storage, http = http)
    val snapshots = mutableListOf<StreamSnapshot>()

    client.getStream(request("stream-1")).subscribe { snapshots += it }
    advanceUntilIdle()

    val completed = snapshots.last()
    assertEquals(StreamStatus.Completed, completed.status)
    assertEquals("done", (completed.parts[0] as StreamPart.Text).textState)
    assertEquals("done", (completed.parts[1] as StreamPart.Reasoning).reasoningState)
    assertEquals("output-available", (completed.parts[2] as StreamPart.Tool).toolState)
    assertEquals(completed, storage.savedSnapshots.single())
}
```

- [ ] **Step 2: Run the failing SDK lifecycle test**

Run:

```bash
./gradlew :libraries:agentstream:testDebugUnitTest --tests "io.element.android.libraries.agentstream.api.DefaultAgentStreamClientTest.completed stream normalizes active text reasoning and tool states before save"
```

Expected: FAIL because terminal part states are still active or the stream client does not normalize before save.

- [ ] **Step 3: Implement SDK terminal normalization**

In `DefaultAgentStreamClient.kt`, add a private normalization function near the snapshot publishing code:

```kotlin
private fun StreamSnapshot.normalizedForTerminalPublish(): StreamSnapshot {
    if (status != StreamStatus.Completed) return this
    return copy(
        parts = parts.map { part ->
            when (part) {
                is StreamPart.Text -> if (part.textState == TextPartState.Done.wireValue) part else part.copy(textState = TextPartState.Done.wireValue)
                is StreamPart.Reasoning -> if (part.reasoningState == TextPartState.Done.wireValue) part else part.copy(reasoningState = TextPartState.Done.wireValue)
                is StreamPart.Tool -> if (part.toolState in TERMINAL_TOOL_STATES) part else part.copy(toolState = ToolPartState.OutputAvailable.wireValue)
                else -> part
            }
        }
    )
}

private val TERMINAL_TOOL_STATES = setOf(
    ToolPartState.OutputAvailable.wireValue,
    ToolPartState.ApprovalRequested.wireValue,
    ToolPartState.ApprovalResponded.wireValue,
    ToolPartState.OutputError.wireValue,
    ToolPartState.OutputDenied.wireValue,
)
```

Call `normalizedForTerminalPublish()` exactly before publishing and saving a completed final snapshot. Do not apply it to `Failed` or `Cancelled` snapshots.

- [ ] **Step 4: Remove Android reducer state repair**

In `AiSdkStreamReducer.kt`, remove:

```kotlin
.finalizeIfCompleted(snapshot)
```

Delete the `finalizeIfCompleted` function and the `FINALIZED_TOOL_STATES` constant. The reducer must consume SDK state without mutation.

- [ ] **Step 5: Replace Android reducer repair test with non-mutation test**

In `AiSdkStreamReducerTest.kt`, replace the current completed-state repair expectation with:

```kotlin
@Test
fun `reducer does not mutate sdk part states for completed snapshots`() {
    val snapshot = snapshot(
        status = StreamStatus.Completed,
        parts = listOf(
            StreamPart.Tool(
                id = "call-1",
                toolState = "input-available",
                toolName = "GMAIL_FETCH_EMAILS",
                input = Json.parseToJsonElement("""{"query":"from:alice@example.com"}"""),
            ),
        ),
    )

    val result = reducer.mapSnapshot(snapshot, isEdited = false, sender = null)

    val tool = result.parts.single() as AiToolStreamPart
    assertThat(tool.state).isEqualTo("input-available")
}
```

This test proves state repair is not happening in Android. The SDK test proves completed streams normally arrive already normalized.

- [ ] **Step 6: Run SDK and messages tests**

Run:

```bash
./gradlew :libraries:agentstream:testDebugUnitTest --tests "io.element.android.libraries.agentstream.api.DefaultAgentStreamClientTest"
./gradlew :features:messages:impl:testDebugUnitTest --tests "io.element.android.features.messages.impl.timeline.factories.event.AiSdkStreamReducerTest"
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add libraries/agentstream/src/main/kotlin/io/element/android/libraries/agentstream/api/DefaultAgentStreamClient.kt \
  libraries/agentstream/src/test/kotlin/io/element/android/libraries/agentstream/api/DefaultAgentStreamClientTest.kt \
  features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/factories/event/AiSdkStreamReducer.kt \
  features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/timeline/factories/event/AiSdkStreamReducerTest.kt
git commit -m "fix: normalize completed stream parts in sdk"
```

---

### Task 3: Add SDK Lifecycle Readiness Smoke Tests

**Files:**
- Modify: `libraries/agentstream/src/test/kotlin/io/element/android/libraries/agentstream/api/DefaultAgentStreamClientTest.kt`
- Modify: `libraries/agentstream/src/main/kotlin/io/element/android/libraries/agentstream/api/DefaultAgentStreamClient.kt`

- [ ] **Step 1: Add listener replay test**

Add this test:

```kotlin
@Test
fun `late listener immediately receives current snapshot`() = runTest {
    val http = FakeStreamHttpClient(chunks = listOf(streamingJson("stream-1", "ready")))
    val client = createClient(http = http)
    val handle = client.getStream(request("stream-1"))
    advanceUntilIdle()
    val lateSnapshots = mutableListOf<StreamSnapshot>()

    handle.subscribe { lateSnapshots += it }

    assertEquals(StreamStatus.Completed, lateSnapshots.single().status)
    assertEquals("ready", lateSnapshots.single().text())
}
```

- [ ] **Step 2: Add offscreen unsubscribe test**

Add this test:

```kotlin
@Test
fun `subscription cancel does not cancel background completion or store save`() = runTest {
    val storage = FakeStreamStorageProvider()
    val http = FakeStreamHttpClient(chunks = listOf(streamingJson("stream-1", "final")))
    val client = createClient(storage = storage, http = http)
    val handle = client.getStream(request("stream-1"))
    val subscription = handle.subscribe { }

    subscription.cancel()
    advanceUntilIdle()

    assertEquals(StreamStatus.Completed, handle.snapshot().status)
    assertEquals("final", handle.snapshot().text())
    assertEquals("final", storage.savedSnapshots.single().text())
}
```

- [ ] **Step 3: Add retryable failure does not overwrite completed store test**

Add this test:

```kotlin
@Test
fun `retryable failure does not persist over existing completed store`() = runTest {
    val completed = completedSnapshot("stream-1", "stored")
    val storage = FakeStreamStorageProvider(loadResult = null).apply {
        savedSnapshots += completed
    }
    val http = FakeStreamHttpClient(error = StreamTransportException("timeout", retryable = true))
    val client = createClient(storage = storage, http = http)
    val snapshots = mutableListOf<StreamSnapshot>()

    client.getStream(request("stream-1")).subscribe { snapshots += it }
    advanceUntilIdle()

    assertEquals(StreamStatus.Failed, snapshots.last().status)
    assertEquals(listOf(completed), storage.savedSnapshots)
}
```

- [ ] **Step 4: Run lifecycle tests**

Run:

```bash
./gradlew :libraries:agentstream:testDebugUnitTest --tests "io.element.android.libraries.agentstream.api.DefaultAgentStreamClientTest"
```

Expected: PASS. If a test fails because `DefaultAgentStreamClient` treats subscription cancellation like handle cancellation, change only subscription cancellation so it removes the listener and leaves the SDK task running.

- [ ] **Step 5: Commit**

```bash
git add libraries/agentstream/src/main/kotlin/io/element/android/libraries/agentstream/api/DefaultAgentStreamClient.kt \
  libraries/agentstream/src/test/kotlin/io/element/android/libraries/agentstream/api/DefaultAgentStreamClientTest.kt
git commit -m "test: lock stream sdk lifecycle readiness"
```

---

### Task 4: Create iOS Tool Card Manifest And Fixture Matrix

**Files:**
- Create: `features/messages/impl/src/test/resources/toolcards/ios_tool_card_manifest.json`
- Create: `features/messages/impl/src/test/resources/toolcards/fixtures/gmail_fetch_emails.json`
- Create: `features/messages/impl/src/test/resources/toolcards/fixtures/multi_execute_gmail_done.json`
- Create: `features/messages/impl/src/test/resources/toolcards/fixtures/sub_agent_nested_schedule.json`
- Create: `features/messages/impl/src/test/resources/toolcards/fixtures/tool_error_denied.json`
- Create: `features/messages/impl/src/test/resources/toolcards/fixtures/moltbook_register_display_only.json`
- Create: `features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/ToolCardRegistryTest.kt`
- Create: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/ToolCardRegistry.kt`

- [ ] **Step 1: Add golden manifest JSON**

Create `ios_tool_card_manifest.json` with this content:

```json
{
  "schemaVersion": 1,
  "rootDispatchCardTypes": [
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
    "generic"
  ],
  "standaloneSuspendedCardTypes": [
    "moltbookRegister"
  ],
  "registryMappings": {
    "GMAIL_FETCH_EMAILS": { "cardType": "composeEmail", "displayName": "Emails" },
    "GMAIL_FETCH_MESSAGE_BY_MESSAGE_ID": { "cardType": "composeEmail", "displayName": "Email" },
    "GMAIL_CREATE_EMAIL_DRAFT": { "cardType": "composeEmail", "displayName": "Draft" },
    "GOOGLEDRIVE_FIND_FILE": { "cardType": "fileAttachment", "displayName": "Files" },
    "GOOGLEDRIVE_GET_FILE_METADATA": { "cardType": "fileAttachment", "displayName": "File" },
    "COMPOSIO_SEARCH_NEWS": { "cardType": "headlineList", "displayName": "News" },
    "COMPOSIO_SEARCH_WEB": { "cardType": "headlineList", "displayName": "Web Search" },
    "COMPOSIO_SEARCH_IMAGE": { "cardType": "imageGrid", "displayName": "Images" },
    "COMPOSIO_SEARCH_SHOPPING": { "cardType": "productList", "displayName": "Shopping" },
    "COMPOSIO_SEARCH_FINANCE": { "cardType": "finance", "displayName": "Finance" },
    "COMPOSIO_SEARCH_EVENT": { "cardType": "eventList", "displayName": "Events" },
    "COMPOSIO_SEARCH_GOOGLE_MAPS": { "cardType": "placeList", "displayName": "Places" },
    "COMPOSIO_SEARCH_FETCH_URL_CONTENT": { "cardType": "urlContent", "displayName": "Web Content" },
    "COMPOSIO_SEARCH_FLIGHTS": { "cardType": "flightAlert", "displayName": "Flights" },
    "COMPOSIO_SEARCH_HOTELS": { "cardType": "hotelBooking", "displayName": "Hotels" },
    "GITHUB_LIST_REPOSITORY_ISSUES": { "cardType": "githubIssuesList", "displayName": "Issues" },
    "GITHUB_CREATE_AN_ISSUE": { "cardType": "githubIssue", "displayName": "Issue" },
    "GITHUB_GET_AN_ISSUE": { "cardType": "githubIssue", "displayName": "Issue" },
    "GITHUB_LIST_CHECK_RUNS_FOR_A_REF": { "cardType": "checkRuns", "displayName": "Check Runs" },
    "GITHUB_COMPARE_TWO_COMMITS": { "cardType": "commitComparison", "displayName": "Commits" },
    "GITHUB_LIST_REPOSITORY_CONTRIBUTORS": { "cardType": "contributors", "displayName": "Contributors" },
    "GITHUB_LIST_DEPLOYMENTS": { "cardType": "deployments", "displayName": "Deployments" },
    "GITHUB_LIST_NOTIFICATIONS": { "cardType": "notifications", "displayName": "Notifications" },
    "GITHUB_LIST_ORGANIZATIONS_FOR_A_USER": { "cardType": "orgsList", "displayName": "Organizations" },
    "GITHUB_CREATE_A_RELEASE": { "cardType": "release", "displayName": "Release" },
    "GITHUB_FIND_REPOSITORIES": { "cardType": "repoList", "displayName": "Repositories" },
    "GITHUB_SEARCH_REPOSITORIES": { "cardType": "repoList", "displayName": "Repositories" },
    "GITHUB_LIST_SECRET_SCANNING_ALERTS_FOR_A_REPOSITORY": { "cardType": "secretAlerts", "displayName": "Secret Alerts" },
    "GITHUB_LIST_REPOSITORY_WORKFLOWS": { "cardType": "workflows", "displayName": "Workflows" },
    "GITHUB_CREATE_AN_ISSUE_COMMENT": { "cardType": "commentThread", "displayName": "Comments" },
    "LINEAR_CREATE_LINEAR_ISSUE": { "cardType": "linearIssue", "displayName": "Issue" },
    "LINEAR_LIST_LINEAR_ISSUES": { "cardType": "linearIssuesList", "displayName": "Issues" },
    "TWITTER_USER_HOME_TIMELINE_BY_USER_ID": { "cardType": "socialPostFeed", "displayName": "Timeline" },
    "TWITTER_FULL_ARCHIVE_SEARCH": { "cardType": "socialPostFeed", "displayName": "Posts" },
    "TWITTER_POST_LOOKUP_BY_POST_ID": { "cardType": "socialPostFeed", "displayName": "Post" },
    "createSchedule": { "cardType": "createSchedule", "displayName": "Create Schedule" },
    "updateSchedule": { "cardType": "updateSchedule", "displayName": "Update Schedule" },
    "updateScheduleStatus": { "cardType": "updateScheduleStatus", "displayName": "Schedule Status" }
  },
  "metaTools": ["COMPOSIO_MULTI_EXECUTE_TOOL"],
  "ignoredTools": ["COMPOSIO_SEARCH_TOOLS"]
}
```

- [ ] **Step 2: Add fixture JSON files**

Create `gmail_fetch_emails.json`:

```json
{
  "toolName": "GMAIL_FETCH_EMAILS",
  "state": "output-available",
  "input": { "query": "from:alice@example.com" },
  "output": {
    "successful": true,
    "data": {
      "messages": [
        {
          "subject": "Authorization complete",
          "from": { "email": "alice@example.com", "name": "Alice" },
          "snippet": "Your Gmail authorization has completed.",
          "date": "2026-06-12T10:00:00Z",
          "url": "https://mail.google.com/mail/u/0/#inbox/abc"
        }
      ]
    }
  },
  "expected": {
    "_cardType": "composeEmail",
    "entryName": "Emails",
    "entryState": "done",
    "containsText": "Authorization complete",
    "clickTarget": "https://mail.google.com/mail/u/0/#inbox/abc"
  }
}
```

Create `multi_execute_gmail_done.json`:

```json
{
  "toolName": "COMPOSIO_MULTI_EXECUTE_TOOL",
  "state": "output-available",
  "input": {
    "tools": [
      { "tool_slug": "GMAIL_FETCH_EMAILS" }
    ]
  },
  "output": {
    "data": {
      "results": [
        {
          "tool_slug": "GMAIL_FETCH_EMAILS",
          "response": {
            "successful": true,
            "data": {
              "messages": [
                { "subject": "Invoice", "from": "billing@example.com", "snippet": "June invoice", "url": "https://mail.example/invoice" }
              ]
            }
          }
        }
      ]
    }
  },
  "expected": {
    "_cardType": "composeEmail",
    "entryName": "Emails",
    "entryState": "done",
    "containsText": "Invoice"
  }
}
```

Create `sub_agent_nested_schedule.json`:

```json
{
  "toolName": "agent-mailAgent",
  "state": "output-available",
  "output": {
    "subAgentToolResults": [
      {
        "toolName": "createSchedule",
        "args": { "name": "Daily mail triage", "cron": "0 9 * * *", "timezone": "Asia/Shanghai", "action": "Summarize inbox" },
        "result": { "successful": true, "data": { "scheduleId": "sched-1" } }
      }
    ]
  },
  "expected": {
    "_cardType": "createSchedule",
    "entryName": "Create Schedule",
    "entryState": "done",
    "containsText": "Daily mail triage"
  }
}
```

Create `tool_error_denied.json`:

```json
{
  "toolName": "GMAIL_FETCH_EMAILS",
  "state": "output-denied",
  "input": { "query": "newer_than:1d" },
  "errorText": "User denied Gmail access",
  "expected": {
    "_cardType": "composeEmail",
    "entryName": "Emails",
    "entryState": "error",
    "containsText": "User denied Gmail access"
  }
}
```

Create `moltbook_register_display_only.json`:

```json
{
  "cardType": "moltbookRegister",
  "payload": {
    "title": "Register Moltbook account",
    "status": "Action required",
    "url": "https://moltbook.example/register"
  },
  "expected": {
    "_cardType": "moltbookRegister",
    "displayOnly": true,
    "containsText": "Register Moltbook account",
    "disabledReason": "Open on iOS or web to complete this action"
  }
}
```

- [ ] **Step 3: Add registry Kotlin source**

Create `ToolCardRegistry.kt`:

```kotlin
package io.element.android.features.messages.impl.timeline.components.event.toolcards

internal data class ToolRegistryEntry(
    val cardType: String,
    val displayName: String,
)

internal val ROOT_DISPATCH_CARD_TYPES = setOf(
    "checkRuns", "commentThread", "commitComparison", "contributors", "deployments",
    "githubIssue", "notifications", "orgsList", "release", "repoList", "secretAlerts",
    "workflows", "githubIssuesList", "breakingNews", "flightAlert", "headlineList",
    "imageGrid", "productList", "finance", "eventList", "placeList", "urlContent",
    "hotelBooking", "linearIssue", "linearIssuesList", "composeEmail", "fileAttachment",
    "socialPostFeed", "createSchedule", "updateSchedule", "updateScheduleStatus", "generic",
)

internal val STANDALONE_SUSPENDED_CARD_TYPES = setOf("moltbookRegister")

internal val META_TOOL_NAMES = setOf("COMPOSIO_MULTI_EXECUTE_TOOL")

internal val IGNORED_TOOL_NAMES = setOf("COMPOSIO_SEARCH_TOOLS")

internal val TOOL_CARD_REGISTRY_WITH_DISPLAY = mapOf(
    "GMAIL_FETCH_EMAILS" to ToolRegistryEntry("composeEmail", "Emails"),
    "GMAIL_FETCH_MESSAGE_BY_MESSAGE_ID" to ToolRegistryEntry("composeEmail", "Email"),
    "GMAIL_CREATE_EMAIL_DRAFT" to ToolRegistryEntry("composeEmail", "Draft"),
    "GOOGLEDRIVE_FIND_FILE" to ToolRegistryEntry("fileAttachment", "Files"),
    "GOOGLEDRIVE_GET_FILE_METADATA" to ToolRegistryEntry("fileAttachment", "File"),
    "COMPOSIO_SEARCH_NEWS" to ToolRegistryEntry("headlineList", "News"),
    "COMPOSIO_SEARCH_WEB" to ToolRegistryEntry("headlineList", "Web Search"),
    "COMPOSIO_SEARCH_IMAGE" to ToolRegistryEntry("imageGrid", "Images"),
    "COMPOSIO_SEARCH_SHOPPING" to ToolRegistryEntry("productList", "Shopping"),
    "COMPOSIO_SEARCH_FINANCE" to ToolRegistryEntry("finance", "Finance"),
    "COMPOSIO_SEARCH_EVENT" to ToolRegistryEntry("eventList", "Events"),
    "COMPOSIO_SEARCH_GOOGLE_MAPS" to ToolRegistryEntry("placeList", "Places"),
    "COMPOSIO_SEARCH_FETCH_URL_CONTENT" to ToolRegistryEntry("urlContent", "Web Content"),
    "COMPOSIO_SEARCH_FLIGHTS" to ToolRegistryEntry("flightAlert", "Flights"),
    "COMPOSIO_SEARCH_HOTELS" to ToolRegistryEntry("hotelBooking", "Hotels"),
    "GITHUB_LIST_REPOSITORY_ISSUES" to ToolRegistryEntry("githubIssuesList", "Issues"),
    "GITHUB_CREATE_AN_ISSUE" to ToolRegistryEntry("githubIssue", "Issue"),
    "GITHUB_GET_AN_ISSUE" to ToolRegistryEntry("githubIssue", "Issue"),
    "GITHUB_LIST_CHECK_RUNS_FOR_A_REF" to ToolRegistryEntry("checkRuns", "Check Runs"),
    "GITHUB_COMPARE_TWO_COMMITS" to ToolRegistryEntry("commitComparison", "Commits"),
    "GITHUB_LIST_REPOSITORY_CONTRIBUTORS" to ToolRegistryEntry("contributors", "Contributors"),
    "GITHUB_LIST_DEPLOYMENTS" to ToolRegistryEntry("deployments", "Deployments"),
    "GITHUB_LIST_NOTIFICATIONS" to ToolRegistryEntry("notifications", "Notifications"),
    "GITHUB_LIST_ORGANIZATIONS_FOR_A_USER" to ToolRegistryEntry("orgsList", "Organizations"),
    "GITHUB_CREATE_A_RELEASE" to ToolRegistryEntry("release", "Release"),
    "GITHUB_FIND_REPOSITORIES" to ToolRegistryEntry("repoList", "Repositories"),
    "GITHUB_SEARCH_REPOSITORIES" to ToolRegistryEntry("repoList", "Repositories"),
    "GITHUB_LIST_SECRET_SCANNING_ALERTS_FOR_A_REPOSITORY" to ToolRegistryEntry("secretAlerts", "Secret Alerts"),
    "GITHUB_LIST_REPOSITORY_WORKFLOWS" to ToolRegistryEntry("workflows", "Workflows"),
    "GITHUB_CREATE_AN_ISSUE_COMMENT" to ToolRegistryEntry("commentThread", "Comments"),
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
```

- [ ] **Step 4: Add registry manifest tests**

Create `ToolCardRegistryTest.kt`:

```kotlin
package io.element.android.features.messages.impl.timeline.components.event.toolcards

import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.Test

class ToolCardRegistryTest {
    private val manifest = JSONObject(
        javaClass.classLoader!!.getResource("toolcards/ios_tool_card_manifest.json")!!.readText()
    )

    @Test
    fun `android registry card types match ios manifest mappings`() {
        val mappings = manifest.getJSONObject("registryMappings")
        val names = mappings.keys().asSequence().toSet()

        assertThat(TOOL_CARD_REGISTRY_WITH_DISPLAY.keys).containsAtLeastElementsIn(names)
        names.forEach { name ->
            val expected = mappings.getJSONObject(name)
            val actual = TOOL_CARD_REGISTRY_WITH_DISPLAY.getValue(name)
            assertThat(actual.cardType).isEqualTo(expected.getString("cardType"))
            assertThat(actual.displayName).isEqualTo(expected.getString("displayName"))
        }
    }

    @Test
    fun `root and suspended card sets match ios manifest`() {
        assertThat(ROOT_DISPATCH_CARD_TYPES).containsAtLeastElementsIn(manifest.getJSONArray("rootDispatchCardTypes").toStringSet())
        assertThat(STANDALONE_SUSPENDED_CARD_TYPES).containsAtLeastElementsIn(manifest.getJSONArray("standaloneSuspendedCardTypes").toStringSet())
    }
}

private fun org.json.JSONArray.toStringSet(): Set<String> =
    (0 until length()).map { getString(it) }.toSet()
```

- [ ] **Step 5: Remove duplicated registry definitions from AiToolCardLogic**

In `AiToolCardLogic.kt`, delete local definitions of:

```kotlin
private data class ToolRegistryEntry(...)
internal val TOOL_CARD_REGISTRY = ...
internal val TOOL_CARD_REGISTRY_WITH_DISPLAY = ...
internal val IGNORED_TOOL_NAMES = ...
internal val META_TOOL_NAMES = ...
internal val String.isRegisteredToolName: Boolean = ...
```

Keep call sites using the same names from `ToolCardRegistry.kt`.

- [ ] **Step 6: Run registry tests**

Run:

```bash
./gradlew :features:messages:impl:testDebugUnitTest --tests "io.element.android.features.messages.impl.timeline.components.event.toolcards.ToolCardRegistryTest"
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/ToolCardRegistry.kt \
  features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/AiToolCardLogic.kt \
  features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/ToolCardRegistryTest.kt \
  features/messages/impl/src/test/resources/toolcards
git commit -m "test: add ios tool card parity manifest"
```

---

### Task 5: Extract ToolCallRootCardAdapter Parity Logic

**Files:**
- Create: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/ToolCallRootCardAdapter.kt`
- Modify: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/AiToolCardLogic.kt`
- Create: `features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/ToolCallRootCardAdapterTest.kt`

- [ ] **Step 1: Add failing adapter tests for direct, meta, sub-agent, and denied tools**

Create `ToolCallRootCardAdapterTest.kt`:

```kotlin
package io.element.android.features.messages.impl.timeline.components.event.toolcards

import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.timeline.model.event.AiToolStreamPart
import org.json.JSONObject
import org.junit.Test

class ToolCallRootCardAdapterTest {
    @Test
    fun `direct gmail output creates compose email done entry`() {
        val part = AiToolStreamPart(
            id = "tool-1",
            state = "output-available",
            toolName = "GMAIL_FETCH_EMAILS",
            title = null,
            input = """{"query":"from:alice@example.com"}""",
            rawInput = null,
            output = """{"successful":true,"data":{"messages":[{"subject":"Authorization complete","from":"alice@example.com","snippet":"done"}]}}""",
            errorText = null,
        )

        val entry = ToolCallRootCardAdapter.toolCallEntries(listOf(part)).single()

        assertThat(entry.id).isEqualTo("tool-1")
        assertThat(entry.name).isEqualTo("Emails")
        assertThat(entry.state).isEqualTo("done")
        val props = JSONObject(entry.props)
        assertThat(props.getString("_cardType")).isEqualTo("composeEmail")
        assertThat(props.toString()).contains("Authorization complete")
    }

    @Test
    fun `multi execute done expands grouped child entries`() {
        val part = AiToolStreamPart(
            id = "multi-1",
            state = "output-available",
            toolName = "COMPOSIO_MULTI_EXECUTE_TOOL",
            title = null,
            input = """{"tools":[{"tool_slug":"GMAIL_FETCH_EMAILS"}]}""",
            rawInput = null,
            output = """{"data":{"results":[{"tool_slug":"GMAIL_FETCH_EMAILS","response":{"successful":true,"data":{"messages":[{"subject":"Invoice"}]}}}]}}""",
            errorText = null,
        )

        val entry = ToolCallRootCardAdapter.toolCallEntries(listOf(part)).single()

        assertThat(entry.id).isEqualTo("multi-1_GMAIL_FETCH_EMAILS")
        assertThat(entry.name).isEqualTo("Emails")
        assertThat(entry.state).isEqualTo("done")
        assertThat(JSONObject(entry.props).getString("_cardType")).isEqualTo("composeEmail")
        assertThat(entry.props).contains("Invoice")
    }

    @Test
    fun `sub agent calling creates generic calling entry`() {
        val part = AiToolStreamPart(
            id = "agent-1",
            state = "input-available",
            toolName = "agent-mailAgent",
            title = null,
            input = null,
            rawInput = null,
            output = null,
            errorText = null,
        )

        val entry = ToolCallRootCardAdapter.toolCallEntries(listOf(part)).single()

        assertThat(entry.id).isEqualTo("agent-1")
        assertThat(entry.name).isEqualTo("Mail Agent")
        assertThat(entry.state).isEqualTo("calling")
        assertThat(JSONObject(entry.props).getString("_cardType")).isEqualTo("generic")
    }

    @Test
    fun `denied tool creates error entry with readable error props`() {
        val part = AiToolStreamPart(
            id = "tool-denied",
            state = "output-denied",
            toolName = "GMAIL_FETCH_EMAILS",
            title = null,
            input = """{"query":"newer_than:1d"}""",
            rawInput = null,
            output = null,
            errorText = "User denied Gmail access",
        )

        val entry = ToolCallRootCardAdapter.toolCallEntries(listOf(part)).single()

        assertThat(entry.state).isEqualTo("error")
        assertThat(entry.props).contains("User denied Gmail access")
        assertThat(JSONObject(entry.props).getString("_cardType")).isEqualTo("composeEmail")
    }
}
```

- [ ] **Step 2: Run the failing adapter tests**

Run:

```bash
./gradlew :features:messages:impl:testDebugUnitTest --tests "io.element.android.features.messages.impl.timeline.components.event.toolcards.ToolCallRootCardAdapterTest"
```

Expected: FAIL because `ToolCallRootCardAdapter` does not exist.

- [ ] **Step 3: Create adapter object**

Create `ToolCallRootCardAdapter.kt`:

```kotlin
package io.element.android.features.messages.impl.timeline.components.event.toolcards

import io.element.android.features.messages.impl.timeline.model.event.AiToolCardEntry
import io.element.android.features.messages.impl.timeline.model.event.AiToolStreamPart
import org.json.JSONObject

internal object ToolCallRootCardAdapter {
    fun toolCallEntries(parts: List<AiToolStreamPart>): List<AiToolCardEntry> {
        return parts.flatMap { it.toToolCardEntries() }
    }

    fun renderableToolParts(parts: List<AiToolStreamPart>): List<AiToolStreamPart> {
        return parts.flatMap { it.toRenderableToolParts() }
    }
}

internal fun errorProps(cardType: String, message: String?): JSONObject {
    return JSONObject()
        .put("_cardType", cardType)
        .put("errorText", message?.takeIf { it.isNotBlank() } ?: "Tool call failed")
}
```

Update `AiToolCardLogic.kt` direct tool entry generation so `output-denied` and `output-error` include readable error props:

```kotlin
val props = when {
    cardState == CARD_STATE_ERROR -> errorProps(registry.cardType, errorText)
    registry.cardType.isScheduleCardType() -> scheduleProps(cardType = registry.cardType, input = input)
    else -> JSONObject().put("_cardType", registry.cardType).also { props ->
        if (cardState == CARD_STATE_DONE) {
            val raw = extractProps(output)
            val transformed = CardTransforms.transform(raw, registry.cardType)
            transformed.copyInto(props)
        }
    }
}
```

- [ ] **Step 4: Wire reducer to adapter object**

In `AiSdkStreamReducer.kt`, replace direct calls:

```kotlin
.flatMap { it.toRenderableToolParts() }
```

with:

```kotlin
ToolCallRootCardAdapter.renderableToolParts(...)
```

and populate `toolCardEntries` using:

```kotlin
val toolCardEntries = ToolCallRootCardAdapter.toolCallEntries(visible.filterIsInstance<AiToolStreamPart>())
    .toImmutableList()
```

Add import:

```kotlin
import io.element.android.features.messages.impl.timeline.components.event.toolcards.ToolCallRootCardAdapter
```

- [ ] **Step 5: Run adapter and reducer tests**

Run:

```bash
./gradlew :features:messages:impl:testDebugUnitTest --tests "io.element.android.features.messages.impl.timeline.components.event.toolcards.ToolCallRootCardAdapterTest" --tests "io.element.android.features.messages.impl.timeline.factories.event.AiSdkStreamReducerTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/ToolCallRootCardAdapter.kt \
  features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/AiToolCardLogic.kt \
  features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/factories/event/AiSdkStreamReducer.kt \
  features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/ToolCallRootCardAdapterTest.kt
git commit -m "feat: extract tool call root card adapter"
```

---

### Task 6: Make TimelineItemAiContent A Stable SDK Render Model

**Files:**
- Modify: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/model/event/TimelineItemAiContent.kt`
- Modify: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/factories/event/AiSdkStreamReducer.kt`
- Modify: `features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/timeline/factories/event/AiSdkStreamReducerTest.kt`

- [ ] **Step 1: Add failing reducer metadata test**

Add to `AiSdkStreamReducerTest.kt`:

```kotlin
@Test
fun `render model preserves sdk snapshot metadata`() {
    val snapshot = StreamSnapshot(
        schemaVersion = AGENT_STREAM_SCHEMA_VERSION,
        streamId = "stream-meta",
        status = StreamStatus.Completed,
        parts = listOf(StreamPart.Text(id = "text-1", text = "Done", textState = "done")),
        rawEvents = emptyList(),
        updatedAtMs = 42L,
        completedAtMs = 64L,
        error = null,
    )

    val result = reducer.mapSnapshot(snapshot, isEdited = false, sender = "@alice:example.org")

    assertThat(result.streamId).isEqualTo("stream-meta")
    assertThat(result.schemaVersion).isEqualTo(AGENT_STREAM_SCHEMA_VERSION)
    assertThat(result.streamStatus).isEqualTo(StreamStatus.Completed.name)
    assertThat(result.updatedAtMs).isEqualTo(42L)
    assertThat(result.completedAtMs).isEqualTo(64L)
    assertThat(result.renderVersion).isEqualTo("stream-meta:1:42:64:Completed")
}
```

- [ ] **Step 2: Run failing reducer metadata test**

Run:

```bash
./gradlew :features:messages:impl:testDebugUnitTest --tests "io.element.android.features.messages.impl.timeline.factories.event.AiSdkStreamReducerTest.render model preserves sdk snapshot metadata"
```

Expected: FAIL because `schemaVersion`, `streamStatus`, `updatedAtMs`, `completedAtMs`, or `renderVersion` are missing.

- [ ] **Step 3: Extend TimelineItemAiContent**

Add fields to `TimelineItemAiContent`:

```kotlin
val schemaVersion: Int = 1,
val streamStatus: String? = null,
val updatedAtMs: Long? = null,
val completedAtMs: Long? = null,
val streamError: String? = null,
val renderVersion: String? = null,
```

Keep defaults so existing construction sites compile.

- [ ] **Step 4: Populate metadata in reducer**

In `AiSdkStreamReducer.mapSnapshot`, set:

```kotlin
schemaVersion = snapshot.schemaVersion,
streamStatus = snapshot.status.name,
updatedAtMs = snapshot.updatedAtMs,
completedAtMs = snapshot.completedAtMs,
streamError = snapshot.error?.message,
renderVersion = listOf(
    snapshot.streamId,
    snapshot.schemaVersion.toString(),
    snapshot.updatedAtMs.toString(),
    snapshot.completedAtMs?.toString().orEmpty(),
    snapshot.status.name,
).joinToString(":"),
```

- [ ] **Step 5: Run reducer tests**

Run:

```bash
./gradlew :features:messages:impl:testDebugUnitTest --tests "io.element.android.features.messages.impl.timeline.factories.event.AiSdkStreamReducerTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/model/event/TimelineItemAiContent.kt \
  features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/factories/event/AiSdkStreamReducer.kt \
  features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/timeline/factories/event/AiSdkStreamReducerTest.kt
git commit -m "feat: preserve stream sdk metadata in ai render model"
```

---

### Task 7: Implement iOS-Parity Part Orchestration In Reducer

**Files:**
- Modify: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/factories/event/AiSdkStreamReducer.kt`
- Modify: `features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/timeline/factories/event/AiSdkStreamReducerTest.kt`

- [ ] **Step 1: Add failing first-tool insertion test**

Add to `AiSdkStreamReducerTest.kt`:

```kotlin
@Test
fun `reducer inserts one tool root card at first registered tool candidate`() {
    val snapshot = snapshot(
        status = StreamStatus.Streaming,
        parts = listOf(
            StreamPart.Text(id = "text-before", text = "Before", textState = "done"),
            StreamPart.Tool(id = "ignored", toolState = "input-available", toolName = "COMPOSIO_SEARCH_TOOLS"),
            StreamPart.Tool(
                id = "gmail",
                toolState = "output-available",
                toolName = "GMAIL_FETCH_EMAILS",
                output = Json.parseToJsonElement("""{"successful":true,"data":{"messages":[{"subject":"Hello"}]}}"""),
            ),
            StreamPart.Text(id = "text-after", text = "After", textState = "streaming"),
        ),
    )

    val result = reducer.mapSnapshot(snapshot, isEdited = false, sender = null)

    assertThat(result.visibleParts.map { it.id }).containsExactly("text-before", "ignored", "gmail", "text-after").inOrder()
    assertThat(result.toolCardEntries).hasSize(1)
    assertThat(result.firstToolPartIndex).isEqualTo(1)
    assertThat(result.passthroughParts.map { it.id }).containsExactly("text-before", "text-after").inOrder()
    assertThat(result.lastPartIsStreamingText).isTrue()
}
```

- [ ] **Step 2: Run failing orchestration test**

Run:

```bash
./gradlew :features:messages:impl:testDebugUnitTest --tests "io.element.android.features.messages.impl.timeline.factories.event.AiSdkStreamReducerTest.reducer inserts one tool root card at first registered tool candidate"
```

Expected: FAIL because `firstToolPartIndex` or `lastPartIsStreamingText` are not present or the reducer computes candidates incorrectly.

- [ ] **Step 3: Extend TimelineItemAiContent with orchestration fields**

In `TimelineItemAiContent.kt`, add:

```kotlin
val firstToolPartIndex: Int? = null,
val lastPartIsStreamingText: Boolean = false,
```

- [ ] **Step 4: Implement candidate algorithm**

In `AiSdkStreamReducer.mapSnapshot`, replace current tool filtering logic with:

```kotlin
val visible = streamParts.filterNot { it.isHiddenStreamPart }
val visibleToolCandidates = visible.filterIsInstance<AiToolStreamPart>()
    .filter { it.toolName.isRegisteredToolName }
val firstToolPartIndex = visible.indexOfFirst { part ->
    part is AiToolStreamPart && part.toolName.isRegisteredToolName
}.takeIf { it >= 0 }
val toolCardEntries = ToolCallRootCardAdapter.toolCallEntries(visibleToolCandidates).toImmutableList()
val passthroughParts = visible.filterNot { part ->
    part is AiToolStreamPart && part.toolName.isRegisteredToolName
}.toImmutableList()
val lastPartIsStreamingText = visible.lastOrNull().let { part ->
    part is AiTextStreamPart && part.state == "streaming"
}
```

Set the corresponding `TimelineItemAiContent` fields:

```kotlin
toolCardEntries = toolCardEntries,
passthroughParts = passthroughParts,
visibleParts = visible.toImmutableList(),
firstToolPartIndex = firstToolPartIndex,
lastPartIsStreamingText = lastPartIsStreamingText,
```

- [ ] **Step 5: Run reducer tests**

Run:

```bash
./gradlew :features:messages:impl:testDebugUnitTest --tests "io.element.android.features.messages.impl.timeline.factories.event.AiSdkStreamReducerTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/model/event/TimelineItemAiContent.kt \
  features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/factories/event/AiSdkStreamReducer.kt \
  features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/timeline/factories/event/AiSdkStreamReducerTest.kt
git commit -m "feat: align ai part orchestration with ios"
```

---

### Task 8: Add Room-Scoped Stream Handle Store

**Files:**
- Create: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/factories/event/AiStreamHandleStore.kt`
- Create: `features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/timeline/factories/event/AiStreamHandleStoreTest.kt`
- Modify: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/TimelineItemAiPresenter.kt`

- [ ] **Step 1: Add handle store tests**

Create `AiStreamHandleStoreTest.kt`:

```kotlin
package io.element.android.features.messages.impl.timeline.factories.event

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.agentstream.api.AgentStreamClient
import io.element.android.libraries.agentstream.api.AGENT_STREAM_SCHEMA_VERSION
import io.element.android.libraries.agentstream.api.RawStreamEvent
import io.element.android.libraries.agentstream.api.StreamError
import io.element.android.libraries.agentstream.api.StreamHandle
import io.element.android.libraries.agentstream.api.StreamListener
import io.element.android.libraries.agentstream.api.StreamPart
import io.element.android.libraries.agentstream.api.StreamRequest
import io.element.android.libraries.agentstream.api.StreamSnapshot
import io.element.android.libraries.agentstream.api.StreamStatus
import io.element.android.libraries.agentstream.api.StreamSubscription
import org.junit.Test

class AiStreamHandleStoreTest {
    @Test
    fun `same stream id reuses one sdk handle`() {
        val client = FakeAgentStreamClient()
        val store = AiStreamHandleStore(client)
        val request = StreamRequest(streamId = "stream-1", sender = "@a:b", roomId = "!room:b", eventId = "event-1")

        val first = store.bind(request) { }
        val second = store.bind(request) { }

        assertThat(client.requests).hasSize(1)
        assertThat(first.streamId).isEqualTo("stream-1")
        assertThat(second.streamId).isEqualTo("stream-1")
    }

    @Test
    fun `unbind cancels listener subscription but does not cancel sdk handle`() {
        val client = FakeAgentStreamClient()
        val store = AiStreamHandleStore(client)
        val binding = store.bind(StreamRequest("stream-1", "@a:b", "!room:b", "event-1")) { }

        binding.close()

        assertThat(client.handle.cancelCount).isEqualTo(0)
        assertThat(client.handle.subscriptionCancelCount).isEqualTo(1)
    }

    @Test
    fun `refresh delegates to existing sdk handle`() {
        val client = FakeAgentStreamClient()
        val store = AiStreamHandleStore(client)
        store.bind(StreamRequest("stream-1", "@a:b", "!room:b", "event-1")) { }

        store.refresh("stream-1")

        assertThat(client.handle.refreshCount).isEqualTo(1)
    }
}

private class FakeAgentStreamClient : AgentStreamClient {
    val requests = mutableListOf<StreamRequest>()
    val handle = FakeStreamHandle()
    override fun getStream(request: StreamRequest): StreamHandle {
        requests += request
        return handle
    }
}

private class FakeStreamHandle : StreamHandle {
    var refreshCount = 0
    var cancelCount = 0
    var subscriptionCancelCount = 0
    private val current = StreamSnapshot(
        schemaVersion = AGENT_STREAM_SCHEMA_VERSION,
        streamId = "stream-1",
        status = StreamStatus.Completed,
        parts = emptyList<StreamPart>(),
        rawEvents = emptyList<RawStreamEvent>(),
        updatedAtMs = 1L,
        completedAtMs = 1L,
        error = null as StreamError?,
    )

    override fun snapshot(): StreamSnapshot = current
    override fun subscribe(listener: StreamListener): StreamSubscription {
        listener.onSnapshot(current)
        return StreamSubscription { subscriptionCancelCount += 1 }
    }
    override fun refresh() { refreshCount += 1 }
    override fun cancel() { cancelCount += 1 }
}
```

- [ ] **Step 2: Run failing handle store tests**

Run:

```bash
./gradlew :features:messages:impl:testDebugUnitTest --tests "io.element.android.features.messages.impl.timeline.factories.event.AiStreamHandleStoreTest"
```

Expected: FAIL because `AiStreamHandleStore` does not exist.

- [ ] **Step 3: Implement handle store**

Create `AiStreamHandleStore.kt`:

```kotlin
package io.element.android.features.messages.impl.timeline.factories.event

import io.element.android.libraries.agentstream.api.AgentStreamClient
import io.element.android.libraries.agentstream.api.StreamHandle
import io.element.android.libraries.agentstream.api.StreamListener
import io.element.android.libraries.agentstream.api.StreamRequest
import io.element.android.libraries.agentstream.api.StreamSnapshot
import io.element.android.libraries.agentstream.api.StreamSubscription
import java.io.Closeable

class AiStreamHandleStore(
    private val client: AgentStreamClient,
) {
    private val handles = linkedMapOf<String, StreamHandle>()

    fun bind(
        request: StreamRequest,
        onSnapshot: (StreamSnapshot) -> Unit,
    ): Binding {
        val handle = handles.getOrPut(request.streamId) { client.getStream(request) }
        onSnapshot(handle.snapshot())
        val subscription = handle.subscribe(StreamListener { snapshot -> onSnapshot(snapshot) })
        return Binding(
            streamId = request.streamId,
            subscription = subscription,
        )
    }

    fun refresh(streamId: String) {
        handles[streamId]?.refresh()
    }

    fun cancelStream(streamId: String) {
        handles.remove(streamId)?.cancel()
    }

    class Binding(
        val streamId: String,
        private val subscription: StreamSubscription,
    ) : Closeable {
        override fun close() {
            subscription.cancel()
        }
    }
}
```

- [ ] **Step 4: Wire TimelineItemAiPresenter to handle store**

In `TimelineItemAiPresenter.kt`, replace constructor dependency:

```kotlin
private val agentStreamClient: AgentStreamClient,
```

with:

```kotlin
private val aiStreamHandleStore: AiStreamHandleStore,
```

Add import:

```kotlin
import io.element.android.features.messages.impl.timeline.factories.event.AiStreamHandleStore
```

Remove imports:

```kotlin
import io.element.android.libraries.agentstream.api.AgentStreamClient
```

Inside `collectStreamContent`, replace direct `agentStreamClient.getStream(...)` and `handle.subscribe` setup with:

```kotlin
val snapshots = Channel<StreamSnapshot>(Channel.UNLIMITED)
val updatePolicy = StreamSnapshotUpdatePolicy()
val binding = aiStreamHandleStore.bind(
    request = StreamRequest(
        streamId = streamId,
        sender = fallbackContent.sender.orEmpty(),
        roomId = "",
        eventId = "",
        includeRawEvents = false,
    ),
) { snapshot ->
    Timber.tag(DBG).d("recv stream=%s status=%s parts=%d", streamId, snapshot.status, snapshot.parts.size)
    snapshots.trySend(snapshot)
}
```

Keep the existing `emit`, `flushPendingPatch`, and `while` loop that apply `StreamSnapshotUpdatePolicy`. In the `finally` block, replace `subscription.cancel()` with:

```kotlin
binding.close()
```

Do not call `aiStreamHandleStore.cancelStream(streamId)` from normal list recycling or terminal completion.

- [ ] **Step 5: Run handle store tests and compile messages**

Run:

```bash
./gradlew :features:messages:impl:testDebugUnitTest --tests "io.element.android.features.messages.impl.timeline.factories.event.AiStreamHandleStoreTest"
./gradlew :features:messages:impl:compileDebugKotlin
```

Expected: PASS and `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/factories/event/AiStreamHandleStore.kt \
  features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/timeline/factories/event/AiStreamHandleStoreTest.kt \
  features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/TimelineItemAiPresenter.kt
git commit -m "feat: manage ai stream handles outside compose"
```

---

### Task 9: Render Tool Root Card And Markdown From Render Model Only

**Files:**
- Modify: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/TimelineItemAiView.kt`
- Modify: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/MarkdownBody.kt`
- Modify: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/ToolCardDispatcher.kt`
- Modify: `features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/ToolCardDispatcherTest.kt`
- Create: `features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/ToolCardDispatcherCoverageTest.kt`

- [ ] **Step 1: Add dispatcher coverage test**

Create `ToolCardDispatcherCoverageTest.kt`:

```kotlin
package io.element.android.features.messages.impl.timeline.components.event.toolcards

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ToolCardDispatcherCoverageTest {
    @Test
    fun `all root dispatch card types have dispatcher coverage`() {
        val covered = TOOL_CARD_DISPATCHER_CARD_TYPES

        assertThat(covered).containsAtLeastElementsIn(ROOT_DISPATCH_CARD_TYPES)
    }

    @Test
    fun `suspended cards have display only coverage`() {
        assertThat(TOOL_CARD_DISPATCHER_CARD_TYPES).containsAtLeastElementsIn(STANDALONE_SUSPENDED_CARD_TYPES)
    }
}
```

- [ ] **Step 2: Run failing dispatcher coverage test**

Run:

```bash
./gradlew :features:messages:impl:testDebugUnitTest --tests "io.element.android.features.messages.impl.timeline.components.event.toolcards.ToolCardDispatcherCoverageTest"
```

Expected: FAIL because `TOOL_CARD_DISPATCHER_CARD_TYPES` does not exist or lacks card types.

- [ ] **Step 3: Add dispatcher card type constant**

In `ToolCardDispatcher.kt`, add:

```kotlin
internal val TOOL_CARD_DISPATCHER_CARD_TYPES = ROOT_DISPATCH_CARD_TYPES + STANDALONE_SUSPENDED_CARD_TYPES
```

Update `ToolCard` so `moltbookRegister` is handled as display-only:

```kotlin
if (cardType == "moltbookRegister") {
    DisplayOnlySuspendedCard(data)
    return true
}
```

Add this composable to `ToolCardDispatcher.kt`:

```kotlin
@Composable
internal fun DisplayOnlySuspendedCard(data: JSONObject) {
    val title = data.cardString("title", "name") ?: "Action required"
    val status = data.cardString("status") ?: "Open on iOS or web to complete this action"
    ToolCardSurface {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Open on iOS or web to complete this action",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
```

- [ ] **Step 4: Change ToolCallRootCard to consume entries**

In `TimelineItemAiView.kt`, change `AiStreamPartsView` parameters from tool stream parts to tool card entries:

```kotlin
private fun AiStreamPartsView(
    visibleParts: List<AiStreamPart>,
    toolCardEntries: List<AiToolCardEntry>,
    firstToolPartIndex: Int?,
    isStreaming: Boolean,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
)
```

Import:

```kotlin
import io.element.android.features.messages.impl.timeline.model.event.AiToolCardEntry
import org.json.JSONObject
```

Render the single root card from `content.toolCardEntries` at `content.firstToolPartIndex`. The card call must use only precomputed entries:

```kotlin
if (toolCardEntries.isNotEmpty() && index == firstToolPartIndex) {
    ToolCallRootCard(
        entries = toolCardEntries,
        isStreaming = isStreaming,
        onLinkClick = onLinkClick,
        onLinkLongClick = onLinkLongClick,
    )
}
```

Change `ToolCallRootCard` signature to:

```kotlin
@Composable
private fun ToolCallRootCard(
    entries: List<AiToolCardEntry>,
    isStreaming: Boolean,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
)
```

Inside `ToolCallRootCard`, replace `parts` state calculations with entry state calculations:

```kotlin
if (entries.isEmpty()) return
var selectedIndex by remember(entries.joinToString(separator = "|") { it.id }) { mutableStateOf(entries.lastIndex) }
if (selectedIndex !in entries.indices) selectedIndex = entries.lastIndex
val selectedEntry = entries[selectedIndex]
val doneCount = entries.count { it.state == "done" }
val errorCount = entries.count { it.state == "error" }
val callingCount = entries.size - doneCount - errorCount
```

Render content from entry props:

```kotlin
val props = runCatching { JSONObject(selectedEntry.props) }.getOrDefault(JSONObject())
val cardType = props.optString("_cardType").takeIf { it.isNotBlank() } ?: "generic"
val rendered = ToolCard(
    cardType = cardType,
    rawData = props,
    onLinkClick = { },
)
if (!rendered) {
    Text(
        text = props.optString("errorText").takeIf { it.isNotBlank() }
            ?: if (selectedEntry.state == "calling") "Running tool..." else "Completed",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
```

Use `selectedEntry.name` in the header and tabs. Remove calls that pass `AiToolStreamPart` into `ToolPartContent` for root-card content.

- [ ] **Step 5: Ensure MarkdownBody handles links and incomplete markdown**

In `MarkdownBody.kt`, keep the existing `CompositionLocalProvider(LocalUriHandler provides uriHandler)` pattern. Verify the body still contains this exact logic:

```kotlin
val uriHandler = remember(onLinkClick) {
    object : UriHandler {
        override fun openUri(uri: String) = onLinkClick(Link(uri))
    }
}
```

Verify stable markdown parse caching still happens only by markdown text and never by full stream JSON:

```kotlin
val cached = if (isStreaming) null else MarkdownParseCache.get(text)
SideEffect { if (!isStreaming) MarkdownParseCache.put(text, state) }
```

- [ ] **Step 6: Run UI compile and dispatcher tests**

Run:

```bash
./gradlew :features:messages:impl:testDebugUnitTest --tests "io.element.android.features.messages.impl.timeline.components.event.toolcards.ToolCardDispatcherCoverageTest" --tests "io.element.android.features.messages.impl.timeline.components.event.toolcards.ToolCardDispatcherTest"
./gradlew :features:messages:impl:compileDebugKotlin
```

Expected: PASS and `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/TimelineItemAiView.kt \
  features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/MarkdownBody.kt \
  features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/ToolCardDispatcher.kt \
  features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/ToolCardDispatcherTest.kt \
  features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/ToolCardDispatcherCoverageTest.kt
git commit -m "feat: render ai stream cards from render model"
```

---

### Task 10: Verify Performance Boundaries And Document Android Stream SDK Use

**Files:**
- Modify: `AGENTS.md`
- Modify: `HANDOFF_AGENT_MANAGEMENT.md`
- Modify: `docs/superpowers/specs/2026-06-12-ai-tool-card-parity-design.md` only if implementation reveals a necessary correction

- [ ] **Step 1: Verify documentation files**

Run:

```bash
ls -1 AGENTS.md HANDOFF_AGENT_MANAGEMENT.md
```

Expected:

```text
AGENTS.md
HANDOFF_AGENT_MANAGEMENT.md
```

- [ ] **Step 2: Run full local verification**

Run:

```bash
./gradlew :libraries:agentstream:testDebugUnitTest
./gradlew :features:messages:impl:testDebugUnitTest
./gradlew :features:messages:impl:compileDebugKotlin
```

Expected: each command ends with `BUILD SUCCESSFUL`.

- [ ] **Step 3: Add documentation section for Stream SDK dependency**

Append this section to `AGENTS.md`:

```markdown
## Agent Stream SDK For Android AI Rendering

Android AI stream rendering must consume `libraries/agentstream` through `AgentStreamClient`.

Required flow:

1. Matrix timeline event exposes `streamId`.
2. Room/timeline binding calls `AgentStreamClient.getStream(StreamRequest(...))`.
3. The binding subscribes to `StreamHandle` snapshots.
4. `AiSdkStreamReducer.mapSnapshot()` converts SDK `StreamSnapshot` to `TimelineItemAiContent`.
5. Compose renders `TimelineItemAiContent` only.

Do not fetch SSE, parse full stream JSON, or write stream store from Compose or messages UI code.

Useful commands:

```bash
./gradlew :libraries:agentstream:testDebugUnitTest
./gradlew :features:messages:impl:testDebugUnitTest
./gradlew :features:messages:impl:compileDebugKotlin
```
```

- [ ] **Step 4: Update handoff document**

Append this section to `HANDOFF_AGENT_MANAGEMENT.md`:

```markdown
## AI SDK Stream Render Parity Status

The Android AI stream renderer now treats Stream SDK as the only stream data source. Stream SDK owns SSE parsing, terminal part normalization, cache/store writes, background execution, and lifecycle dedupe. Android messages code consumes SDK `StreamSnapshot` values and converts them with `AiSdkStreamReducer`.

Important files:

- `libraries/agentstream/src/main/kotlin/io/element/android/libraries/agentstream/api/`
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/factories/event/AiSdkStreamReducer.kt`
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/factories/event/AiStreamHandleStore.kt`
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/`
- `features/messages/impl/src/test/resources/toolcards/ios_tool_card_manifest.json`

Rules for follow-up work:

- Do not reimplement SSE fetching in `features/messages`.
- Do not mutate SDK part states in Android reducer or Compose.
- Add every new iOS tool card mapping to the checked-in manifest and fixture matrix.
- Keep tool card JSON transforms outside Compose.
- Normal list recycling cancels only UI subscriptions, not SDK background stream completion.
```

- [ ] **Step 5: Manual performance smoke**

On a release or debug build installed on the test phone, open a room with at least:

- 20 AI stream messages.
- 10 tool root cards.
- At least one Gmail card.
- At least one multi-tool card.
- At least one markdown message containing headings, list, code block, and link.

Verify:

- Fast scroll does not show multi-second stalls.
- Completed streams do not flash `thinking`, `running`, or `running tool`.
- Re-entering the room shows completed content from cache/store.
- Tool tabs switch.
- Tool expanded content is readable and not raw envelope JSON.
- URLs in markdown and cards are clickable.

- [ ] **Step 6: Commit**

```bash
git add AGENTS.md HANDOFF_AGENT_MANAGEMENT.md docs/superpowers/specs/2026-06-12-ai-tool-card-parity-design.md
git commit -m "docs: document android stream sdk rendering flow"
```

If `docs/superpowers/specs/2026-06-12-ai-tool-card-parity-design.md` was not modified, use this command instead:

```bash
git add AGENTS.md HANDOFF_AGENT_MANAGEMENT.md
git commit -m "docs: document android stream sdk rendering flow"
```

---

## Final Verification

Run:

```bash
git status --short
./gradlew :libraries:agentstream:testDebugUnitTest
./gradlew :features:messages:impl:testDebugUnitTest
./gradlew :features:messages:impl:compileDebugKotlin
```

Expected:

- `git status --short` shows no uncommitted files from this implementation branch, except unrelated pre-existing user changes that were intentionally not touched.
- Each Gradle command ends with `BUILD SUCCESSFUL`.

## Self-Review

Spec coverage:

- Stream SDK preconditions: Tasks 1, 2, 3.
- SDK terminal state normalization: Task 2.
- No Android duplicate stream model: Tasks 6, 7.
- Android handle ownership: Task 8.
- Tool registry, manifest, fixtures: Task 4.
- ToolCallRootCardAdapter parity: Task 5.
- Reducer orchestration and root-card insertion: Task 7.
- Markdown and URL rendering: Task 9.
- Suspended/display-only `moltbookRegister`: Tasks 4 and 9.
- Performance and documentation: Task 10.

Placeholder scan:

- No task uses `TBD`, `TODO`, `implement later`, or "similar to".
- Steps include exact file paths, commands, expected result, and concrete code snippets.

Type consistency:

- SDK source type is `StreamSnapshot`.
- Android render model is `TimelineItemAiContent`.
- Tool adapter input is `List<AiToolStreamPart>`.
- Tool adapter output is `List<AiToolCardEntry>`.
- Handle owner is `AiStreamHandleStore`.

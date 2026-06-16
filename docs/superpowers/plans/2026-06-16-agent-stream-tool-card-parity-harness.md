# Agent Stream Tool Card Parity Harness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first milestone of the cross-platform agent stream tool card parity harness: five shared AI SDK SSE fixtures, Android/iOS replay-to-render JSON export, component screenshot hosts, and parity reports.

**Architecture:** The manually maintained fixture source is AI SDK SSE JSONL under `unseal-android/docs/agent-stream-fixtures`. Android and iOS each replay those SSE events through their own stream parser/reducer path, export a normalized render JSON, and render deterministic component/full-client screenshot hosts. A small report script compares render JSON and points card work in the correct migration direction.

**Tech Stack:** Kotlin/JUnit/Truth/Compose for Android, Swift Package/XCTest/SwiftUI for iOS, JSONL SSE fixtures, Node.js for cross-repo artifact comparison.

---

## Source Spec

Implement the first milestone from:

- `/Users/Ruihan/go/src/unseal-android/docs/superpowers/specs/2026-06-16-agent-stream-tool-card-parity-harness-design.md`

Do not implement the full card migration matrix in this plan. This plan stops when the harness can reproduce and report P0 differences. Follow-up plans should fix `composeEmail`, `weather`, `moltbookRegister`, `hotelBooking`, and `fileAttachment` after the reports identify exact platform gaps.

## Worktree Safety

Before starting any task, inspect the Android and iOS repos:

```bash
git -C /Users/Ruihan/go/src/unseal-android status --short
git -C /Users/Ruihan/go/src/unseal-agent-ios status --short
git -C /Users/Ruihan/go/src/unseal-ios status --short
```

There are known unrelated Android edits in the working tree. Do not revert them. Each task below commits only the files it creates or modifies.

## File Structure

### Shared Fixture Files

- Create: `/Users/Ruihan/go/src/unseal-android/docs/agent-stream-fixtures/manifest.json`
  - Declares fixture ids, source files, priority, frames, and known current differences.
- Create: `/Users/Ruihan/go/src/unseal-android/docs/agent-stream-fixtures/fixtures/*.sse.jsonl`
  - Stores AI SDK stream events, one JSON object per line.
- Create: `/Users/Ruihan/go/src/unseal-android/docs/agent-stream-fixtures/artifacts/.gitignore`
  - Keeps generated render JSON, screenshots, and reports out of git.

### Android Harness Files

- Create: `/Users/Ruihan/go/src/unseal-android/libraries/agentstream/src/test/kotlin/io/element/android/libraries/agentstream/api/AgentStreamSseFixture.kt`
  - Loads shared JSONL fixture files and converts each line to an SSE chunk string for `AgentStreamSession`.
- Create: `/Users/Ruihan/go/src/unseal-android/libraries/agentstream/src/test/kotlin/io/element/android/libraries/agentstream/api/AgentStreamSseFixtureReplayTest.kt`
  - Verifies the native Stream SDK can replay the first P0 fixture when native reducer support is present.
- Create: `/Users/Ruihan/go/src/unseal-android/features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/timeline/factories/event/AgentStreamParityExport.kt`
  - Converts a `TimelineItemAiContent` into deterministic normalized JSON.
- Create: `/Users/Ruihan/go/src/unseal-android/features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/timeline/factories/event/AgentStreamParityExportTest.kt`
  - Tests normalized export shape without screenshots.
- Create: `/Users/Ruihan/go/src/unseal-android/features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/timeline/factories/event/AgentStreamParityReplayTest.kt`
  - Loads P0 fixture snapshots, maps through `AiSdkStreamReducer`, and writes Android render JSON artifacts.
- Create: `/Users/Ruihan/go/src/unseal-android/features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/AgentStreamParityPreview.kt`
  - Debug-only Compose host that renders one `TimelineItemAiView` from a provided `TimelineItemAiContent`.

### iOS Harness Files

- Modify: `/Users/Ruihan/go/src/unseal-agent-ios/Package.swift`
  - Adds an `UnsealUITests` target.
- Create: `/Users/Ruihan/go/src/unseal-agent-ios/UnsealUITests/AgentStreamSSEFixture.swift`
  - Loads shared fixture files from the Android repo and maps each JSONL line to `UIMessageChunk`.
- Create: `/Users/Ruihan/go/src/unseal-agent-ios/UnsealUITests/AgentStreamParityExport.swift`
  - Replays fixture chunks into `AgentParser`, collects `UIMessage`, and exports normalized render JSON.
- Create: `/Users/Ruihan/go/src/unseal-agent-ios/UnsealUITests/AgentStreamParityReplayTests.swift`
  - Verifies iOS replay/export for the P0 fixtures.
- Create: `/Users/Ruihan/go/src/unseal-agent-ios/UnsealUI/Views/Debug/AgentStreamParityPreview.swift`
  - Debug-only SwiftUI host that renders `BubbleMessageView` from a supplied `UIMessage`.

### Cross-Platform Report Files

- Create: `/Users/Ruihan/go/src/unseal-android/tools/agent-stream-parity/report.mjs`
  - Compares iOS and Android normalized render JSON artifacts and writes `parity-report.md`.
- Create: `/Users/Ruihan/go/src/unseal-android/tools/agent-stream-parity/README.md`
  - Documents local commands and artifact locations.

## Commands

Use these commands throughout:

```bash
./gradlew :libraries:agentstream:testDebugUnitTest
./gradlew :features:messages:impl:testDebugUnitTest
swift test --package-path /Users/Ruihan/go/src/unseal-agent-ios
node /Users/Ruihan/go/src/unseal-android/tools/agent-stream-parity/report.mjs \
  --root /Users/Ruihan/go/src/unseal-android/docs/agent-stream-fixtures
```

Expected successful test output contains `BUILD SUCCESSFUL` for Gradle and `Test Suite 'All tests' passed` for Swift.

---

### Task 1: Add Shared P0 SSE Fixtures

**Files:**
- Create: `/Users/Ruihan/go/src/unseal-android/docs/agent-stream-fixtures/manifest.json`
- Create: `/Users/Ruihan/go/src/unseal-android/docs/agent-stream-fixtures/fixtures/compose-email-list.sse.jsonl`
- Create: `/Users/Ruihan/go/src/unseal-android/docs/agent-stream-fixtures/fixtures/weather-current-forecast.sse.jsonl`
- Create: `/Users/Ruihan/go/src/unseal-android/docs/agent-stream-fixtures/fixtures/moltbook-register.sse.jsonl`
- Create: `/Users/Ruihan/go/src/unseal-android/docs/agent-stream-fixtures/fixtures/hotel-booking-gallery.sse.jsonl`
- Create: `/Users/Ruihan/go/src/unseal-android/docs/agent-stream-fixtures/fixtures/file-attachment-list.sse.jsonl`
- Create: `/Users/Ruihan/go/src/unseal-android/docs/agent-stream-fixtures/artifacts/.gitignore`

- [ ] **Step 1: Create the manifest**

Create `/Users/Ruihan/go/src/unseal-android/docs/agent-stream-fixtures/manifest.json`:

```json
{
  "version": 1,
  "fixtureRoot": "docs/agent-stream-fixtures",
  "fixtures": [
    {
      "id": "compose-email-list",
      "cardTypes": ["composeEmail"],
      "source": "fixtures/compose-email-list.sse.jsonl",
      "priority": "P0",
      "knownDifference": "Android renders Gmail message lists; iOS currently renders only single-message composeEmail fields.",
      "expectedInitialStatus": "ios-missing",
      "migrationDirection": "android-to-ios",
      "componentScreenshots": true,
      "fullClientScreenshots": true,
      "frames": [
        {"name": "calling", "afterEvent": 5},
        {"name": "completed", "afterEvent": "end"},
        {"name": "completed-expanded", "afterEvent": "end", "actions": ["expand-root-card"]}
      ]
    },
    {
      "id": "weather-current-forecast",
      "cardTypes": ["weather"],
      "source": "fixtures/weather-current-forecast.sse.jsonl",
      "priority": "P0",
      "knownDifference": "Android has weather registry, transform, and card UI; iOS root card registry and dispatch omit weather.",
      "expectedInitialStatus": "ios-missing",
      "migrationDirection": "android-to-ios",
      "componentScreenshots": true,
      "fullClientScreenshots": true,
      "frames": [
        {"name": "calling", "afterEvent": 5},
        {"name": "completed", "afterEvent": "end"}
      ]
    },
    {
      "id": "moltbook-register",
      "cardTypes": ["moltbookRegister"],
      "source": "fixtures/moltbook-register.sse.jsonl",
      "priority": "P0",
      "knownDifference": "iOS renders an interactive Moltbook registration card; Android renders display-only suspended content.",
      "expectedInitialStatus": "interaction-divergent",
      "migrationDirection": "ios-to-android",
      "componentScreenshots": true,
      "fullClientScreenshots": true,
      "frames": [
        {"name": "completed", "afterEvent": "end"},
        {"name": "interaction", "afterEvent": "end", "actions": ["focus-first-field"]}
      ]
    },
    {
      "id": "hotel-booking-gallery",
      "cardTypes": ["hotelBooking"],
      "source": "fixtures/hotel-booking-gallery.sse.jsonl",
      "priority": "P0",
      "knownDifference": "Android preserves richer hotel image data; iOS visual hierarchy and gallery behavior are stronger.",
      "expectedInitialStatus": "data-divergent",
      "migrationDirection": "bidirectional",
      "componentScreenshots": true,
      "fullClientScreenshots": true,
      "frames": [
        {"name": "calling", "afterEvent": 5},
        {"name": "completed", "afterEvent": "end"},
        {"name": "interaction", "afterEvent": "end", "actions": ["open-gallery"]}
      ]
    },
    {
      "id": "file-attachment-list",
      "cardTypes": ["fileAttachment"],
      "source": "fixtures/file-attachment-list.sse.jsonl",
      "priority": "P0",
      "knownDifference": "Android has stronger raw file transform; iOS has stronger row visual style.",
      "expectedInitialStatus": "data-divergent",
      "migrationDirection": "bidirectional",
      "componentScreenshots": true,
      "fullClientScreenshots": true,
      "frames": [
        {"name": "calling", "afterEvent": 5},
        {"name": "completed", "afterEvent": "end"}
      ]
    }
  ]
}
```

- [ ] **Step 2: Create compose email list fixture**

Create `/Users/Ruihan/go/src/unseal-android/docs/agent-stream-fixtures/fixtures/compose-email-list.sse.jsonl`:

```jsonl
{"type":"start","messageId":"msg-compose-email-list"}
{"type":"text-start","id":"text-1"}
{"type":"text-delta","id":"text-1","delta":"I found three recent emails from the team."}
{"type":"text-end","id":"text-1"}
{"type":"tool-input-start","toolCallId":"tool-gmail-list","toolName":"GMAIL_FETCH_EMAILS","title":"GMAIL_FETCH_EMAILS"}
{"type":"tool-input-available","toolCallId":"tool-gmail-list","toolName":"GMAIL_FETCH_EMAILS","title":"GMAIL_FETCH_EMAILS","input":{"query":"from:team@unseal.ai newer_than:7d"}}
{"type":"tool-output-available","toolCallId":"tool-gmail-list","output":{"successful":true,"data":{"messages":[{"subject":"Q3 planning meeting notes","from":{"name":"Jelf Liang","email":"jelf@unseal.ai"},"snippet":"Attached are the notes from today's Q3 planning session.","date":"Jun 15, 2026","url":"https://mail.google.com/mail/u/0/#inbox/1","labels":["Work","Planning"],"starred":true,"hasAttachments":true},{"subject":"Agent stream card parity","from":{"name":"Ruihan","email":"ruihan@unseal.ai"},"snippet":"Let's compare iOS and Android tool cards from the same stream.","date":"Jun 14, 2026","url":"https://mail.google.com/mail/u/0/#inbox/2","labels":["Engineering"],"starred":false,"hasAttachments":false},{"subject":"Hotel options for offsite","from":{"name":"Ops","email":"ops@unseal.ai"},"snippet":"Three options with gallery links are ready for review.","date":"Jun 13, 2026","url":"https://mail.google.com/mail/u/0/#inbox/3","labels":["Travel"],"starred":false,"hasAttachments":true}]}}}
{"type":"finish","finishReason":"stop"}
```

- [ ] **Step 3: Create weather fixture**

Create `/Users/Ruihan/go/src/unseal-android/docs/agent-stream-fixtures/fixtures/weather-current-forecast.sse.jsonl`:

```jsonl
{"type":"start","messageId":"msg-weather-current-forecast"}
{"type":"text-start","id":"text-1"}
{"type":"text-delta","id":"text-1","delta":"Here is the current weather and short forecast."}
{"type":"text-end","id":"text-1"}
{"type":"tool-input-start","toolCallId":"tool-weather","toolName":"COMPOSIO_SEARCH_WEATHER","title":"COMPOSIO_SEARCH_WEATHER"}
{"type":"tool-input-available","toolCallId":"tool-weather","toolName":"COMPOSIO_SEARCH_WEATHER","title":"COMPOSIO_SEARCH_WEATHER","input":{"location":"Shanghai, China"}}
{"type":"tool-output-available","toolCallId":"tool-weather","output":{"successful":true,"data":{"location":"Shanghai, China","weather_result":{"city":"Shanghai","country":"China","temperature":28,"feels_like":31,"humidity":74,"wind_speed":13,"condition":"Cloudy"},"forecast":[{"day":"Today","high":30,"low":24,"condition":"Cloudy"},{"day":"Tomorrow","high":32,"low":25,"condition":"Light rain"},{"day":"Thursday","high":31,"low":24,"condition":"Partly cloudy"}]}}}
{"type":"finish","finishReason":"stop"}
```

- [ ] **Step 4: Create Moltbook register fixture**

Create `/Users/Ruihan/go/src/unseal-android/docs/agent-stream-fixtures/fixtures/moltbook-register.sse.jsonl`:

```jsonl
{"type":"start","messageId":"msg-moltbook-register"}
{"type":"text-start","id":"text-1"}
{"type":"text-delta","id":"text-1","delta":"Moltbook needs authorization before I can continue."}
{"type":"text-end","id":"text-1"}
{"type":"data-tool-call-suspended","id":"suspend-moltbook","data":{"toolCallId":"moltbook-register-1","toolName":"moltbookRegister","targetUserId":"@ruihan:unseal.ai","title":"Connect Moltbook","reason":"Enter your Moltbook credentials to register this agent.","suspendPayload":{"kind":"moltbookRegister","agentId":"agent-mail","moltyName":"Mail Agent","claimUrl":"https://moltbook.example/claim/abc","verificationCode":"842193"}}}
{"type":"finish","finishReason":"stop"}
```

- [ ] **Step 5: Create hotel booking fixture**

Create `/Users/Ruihan/go/src/unseal-android/docs/agent-stream-fixtures/fixtures/hotel-booking-gallery.sse.jsonl`:

```jsonl
{"type":"start","messageId":"msg-hotel-booking-gallery"}
{"type":"text-start","id":"text-1"}
{"type":"text-delta","id":"text-1","delta":"I found hotel options with photos near the venue."}
{"type":"text-end","id":"text-1"}
{"type":"tool-input-start","toolCallId":"tool-hotels","toolName":"COMPOSIO_SEARCH_HOTELS","title":"COMPOSIO_SEARCH_HOTELS"}
{"type":"tool-input-available","toolCallId":"tool-hotels","toolName":"COMPOSIO_SEARCH_HOTELS","title":"COMPOSIO_SEARCH_HOTELS","input":{"location":"San Francisco","checkIn":"2026-07-10","checkOut":"2026-07-12"}}
{"type":"tool-output-available","toolCallId":"tool-hotels","output":{"successful":true,"data":{"results":{"hotels":[{"name":"Harbor Court Hotel","description":"Boutique waterfront hotel near the Ferry Building.","price":"$289","rating":4.5,"reviews":1240,"link":"https://travel.example/hotels/harbor-court","images":[{"url":"https://images.example/hotel/harbor-1.jpg","caption":"Lobby"},{"url":"https://images.example/hotel/harbor-2.jpg","caption":"Bay view room"}],"thumbnail":"https://images.example/hotel/harbor-thumb.jpg","amenities":["Free Wi-Fi","Gym","Restaurant"]},{"name":"Market Street Suites","description":"Apartment-style suites close to transit.","price":"$241","rating":4.2,"reviews":860,"link":"https://travel.example/hotels/market-suites","images":[{"url":"https://images.example/hotel/market-1.jpg","caption":"Suite"},{"url":"https://images.example/hotel/market-2.jpg","caption":"Kitchen"}],"thumbnail":"https://images.example/hotel/market-thumb.jpg","amenities":["Kitchen","Laundry","Workspace"]}]}}}}
{"type":"finish","finishReason":"stop"}
```

- [ ] **Step 6: Create file attachment fixture**

Create `/Users/Ruihan/go/src/unseal-android/docs/agent-stream-fixtures/fixtures/file-attachment-list.sse.jsonl`:

```jsonl
{"type":"start","messageId":"msg-file-attachment-list"}
{"type":"text-start","id":"text-1"}
{"type":"text-delta","id":"text-1","delta":"I found the relevant Drive files."}
{"type":"text-end","id":"text-1"}
{"type":"tool-input-start","toolCallId":"tool-drive-files","toolName":"GOOGLEDRIVE_FIND_FILE","title":"GOOGLEDRIVE_FIND_FILE"}
{"type":"tool-input-available","toolCallId":"tool-drive-files","toolName":"GOOGLEDRIVE_FIND_FILE","title":"GOOGLEDRIVE_FIND_FILE","input":{"query":"agent stream parity"}}
{"type":"tool-output-available","toolCallId":"tool-drive-files","output":{"successful":true,"data":{"files":[{"name":"Agent Stream Parity Plan","mimeType":"application/vnd.google-apps.document","webViewLink":"https://drive.google.com/file/d/doc-1/view","modifiedTime":"2026-06-15T09:30:00Z","size":"18432"},{"name":"Tool Card Screenshots","mimeType":"application/vnd.google-apps.folder","webViewLink":"https://drive.google.com/drive/folders/folder-1","modifiedTime":"2026-06-14T18:15:00Z"},{"name":"Fixture Matrix.csv","mimeType":"text/csv","webViewLink":"https://drive.google.com/file/d/csv-1/view","modifiedTime":"2026-06-13T11:20:00Z","size":"4096"}]}}}
{"type":"finish","finishReason":"stop"}
```

- [ ] **Step 7: Ignore generated artifacts**

Create `/Users/Ruihan/go/src/unseal-android/docs/agent-stream-fixtures/artifacts/.gitignore`:

```gitignore
*
!.gitignore
```

- [ ] **Step 8: Validate fixture JSONL syntax**

Run:

```bash
ruby -rjson -e 'ARGV.each { |path| File.readlines(path, chomp: true).each_with_index { |line, index| JSON.parse(line); raise "#{path}:#{index + 1} empty line" if line.strip.empty? } }' \
  /Users/Ruihan/go/src/unseal-android/docs/agent-stream-fixtures/fixtures/*.sse.jsonl
```

Expected: no output and exit code `0`.

- [ ] **Step 9: Commit shared fixtures**

```bash
git -C /Users/Ruihan/go/src/unseal-android add docs/agent-stream-fixtures
git -C /Users/Ruihan/go/src/unseal-android commit -m "test: add agent stream parity fixtures"
```

---

### Task 2: Add Android Stream SDK Fixture Replay Test

**Files:**
- Create: `/Users/Ruihan/go/src/unseal-android/libraries/agentstream/src/test/kotlin/io/element/android/libraries/agentstream/api/AgentStreamSseFixture.kt`
- Create: `/Users/Ruihan/go/src/unseal-android/libraries/agentstream/src/test/kotlin/io/element/android/libraries/agentstream/api/AgentStreamSseFixtureReplayTest.kt`

- [ ] **Step 1: Create the Android fixture loader**

Create `/Users/Ruihan/go/src/unseal-android/libraries/agentstream/src/test/kotlin/io/element/android/libraries/agentstream/api/AgentStreamSseFixture.kt`:

```kotlin
package io.element.android.libraries.agentstream.api

import java.io.File

internal data class AgentStreamSseFixture(
    val id: String,
    val events: List<String>,
) {
    fun asSseChunks(): List<String> {
        return events.map { line -> "data: $line\n\n" }
    }
}

internal object AgentStreamSseFixtures {
    private val root = File("docs/agent-stream-fixtures/fixtures")

    fun load(id: String): AgentStreamSseFixture {
        val file = root.resolve("$id.sse.jsonl")
        require(file.isFile) { "Missing fixture file: ${file.absolutePath}" }
        val events = file.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        require(events.isNotEmpty()) { "Fixture has no events: ${file.absolutePath}" }
        return AgentStreamSseFixture(id = id, events = events)
    }
}
```

- [ ] **Step 2: Add native replay test**

Create `/Users/Ruihan/go/src/unseal-android/libraries/agentstream/src/test/kotlin/io/element/android/libraries/agentstream/api/AgentStreamSseFixtureReplayTest.kt`:

```kotlin
package io.element.android.libraries.agentstream.api

import org.junit.Assume.assumeTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentStreamSseFixtureReplayTest {
    @Test
    fun `native reducer replays compose email list fixture`() {
        assumeTrue("Native agent stream reducer is unavailable in this unit test runtime", AgentStreamSession.isNativeAvailable())
        val fixture = AgentStreamSseFixtures.load("compose-email-list")
        val parser = StreamSnapshotParser(clock = { 1000L })

        AgentStreamSession(streamId = fixture.id, includeRawEvents = true).use { session ->
            var latest = parser.parse(session.snapshot())
            fixture.asSseChunks().forEach { chunk ->
                latest = parser.parse(session.applySseChunk(chunk))
            }
            latest = parser.parse(session.finish())

            assertEquals(fixture.id, latest.streamId)
            assertEquals(StreamStatus.Completed, latest.status)
            assertTrue(latest.parts.any { part ->
                part is StreamPart.Tool &&
                    part.toolName == "GMAIL_FETCH_EMAILS" &&
                    part.toolState == ToolPartState.OutputAvailable.wireValue
            })
        }
    }
}
```

- [ ] **Step 3: Run the replay test**

Run:

```bash
cd /Users/Ruihan/go/src/unseal-android
./gradlew :libraries:agentstream:testDebugUnitTest --tests "io.element.android.libraries.agentstream.api.AgentStreamSseFixtureReplayTest"
```

Expected: `BUILD SUCCESSFUL`. If the native reducer is not available in the local JVM test runtime, the test is skipped by JUnit assumption and the Gradle task still succeeds.

- [ ] **Step 4: Commit Android SDK replay test**

```bash
git -C /Users/Ruihan/go/src/unseal-android add \
  libraries/agentstream/src/test/kotlin/io/element/android/libraries/agentstream/api/AgentStreamSseFixture.kt \
  libraries/agentstream/src/test/kotlin/io/element/android/libraries/agentstream/api/AgentStreamSseFixtureReplayTest.kt
git -C /Users/Ruihan/go/src/unseal-android commit -m "test: replay agent stream sse fixtures in sdk"
```

---

### Task 3: Add Android Render JSON Export

**Files:**
- Create: `/Users/Ruihan/go/src/unseal-android/features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/timeline/factories/event/AgentStreamParityExport.kt`
- Create: `/Users/Ruihan/go/src/unseal-android/features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/timeline/factories/event/AgentStreamParityExportTest.kt`

- [ ] **Step 1: Create normalized export helper**

Create `/Users/Ruihan/go/src/unseal-android/features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/timeline/factories/event/AgentStreamParityExport.kt`:

```kotlin
package io.element.android.features.messages.impl.timeline.factories.event

import io.element.android.features.messages.impl.timeline.model.event.AiDataStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiErrorStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiFileStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiReasoningStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiSourceStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiTextStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiToolStreamPart
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemAiContent
import org.json.JSONArray
import org.json.JSONObject

internal object AgentStreamParityExport {
    fun renderJson(content: TimelineItemAiContent): JSONObject {
        return JSONObject()
            .put("streamId", content.streamId)
            .put("isStreaming", content.isStreaming)
            .put("isTerminal", content.isTerminal)
            .put("parts", JSONArray(content.visibleParts.map(::partJson)))
            .put("toolRoot", content.toolCallRoot?.let { root ->
                JSONObject()
                    .put("title", root.title)
                    .put("doneCount", root.doneCount)
                    .put("errorCount", root.errorCount)
                    .put("callingCount", root.callingCount)
                    .put("entries", JSONArray(root.entries.map { entry ->
                        JSONObject()
                            .put("id", entry.id)
                            .put("name", entry.name)
                            .put("cardType", entry.cardType)
                            .put("state", entry.state)
                            .put("propsKeys", sortedPropsKeys(entry.props))
                    }))
            } ?: JSONObject.NULL)
    }

    private fun partJson(part: AiStreamPart): JSONObject {
        return when (part) {
            is AiTextStreamPart -> JSONObject()
                .put("type", "text")
                .put("id", part.id)
                .put("state", part.state)
                .put("textLength", part.text.length)
            is AiReasoningStreamPart -> JSONObject()
                .put("type", "reasoning")
                .put("id", part.id)
                .put("state", part.state)
                .put("textLength", part.text.length)
            is AiToolStreamPart -> JSONObject()
                .put("type", "tool")
                .put("id", part.id)
                .put("state", part.state)
                .put("toolName", part.toolName)
            is AiDataStreamPart -> JSONObject()
                .put("type", part.type)
                .put("id", part.id)
                .put("state", part.state)
            is AiErrorStreamPart -> JSONObject()
                .put("type", "error")
                .put("id", part.id)
                .put("state", part.state)
                .put("message", part.errorText)
            is AiSourceStreamPart -> JSONObject()
                .put("type", "source")
                .put("id", part.id)
                .put("state", part.state)
                .put("sourceType", part.sourceType)
            is AiFileStreamPart -> JSONObject()
                .put("type", "file")
                .put("id", part.id)
                .put("state", part.state)
                .put("mediaType", part.mediaType)
            else -> JSONObject()
                .put("type", part::class.simpleName.orEmpty())
                .put("id", part.id)
                .put("state", part.state)
        }
    }

    private fun sortedPropsKeys(props: String): JSONArray {
        val json = runCatching { JSONObject(props) }.getOrNull() ?: return JSONArray()
        return JSONArray(json.keys().asSequence().toList().sorted())
    }
}
```

- [ ] **Step 2: Add exporter unit test**

Create `/Users/Ruihan/go/src/unseal-android/features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/timeline/factories/event/AgentStreamParityExportTest.kt`:

```kotlin
package io.element.android.features.messages.impl.timeline.factories.event

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.agentstream.api.StreamPart
import io.element.android.libraries.agentstream.api.StreamSnapshot
import io.element.android.libraries.agentstream.api.StreamStatus
import kotlinx.serialization.json.Json
import org.junit.Test

class AgentStreamParityExportTest {
    @Test
    fun `export includes visible parts and tool root entries`() {
        val content = AiSdkStreamReducer().mapSnapshot(
            snapshot = StreamSnapshot(
                streamId = "compose-email-list",
                status = StreamStatus.Completed,
                parts = listOf(
                    StreamPart.Text(id = "text-1", text = "Done", textState = "done"),
                    StreamPart.Tool(
                        id = "tool-gmail-list",
                        toolState = "output-available",
                        toolName = "GMAIL_FETCH_EMAILS",
                        input = Json.parseToJsonElement("""{"query":"from:team"}"""),
                        output = Json.parseToJsonElement("""{"successful":true,"data":{"messages":[{"subject":"A"}]}}"""),
                    ),
                ),
                updatedAtMs = 1L,
                completedAtMs = 2L,
            ),
            isEdited = false,
            sender = null,
        )

        val exported = AgentStreamParityExport.renderJson(content)

        assertThat(exported.getString("streamId")).isEqualTo("compose-email-list")
        assertThat(exported.getJSONArray("parts").length()).isEqualTo(2)
        val entries = exported.getJSONObject("toolRoot").getJSONArray("entries")
        assertThat(entries.length()).isEqualTo(1)
        assertThat(entries.getJSONObject(0).getString("cardType")).isEqualTo("composeEmail")
        assertThat(entries.getJSONObject(0).getJSONArray("propsKeys").toString()).contains("messages")
    }
}
```

- [ ] **Step 3: Run exporter test**

Run:

```bash
cd /Users/Ruihan/go/src/unseal-android
./gradlew :features:messages:impl:testDebugUnitTest --tests "io.element.android.features.messages.impl.timeline.factories.event.AgentStreamParityExportTest"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit Android export helper**

```bash
git -C /Users/Ruihan/go/src/unseal-android add \
  features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/timeline/factories/event/AgentStreamParityExport.kt \
  features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/timeline/factories/event/AgentStreamParityExportTest.kt
git -C /Users/Ruihan/go/src/unseal-android commit -m "test: export android agent stream parity json"
```

---

### Task 4: Add Android Fixture-to-Render Artifact Test

**Files:**
- Create: `/Users/Ruihan/go/src/unseal-android/features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/timeline/factories/event/AgentStreamParityReplayTest.kt`

- [ ] **Step 1: Create the artifact-writing replay test**

Create `/Users/Ruihan/go/src/unseal-android/features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/timeline/factories/event/AgentStreamParityReplayTest.kt`:

```kotlin
package io.element.android.features.messages.impl.timeline.factories.event

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.agentstream.api.AgentStreamSession
import io.element.android.libraries.agentstream.api.StreamSnapshotParser
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class AgentStreamParityReplayTest {
    @Test
    fun `write android render json for compose email list fixture`() {
        assumeTrue("Native agent stream reducer is unavailable in this unit test runtime", AgentStreamSession.isNativeAvailable())
        val content = replayFixture("compose-email-list")
        val exported = AgentStreamParityExport.renderJson(content)
        val out = artifactFile("compose-email-list", "android-render-completed.json")
        out.parentFile.mkdirs()
        out.writeText(exported.toString(2))

        assertThat(out.isFile).isTrue()
        assertThat(exported.getJSONObject("toolRoot").getJSONArray("entries").length()).isEqualTo(1)
    }

    private fun replayFixture(id: String) = AgentStreamSession(streamId = id, includeRawEvents = true).use { session ->
        val parser = StreamSnapshotParser(clock = { 1000L })
        val fixture = File("docs/agent-stream-fixtures/fixtures/$id.sse.jsonl")
        require(fixture.isFile) { "Missing fixture: ${fixture.absolutePath}" }
        var latest = parser.parse(session.snapshot())
        fixture.readLines().filter { it.isNotBlank() }.forEach { line ->
            latest = parser.parse(session.applySseChunk("data: $line\n\n"))
        }
        latest = parser.parse(session.finish())
        AiSdkStreamReducer().mapSnapshot(latest, isEdited = false, sender = "@agent:unseal.ai")
    }

    private fun artifactFile(fixtureId: String, name: String): File {
        return File("docs/agent-stream-fixtures/artifacts/$fixtureId/$name")
    }
}
```

- [ ] **Step 2: Run artifact test**

Run:

```bash
cd /Users/Ruihan/go/src/unseal-android
./gradlew :features:messages:impl:testDebugUnitTest --tests "io.element.android.features.messages.impl.timeline.factories.event.AgentStreamParityReplayTest"
```

Expected: `BUILD SUCCESSFUL`. If native reducer support is unavailable, the test is skipped and the task succeeds without writing an artifact.

- [ ] **Step 3: Confirm generated artifact is ignored**

Run:

```bash
git -C /Users/Ruihan/go/src/unseal-android status --short docs/agent-stream-fixtures/artifacts
```

Expected:

```text
```

- [ ] **Step 4: Commit Android artifact replay test**

```bash
git -C /Users/Ruihan/go/src/unseal-android add \
  features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/timeline/factories/event/AgentStreamParityReplayTest.kt
git -C /Users/Ruihan/go/src/unseal-android commit -m "test: write android parity render artifacts"
```

---

### Task 5: Add iOS Test Target and SSE Replay Export

**Files:**
- Modify: `/Users/Ruihan/go/src/unseal-agent-ios/Package.swift`
- Create: `/Users/Ruihan/go/src/unseal-agent-ios/UnsealUITests/AgentStreamSSEFixture.swift`
- Create: `/Users/Ruihan/go/src/unseal-agent-ios/UnsealUITests/AgentStreamParityExport.swift`
- Create: `/Users/Ruihan/go/src/unseal-agent-ios/UnsealUITests/AgentStreamParityReplayTests.swift`

- [ ] **Step 1: Add the iOS test target**

Modify `/Users/Ruihan/go/src/unseal-agent-ios/Package.swift` by adding this target after the `UnsealUI` target:

```swift
        .testTarget(
            name: "UnsealUITests",
            dependencies: [
                "UnsealAgent",
                "UnsealUI",
                .product(name: "ToolCardsIOS", package: "ToolCardsIOS")
            ],
            path: "UnsealUITests",
            swiftSettings: [.swiftLanguageMode(.v5)]
        )
```

The final `targets` array must contain `UnsealAgent`, `UnsealUI`, and `UnsealUITests`.

- [ ] **Step 2: Create iOS fixture loader**

Create `/Users/Ruihan/go/src/unseal-agent-ios/UnsealUITests/AgentStreamSSEFixture.swift`:

```swift
import Foundation
import UnsealUI

struct AgentStreamSSEFixture {
    let id: String
    let events: [String]

    static func load(_ id: String) throws -> AgentStreamSSEFixture {
        let root = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent("unseal-android/docs/agent-stream-fixtures/fixtures")
        let url = root.appendingPathComponent("\(id).sse.jsonl")
        let text = try String(contentsOf: url, encoding: .utf8)
        let events = text
            .split(separator: "\n")
            .map { String($0).trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
        precondition(!events.isEmpty, "Fixture has no events: \(url.path)")
        return AgentStreamSSEFixture(id: id, events: events)
    }

    func chunks() -> [UIMessageChunk] {
        events.compactMap { UIMessageChunk(data: $0) }
    }
}
```

- [ ] **Step 3: Create iOS parity exporter**

Create `/Users/Ruihan/go/src/unseal-agent-ios/UnsealUITests/AgentStreamParityExport.swift`:

```swift
import Foundation
import UnsealAgent
import UnsealUI
import ToolCardsIOS

final class ReplayCallbacks: StreamCallbacks {
    var latestAgentMessage: UIMessage?
    var errors: [Error] = []

    func onMessage(_ message: Message) {}
    func onElements(_ elements: [AnyElement]) {}
    func onMessage(_ message: UIMessage, from: String) { latestAgentMessage = message }
    func onError(_ error: Error) { errors.append(error) }
    func onError(_ error: Error, from: String) { errors.append(error) }
    func onComplete(_ data: Message?) {}
    func onComplete(_ data: UIMessage?, from: String) { latestAgentMessage = data }
    func onMessageStart(_ messageId: String?) {}
    func onMessageStart(_ messageId: String?, from: String) {}
}

enum AgentStreamParityExport {
    static func replay(_ fixture: AgentStreamSSEFixture) throws -> UIMessage {
        let callbacks = ReplayCallbacks()
        let parser = AgentParser(callbacks: callbacks)
        for chunk in fixture.chunks() {
            parser.parse(chunk: chunk)
        }
        guard callbacks.errors.isEmpty else {
            throw callbacks.errors[0]
        }
        guard let message = callbacks.latestAgentMessage else {
            throw NSError(domain: "AgentStreamParityExport", code: 1, userInfo: [NSLocalizedDescriptionKey: "No UIMessage produced"])
        }
        return message
    }

    static func renderJSON(message: UIMessage, streamId: String) throws -> [String: Any] {
        let toolParts = message.parts.compactMap { $0 as? ToolUIPart }
        let entries = toolCallEntries(from: toolParts)
        return [
            "streamId": streamId,
            "parts": message.parts.compactMap(partJSON(_:)),
            "toolRoot": entries.isEmpty ? NSNull() : [
                "entries": entries.map(entryJSON(_:))
            ]
        ]
    }

    static func writeRenderJSON(message: UIMessage, streamId: String, fileName: String) throws {
        let root = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent("unseal-android/docs/agent-stream-fixtures/artifacts")
            .appendingPathComponent(streamId)
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        let payload = try renderJSON(message: message, streamId: streamId)
        let data = try JSONSerialization.data(withJSONObject: payload, options: [.prettyPrinted, .sortedKeys])
        try data.write(to: root.appendingPathComponent(fileName))
    }

    private static func partJSON(_ part: Any) -> [String: Any]? {
        if let text = part as? TextUIPart {
            return ["type": "text", "state": text.state.rawValue, "textLength": text.text.count]
        }
        if let reasoning = part as? ReasoningUIPart {
            return ["type": "reasoning", "state": reasoning.state.rawValue, "textLength": reasoning.text.count]
        }
        if let tool = part as? ToolUIPart {
            return ["type": "tool", "id": tool.toolCallId, "state": tool.state.rawValue, "toolName": toolName(from: tool)]
        }
        if let data = part as? DataUIPart {
            return ["type": data.type.rawValue, "id": data.id ?? "", "state": "done"]
        }
        return nil
    }

    private static func entryJSON(_ entry: ToolCallEntry) -> [String: Any] {
        return [
            "id": entry.id,
            "name": entry.name,
            "cardType": (entry.props["_cardType"] as? String) ?? "",
            "state": entry.state.rawValue,
            "propsKeys": entry.props.keys.sorted()
        ]
    }
}
```

- [ ] **Step 4: Add iOS replay tests**

Create `/Users/Ruihan/go/src/unseal-agent-ios/UnsealUITests/AgentStreamParityReplayTests.swift`:

```swift
import XCTest
import UnsealAgent
import UnsealUI

final class AgentStreamParityReplayTests: XCTestCase {
    func testComposeEmailListReplayExportsRenderJSON() throws {
        let fixture = try AgentStreamSSEFixture.load("compose-email-list")
        let message = try AgentStreamParityExport.replay(fixture)
        try AgentStreamParityExport.writeRenderJSON(
            message: message,
            streamId: fixture.id,
            fileName: "ios-render-completed.json"
        )

        let json = try AgentStreamParityExport.renderJSON(message: message, streamId: fixture.id)
        let toolRoot = try XCTUnwrap(json["toolRoot"] as? [String: Any])
        let entries = try XCTUnwrap(toolRoot["entries"] as? [[String: Any]])
        XCTAssertEqual(entries.first?["cardType"] as? String, "composeEmail")
        XCTAssertEqual(entries.first?["state"] as? String, "done")
    }

    func testWeatherReplayCurrentlyProducesNoIOSSRootCardEntry() throws {
        let fixture = try AgentStreamSSEFixture.load("weather-current-forecast")
        let message = try AgentStreamParityExport.replay(fixture)
        let json = try AgentStreamParityExport.renderJSON(message: message, streamId: fixture.id)

        XCTAssertTrue(message.parts.contains { part in
            guard let tool = part as? ToolUIPart else { return false }
            return toolName(from: tool) == "COMPOSIO_SEARCH_WEATHER"
        })
        XCTAssertTrue(json["toolRoot"] is NSNull)
    }
}
```

- [ ] **Step 5: Run iOS tests**

Run:

```bash
swift test --package-path /Users/Ruihan/go/src/unseal-agent-ios --filter AgentStreamParityReplayTests
```

Expected: `Test Suite 'AgentStreamParityReplayTests' passed`.

- [ ] **Step 6: Confirm generated iOS artifact is ignored by Android repo**

Run:

```bash
git -C /Users/Ruihan/go/src/unseal-android status --short docs/agent-stream-fixtures/artifacts
```

Expected:

```text
```

- [ ] **Step 7: Commit iOS replay/export harness**

```bash
git -C /Users/Ruihan/go/src/unseal-agent-ios add \
  Package.swift \
  UnsealUITests/AgentStreamSSEFixture.swift \
  UnsealUITests/AgentStreamParityExport.swift \
  UnsealUITests/AgentStreamParityReplayTests.swift
git -C /Users/Ruihan/go/src/unseal-agent-ios commit -m "test: replay agent stream parity fixtures"
```

---

### Task 6: Add Component Preview Hosts

**Files:**
- Create: `/Users/Ruihan/go/src/unseal-android/features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/AgentStreamParityPreview.kt`
- Create: `/Users/Ruihan/go/src/unseal-agent-ios/UnsealUI/Views/Debug/AgentStreamParityPreview.swift`

- [ ] **Step 1: Add Android Compose preview host**

Create `/Users/Ruihan/go/src/unseal-android/features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/AgentStreamParityPreview.kt`:

```kotlin
package io.element.android.features.messages.impl.timeline.components.event

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemAiContent
import io.element.android.wysiwyg.link.Link

@Composable
internal fun AgentStreamParityPreview(
    content: TimelineItemAiContent,
    modifier: Modifier = Modifier,
) {
    TimelineItemAiView(
        content = content,
        onLinkClick = { _: Link -> },
        onLinkLongClick = { _: Link -> },
        modifier = modifier,
    )
}
```

- [ ] **Step 2: Add iOS SwiftUI preview host**

Create `/Users/Ruihan/go/src/unseal-agent-ios/UnsealUI/Views/Debug/AgentStreamParityPreview.swift`:

```swift
import SwiftUI
import UnsealAgent

@available(iOS 17.0, macOS 14.0, *)
public struct AgentStreamParityPreview: View {
    private let message: UIMessage
    private let content: IContent

    public init(message: UIMessage, streamId: String = "agent-stream-parity-preview") {
        self.message = message
        self.content = IContent(json: [
            "content": [
                "msgtype": "m.text",
                "body": "",
                "stream_id": streamId,
                "sender": "@agent:unseal.ai"
            ]
        ])
    }

    public var body: some View {
        BubbleMessageView(
            message: message,
            content: content,
            delegate: nil,
            isStreaming: false
        )
        .frame(width: 360, alignment: .leading)
        .padding(16)
    }
}
```

- [ ] **Step 3: Compile Android messages module**

Run:

```bash
cd /Users/Ruihan/go/src/unseal-android
./gradlew :features:messages:impl:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Compile iOS package tests**

Run:

```bash
swift test --package-path /Users/Ruihan/go/src/unseal-agent-ios --filter AgentStreamParityReplayTests
```

Expected: `Test Suite 'AgentStreamParityReplayTests' passed`.

- [ ] **Step 5: Commit component preview hosts**

```bash
git -C /Users/Ruihan/go/src/unseal-android add \
  features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/AgentStreamParityPreview.kt
git -C /Users/Ruihan/go/src/unseal-android commit -m "test: add android agent stream parity preview host"

git -C /Users/Ruihan/go/src/unseal-agent-ios add \
  UnsealUI/Views/Debug/AgentStreamParityPreview.swift
git -C /Users/Ruihan/go/src/unseal-agent-ios commit -m "test: add ios agent stream parity preview host"
```

---

### Task 7: Add Cross-Platform Parity Report Script

**Files:**
- Create: `/Users/Ruihan/go/src/unseal-android/tools/agent-stream-parity/report.mjs`
- Create: `/Users/Ruihan/go/src/unseal-android/tools/agent-stream-parity/README.md`

- [ ] **Step 1: Create report script**

Create `/Users/Ruihan/go/src/unseal-android/tools/agent-stream-parity/report.mjs`:

```javascript
#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";

const rootArgIndex = process.argv.indexOf("--root");
const root = rootArgIndex >= 0
  ? process.argv[rootArgIndex + 1]
  : path.resolve("docs/agent-stream-fixtures");

const manifestPath = path.join(root, "manifest.json");
const manifest = JSON.parse(fs.readFileSync(manifestPath, "utf8"));

function readJsonIfExists(file) {
  if (!fs.existsSync(file)) return null;
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

function entries(render) {
  if (!render || !render.toolRoot || render.toolRoot === null) return [];
  return Array.isArray(render.toolRoot.entries) ? render.toolRoot.entries : [];
}

function summarizeFixture(fixture) {
  const artifactDir = path.join(root, "artifacts", fixture.id);
  const ios = readJsonIfExists(path.join(artifactDir, "ios-render-completed.json"));
  const android = readJsonIfExists(path.join(artifactDir, "android-render-completed.json"));
  const iosEntries = entries(ios);
  const androidEntries = entries(android);
  const lines = [];
  lines.push(`# ${fixture.id}`);
  lines.push("");
  lines.push(`Priority: ${fixture.priority}`);
  lines.push(`Expected initial status: ${fixture.expectedInitialStatus}`);
  lines.push(`Migration direction: ${fixture.migrationDirection}`);
  lines.push("");
  lines.push("## Replay");
  lines.push("");
  lines.push(`- iOS render JSON: ${ios ? "present" : "missing"}`);
  lines.push(`- Android render JSON: ${android ? "present" : "missing"}`);
  lines.push("");
  lines.push("## Tool Entries");
  lines.push("");
  lines.push(`- iOS entries: ${iosEntries.length}`);
  lines.push(`- Android entries: ${androidEntries.length}`);
  lines.push(`- iOS card types: ${iosEntries.map((entry) => entry.cardType).join(", ") || "none"}`);
  lines.push(`- Android card types: ${androidEntries.map((entry) => entry.cardType).join(", ") || "none"}`);
  lines.push("");
  lines.push("## Props Keys");
  lines.push("");
  const max = Math.max(iosEntries.length, androidEntries.length);
  for (let index = 0; index < max; index += 1) {
    const iosKeys = iosEntries[index]?.propsKeys ?? [];
    const androidKeys = androidEntries[index]?.propsKeys ?? [];
    lines.push(`- Entry ${index + 1} iOS: ${iosKeys.join(", ") || "none"}`);
    lines.push(`- Entry ${index + 1} Android: ${androidKeys.join(", ") || "none"}`);
  }
  lines.push("");
  lines.push("## Current Status");
  lines.push("");
  lines.push(statusFor(fixture, ios, android, iosEntries, androidEntries));
  lines.push("");
  fs.mkdirSync(artifactDir, { recursive: true });
  fs.writeFileSync(path.join(artifactDir, "parity-report.md"), `${lines.join("\n")}\n`);
  return { id: fixture.id, report: path.join(artifactDir, "parity-report.md") };
}

function statusFor(fixture, ios, android, iosEntries, androidEntries) {
  if (!ios && !android) return "- `blocked`: both clients are missing render JSON.";
  if (!ios) return "- `blocked`: iOS render JSON is missing.";
  if (!android) return "- `blocked`: Android render JSON is missing.";
  if (iosEntries.length === 0 && androidEntries.length > 0) return "- `ios-missing`: Android has tool card entries but iOS does not.";
  if (androidEntries.length === 0 && iosEntries.length > 0) return "- `android-missing`: iOS has tool card entries but Android does not.";
  const iosTypes = iosEntries.map((entry) => entry.cardType).join("|");
  const androidTypes = androidEntries.map((entry) => entry.cardType).join("|");
  if (iosTypes !== androidTypes) return "- `data-divergent`: card type sequence differs.";
  return `- \`${fixture.expectedInitialStatus}\`: render JSON exists on both clients; review screenshots and props key differences.`;
}

const results = manifest.fixtures.map(summarizeFixture);
console.log(`Wrote ${results.length} parity reports`);
for (const result of results) {
  console.log(`${result.id}: ${result.report}`);
}
```

- [ ] **Step 2: Create report README**

Create `/Users/Ruihan/go/src/unseal-android/tools/agent-stream-parity/README.md`:

```markdown
# Agent Stream Parity Tools

This folder contains local tooling for comparing iOS and Android render artifacts generated from shared AI SDK SSE fixtures.

Run from the Android repo:

```bash
node tools/agent-stream-parity/report.mjs --root docs/agent-stream-fixtures
```

The script reads:

- `docs/agent-stream-fixtures/manifest.json`
- `docs/agent-stream-fixtures/artifacts/<fixture>/ios-render-completed.json`
- `docs/agent-stream-fixtures/artifacts/<fixture>/android-render-completed.json`

It writes:

- `docs/agent-stream-fixtures/artifacts/<fixture>/parity-report.md`

Generated artifacts are ignored by git.
```

- [ ] **Step 3: Run report script**

Run:

```bash
node /Users/Ruihan/go/src/unseal-android/tools/agent-stream-parity/report.mjs \
  --root /Users/Ruihan/go/src/unseal-android/docs/agent-stream-fixtures
```

Expected output:

```text
Wrote 5 parity reports
compose-email-list: /Users/Ruihan/go/src/unseal-android/docs/agent-stream-fixtures/artifacts/compose-email-list/parity-report.md
weather-current-forecast: /Users/Ruihan/go/src/unseal-android/docs/agent-stream-fixtures/artifacts/weather-current-forecast/parity-report.md
moltbook-register: /Users/Ruihan/go/src/unseal-android/docs/agent-stream-fixtures/artifacts/moltbook-register/parity-report.md
hotel-booking-gallery: /Users/Ruihan/go/src/unseal-android/docs/agent-stream-fixtures/artifacts/hotel-booking-gallery/parity-report.md
file-attachment-list: /Users/Ruihan/go/src/unseal-android/docs/agent-stream-fixtures/artifacts/file-attachment-list/parity-report.md
```

- [ ] **Step 4: Confirm reports are ignored**

Run:

```bash
git -C /Users/Ruihan/go/src/unseal-android status --short docs/agent-stream-fixtures/artifacts
```

Expected:

```text
```

- [ ] **Step 5: Commit report tool**

```bash
git -C /Users/Ruihan/go/src/unseal-android add tools/agent-stream-parity
git -C /Users/Ruihan/go/src/unseal-android commit -m "test: add agent stream parity report tool"
```

---

### Task 8: Add Full-Client Smoke Entry Points

**Files:**
- Modify: `/Users/Ruihan/go/src/unseal-android/features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/AgentStreamParityPreview.kt`
- Modify: `/Users/Ruihan/go/src/unseal-agent-ios/UnsealUI/Views/Debug/AgentStreamParityPreview.swift`

- [ ] **Step 1: Extend Android preview host with fixed smoke width**

Modify `/Users/Ruihan/go/src/unseal-android/features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/AgentStreamParityPreview.kt` so the file becomes:

```kotlin
package io.element.android.features.messages.impl.timeline.components.event

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemAiContent
import io.element.android.wysiwyg.link.Link

@Composable
internal fun AgentStreamParityPreview(
    content: TimelineItemAiContent,
    modifier: Modifier = Modifier,
) {
    TimelineItemAiView(
        content = content,
        onLinkClick = { _: Link -> },
        onLinkLongClick = { _: Link -> },
        modifier = modifier.width(360.dp),
    )
}
```

- [ ] **Step 2: Extend iOS preview host with stable background**

Modify `/Users/Ruihan/go/src/unseal-agent-ios/UnsealUI/Views/Debug/AgentStreamParityPreview.swift` so the `body` is:

```swift
    public var body: some View {
        BubbleMessageView(
            message: message,
            content: content,
            delegate: nil,
            isStreaming: false
        )
        .frame(width: 360, alignment: .leading)
        .padding(16)
        .background(Color(.systemBackground))
    }
```

- [ ] **Step 3: Run compile checks**

Run:

```bash
cd /Users/Ruihan/go/src/unseal-android
./gradlew :features:messages:impl:compileDebugKotlin
swift test --package-path /Users/Ruihan/go/src/unseal-agent-ios --filter AgentStreamParityReplayTests
```

Expected: Android prints `BUILD SUCCESSFUL`; Swift prints `Test Suite 'AgentStreamParityReplayTests' passed`.

- [ ] **Step 4: Commit smoke host refinements**

```bash
git -C /Users/Ruihan/go/src/unseal-android add \
  features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/AgentStreamParityPreview.kt
git -C /Users/Ruihan/go/src/unseal-android commit -m "test: stabilize android parity preview sizing"

git -C /Users/Ruihan/go/src/unseal-agent-ios add \
  UnsealUI/Views/Debug/AgentStreamParityPreview.swift
git -C /Users/Ruihan/go/src/unseal-agent-ios commit -m "test: stabilize ios parity preview background"
```

---

### Task 9: Run Milestone Verification

**Files:**
- No file changes expected.

- [ ] **Step 1: Run Android SDK tests**

```bash
cd /Users/Ruihan/go/src/unseal-android
./gradlew :libraries:agentstream:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run Android message tests**

```bash
cd /Users/Ruihan/go/src/unseal-android
./gradlew :features:messages:impl:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Run iOS package tests**

```bash
swift test --package-path /Users/Ruihan/go/src/unseal-agent-ios
```

Expected: `Test Suite 'All tests' passed`.

- [ ] **Step 4: Generate parity reports**

```bash
node /Users/Ruihan/go/src/unseal-android/tools/agent-stream-parity/report.mjs \
  --root /Users/Ruihan/go/src/unseal-android/docs/agent-stream-fixtures
```

Expected output begins with:

```text
Wrote 5 parity reports
```

- [ ] **Step 5: Confirm no generated artifacts are tracked**

```bash
git -C /Users/Ruihan/go/src/unseal-android status --short docs/agent-stream-fixtures/artifacts
```

Expected:

```text
```

- [ ] **Step 6: Record verification in the final implementation handoff**

When implementation is complete, summarize:

```text
Verified:
- Android SDK tests: passed
- Android messages tests: passed
- iOS package tests: passed
- Parity reports generated for 5 fixtures

Known expected initial differences:
- compose-email-list: iOS missing list-mode rendering
- weather-current-forecast: iOS missing weather root dispatch
- moltbook-register: Android interaction divergent
- hotel-booking-gallery: bidirectional data/visual divergence
- file-attachment-list: bidirectional data/visual divergence
```

---

## Self-Review Checklist

- Spec coverage: This plan covers the first implementation milestone from the spec: five P0 fixtures, replay/export on both clients, component preview hosts, full-client smoke host defaults, and parity reports.
- Intentional split: Full card migration is not implemented here because the spec explicitly needs the harness to identify exact differences before bidirectional card fixes.
- Placeholder scan: The plan contains no `TBD`, `TODO`, or intentionally vague implementation steps.
- Type consistency: Android uses `TimelineItemAiContent`, `AiSdkStreamReducer`, `AgentStreamSession`, and `ToolCallRootCardAdapter` names already present in the codebase. iOS uses `AgentParser`, `UIMessageChunk`, `UIMessage`, `ToolUIPart`, and `toolCallEntries(from:)` names already present in the codebase.

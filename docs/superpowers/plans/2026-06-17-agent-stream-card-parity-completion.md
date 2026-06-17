# Agent Stream Card Parity Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish the remaining Android/iOS agent stream card parity work so every P0 fixture renders the same card data, status semantics, layout density, loading state, and stream update behavior across both mobile clients.

**Architecture:** The shared fixture source and report artifacts live in the Android worktree; Android owns Jetpack Compose card rendering and reducer/export tests, `unseal-agent-ios` owns SwiftUI card rendering and stream replay/export, and `unseal-ios` owns only full-client smoke/debug rendering. Each task closes one parity gap with a failing export or view-model test first, then a scoped UI/data fix, then artifact regeneration.

**Tech Stack:** Kotlin/JUnit/Truth/Jetpack Compose for Android, Swift/XCTest/SwiftUI for iOS rendering, ElementX SwiftUI client smoke host, JSONL AI SDK SSE fixtures, normalized render JSON parity artifacts.

---

## Current Worktrees

Use these exact worktrees for this completion pass:

- Android: `/Users/Ruihan/.config/superpowers/worktrees/unseal-android/agent-stream-card-parity-harness`
- iOS rendering library and agent stream models: `/Users/Ruihan/.config/superpowers/worktrees/unseal-agent-ios/agent-stream-card-parity-harness`
- iOS client app: `/Users/Ruihan/.config/superpowers/worktrees/unseal-ios/agent-stream-card-parity-harness`

Confirm branch and dirty state before each task:

```bash
git -C /Users/Ruihan/.config/superpowers/worktrees/unseal-android/agent-stream-card-parity-harness status --short --branch
git -C /Users/Ruihan/.config/superpowers/worktrees/unseal-agent-ios/agent-stream-card-parity-harness status --short --branch
git -C /Users/Ruihan/.config/superpowers/worktrees/unseal-ios/agent-stream-card-parity-harness status --short --branch
```

Expected branch line in all three repos:

```text
## agent-stream-card-parity-harness
```

Do not revert unrelated local edits. Commit only files touched by the task being executed.

## iOS Repository Boundary

`unseal-agent-ios` is the rendering and stream library. Modify it for:

- Tool card transforms and SwiftUI card views under `/Users/Ruihan/.config/superpowers/worktrees/unseal-agent-ios/agent-stream-card-parity-harness/ToolCardsIOS/Sources/ToolCardsIOS/`
- Agent stream render support under `/Users/Ruihan/.config/superpowers/worktrees/unseal-agent-ios/agent-stream-card-parity-harness/UnsealAgent/JsonRender/Core/`
- Agent/UI replay tests under `/Users/Ruihan/.config/superpowers/worktrees/unseal-agent-ios/agent-stream-card-parity-harness/UnsealUITests/`
- Bubble stream rendering under `/Users/Ruihan/.config/superpowers/worktrees/unseal-agent-ios/agent-stream-card-parity-harness/UnsealUI/Views/`

`unseal-ios` is the client. Modify it only for:

- Full-client smoke/debug host under `/Users/Ruihan/.config/superpowers/worktrees/unseal-ios/agent-stream-card-parity-harness/ElementX/Sources/Screens/Timeline/View/TimelineItemViews/AIMessage/Debug/`
- Minimal AI timeline wiring only if the debug smoke cannot reach the real render path.

## Remaining Completion Matrix

| Fixture | Card or Surface | Remaining Gap | Direction |
| --- | --- | --- | --- |
| `compose-email-list` | `composeEmail` | iOS must render Android's list-mode Gmail card, not only single compose fields. | Android -> iOS |
| `weather-current-forecast` | `weather` | iOS root dispatch/export must prove weather card is rendered with current and forecast data. | Android -> iOS |
| `moltbook-register` | `moltbookRegister` | Android must match iOS interactive registration fields and suspended callback state. | iOS -> Android |
| `hotel-booking-gallery` | `hotelBooking` | Both sides need the same compact layout, separate tag row, image data, and gallery affordance. | Bidirectional |
| `file-attachment-list` | `fileAttachment` | Android data transform and iOS row styling must converge into the same file rows. | Bidirectional |
| `stream-mixed-parts` | text/reasoning/source/file/error | Non-tool stream parts need exported iOS artifacts and smooth Android incremental rendering verification. | Bidirectional |

## Execution Status Snapshot

Updated after the Android card completion pass on 2026-06-17:

- Task-list migration pass: complete. The known parity gaps are represented as executable tasks in Task 1 through Task 8.
- Android card implementation pass: complete for Moltbook suspended fields, hotel data/layout normalization, file attachment normalization, root status export coverage, and Android render artifact regeneration.
- iOS implementation pass: pending. Compose email list mode, weather dispatch/export proof, hotel layout/data convergence, file attachment row styling, loading shimmer, and client smoke remain in the task list.
- Final visual parity: pending. Android and iOS still need same-fixture screenshots and artifact comparison before claiming full card effect parity.

## Shared Verification Commands

Run Android tests from:

```bash
cd /Users/Ruihan/.config/superpowers/worktrees/unseal-android/agent-stream-card-parity-harness
```

Core Android verification:

```bash
./gradlew :features:messages:impl:compileDebugKotlin
./gradlew :features:messages:impl:testDebugUnitTest --tests "io.element.android.features.messages.impl.timeline.factories.event.AgentStreamParityReplayTest"
./gradlew :features:messages:impl:testDebugUnitTest --tests "io.element.android.features.messages.impl.timeline.components.event.toolcards.ToolCardRegistryTest"
./gradlew :features:messages:impl:testDebugUnitTest --tests "io.element.android.features.messages.impl.timeline.components.event.toolcards.ToolCardDispatcherCoverageTest"
```

Expected Gradle result:

```text
BUILD SUCCESSFUL
```

Run iOS render-library parse checks from:

```bash
cd /Users/Ruihan/.config/superpowers/worktrees/unseal-agent-ios/agent-stream-card-parity-harness
```

Core iOS verification that does not require full Xcode:

```bash
swiftc -parse UnsealUITests/AgentStreamParityExport.swift UnsealUITests/AgentStreamParityReplayTests.swift UnsealUITests/AgentStreamSSEFixture.swift
swiftc -parse $(rg --files ToolCardsIOS/Sources/ToolCardsIOS | sort)
```

Expected result: both commands exit with status `0` and no Swift parse errors.

When full Xcode is available, also run:

```bash
xcodebuild -project /Users/Ruihan/.config/superpowers/worktrees/unseal-agent-ios/agent-stream-card-parity-harness/UnsealAgent.xcodeproj \
  -scheme UnsealAgent \
  -destination 'generic/platform=iOS' build
xcodebuild -project /Users/Ruihan/.config/superpowers/worktrees/unseal-agent-ios/agent-stream-card-parity-harness/UnsealAgent.xcodeproj \
  -scheme UnsealAgent \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' test
```

Expected Xcode result:

```text
** BUILD SUCCEEDED **
** TEST SUCCEEDED **
```

If the machine reports `/Library/Developer/CommandLineTools` as the active developer directory, record this as an environment blocker for full iOS export/screenshots and continue with parse-level and Android verification.

---

### Task 1: Lock the Fixture Status Gate

**Files:**
- Modify: `/Users/Ruihan/.config/superpowers/worktrees/unseal-android/agent-stream-card-parity-harness/features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/timeline/factories/event/AgentStreamParityReplayTest.kt`
- Modify: `/Users/Ruihan/.config/superpowers/worktrees/unseal-agent-ios/agent-stream-card-parity-harness/UnsealUITests/AgentStreamParityReplayTests.swift`
- Read: `/Users/Ruihan/.config/superpowers/worktrees/unseal-android/agent-stream-card-parity-harness/docs/agent-stream-fixtures/manifest.json`

- [ ] **Step 1: Add Android fixture coverage assertions**

In `AgentStreamParityReplayTest.kt`, add this test beside the existing replay tests:

```kotlin
@Test
fun `all remaining parity fixtures expose a root card or stream parts`() {
    val expected = mapOf(
        "compose-email-list" to "composeEmail",
        "weather-current-forecast" to "weather",
        "moltbook-register" to "moltbookRegister",
        "hotel-booking-gallery" to "hotelBooking",
        "file-attachment-list" to "fileAttachment",
    )

    expected.forEach { (fixtureId, cardType) ->
        val exported = AgentStreamParityExport.renderJson(replayFixture(fixtureId))
        val entries = exported.getJSONObject("toolRoot").getJSONArray("entries")
        assertThat(entries.length()).isAtLeast(1)
        assertThat(entries.getJSONObject(0).getString("cardType")).isEqualTo(cardType)
    }

    val mixed = AgentStreamParityExport.renderJson(replayFixture("stream-mixed-parts"))
    val types = (0 until mixed.getJSONArray("parts").length()).map {
        mixed.getJSONArray("parts").getJSONObject(it).getString("type")
    }
    assertThat(types).containsAtLeast("text", "reasoning", "source-url", "source-document", "file", "data-error-card")
}
```

- [ ] **Step 2: Run Android fixture test and verify it passes**

Run:

```bash
cd /Users/Ruihan/.config/superpowers/worktrees/unseal-android/agent-stream-card-parity-harness
./gradlew :features:messages:impl:testDebugUnitTest --tests "io.element.android.features.messages.impl.timeline.factories.event.AgentStreamParityReplayTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 3: Add iOS fixture coverage assertions**

In `AgentStreamParityReplayTests.swift`, add this test beside the existing replay tests:

```swift
func testAllRemainingToolCardFixturesExportExpectedCardTypes() throws {
    let expected: [String: String] = [
        "compose-email-list": "composeEmail",
        "weather-current-forecast": "weather",
        "moltbook-register": "moltbookRegister",
        "hotel-booking-gallery": "hotelBooking",
        "file-attachment-list": "fileAttachment"
    ]

    for (fixtureId, cardType) in expected {
        let fixture = try AgentStreamSSEFixture.load(fixtureId)
        let message = try AgentStreamParityExport.replay(fixture)
        let json = try AgentStreamParityExport.renderJSON(message: message, streamId: fixture.id)
        let toolRoot = try XCTUnwrap(json["toolRoot"] as? [String: Any], fixtureId)
        let entries = try XCTUnwrap(toolRoot["entries"] as? [[String: Any]], fixtureId)
        XCTAssertFalse(entries.isEmpty, fixtureId)
        XCTAssertEqual(entries.first?["cardType"] as? String, cardType, fixtureId)
    }
}
```

- [ ] **Step 4: Run Swift parse check**

Run:

```bash
cd /Users/Ruihan/.config/superpowers/worktrees/unseal-agent-ios/agent-stream-card-parity-harness
swiftc -parse UnsealUITests/AgentStreamParityExport.swift UnsealUITests/AgentStreamParityReplayTests.swift UnsealUITests/AgentStreamSSEFixture.swift
```

Expected: exit status `0`.

- [ ] **Step 5: Commit the fixture status gate**

```bash
git -C /Users/Ruihan/.config/superpowers/worktrees/unseal-android/agent-stream-card-parity-harness add features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/timeline/factories/event/AgentStreamParityReplayTest.kt
git -C /Users/Ruihan/.config/superpowers/worktrees/unseal-android/agent-stream-card-parity-harness commit -m "test: lock android agent stream parity fixtures"
git -C /Users/Ruihan/.config/superpowers/worktrees/unseal-agent-ios/agent-stream-card-parity-harness add UnsealUITests/AgentStreamParityReplayTests.swift
git -C /Users/Ruihan/.config/superpowers/worktrees/unseal-agent-ios/agent-stream-card-parity-harness commit -m "test: lock ios agent stream parity fixtures"
```

### Task 2: Finish `composeEmail` List-Mode on iOS

**Files:**
- Modify: `/Users/Ruihan/.config/superpowers/worktrees/unseal-agent-ios/agent-stream-card-parity-harness/ToolCardsIOS/Sources/ToolCardsIOS/Gmail/ComposeEmailCard.swift`
- Modify: `/Users/Ruihan/.config/superpowers/worktrees/unseal-agent-ios/agent-stream-card-parity-harness/UnsealAgent/JsonRender/Core/RendererSupport+ComposeEmailCard.swift`
- Modify: `/Users/Ruihan/.config/superpowers/worktrees/unseal-agent-ios/agent-stream-card-parity-harness/UnsealUITests/AgentStreamParityReplayTests.swift`

- [ ] **Step 1: Write the failing iOS list-mode export test**

Add this test to `AgentStreamParityReplayTests.swift`:

```swift
func testComposeEmailListExportsMessageRowsForIOSRendering() throws {
    let fixture = try AgentStreamSSEFixture.load("compose-email-list")
    let message = try AgentStreamParityExport.replay(fixture)
    let json = try AgentStreamParityExport.renderJSON(message: message, streamId: fixture.id)
    let toolRoot = try XCTUnwrap(json["toolRoot"] as? [String: Any])
    let entries = try XCTUnwrap(toolRoot["entries"] as? [[String: Any]])
    let entry = try XCTUnwrap(entries.first)
    let propsKeys = try XCTUnwrap(entry["propsKeys"] as? [String])

    XCTAssertEqual(entry["cardType"] as? String, "composeEmail")
    XCTAssertTrue(propsKeys.contains("messages"))
    XCTAssertTrue(propsKeys.contains("mode"))
}
```

- [ ] **Step 2: Run Swift parse check**

Run:

```bash
cd /Users/Ruihan/.config/superpowers/worktrees/unseal-agent-ios/agent-stream-card-parity-harness
swiftc -parse UnsealUITests/AgentStreamParityExport.swift UnsealUITests/AgentStreamParityReplayTests.swift UnsealUITests/AgentStreamSSEFixture.swift
```

Expected: exit status `0`. If full Xcode is available, run the XCTest target and expect this new test to fail before implementation because `mode` or list rows are missing.

- [ ] **Step 3: Normalize compose email props for list mode**

In `RendererSupport+ComposeEmailCard.swift`, make the normalized props always expose `mode` and `messages` when the raw payload contains a message list:

```swift
let rawMessages = raw["messages"] as? [[String: Any]]
    ?? raw["items"] as? [[String: Any]]
    ?? raw["emails"] as? [[String: Any]]

if let rawMessages, !rawMessages.isEmpty {
    props["mode"] = "list"
    props["messages"] = rawMessages.map { message in
        [
            "subject": message["subject"] as? String ?? "",
            "from": message["from"] as? String ?? message["sender"] as? String ?? "",
            "to": message["to"] as? String ?? "",
            "snippet": message["snippet"] as? String ?? message["body"] as? String ?? "",
            "time": message["time"] as? String ?? message["date"] as? String ?? ""
        ]
    }
}
```

- [ ] **Step 4: Render compact list rows in `ComposeEmailCard.swift`**

Add a list-mode branch before the single-message compose body:

```swift
if let mode = props["mode"] as? String,
   mode == "list",
   let messages = props["messages"] as? [[String: Any]] {
    VStack(alignment: .leading, spacing: 10) {
        ForEach(Array(messages.prefix(4).enumerated()), id: \.offset) { _, message in
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 8) {
                    Text(message["from"] as? String ?? "Unknown")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.primary)
                        .lineLimit(1)
                    Spacer(minLength: 8)
                    Text(message["time"] as? String ?? "")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
                Text(message["subject"] as? String ?? "No subject")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.primary)
                    .lineLimit(1)
                Text(message["snippet"] as? String ?? "")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
            }
            .padding(.vertical, 2)
        }
    }
}
```

Use the existing colors, spacing helpers, and card chrome in the file instead of adding a nested card container.

- [ ] **Step 5: Verify iOS compose export and ToolCards parse**

Run:

```bash
cd /Users/Ruihan/.config/superpowers/worktrees/unseal-agent-ios/agent-stream-card-parity-harness
swiftc -parse UnsealUITests/AgentStreamParityExport.swift UnsealUITests/AgentStreamParityReplayTests.swift UnsealUITests/AgentStreamSSEFixture.swift
swiftc -parse $(rg --files ToolCardsIOS/Sources/ToolCardsIOS | sort)
```

Expected: both commands exit with status `0`.

- [ ] **Step 6: Commit iOS compose parity**

```bash
git -C /Users/Ruihan/.config/superpowers/worktrees/unseal-agent-ios/agent-stream-card-parity-harness add ToolCardsIOS/Sources/ToolCardsIOS/Gmail/ComposeEmailCard.swift UnsealAgent/JsonRender/Core/RendererSupport+ComposeEmailCard.swift UnsealUITests/AgentStreamParityReplayTests.swift
git -C /Users/Ruihan/.config/superpowers/worktrees/unseal-agent-ios/agent-stream-card-parity-harness commit -m "feat: render compose email lists on ios"
```

### Task 3: Finish `weather` Root Dispatch on iOS

**Files:**
- Modify: `/Users/Ruihan/.config/superpowers/worktrees/unseal-agent-ios/agent-stream-card-parity-harness/ToolCardsIOS/Sources/ToolCardsIOS/CardTransforms.swift`
- Modify: `/Users/Ruihan/.config/superpowers/worktrees/unseal-agent-ios/agent-stream-card-parity-harness/ToolCardsIOS/Sources/ToolCardsIOS/ComposioSearch/WeatherCard.swift`
- Modify: `/Users/Ruihan/.config/superpowers/worktrees/unseal-agent-ios/agent-stream-card-parity-harness/UnsealAgent/JsonRender/Core/RendererSupport+Weather.swift`
- Modify: `/Users/Ruihan/.config/superpowers/worktrees/unseal-agent-ios/agent-stream-card-parity-harness/UnsealUI/Views/UIParts/ToolParts/ToolCallRootCardAdapter.swift`
- Modify: `/Users/Ruihan/.config/superpowers/worktrees/unseal-agent-ios/agent-stream-card-parity-harness/UnsealUITests/AgentStreamParityReplayTests.swift`

- [ ] **Step 1: Write the failing iOS weather dispatch test**

Add this test to `AgentStreamParityReplayTests.swift`:

```swift
func testWeatherFixtureExportsRenderableWeatherCard() throws {
    let fixture = try AgentStreamSSEFixture.load("weather-current-forecast")
    let message = try AgentStreamParityExport.replay(fixture)
    let json = try AgentStreamParityExport.renderJSON(message: message, streamId: fixture.id)
    let toolRoot = try XCTUnwrap(json["toolRoot"] as? [String: Any])
    let entries = try XCTUnwrap(toolRoot["entries"] as? [[String: Any]])
    let entry = try XCTUnwrap(entries.first)
    let propsKeys = try XCTUnwrap(entry["propsKeys"] as? [String])

    XCTAssertEqual(entry["cardType"] as? String, "weather")
    XCTAssertTrue(propsKeys.contains("city"))
    XCTAssertTrue(propsKeys.contains("current"))
    XCTAssertTrue(propsKeys.contains("forecast"))
}
```

- [ ] **Step 2: Verify current iOS test state**

Run:

```bash
cd /Users/Ruihan/.config/superpowers/worktrees/unseal-agent-ios/agent-stream-card-parity-harness
swiftc -parse UnsealUITests/AgentStreamParityExport.swift UnsealUITests/AgentStreamParityReplayTests.swift UnsealUITests/AgentStreamSSEFixture.swift
```

Expected: parse succeeds. With full Xcode, the XCTest should fail if the card type is exported but not dispatched/rendered.

- [ ] **Step 3: Ensure weather transform returns Android-equivalent keys**

In `CardTransforms.swift`, keep or add a `weather` transform that emits this shape:

```swift
return [
    "city": city,
    "country": country,
    "current": [
        "temperature": temperature,
        "condition": condition,
        "humidity": humidity,
        "wind": wind
    ],
    "forecast": forecastRows
]
```

For `forecastRows`, map each incoming day into:

```swift
[
    "day": row["day"] as? String ?? row["date"] as? String ?? "",
    "condition": row["condition"] as? String ?? row["summary"] as? String ?? "",
    "high": row["high"] as? Int ?? row["max"] as? Int ?? 0,
    "low": row["low"] as? Int ?? row["min"] as? Int ?? 0
]
```

- [ ] **Step 4: Confirm `ToolCallRootCardAdapter.swift` dispatches weather**

Add or keep this case in the adapter's card switch:

```swift
case "weather":
    WeatherCard(props: entry.props)
```

Do not add weather rendering in `unseal-ios`; the client should receive it through `UnsealUI`.

- [ ] **Step 5: Verify iOS weather parse**

Run:

```bash
cd /Users/Ruihan/.config/superpowers/worktrees/unseal-agent-ios/agent-stream-card-parity-harness
swiftc -parse UnsealUITests/AgentStreamParityExport.swift UnsealUITests/AgentStreamParityReplayTests.swift UnsealUITests/AgentStreamSSEFixture.swift
swiftc -parse $(rg --files ToolCardsIOS/Sources/ToolCardsIOS | sort)
```

Expected: both commands exit with status `0`.

- [ ] **Step 6: Commit iOS weather parity**

```bash
git -C /Users/Ruihan/.config/superpowers/worktrees/unseal-agent-ios/agent-stream-card-parity-harness add ToolCardsIOS/Sources/ToolCardsIOS/CardTransforms.swift ToolCardsIOS/Sources/ToolCardsIOS/ComposioSearch/WeatherCard.swift UnsealAgent/JsonRender/Core/RendererSupport+Weather.swift UnsealUI/Views/UIParts/ToolParts/ToolCallRootCardAdapter.swift UnsealUITests/AgentStreamParityReplayTests.swift
git -C /Users/Ruihan/.config/superpowers/worktrees/unseal-agent-ios/agent-stream-card-parity-harness commit -m "feat: dispatch weather card on ios"
```

### Task 4: Finish Interactive `moltbookRegister` on Android

**Files:**
- Modify: `/Users/Ruihan/.config/superpowers/worktrees/unseal-android/agent-stream-card-parity-harness/features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/ScheduleMoltbookCards.kt`
- Modify: `/Users/Ruihan/.config/superpowers/worktrees/unseal-android/agent-stream-card-parity-harness/features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/CardTransforms.kt`
- Modify: `/Users/Ruihan/.config/superpowers/worktrees/unseal-android/agent-stream-card-parity-harness/features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/timeline/factories/event/AgentStreamParityReplayTest.kt`

- [ ] **Step 1: Write the failing Android Moltbook field export test**

Add this test to `AgentStreamParityReplayTest.kt`:

```kotlin
@Test
fun `moltbook register fixture exports interactive fields`() {
    val exported = AgentStreamParityExport.renderJson(replayFixture("moltbook-register"))
    val entries = exported.getJSONObject("toolRoot").getJSONArray("entries")
    val entry = entries.getJSONObject(0)
    val propsKeys = entry.getJSONArray("propsKeys").let { array ->
        (0 until array.length()).map { array.getString(it) }
    }

    assertThat(entry.getString("cardType")).isEqualTo("moltbookRegister")
    assertThat(propsKeys).containsAtLeast("fields", "submitLabel")
}
```

- [ ] **Step 2: Run Android replay test and observe failure if fields are missing**

Run:

```bash
cd /Users/Ruihan/.config/superpowers/worktrees/unseal-android/agent-stream-card-parity-harness
./gradlew :features:messages:impl:testDebugUnitTest --tests "io.element.android.features.messages.impl.timeline.factories.event.AgentStreamParityReplayTest.moltbook register fixture exports interactive fields"
```

Expected before implementation: failure mentioning missing `fields` or `submitLabel`.

- [ ] **Step 3: Normalize Android Moltbook props**

In `CardTransforms.kt`, ensure the `moltbookRegister` transform emits:

```kotlin
mapOf(
    "title" to (raw["title"] ?: "Register"),
    "subtitle" to (raw["subtitle"] ?: raw["description"] ?: ""),
    "fields" to listOf(
        mapOf("id" to "name", "label" to "Name", "value" to ""),
        mapOf("id" to "email", "label" to "Email", "value" to ""),
        mapOf("id" to "phone", "label" to "Phone", "value" to "")
    ),
    "submitLabel" to (raw["submitLabel"] ?: "Submit")
)
```

If the raw payload already contains `fields`, preserve incoming order and only fill missing `id`, `label`, and `value`.

- [ ] **Step 4: Render Android interactive fields**

In `ScheduleMoltbookCards.kt`, replace display-only field rows with editable Compose state:

```kotlin
val fieldValues = remember(entry.id) {
    mutableStateMapOf<String, String>().apply {
        fields.forEach { field ->
            val id = field["id"] as? String ?: return@forEach
            put(id, field["value"] as? String ?: "")
        }
    }
}

fields.forEach { field ->
    val id = field["id"] as? String ?: return@forEach
    OutlinedTextField(
        value = fieldValues[id].orEmpty(),
        onValueChange = { value -> fieldValues[id] = value },
        label = { Text(field["label"] as? String ?: id) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}
```

Use the existing `ToolCardSurface` and do not add an extra nested `Surface`.

- [ ] **Step 5: Verify Android Moltbook tests**

Run:

```bash
cd /Users/Ruihan/.config/superpowers/worktrees/unseal-android/agent-stream-card-parity-harness
./gradlew :features:messages:impl:compileDebugKotlin
./gradlew :features:messages:impl:testDebugUnitTest --tests "io.element.android.features.messages.impl.timeline.factories.event.AgentStreamParityReplayTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 6: Commit Android Moltbook parity**

```bash
git -C /Users/Ruihan/.config/superpowers/worktrees/unseal-android/agent-stream-card-parity-harness add features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/ScheduleMoltbookCards.kt features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/CardTransforms.kt features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/timeline/factories/event/AgentStreamParityReplayTest.kt
git -C /Users/Ruihan/.config/superpowers/worktrees/unseal-android/agent-stream-card-parity-harness commit -m "feat: align moltbook register card on android"
```

### Task 5: Rework `hotelBooking` Layout and Data on Both Platforms

**Files:**
- Modify: `/Users/Ruihan/.config/superpowers/worktrees/unseal-android/agent-stream-card-parity-harness/features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/ComposioSearchCards.kt`
- Modify: `/Users/Ruihan/.config/superpowers/worktrees/unseal-android/agent-stream-card-parity-harness/features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/CardTransforms.kt`
- Modify: `/Users/Ruihan/.config/superpowers/worktrees/unseal-agent-ios/agent-stream-card-parity-harness/ToolCardsIOS/Sources/ToolCardsIOS/ComposioSearch/HotelBookingCard.swift`
- Modify: `/Users/Ruihan/.config/superpowers/worktrees/unseal-agent-ios/agent-stream-card-parity-harness/ToolCardsIOS/Sources/ToolCardsIOS/CardTransforms.swift`
- Modify: `/Users/Ruihan/.config/superpowers/worktrees/unseal-agent-ios/agent-stream-card-parity-harness/UnsealAgent/JsonRender/Core/RendererSupport+HotelCard.swift`

- [ ] **Step 1: Add Android hotel data assertions**

Add this test to `AgentStreamParityReplayTest.kt`:

```kotlin
@Test
fun `hotel fixture exports images and amenities separately`() {
    val exported = AgentStreamParityExport.renderJson(replayFixture("hotel-booking-gallery"))
    val entries = exported.getJSONObject("toolRoot").getJSONArray("entries")
    val entry = entries.getJSONObject(0)
    val propsKeys = entry.getJSONArray("propsKeys").let { array ->
        (0 until array.length()).map { array.getString(it) }
    }

    assertThat(entry.getString("cardType")).isEqualTo("hotelBooking")
    assertThat(propsKeys).containsAtLeast("hotels", "images", "amenities")
}
```

- [ ] **Step 2: Add iOS hotel data assertions**

Add this test to `AgentStreamParityReplayTests.swift`:

```swift
func testHotelFixtureExportsImagesAndAmenitiesSeparately() throws {
    let fixture = try AgentStreamSSEFixture.load("hotel-booking-gallery")
    let message = try AgentStreamParityExport.replay(fixture)
    let json = try AgentStreamParityExport.renderJSON(message: message, streamId: fixture.id)
    let toolRoot = try XCTUnwrap(json["toolRoot"] as? [String: Any])
    let entries = try XCTUnwrap(toolRoot["entries"] as? [[String: Any]])
    let entry = try XCTUnwrap(entries.first)
    let propsKeys = try XCTUnwrap(entry["propsKeys"] as? [String])

    XCTAssertEqual(entry["cardType"] as? String, "hotelBooking")
    XCTAssertTrue(propsKeys.contains("hotels"))
    XCTAssertTrue(propsKeys.contains("images"))
    XCTAssertTrue(propsKeys.contains("amenities"))
}
```

- [ ] **Step 3: Normalize hotel props on Android**

In Android `CardTransforms.kt`, emit the same top-level keys for hotel:

```kotlin
mapOf(
    "hotels" to hotels,
    "images" to hotels.flatMap { hotel -> hotel.images }.take(6),
    "amenities" to hotels.firstOrNull()?.amenities.orEmpty(),
    "checkIn" to raw["checkIn"],
    "checkOut" to raw["checkOut"]
)
```

Each hotel row should contain:

```kotlin
mapOf(
    "name" to name,
    "area" to area,
    "rating" to rating,
    "reviews" to reviews,
    "price" to price,
    "total" to total,
    "thumbnail" to thumbnail,
    "images" to images,
    "amenities" to amenities,
    "mapUrl" to mapUrl
)
```

- [ ] **Step 4: Normalize hotel props on iOS**

In iOS `CardTransforms.swift` and `RendererSupport+HotelCard.swift`, emit the same keys:

```swift
[
    "hotels": hotels,
    "images": Array(hotels.flatMap { $0["images"] as? [String] ?? [] }.prefix(6)),
    "amenities": hotels.first?["amenities"] as? [String] ?? [],
    "checkIn": raw["checkIn"] as? String ?? "",
    "checkOut": raw["checkOut"] as? String ?? ""
]
```

- [ ] **Step 5: Apply the unified hotel layout**

Use this visual structure on both platforms:

```text
Hotel row:
  thumbnail  title + rating + area                 price
             review badge + review count           total
  amenities tag row on its own line
  image strip on its own line when images exist
```

Android `ComposioSearchCards.kt` must use:

```kotlin
Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    HotelHeaderRow(hotel)
    HotelAmenityRow(amenities = hotel.amenities.take(5))
    HotelImageStrip(images = hotel.images.take(3))
}
```

iOS `HotelBookingCard.swift` must use:

```swift
VStack(alignment: .leading, spacing: 10) {
    HotelHeaderRow(hotel: hotel)
    HotelAmenityRow(amenities: Array(hotel.amenities.prefix(5)))
    HotelImageStrip(images: Array(hotel.images.prefix(3)))
}
```

The tag row must not share the same horizontal line as name, rating, location, or price.

- [ ] **Step 6: Verify hotel tests and parse**

Run Android:

```bash
cd /Users/Ruihan/.config/superpowers/worktrees/unseal-android/agent-stream-card-parity-harness
./gradlew :features:messages:impl:compileDebugKotlin
./gradlew :features:messages:impl:testDebugUnitTest --tests "io.element.android.features.messages.impl.timeline.factories.event.AgentStreamParityReplayTest"
```

Run iOS parse:

```bash
cd /Users/Ruihan/.config/superpowers/worktrees/unseal-agent-ios/agent-stream-card-parity-harness
swiftc -parse UnsealUITests/AgentStreamParityExport.swift UnsealUITests/AgentStreamParityReplayTests.swift UnsealUITests/AgentStreamSSEFixture.swift
swiftc -parse $(rg --files ToolCardsIOS/Sources/ToolCardsIOS | sort)
```

Expected: Android `BUILD SUCCESSFUL`; Swift parse exits `0`.

- [ ] **Step 7: Commit hotel parity**

```bash
git -C /Users/Ruihan/.config/superpowers/worktrees/unseal-android/agent-stream-card-parity-harness add features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/ComposioSearchCards.kt features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/CardTransforms.kt features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/timeline/factories/event/AgentStreamParityReplayTest.kt
git -C /Users/Ruihan/.config/superpowers/worktrees/unseal-android/agent-stream-card-parity-harness commit -m "feat: align hotel booking card on android"
git -C /Users/Ruihan/.config/superpowers/worktrees/unseal-agent-ios/agent-stream-card-parity-harness add ToolCardsIOS/Sources/ToolCardsIOS/ComposioSearch/HotelBookingCard.swift ToolCardsIOS/Sources/ToolCardsIOS/CardTransforms.swift UnsealAgent/JsonRender/Core/RendererSupport+HotelCard.swift UnsealUITests/AgentStreamParityReplayTests.swift
git -C /Users/Ruihan/.config/superpowers/worktrees/unseal-agent-ios/agent-stream-card-parity-harness commit -m "feat: align hotel booking card on ios"
```

### Task 6: Finish `fileAttachment` Data and Row Style

**Files:**
- Modify: `/Users/Ruihan/.config/superpowers/worktrees/unseal-android/agent-stream-card-parity-harness/features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/GmailDriveCards.kt`
- Modify: `/Users/Ruihan/.config/superpowers/worktrees/unseal-android/agent-stream-card-parity-harness/features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/CardTransforms.kt`
- Modify: `/Users/Ruihan/.config/superpowers/worktrees/unseal-agent-ios/agent-stream-card-parity-harness/ToolCardsIOS/Sources/ToolCardsIOS/GoogleDrive/FileAttachmentCard.swift`
- Modify: `/Users/Ruihan/.config/superpowers/worktrees/unseal-agent-ios/agent-stream-card-parity-harness/UnsealAgent/JsonRender/Core/RendererSupport+FileAttachmentCard.swift`

- [ ] **Step 1: Add Android file attachment export assertion**

Add this test to `AgentStreamParityReplayTest.kt`:

```kotlin
@Test
fun `file attachment fixture exports file rows`() {
    val exported = AgentStreamParityExport.renderJson(replayFixture("file-attachment-list"))
    val entries = exported.getJSONObject("toolRoot").getJSONArray("entries")
    val entry = entries.getJSONObject(0)
    val propsKeys = entry.getJSONArray("propsKeys").let { array ->
        (0 until array.length()).map { array.getString(it) }
    }

    assertThat(entry.getString("cardType")).isEqualTo("fileAttachment")
    assertThat(propsKeys).containsAtLeast("files", "title")
}
```

- [ ] **Step 2: Add iOS file attachment export assertion**

Add this test to `AgentStreamParityReplayTests.swift`:

```swift
func testFileAttachmentFixtureExportsFileRows() throws {
    let fixture = try AgentStreamSSEFixture.load("file-attachment-list")
    let message = try AgentStreamParityExport.replay(fixture)
    let json = try AgentStreamParityExport.renderJSON(message: message, streamId: fixture.id)
    let toolRoot = try XCTUnwrap(json["toolRoot"] as? [String: Any])
    let entries = try XCTUnwrap(toolRoot["entries"] as? [[String: Any]])
    let entry = try XCTUnwrap(entries.first)
    let propsKeys = try XCTUnwrap(entry["propsKeys"] as? [String])

    XCTAssertEqual(entry["cardType"] as? String, "fileAttachment")
    XCTAssertTrue(propsKeys.contains("files"))
    XCTAssertTrue(propsKeys.contains("title"))
}
```

- [ ] **Step 3: Normalize file attachment rows on both platforms**

Android `CardTransforms.kt` and iOS `RendererSupport+FileAttachmentCard.swift` must emit:

```text
title: string
files: [
  {
    name: string
    mimeType: string
    sizeLabel: string
    url: string
    icon: string
  }
]
```

Use these mappings:

```text
name = filename | name | title | "Attachment"
mimeType = mimeType | mediaType | type | ""
sizeLabel = sizeLabel | size | fileSize | ""
url = url | downloadUrl | webUrl | ""
icon = "image" when mimeType starts with "image/", "pdf" when mimeType contains "pdf", otherwise "file"
```

- [ ] **Step 4: Align row visual style**

Android `GmailDriveCards.kt` row structure:

```kotlin
Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp)
) {
    FileTypeIcon(icon = file.icon)
    Column(modifier = Modifier.weight(1f)) {
        Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(file.sizeLabel, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
```

iOS `FileAttachmentCard.swift` row structure:

```swift
HStack(spacing: 10) {
    FileTypeIcon(icon: file.icon)
    VStack(alignment: .leading, spacing: 2) {
        Text(file.name)
            .font(.subheadline.weight(.semibold))
            .lineLimit(1)
        Text(file.sizeLabel)
            .font(.caption)
            .foregroundStyle(.secondary)
            .lineLimit(1)
    }
    Spacer(minLength: 8)
}
```

- [ ] **Step 5: Verify file attachment tests**

Run Android replay test and iOS parse:

```bash
cd /Users/Ruihan/.config/superpowers/worktrees/unseal-android/agent-stream-card-parity-harness
./gradlew :features:messages:impl:testDebugUnitTest --tests "io.element.android.features.messages.impl.timeline.factories.event.AgentStreamParityReplayTest"
cd /Users/Ruihan/.config/superpowers/worktrees/unseal-agent-ios/agent-stream-card-parity-harness
swiftc -parse UnsealUITests/AgentStreamParityExport.swift UnsealUITests/AgentStreamParityReplayTests.swift UnsealUITests/AgentStreamSSEFixture.swift
swiftc -parse $(rg --files ToolCardsIOS/Sources/ToolCardsIOS | sort)
```

Expected: Android `BUILD SUCCESSFUL`; Swift parse exits `0`.

- [ ] **Step 6: Commit file attachment parity**

```bash
git -C /Users/Ruihan/.config/superpowers/worktrees/unseal-android/agent-stream-card-parity-harness add features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/GmailDriveCards.kt features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/CardTransforms.kt features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/timeline/factories/event/AgentStreamParityReplayTest.kt
git -C /Users/Ruihan/.config/superpowers/worktrees/unseal-android/agent-stream-card-parity-harness commit -m "feat: align file attachment card on android"
git -C /Users/Ruihan/.config/superpowers/worktrees/unseal-agent-ios/agent-stream-card-parity-harness add ToolCardsIOS/Sources/ToolCardsIOS/GoogleDrive/FileAttachmentCard.swift UnsealAgent/JsonRender/Core/RendererSupport+FileAttachmentCard.swift UnsealUITests/AgentStreamParityReplayTests.swift
git -C /Users/Ruihan/.config/superpowers/worktrees/unseal-agent-ios/agent-stream-card-parity-harness commit -m "feat: align file attachment card on ios"
```

### Task 7: Close Root Card Status, Loading, and Streaming Smoothness

**Files:**
- Modify: `/Users/Ruihan/.config/superpowers/worktrees/unseal-android/agent-stream-card-parity-harness/features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/TimelineItemAiView.kt`
- Modify: `/Users/Ruihan/.config/superpowers/worktrees/unseal-android/agent-stream-card-parity-harness/features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/ToolCardKit.kt`
- Modify: `/Users/Ruihan/.config/superpowers/worktrees/unseal-agent-ios/agent-stream-card-parity-harness/ToolCardsIOS/Sources/ToolCardsIOS/ToolCallRootCard.swift`
- Modify: `/Users/Ruihan/.config/superpowers/worktrees/unseal-agent-ios/agent-stream-card-parity-harness/UnsealUI/Views/BubbleMessageView.swift`
- Modify: `/Users/Ruihan/.config/superpowers/worktrees/unseal-ios/agent-stream-card-parity-harness/ElementX/Sources/Screens/Timeline/View/TimelineItemViews/AIMessage/Debug/AgentStreamParityClientSmokeView.swift`

- [ ] **Step 1: Add a mixed status fixture assertion on Android**

Add this assertion to an existing root-card status test or `AgentStreamParityReplayTest.kt`:

```kotlin
@Test
fun `root card exports partial failure counts`() {
    val exported = AgentStreamParityExport.renderJson(replayFixture("stream-mixed-parts"))
    val toolRoot = exported.optJSONObject("toolRoot")
    if (toolRoot != null) {
        assertThat(toolRoot.getInt("doneCount")).isAtLeast(0)
        assertThat(toolRoot.getInt("errorCount")).isAtLeast(0)
        assertThat(toolRoot.getInt("callingCount")).isAtLeast(0)
    }
}
```

- [ ] **Step 2: Keep Android root card as one visual container**

In `TimelineItemAiView.kt` and `ToolCardKit.kt`, verify the tool root card follows these rules:

```text
Root container owns the border, radius, background, and max width.
ToolCardSurface does not add a second nested card background.
The progress icon shows partial success/failure ratio, not a fixed full-error icon.
The selected tool row is stable by tool call id.
```

If code changes are needed, keep the structural pattern:

```kotlin
key(part.id) {
    ToolCallRootCardAdapter(
        part = part,
        modifier = Modifier.fillMaxWidth()
    )
}
```

- [ ] **Step 3: Keep Android stream updates incremental**

In `TimelineItemAiView.kt`, ensure streamed parts are keyed by stable ids:

```kotlin
parts.forEach { part ->
    key(part.id) {
        AiStreamPartView(part = part)
    }
}
```

Do not use a list key that changes when text content grows. Text content should update in place, so long responses do not visually restart from the beginning.

- [ ] **Step 4: Keep iOS loading shimmer deterministic**

In `ToolCallRootCard.swift`, loading rows must animate without random widths:

```swift
RoundedRectangle(cornerRadius: 4)
    .fill(.secondary.opacity(0.18))
    .frame(width: width, height: 8)
    .opacity(isAnimating ? 0.45 : 0.85)
    .animation(.easeInOut(duration: 0.9).repeatForever(autoreverses: true), value: isAnimating)
```

Use fixed `width` values from the row index so snapshots remain stable.

- [ ] **Step 5: Verify stream smoke view includes every surface**

In `AgentStreamParityClientSmokeView.swift`, keep the debug fixture list covering:

```swift
[
    "compose-email-list",
    "weather-current-forecast",
    "moltbook-register",
    "hotel-booking-gallery",
    "file-attachment-list",
    "stream-mixed-parts"
]
```

The smoke view should render through the real AI message timeline path, not a separate mock-only layout.

- [ ] **Step 6: Verify root card and stream checks**

Run:

```bash
cd /Users/Ruihan/.config/superpowers/worktrees/unseal-android/agent-stream-card-parity-harness
./gradlew :features:messages:impl:compileDebugKotlin
./gradlew :features:messages:impl:testDebugUnitTest --tests "io.element.android.features.messages.impl.timeline.factories.event.AgentStreamParityReplayTest"
cd /Users/Ruihan/.config/superpowers/worktrees/unseal-agent-ios/agent-stream-card-parity-harness
swiftc -parse UnsealUITests/AgentStreamParityExport.swift UnsealUITests/AgentStreamParityReplayTests.swift UnsealUITests/AgentStreamSSEFixture.swift
swiftc -parse $(rg --files ToolCardsIOS/Sources/ToolCardsIOS | sort)
cd /Users/Ruihan/.config/superpowers/worktrees/unseal-ios/agent-stream-card-parity-harness
swiftc -parse ElementX/Sources/Screens/Timeline/View/TimelineItemViews/AIMessage/Debug/AgentStreamParityClientSmokeView.swift
```

Expected: Android `BUILD SUCCESSFUL`; Swift parse exits `0`. If the client smoke file depends on project-only imports and standalone parse fails, record the exact missing module name and use full Xcode client build when available.

- [ ] **Step 7: Commit root and stream parity**

```bash
git -C /Users/Ruihan/.config/superpowers/worktrees/unseal-android/agent-stream-card-parity-harness add features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/TimelineItemAiView.kt features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/ToolCardKit.kt features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/timeline/factories/event/AgentStreamParityReplayTest.kt
git -C /Users/Ruihan/.config/superpowers/worktrees/unseal-android/agent-stream-card-parity-harness commit -m "fix: smooth android agent stream card updates"
git -C /Users/Ruihan/.config/superpowers/worktrees/unseal-agent-ios/agent-stream-card-parity-harness add ToolCardsIOS/Sources/ToolCardsIOS/ToolCallRootCard.swift UnsealUI/Views/BubbleMessageView.swift
git -C /Users/Ruihan/.config/superpowers/worktrees/unseal-agent-ios/agent-stream-card-parity-harness commit -m "fix: align ios agent stream loading states"
git -C /Users/Ruihan/.config/superpowers/worktrees/unseal-ios/agent-stream-card-parity-harness add ElementX/Sources/Screens/Timeline/View/TimelineItemViews/AIMessage/Debug/AgentStreamParityClientSmokeView.swift
git -C /Users/Ruihan/.config/superpowers/worktrees/unseal-ios/agent-stream-card-parity-harness commit -m "test: cover client agent stream parity smoke"
```

### Task 8: Regenerate Artifacts and Final Parity Report

**Files:**
- Generated: `/Users/Ruihan/.config/superpowers/worktrees/unseal-android/agent-stream-card-parity-harness/docs/agent-stream-fixtures/artifacts/*/android-render-completed.json`
- Generated when Xcode is available: `/Users/Ruihan/.config/superpowers/worktrees/unseal-android/agent-stream-card-parity-harness/docs/agent-stream-fixtures/artifacts/*/ios-render-completed.json`
- Generated: `/Users/Ruihan/.config/superpowers/worktrees/unseal-android/agent-stream-card-parity-harness/docs/agent-stream-fixtures/artifacts/*/parity-report.md`
- Modify if needed: `/Users/Ruihan/.config/superpowers/worktrees/unseal-android/agent-stream-card-parity-harness/docs/agent-stream-fixtures/manifest.json`

- [ ] **Step 1: Regenerate Android render artifacts**

Run:

```bash
cd /Users/Ruihan/.config/superpowers/worktrees/unseal-android/agent-stream-card-parity-harness
./gradlew :features:messages:impl:testDebugUnitTest --tests "io.element.android.features.messages.impl.timeline.factories.event.AgentStreamParityReplayTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

Verify files exist:

```bash
for id in compose-email-list weather-current-forecast moltbook-register hotel-booking-gallery file-attachment-list stream-mixed-parts; do
  test -f "/Users/Ruihan/.config/superpowers/worktrees/unseal-android/agent-stream-card-parity-harness/docs/agent-stream-fixtures/artifacts/$id/android-render-completed.json"
done
```

Expected: command exits `0`.

- [ ] **Step 2: Regenerate iOS render artifacts when full Xcode is available**

Run:

```bash
cd /Users/Ruihan/.config/superpowers/worktrees/unseal-agent-ios/agent-stream-card-parity-harness
xcode-select -p
xcodebuild -project /Users/Ruihan/.config/superpowers/worktrees/unseal-agent-ios/agent-stream-card-parity-harness/UnsealAgent.xcodeproj \
  -scheme UnsealAgent \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' \
  test
```

Expected with full Xcode:

```text
** TEST SUCCEEDED **
```

If `xcode-select -p` prints `/Library/Developer/CommandLineTools`, write this exact blocker into the final report:

```text
iOS render artifact generation blocked locally because xcode-select points to CommandLineTools instead of full Xcode.
```

- [ ] **Step 3: Regenerate parity reports**

If the existing report script is present, run:

```bash
cd /Users/Ruihan/.config/superpowers/worktrees/unseal-android/agent-stream-card-parity-harness
node tools/agent-stream-parity/report.mjs --root docs/agent-stream-fixtures
```

Expected: each fixture artifact directory receives or updates `parity-report.md`.

If the script is not present in this branch, create reports by comparing:

```text
android-render-completed.json
ios-render-completed.json
```

For each fixture, mark:

```text
complete: both artifacts exist and cardType plus required propsKeys match
blocked: iOS artifact missing due to local Xcode environment
failed: both artifacts exist but cardType or propsKeys differ
```

- [ ] **Step 4: Final completion criteria**

The card parity pass is complete only when every row below is true:

```text
compose-email-list: iOS artifact has cardType composeEmail and propsKeys include messages, mode
weather-current-forecast: iOS artifact has cardType weather and propsKeys include city, current, forecast
moltbook-register: Android artifact has cardType moltbookRegister and propsKeys include fields, submitLabel
hotel-booking-gallery: both artifacts have cardType hotelBooking and propsKeys include hotels, images, amenities
file-attachment-list: both artifacts have cardType fileAttachment and propsKeys include files, title
stream-mixed-parts: both artifacts include text, reasoning, source-url, source-document, file, data-error-card
```

- [ ] **Step 5: Commit regenerated tracked docs if any**

Generated artifacts under `docs/agent-stream-fixtures/artifacts/` are ignored unless the repo intentionally tracks specific reports. Commit only tracked docs or manifest updates:

```bash
git -C /Users/Ruihan/.config/superpowers/worktrees/unseal-android/agent-stream-card-parity-harness status --short docs/agent-stream-fixtures tools/agent-stream-parity
git -C /Users/Ruihan/.config/superpowers/worktrees/unseal-android/agent-stream-card-parity-harness add docs/agent-stream-fixtures/manifest.json tools/agent-stream-parity/report.mjs tools/agent-stream-parity/README.md
git -C /Users/Ruihan/.config/superpowers/worktrees/unseal-android/agent-stream-card-parity-harness commit -m "docs: refresh agent stream parity reports"
```

If there are no staged changes, do not create an empty commit.

## Self-Review

Spec coverage:

- `composeEmail` list-mode parity is covered by Task 2.
- `weather` iOS dispatch/export parity is covered by Task 3.
- `moltbookRegister` Android interaction parity is covered by Task 4.
- `hotelBooking` compact visual/data parity is covered by Task 5.
- `fileAttachment` row/data parity is covered by Task 6.
- Root card status, loading shimmer, single-container card chrome, and stream smoothness are covered by Task 7.
- Artifact regeneration and final pass/fail reporting are covered by Task 8.

Placeholder scan:

- This plan contains no unresolved placeholder markers, no open-ended recovery instruction, and no task that says to write tests without giving the concrete assertion shape.

Type consistency:

- Fixture ids match `/Users/Ruihan/.config/superpowers/worktrees/unseal-android/agent-stream-card-parity-harness/docs/agent-stream-fixtures/manifest.json`.
- Card types match the current normalized export names: `composeEmail`, `weather`, `moltbookRegister`, `hotelBooking`, `fileAttachment`.
- iOS repository boundaries keep reusable rendering in `unseal-agent-ios` and client smoke in `unseal-ios`.

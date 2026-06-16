# Agent Stream Tool Card Parity Harness Design

## Goal

Build a repeatable parity workflow that proves iOS and Android render the same agent stream tool call cards from the same mocked AI SDK stream data.

The project has two linked outcomes:

1. Identify real rendering differences between the clients, including cases where a card exists in code but does not render equivalent output from real stream-shaped data.
2. Create a stable mock stream and screenshot harness so future card parity work can be reproduced, reviewed, and regression tested.

## Non-Goals

- Do not replace either client's production stream implementation.
- Do not make Android copy iOS wholesale or iOS copy Android wholesale. For each card, choose the more complete data behavior, visual hierarchy, and interaction model.
- Do not use hand-written platform render models as the primary fixture format.
- Do not require pixel-perfect screenshots in the first version. The initial bar is structural parity plus reviewable visual evidence.

## Current Context

The existing Android parity spec in `docs/superpowers/specs/2026-06-12-ai-tool-card-parity-design.md` already aligned Android stream rendering with the iOS parts architecture:

- Android consumes `libraries/agentstream` snapshots.
- Android maps snapshots through `AiSdkStreamReducer`.
- Android inserts one `ToolCallRootCard` at the first visible tool part, matching iOS `BubbleMessageView`.
- Android has a broad Compose card registry and several transforms that are more complete than iOS.

The remaining problem is not only implementation coverage. It is observability. Today we can read code and infer differences, but we do not have a stable way to feed both clients the same stream and compare:

- parser output
- tool card entries
- transformed props
- rendered screenshots
- interaction state, such as expanded/collapsed and calling/completed cards

This spec adds that loop.

## Source Fixture Format

The single manually maintained fixture source is an AI SDK SSE event sequence.

Each fixture is stored as JSONL, one event per line:

```json
{"event":"message-start","data":{"messageId":"msg-compose-email-list"}}
{"event":"text-start","data":{"id":"text-1"}}
{"event":"text-delta","data":{"id":"text-1","delta":"I found three recent emails."}}
{"event":"text-end","data":{"id":"text-1"}}
{"event":"tool-input-start","data":{"toolCallId":"tool-gmail","toolName":"GMAIL_FETCH_EMAILS"}}
{"event":"tool-input-available","data":{"toolCallId":"tool-gmail","input":{"query":"from:team"}}}
{"event":"tool-output-available","data":{"toolCallId":"tool-gmail","output":{"successful":true,"data":{"messages":[]}}}}
{"event":"message-stop","data":{}}
```

The exact event names and data keys must match the production AI SDK stream shape consumed by the existing iOS parser and Android Stream SDK. If either client currently expects a slightly different envelope, the harness adapter may normalize the envelope at replay time, but the fixture file itself remains the source of truth.

## Fixture Frames

Each fixture defines named capture frames. A frame is a prefix of the SSE event sequence plus optional UI actions.

Required frames:

- `calling`: after tool input is available but before output.
- `completed`: after `message-stop`, with final card data.

Optional frames:

- `partial-output`: for tools that stream progressive output.
- `error`: for failed, denied, or unavailable tool results.
- `completed-expanded`: completed state after manually expanding the root card.
- `completed-collapsed`: completed state after auto-collapse or manual collapse.
- `interaction`: for cards with forms, gallery paging, links, or action buttons.

The manifest stores frame cut points so both clients capture the same stream state.

## Directory Layout

Shared fixtures live under the Android repo because that repo already owns the existing parity docs and test resources:

```text
docs/agent-stream-fixtures/
  manifest.json
  fixtures/
    compose-email-list.sse.jsonl
    weather-current-forecast.sse.jsonl
    moltbook-register.sse.jsonl
    hotel-booking-gallery.sse.jsonl
    file-attachment-list.sse.jsonl
  expected/
    compose-email-list.render.json
  artifacts/
    ios/
    android/
    diff/
```

The iOS repos read these fixtures by path during local parity runs. Fixtures should not be copied into each repo because duplicated fixture sources would drift.

## Manifest

`manifest.json` is the card matrix and runner input.

Example:

```json
{
  "version": 1,
  "fixtures": [
    {
      "id": "compose-email-list",
      "cardTypes": ["composeEmail"],
      "source": "fixtures/compose-email-list.sse.jsonl",
      "priority": "P0",
      "knownDifference": "Android renders email list; iOS only renders single email fields.",
      "frames": [
        {"name": "calling", "afterEvent": 5},
        {"name": "completed", "afterEvent": "end"},
        {"name": "completed-expanded", "afterEvent": "end", "actions": ["expand-root-card"]}
      ]
    }
  ]
}
```

The manifest is also the parity checklist. Each fixture records:

- card types covered
- priority
- known current difference
- expected migration direction
- frame list
- whether component screenshots, full-client screenshots, or both are required

## iOS Harness

The iOS harness is debug/test-only.

Responsibilities:

- Read one `.sse.jsonl` fixture.
- Replay events into the same parser/state path used by live streams, or the closest test wrapper around `AgentParser`.
- Produce the same `UIMessage.parts` that `StreamModel` would publish.
- Render `BubbleMessageView` and `ToolCallRootCardView` in a deterministic host.
- Export a normalized render JSON.
- Capture screenshots for each frame.

The normalized render JSON should include:

```json
{
  "streamId": "compose-email-list",
  "parts": [
    {"type": "text", "state": "done", "textLength": 28},
    {"type": "tool", "toolName": "GMAIL_FETCH_EMAILS", "state": "output-available"}
  ],
  "toolRoot": {
    "entries": [
      {
        "id": "tool-gmail",
        "name": "Emails",
        "cardType": "composeEmail",
        "state": "done",
        "propsKeys": ["_cardType", "messages"]
      }
    ]
  }
}
```

The harness must not hand-write `ToolCallEntry` values. Those must come from `ToolCallRootCardAdapter`.

## Android Harness

The Android harness is debug/test-only.

Responsibilities:

- Read the same `.sse.jsonl` fixture.
- Replay events through `libraries/agentstream` reducer/session.
- Map the resulting `StreamSnapshot` through `AiSdkStreamReducer`.
- Render `TimelineItemAiView` or a narrow agent-bubble host in Compose.
- Export normalized render JSON from `TimelineItemAiContent`.
- Capture screenshots for each frame.

The harness must not parse full stream JSON inside Composables. UI remains `UI = f(renderModel)`.

## Component Screenshots

Component-level screenshots are the daily regression target.

iOS component target:

- A deterministic SwiftUI host that renders one agent bubble.
- Fixed width matching a representative timeline bubble.
- Light mode first; dark mode can be added after the harness is stable.
- Network images either mocked through local fixture images or allowed with stable placeholders.

Android component target:

- A Compose screenshot test or debug host for one `TimelineItemAiView`.
- Fixed width matching the iOS host.
- Same text scale and locale assumptions where practical.
- Stable image loading, preferably local fixture images or deterministic placeholders.

Component screenshots should be cheap enough to run for the full card matrix.

## Full Client Screenshots

Full-client screenshots are a smaller smoke suite.

Purpose:

- Verify timeline integration.
- Verify root card expansion/collapse in the real scroll container.
- Verify timestamp/content layout does not overlap.
- Verify completed cache does not flash back to loading.

Initial full-client smoke fixtures:

1. `compose-email-list`
2. `weather-current-forecast`
3. `moltbook-register`
4. `hotel-booking-gallery`
5. `file-attachment-list`

Full-client screenshots may require debug screens or launch arguments that load a fixture stream into the timeline.

## Comparison Outputs

Each fixture run writes:

```text
docs/agent-stream-fixtures/artifacts/
  compose-email-list/
    ios-render-calling.json
    android-render-calling.json
    ios-calling.png
    android-calling.png
    ios-render-completed.json
    android-render-completed.json
    ios-completed.png
    android-completed.png
    parity-report.md
```

`parity-report.md` includes:

- replay success or failure per client
- parser/render model differences
- tool entry count differences
- card type differences
- state differences
- props key differences
- screenshot paths
- current status
- migration direction

## Similarity Levels

Parity status is tracked per card and per fixture:

- `matched`: card type, state, meaningful props, layout hierarchy, and interactions are equivalent.
- `ios-missing`: Android renders a meaningful card but iOS does not.
- `android-missing`: iOS renders a meaningful card but Android does not.
- `data-divergent`: both render, but one client loses meaningful data or consumes a different raw shape.
- `visual-divergent`: both render the same data, but hierarchy, density, image handling, colors, or scrolling differ enough to matter.
- `interaction-divergent`: both render, but forms, galleries, links, expansion, or actions differ.
- `blocked`: fixture cannot yet be replayed through one client's parser.

## Initial Fixture Set

### `compose-email-list`

Purpose:

- Reproduce the known Gmail list difference.
- Android renders `messages/items` list mode.
- iOS currently has `ComposeEmailCard` but only consumes single-email fields in inline root-card mode.

Expected initial status: `ios-missing`.

Migration:

- Port Android email list mode into iOS `ComposeEmailCard.inlineContent`.
- Keep single-email rendering compatible with both clients.

### `weather-current-forecast`

Purpose:

- Reproduce Android-only weather card.
- Android registry maps `COMPOSIO_SEARCH_WEATHER` and `getWeather` to `weather`.
- iOS root card registry and dispatch currently omit `weather`.

Expected initial status: `ios-missing`.

Migration:

- Add iOS registry mapping.
- Add iOS transform.
- Add iOS `WeatherCard` root dispatch.
- Use Android weather data shape as a candidate canonical shape.

### `moltbook-register`

Purpose:

- Reproduce iOS interactive card versus Android read-only/suspended rendering.

Expected initial status: `android-missing` or `interaction-divergent`.

Migration:

- Decide whether Android needs full credential-entry interaction.
- If yes, create Android interactive card and host callbacks.
- If no, mark Android as intentionally display-only and document product exception.

### `hotel-booking-gallery`

Purpose:

- Reproduce visual and image/gallery differences.
- Android preserves richer image data.
- iOS visual hierarchy is stronger.

Expected initial status: `data-divergent` and `visual-divergent`.

Migration:

- Keep richer Android image/gallery canonical props.
- Add missing iOS props consumption.
- Align Android hierarchy, spacing, image fallback, and gallery interaction.

### `file-attachment-list`

Purpose:

- Reproduce transform versus visual split.
- Android has stronger raw transform.
- iOS has stronger row visual but weaker raw adaptation.

Expected initial status: `data-divergent` and `visual-divergent`.

Migration:

- Port Android file transform to iOS.
- Align Android row density, icon sizing, and metadata layout with iOS.

## Full Card Matrix Expansion

After the P0 harness is working, add fixtures for:

- GitHub: `checkRuns`, `commentThread`, `commitComparison`, `contributors`, `deployments`, `githubIssue`, `githubIssuesList`, `notifications`, `orgsList`, `release`, `repoList`, `secretAlerts`, `workflows`
- Search: `breakingNews`, `headlineList`, `imageGrid`, `productList`, `urlContent`
- Travel/local/market: `flightAlert`, `hotelBooking`, `finance`, `placeList`, `eventList`, `weather`
- Workspace/social/schedule: `linearIssue`, `linearIssuesList`, `composeEmail`, `fileAttachment`, `socialPostFeed`, `createSchedule`, `updateSchedule`, `updateScheduleStatus`, `moltbookRegister`
- Fallback: `generic`, unknown tool, ignored tool, meta tool, sub-agent, denied output, stream-level error

## Migration Priority

### Android to iOS

1. `composeEmail` list mode.
2. `weather` registry, transform, dispatch, and card UI.
3. iOS transforms matching Android for `fileAttachment`, `commentThread`, `socialPostFeed`, `release`, and canonical GitHub/Linear list shapes.
4. Hotel/place richer image props.

### iOS to Android

1. `moltbookRegister` interaction if product requires Android parity.
2. `headlineList`, `productList`, and `fileAttachment` visual density.
3. `hotelBooking` and `placeList` visual hierarchy and gallery behavior.

### Bidirectional

1. `finance` chart, change color, and secondary tabs.
2. GitHub weak-field cards: `notifications`, `release`, `commitComparison`, `deployments`, `secretAlerts`.
3. Root card sizing and paging/scroll behavior.

## Error Handling

Fixture replay failures must be explicit:

- If a fixture cannot be parsed by one client, report `blocked` with the event index and parser error.
- If replay succeeds but produces no card entries, report `ios-missing` or `android-missing` depending on side.
- If screenshots cannot be captured, still write render JSON and mark screenshots unavailable.
- If image loading fails, use deterministic placeholders and record the failure separately from card data parity.

## Testing Strategy

### Unit Tests

- iOS parser replay test for each P0 fixture.
- Android Stream SDK replay test for each P0 fixture.
- Tool entry JSON comparison test for each P0 fixture where both clients can export entries.
- Transform tests for newly ported card transforms.

### Component Screenshot Tests

- One screenshot per required frame per fixture.
- Store artifacts outside normal source snapshots at first.
- Promote stable screenshots into checked-in baselines only after card behavior converges.

### Full Client Smoke Tests

- Run a small P0 fixture set on iOS Simulator and Android Emulator.
- Capture real timeline screenshots.
- Confirm no overlap with timeline metadata.
- Confirm completed streams do not flash back to loading.

## Acceptance Criteria

The first implementation milestone is complete when:

- Five P0 SSE fixtures exist.
- Both clients can replay at least `compose-email-list`, `weather-current-forecast`, and `file-attachment-list` into render JSON.
- Both clients can capture component screenshots for at least three P0 fixtures.
- The harness produces a parity report with migration direction for each P0 fixture.
- No fixture requires hand-written platform render models.

The full parity milestone is complete when:

- Every shared card type in the matrix has at least one SSE fixture.
- Every fixture can produce render JSON on both clients.
- Every P0 and P1 fixture has component screenshots on both clients.
- Full-client smoke screenshots pass for the P0 set.
- All cards are `matched` or have an explicit product-approved exception.

## Implementation Decisions

Use these defaults for the first implementation plan:

- iOS uses both a deterministic SwiftUI component host and a simulator debug screen. The component host is the daily regression target; the simulator screen is for P0 full-client smoke tests.
- Android uses both a Compose component screenshot host and an emulator debug screen. The component host is the daily regression target; the emulator screen is for P0 full-client smoke tests.
- Screenshot artifacts are not committed during the first milestone. They are written under `docs/agent-stream-fixtures/artifacts/`, ignored by git, and suitable for CI upload later.
- The first milestone compares render JSON structurally and uses reviewable screenshots for visual checks. Automated image-diff thresholds are added only after the first stable baseline set is approved.

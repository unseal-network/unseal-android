# Agent Stream Tool Card Handoff

Date: 2026-06-22
Branch: `develop`
Device used for latest real-chat verification: physical Android `5fd76ce3`

## What Is Confirmed

- Android now uses the unified agent stream/tool card path for the current checkpoint.
- Web Search, Flight, Weather, Hotel success, and Hotel failure have been verified in the real `geminirayson` private chat.
- Preview fixtures cover the broader card set, including Web Search, Image, Shopping, Events, Places, Hotels, Weather, GitHub, Gmail/Drive, Linear, Twitter/X, Schedule, generic data, and failure details.
- Completed tool cards default collapsed to reduce timeline cost. Expanded state is remembered by stable key.
- Tool tabs stop auto-switching once the user manually selects a tab.
- Failed calls remain selectable and expose failure detail/raw payload instead of being hidden behind an aggregate failed state.
- Hotel image handling is scoped per hotel instead of reusing one global image source.
- The latest parser handles real streams where `renderUI` is nested inside `COMPOSIO_MULTI_EXECUTE_TOOL` with `tool_slug == "renderUI"` and `arguments.spec`.
- Normalized renderUI props are accepted for `images`, `places`, and `events` payloads.

## Real App Evidence

- Web Search final expanded card:
  `docs/qa/real-app-card-qa-20260622/latest-device/websearch-final-expanded-after-date-patch-20260622.png`
- Flight expanded card:
  `docs/qa/real-app-card-qa-20260622/latest-device/real-flight-after-header-tap-20260622.png`
- Weather expanded card:
  `docs/qa/real-app-card-qa-20260622/latest-device/real-weather-expanded-v2-20260622.png`
- Hotel success after nested renderUI parser fix:
  `docs/qa/real-app-card-qa-20260622/latest-device/renderui-meta-hotel-stream-40s-20260622.png`
- Hotel failure expanded:
  `docs/qa/real-app-card-qa-20260622/latest-device/real-hotel-failure-expanded-20260622.png`

The local pulled stream snapshot database and runtime logs were used during investigation but should stay out of git. The important finding from that database was:

- Places snapshot `a79b1d43045936ee2f7b61030a3ced34` contained `COMPOSIO_MULTI_EXECUTE_TOOL` with a nested `renderUI` `PlaceListCard`.
- Image snapshot `490278bdd5ca26bb4bee2bacb3d6274f` contained `COMPOSIO_MULTI_EXECUTE_TOOL` with a nested `renderUI` image carousel payload.
- Events snapshot `046375bc5815c22d07ba29ee3d87ce0a` did not contain a successful usable event result in that run.

## Key Android Files

- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/AiToolCardLogic.kt`
  Parses AI SDK stream/tool entries, including nested multi-execute `renderUI` payloads.
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/CardTransforms.kt`
  Normalizes card payloads, including already-normalized `images`, `places`, and `events`.
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/ComposioSearchCards.kt`
  Owns the mobile card presentation for Web Search, Hotels, Flights, Weather, Places, Images, Events, Shopping, and related search cards.
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/ToolCardKit.kt`
  Shared card shell, headers, tabs, status indicators, loading/selection behavior, and cross-card styling.
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/MarkdownBody.kt`
  Markdown rendering split between streaming and stable-message modes.
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/TimelineItemAiView.kt`
  AI message composition, stream card placement, and markdown/tool-card integration.
- `features/messages/impl/src/debug/kotlin/io/element/android/features/messages/impl/timeline/components/event/AgentStreamToolCardPreviewActivity.kt`
  Local preview harness used to inspect card fixtures without relying on live agent behavior.

## Verification Already Run

```bash
./gradlew :features:messages:impl:testDebugUnitTest --tests 'io.element.android.features.messages.impl.timeline.components.event.toolcards.ToolCallRootCardAdapterTest' --tests 'io.element.android.features.messages.impl.timeline.components.event.toolcards.ToolCardDispatcherTest'
./gradlew :features:messages:impl:testDebugUnitTest --tests 'io.element.android.features.messages.impl.timeline.factories.event.AiSdkStreamReducerTest'
./gradlew :app:assembleGplayDebug
adb install -r app/build/outputs/apk/gplay/debug/app-gplay-arm64-v8a-debug.apk
```

The latest install succeeded on `5fd76ce3`.

## Continuation Verification (2026-06-22, device `5fd76ce3`, build `26.06.1` / versionCode `202606012`)

All Android-side remaining items were verified in the real `geminirayson` chat. Evidence lives in
`docs/qa/real-app-card-qa-20260622/continuation-20260622/`.

- Places — VERIFIED. "coffee shops in Shanghai" rendered a `Places ✓3` card with per-place scoped
  images, ratings, review counts, price, open/closed status, and addresses, plus a clean markdown
  summary below. Evidence: `places-final-expanded-20260622.png`.
- Image — VERIFIED. "Chengdu hotel rooms" rendered an `Images ✓1` card; expanded it shows a clean
  grid of 5 distinct hotel-room images (no broken/duplicate-source issue).
  Evidence: `image-final-expanded-grid-20260622.png`.
- Events — VERIFIED with real data. The events tool returns **no data for Chinese cities** (Shanghai
  "AI conference" → Events tab shows "No events returned" and the agent falls back to a `Web Search`
  tab; the card handles the empty state gracefully). A US-city query ("concerts in New York this
  weekend") returned a populated `Events ✓1` card with 10 real events (New York Philharmonic / David
  Geffen Hall, Nick Cannon / Barclays Center, etc.), each with title, time, venue, and per-event
  image. The empty-Events handling and populated rendering are both correct; the geo gap is a
  tool/backend data-coverage limitation, not an Android renderer bug.
  Evidence: `events-final-expanded-real-data-20260622.png`, `events-empty-state-graceful-20260622.png`.
- Light mode — VERIFIED. The densest card (Weather) renders correctly in light mode: location pill,
  big temp with condition, FEELS/WIND/HUMID stats row, and a 7-day forecast strip with day tiles.
  Events card and markdown also render cleanly (dark text on white, good contrast).
  Evidence: `weather-light-expanded-dense-20260622.png`, `events-light-mode-20260622.png`.
- Long-press selection layering — VERIFIED. Long-pressing the AI message (card + markdown are one
  timeline event) dims the whole card uniformly under the selection scrim — including the card's
  nested stat/forecast surfaces — and overlays the action sheet (reactions, Reply, Copy link, Select
  text, Copy text) cleanly with no z-order bleed-through. Dismissing restores the card with no
  residual scrim. Evidence: `longpress-layering-card-body-20260622.png`,
  `longpress-layering-markdown-20260622.png`.

## Remaining Work

- Continue iOS parity screenshot comparison after Android is stable. The Android code is now closer to iOS, but final parity is not complete until both clients are captured against the same mock/live stream set.
- (Optional/backend) The events search tool has weak geo coverage for Chinese cities. If a populated
  Events card is wanted for CN queries, that is a tool/agent data-source change, not an Android change.

## Suggested Next Prompts

Use the `geminirayson` private chat and ask for card-only output:

```text
Call places search tool for coffee shops in Shanghai and return a places card only
Call image search tool for Chengdu hotel rooms and return an image card only
Call event search tool for AI conference in Shanghai and return events card only
```

If the agent returns markdown, inspect the stream snapshot before assuming the renderer failed. Recent real streams proved the card spec can be nested inside `COMPOSIO_MULTI_EXECUTE_TOOL`.

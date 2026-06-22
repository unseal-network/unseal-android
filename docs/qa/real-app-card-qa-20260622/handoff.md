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

## Remaining Work

- Re-run real Places and Image prompts after the nested `renderUI` parser fix and capture post-fix screenshots in the actual app.
- Get one real successful Events payload; the last event search returned no usable events.
- Recheck light mode in the real chat for the main cards and markdown, especially Weather and dense cards.
- Recheck long-press selection layering in the real timeline after the card shell changes.
- Continue iOS parity screenshot comparison after Android is stable. The Android code is now closer to iOS, but final parity is not complete until both clients are captured against the same mock/live stream set.

## Suggested Next Prompts

Use the `geminirayson` private chat and ask for card-only output:

```text
Call places search tool for coffee shops in Shanghai and return a places card only
Call image search tool for Chengdu hotel rooms and return an image card only
Call event search tool for AI conference in Shanghai and return events card only
```

If the agent returns markdown, inspect the stream snapshot before assuming the renderer failed. Recent real streams proved the card spec can be nested inside `COMPOSIO_MULTI_EXECUTE_TOOL`.

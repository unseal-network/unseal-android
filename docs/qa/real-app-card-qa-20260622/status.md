# Agent Stream Tool Card QA Status

Date: 2026-06-22

## Current Device State

- Physical Android device `5fd76ce3` (`PHK110`) was connected and used for the latest preview install.
- The latest built APK exists at `app/build/outputs/apk/gplay/debug/app-gplay-arm64-v8a-debug.apk` and installed successfully with `adb install -r`.
- Remaining disk space is low, around 563 MiB after the last check. Avoid unnecessary full rebuilds until space is freed or a device is connected.

## Verified In Installed App Preview

The APK was built, installed to an Android emulator, and verified through `AgentStreamToolCardPreviewActivity`.

- Hotel card renders a per-hotel thumbnail plus image strip.
- Hotel gallery opens from the image strip and shows a per-hotel counter such as `1 / 3`.
- Web Search card renders thumbnails.
- Places card renders thumbnails in light mode.
- Weather card text is readable in light mode.
- Failed tool call tab can be opened and shows failure detail/raw payload.
- Tool tab edge fade was removed to avoid translucent text/ghosting at card edges.
- Web Search row layout was tightened to follow the iOS headline row pattern: text stack on the left, compact 56x42 thumbnail on the right, and separate source/date/domain metadata.

Evidence:

Only selected final screenshots are intended for this checkpoint commit. Some older paths below are local QA captures kept for investigation context and may not be committed.

- `docs/qa/real-app-card-qa-20260622/emulator/preview-fixtures/dark-hotel-gallery-restored.png`
- `docs/qa/real-app-card-qa-20260622/emulator/preview-fixtures/dark-hotel-gallery-dialog-2.png`
- `docs/qa/real-app-card-qa-20260622/emulator/preview-fixtures/dark-websearch-images-new.png`
- `docs/qa/real-app-card-qa-20260622/emulator/preview-fixtures/dark-failure-detail-new.png`
- `docs/qa/real-app-card-qa-20260622/emulator/preview-fixtures/light-weather-new.png`
- `docs/qa/real-app-card-qa-20260622/emulator/preview-fixtures/light-places-new.png`
- `docs/qa/real-app-card-qa-20260622/latest-device/websearch-ios-like-row-placeholder-20260622.png`
- `docs/qa/real-app-card-qa-20260622/latest-device/websearch-ios-like-row-light-20260622-v2.png`

## Verified In Real Chat On Physical Device

The APK was installed on physical device `5fd76ce3` and tested in the real `geminirayson` private chat.

- Web Search real stream produced a completed card, defaulted collapsed, and expanded correctly with result thumbnails and iOS-like headline rows.
- Web Search was re-verified after the final row layout pass in the real `geminirayson` private chat: right thumbnails are fixed-size, source is separated onto its own line, dates stay fully visible, and long domains truncate after the date instead of crowding the title block.
- Flight real stream produced a completed card, defaulted collapsed, and expanded correctly in the real `geminirayson` private chat. The expanded card rendered the compact iOS-like flight rows with airline, flight number, price, route, duration, direct status, and cabin.
- Weather real stream produced a completed card, defaulted collapsed, and expanded correctly in dark mode with readable text, metrics, and 7-day forecast.
- Hotel real stream with past dates produced a failed Hotels card; expanding it showed the failure detail/raw status instead of leaving the failure opaque.
- Hotel real stream with a fresh Zhuhai prompt produced a successful Hotels card after the nested `renderUI` parsing fix. The real app card displayed hotel thumbnails, independent tag rows, price/rating metadata, and Map/Book actions.
- Device snapshot inspection confirmed that some real agent streams wrap card specs inside `COMPOSIO_MULTI_EXECUTE_TOOL` entries where one nested tool has `tool_slug == "renderUI"` and `arguments.spec`. Android now parses that shape instead of treating it as plain markdown.
- The same nested `renderUI` fix also covers normalized `CarouselCard` payloads whose props are already shaped as `images`, `places`, or `events`.
- Hotel real stream with future dates did not produce a hotel tool card; the agent returned a plain text list and said it could not directly generate the interactive hotel card. This is an agent/tool-output gap rather than a proven Android rendering failure.
- Places real stream did not produce a place tool card for `Call places search tool for coffee shops in Shanghai return places card only`; the agent returned a plain markdown list. This is an agent/tool-output gap, while the Android place card remains verified through installed preview fixtures.
- Image Search real stream did not produce an image tool card for `Call image search tool for Chengdu hotel rooms return image card only`; the agent explicitly said it could not directly generate an interactive `ImageCard` and returned markdown links instead. This remains an agent/tool-output gap rather than a proven Android renderer failure.
- Events real stream did not produce an event tool card for `Call event search tool for AI conference in Shanghai return events card only`; the agent explicitly said it could not directly generate an interactive `EventCard` and returned markdown items instead. This remains an agent/tool-output gap rather than a proven Android renderer failure.

Evidence:

- `docs/qa/real-app-card-qa-20260622/latest-device/real-chat-geminirayson-open-correct-20260622.png`
- `docs/qa/real-app-card-qa-20260622/latest-device/real-websearch-expanded-20260622.png`
- `docs/qa/real-app-card-qa-20260622/latest-device/websearch-final-expanded-after-date-patch-20260622.png`
- `docs/qa/real-app-card-qa-20260622/latest-device/real-flight-after-header-tap-20260622.png`
- `docs/qa/real-app-card-qa-20260622/latest-device/places-clean-stream-36s-20260622.png`
- `docs/qa/real-app-card-qa-20260622/latest-device/image-search-stream-35s-20260622.png`
- `docs/qa/real-app-card-qa-20260622/latest-device/events-stream-40s-20260622.png`
- `docs/qa/real-app-card-qa-20260622/latest-device/real-after-scroll-to-bottom-for-future-hotel-20260622.png`
- `docs/qa/real-app-card-qa-20260622/latest-device/real-future-hotel-stream-40s-20260622.png`
- `docs/qa/real-app-card-qa-20260622/latest-device/real-weather-expanded-v2-20260622.png`
- `docs/qa/real-app-card-qa-20260622/latest-device/renderui-meta-hotel-stream-40s-20260622.png`

## Verified By Tests

Passed:

```bash
./gradlew :app:assembleGplayDebug
./gradlew :features:messages:impl:testDebugUnitTest --tests 'io.element.android.features.messages.impl.timeline.components.event.toolcards.ToolCallRootCardAdapterTest' --tests 'io.element.android.features.messages.impl.timeline.components.event.toolcards.ToolCardDispatcherTest'
./gradlew :features:messages:impl:testDebugUnitTest --tests 'io.element.android.features.messages.impl.timeline.factories.event.AiSdkStreamReducerTest'
```

These tests cover:

- Same tool success and failure entries remain separate tabs.
- Failed entries count and expose error props.
- Web Search, Image Search, Shopping, Events, Places, and Hotel transform image fields.
- Hotel gallery images are scoped to each hotel instead of mixed across hotels.
- Serp-style hotel `ads` payloads are transformed into hotel cards.
- Nested `COMPOSIO_MULTI_EXECUTE_TOOL` entries expose their nested `renderUI` specs as Android tool cards.

## Still Required For Completion

The parity goal is not complete, but the current Android checkpoint has been built, installed, and partially verified in the real `geminirayson` chat.

Required next checks:

- Re-run real Places/Image/Event prompts after the nested `renderUI` fix and capture final screenshots. Local stream snapshot inspection proved that Image and Places received usable nested `renderUI` payloads before the fix, but final post-fix screenshots are still needed.
- Trigger a successful real Events payload. The latest captured Events attempt returned no usable events before the renderer could be verified.
- Capture real chat screenshots in light mode where applicable.
- Confirm hotel gallery opens in the real chat timeline and shows the expected per-hotel image count once a real successful hotel card is available.
- Confirm no major card/card-edge/selection overlap regression in real timeline long-press states.

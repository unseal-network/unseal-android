# Agent Stream Tool Card P0 Parity Fixes Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. This plan continues after `2026-06-16-agent-stream-tool-card-parity-harness.md`.

**Goal:** Use the shared agent stream parity harness to start closing real P0 Tool Call Card differences across Android and iOS, with bounded, test-first fixes.

**Scope:** This plan does not try to finish every card in one change. It starts with concrete P0 gaps that have fixture coverage and low ambiguity:

1. iOS `weather`: Android already has registry, transform, and UI. iOS currently lacks root registry/dispatch and transform support.
2. iOS `composeEmail` list mode: Android supports Gmail message lists. iOS currently handles single-message props only.
3. Android `moltbookRegister` interaction gap: iOS has an interactive suspended-card path. Android remains read-only; this needs a follow-up design if mobile credentials submission is product-approved.

## Repository Boundaries

- iOS rendering fixes live only in `/Users/Ruihan/go/src/unseal-agent-ios`:
  - `UnsealUITests/`
  - `UnsealUI/Views/UIParts/ToolParts/ToolCallRootCardAdapter.swift`
  - `ToolCardsIOS/Sources/ToolCardsIOS/CardTransforms.swift`
  - `ToolCardsIOS/Sources/ToolCardsIOS/<CardGroup>/`
- Android fixes live only in `/Users/Ruihan/go/src/unseal-android/features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/` plus targeted tests.
- iOS client `unseal-ios` is not modified by this plan unless a debug smoke host needs a narrow fixture update under `AIMessage/Debug/`.

## Task 1: Add iOS Weather Card Parity

- [x] Add a failing iOS parity test asserting `weather-current-forecast` exports one `weather` entry with meaningful prop keys.
- [x] Add `COMPOSIO_SEARCH_WEATHER` and `getWeather` to iOS `ToolCardRegistry`.
- [x] Add iOS `CardTransforms.weather` that maps Android canonical props: `city`, `country`, `current`, and `forecast`.
- [x] Add `WeatherCard` under `ToolCardsIOS/Sources/ToolCardsIOS/ComposioSearch/`.
- [x] Dispatch `weather` from `ToolCallRootCard`.
- [x] Run available verification. `swiftc -parse` passes for all `ToolCardsIOS` sources. Full iOS test/export is blocked because this machine's active developer directory is `/Library/Developer/CommandLineTools`, and SwiftPM is also blocked by the existing `UnsealMiniApp` macOS 10.13 vs `GCDWebServer` macOS 10.15 mismatch.

## Task 2: Add iOS Compose Email List Mode

- [x] Add an iOS parity test asserting `compose-email-list` export props include `messages`.
- [x] Update `ComposeEmailCard` to render `messages` / `items` as a bounded list before falling back to single-message mode.
- [x] Preserve existing single-message rendering.
- [x] Run available verification or record the Xcode blocker. `swiftc -parse` passes; full iOS test/export remains blocked by the same Xcode/SwiftPM environment issues above.

## Task 3: Refresh Parity Reports

- [x] Re-run Android parity report tool.
- [x] Confirm generated artifacts remain ignored.
- [ ] Refresh iOS render JSON once Xcode is available, then re-run reports so `weather-current-forecast` and `compose-email-list` move from stale `ios-missing` reports to comparable output.
- [ ] Summarize which P0 gaps are closed and which require product/design follow-up.

## Progress Notes

- 2026-06-16: Implemented iOS render-lib parity for `weather` and Gmail message-list cards in `/Users/Ruihan/.config/superpowers/worktrees/unseal-agent-ios/agent-stream-card-parity-harness` commit `2df0e1c`.
- 2026-06-16: Android report tool still runs, but current parity reports are stale for these two cards because iOS render JSON cannot be regenerated in this environment.
- 2026-06-16: Remaining known P0 gap is Android `moltbookRegister` interactivity. Android has a read-only render path; migrating iOS's suspended interactive credential flow needs product/design approval before implementation.

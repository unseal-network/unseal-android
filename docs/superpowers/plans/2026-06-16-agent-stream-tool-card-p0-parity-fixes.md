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

- [ ] Add a failing iOS parity test asserting `weather-current-forecast` exports one `weather` entry with meaningful prop keys.
- [ ] Add `COMPOSIO_SEARCH_WEATHER` and `getWeather` to iOS `ToolCardRegistry`.
- [ ] Add iOS `CardTransforms.weather` that maps Android canonical props: `location`, `current`, and `forecast`.
- [ ] Add `WeatherCard` under `ToolCardsIOS/Sources/ToolCardsIOS/ComposioSearch/`.
- [ ] Dispatch `weather` from `ToolCallRootCard`.
- [ ] Run available verification. If Xcode is unavailable, record the blocker and verify with static diff plus Android report tool.

## Task 2: Add iOS Compose Email List Mode

- [ ] Add an iOS parity test asserting `compose-email-list` export props include `messages`.
- [ ] Update `ComposeEmailCard` to render `messages` / `items` as a bounded list before falling back to single-message mode.
- [ ] Preserve existing single-message rendering.
- [ ] Run available verification or record the Xcode blocker.

## Task 3: Refresh Parity Reports

- [ ] Re-run Android render artifacts and parity report tool.
- [ ] Confirm generated artifacts remain ignored.
- [ ] Summarize which P0 gaps are closed and which require product/design follow-up.

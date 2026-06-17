# iOS Room Parity Manifest

This document is the implementation checklist for Android room parity. iOS is the source of truth for data semantics; Android may use native Compose UI, but must consume equivalent data and state.

## Migration Workflow

Every room feature must move through the same path. Do not implement a Compose-only shortcut.

1. **iOS source audit**
   - Locate the iOS data source, view model, render model, and SwiftUI view.
   - Record the file path and the relevant input fields in this manifest.
2. **Android client/facade**
   - Add or reuse a room-scoped client/facade for network or Matrix data.
   - The room screen must not scatter Unseal API calls across cells or menu composables.
3. **Domain model**
   - Convert raw API/Matrix data into room-scoped domain objects such as `RoomUnsealContext`, `RoomAgentDescriptor`, or typed tool-card props.
   - Preserve partial failure information when iOS does.
4. **Reducer/render model**
   - Convert domain data into stable render models: `TimelinePresentationModel`, `AiStreamRenderModel`, `ToolCallRootRenderModel`, `RoomMenuRenderModel`, `ComposerSuggestionRenderModel`, and future menu/action models.
   - UI components only read these models and emit actions.
5. **Compose UI**
   - Implement Android-native UI that matches iOS layout, ordering, state transitions, and information density.
   - Visual differences are acceptable only when required by platform primitives, not because data was unavailable.
6. **Tests and acceptance**
   - Add unit tests for reducers and client routing.
   - Add fixture/screenshot/manual acceptance for each stream/tool-card type before calling the feature complete.

## Task ID Overlay

These IDs are the execution handles for `docs/superpowers/plans/2026-06-15-ios-parity-independent-tasks.md`.
Use them when updating tests, handoff notes, commits, and acceptance evidence.

| Task ID | Plan task | Existing manifest area | Scope | Status |
|---|---|---|---|---|
| ROOM-API | Task 1 | P0 Data Workflow | Room-scoped API facade, homeserver/unseal/AI-stream route parity, members/agents/schedules/webhooks/working memory/game packages/skills data loading. | P0 next |
| TIMELINE-PRESENTATION | Task 2 | Feature Completion Matrix, P0 Stream / Timeline Workflow | `TimelinePresentationModel` and row policy for self/other/AI/markdown/system/typing/date/footer/edited states. | P0 |
| STREAM-LIFECYCLE | Task 3 | P0 Stream / Timeline Workflow | SDK-only stream lifecycle, completed cache first frame, handle reuse, listener cancel semantics, store provider behavior. | P0 |
| STREAM-PARTS | Task 4 | P0 Stream / Timeline Workflow, P0 Tool Cards | `StreamSnapshot.parts -> AiStreamRenderModel`, root card insertion, visible/hidden part rules, terminal state normalization. | P0 |
| TOOLCARDS | Task 5 | P0 Tool Cards | Weather, finance, news/search, shopping, places/hotels, files/email/drive, GitHub/search, schedule, suspended/approval cards. | P0 |
| COMPOSER-SKILLS | Task 6 | P0 Composer Workflow | Mention suggestions, room-agent descriptors, direct-agent slash trigger, runtime skill catalog, legacy skill fallback. | P0 |
| ROOM-TOPBAR | Task 7 | P0 Menus / Room Interactions | Floating top chrome, right-side actions, schedule/device-agent/terminal visibility, iOS-style expansion behavior. | P0 |
| ROOM-MENUS | Task 9 | P0 Menus / Room Interactions, Message Long-Press Action Parity | Attachment menu, message action menu, reactions/read receipts/pinned/footer menu surfaces. | P0 |
| ROOM-KEY-RECOVERY | Task 10 | Feature Completion Matrix, Room footer/security | Restore room key card and flows: backup, sender retry, room-member recovery, non-bubble timeline presentation. | P0 |
| PERF-CACHE | Task 11 | P0 Stream / Timeline Workflow, Acceptance Fixtures | Timeline jank fixes, stable LazyColumn keys, markdown/tool memoization, stream cache reuse and no repeated loading. | P0 |
| SELECTED-STATE-PARITY | Task 12 | Feature Completion Matrix, P0 Visual State Semantics | Unified iOS-style rounded selected/active/pressed state tokens for tool tabs, secondary card tabs, forecast pills, composer skill picker, room menu chips, and any timeline selected controls. | P0 |

## Feature Completion Matrix

| Area | iOS source of truth | Android status | Remaining work |
|---|---|---|---|
| Room API routing | `ChatbotAPIClientFactory`, `ChatbotAPIClient` | Partial | Finish single room API facade coverage for agents, schedules, webhooks, working memory, game packages, skills. |
| Member/agent enrichment | `RoomAgentMemberEnricher`, `JoinedRoomProxy.updateMembers()` | Partial | Prove Android `RoomUnsealContext.members` matches iOS enriched member semantics, including direct-agent fallback. |
| Top floating chrome | `RoomScreen.roomTopOverlay`, `TopChromeBackdrop` | Partial | Match iOS floating capsule/header spacing, backdrop, and top safe-area behavior across scroll states. |
| Bottom composer chrome | `ComposerChromeBackdrop`, `ComposerToolbar` | Not complete | Add iOS-like floating composer backdrop, focused/disabled/reply/edit/voice/skill states. |
| Timeline presentation | iOS `TimelineView` + stream screenshots | Partial | Finish avatar column, sender grouping, right gutter, date divider, footer, receipt/reaction positioning, self/other direct-room parity. |
| Selected/active visual states | iOS capsule/pill selected controls in room chrome, tool tabs, skill chips, segmented tabs | Partial | Shared `SelectedStatePill` now covers Tool root tabs, Finance tabs, Weather forecast pills, and Composer agent target picker. Continue replacing remaining room menu/tool-card selected states. |
| AI stream render | `unseal-agent-ios` stream views, iOS `BubbleMessageView` behavior | Partial | Keep SDK-only lifecycle, verify cache-first completed streams, finish loading/cursor/text patch animation parity. |
| Markdown render | iOS `MarkdownRenderView` / `_MarkdownBody` | Partial | Confirm link taps, code block, quote, table/list spacing, completed markdown cache and streaming animation. |
| Tool root card | iOS `ToolCallRootCard` | Partial | Confirm single/multi-tool insertion, tab state, fixed internal scroll, no raw JSON fallback. |
| Weather card | iOS `ToolCardsIOS/ComposioSearch` weather | Partial | Match large current conditions, forecast row, selected day details, colors, icon hierarchy. |
| Finance card | iOS finance card | Partial | Add/verify `News / Financials / Stats` tabs, table sections, chart sizing, positive/negative colors. |
| News/search cards | iOS headline/web search cards | Partial | Match full-width list rows, source/time coloring, disclosure, image thumbnail behavior. |
| Shopping card | iOS shopping card | Partial | Fix row compression, image sizing, rating layout, green price color, list height. |
| Places/hotels cards | iOS places/hotels cards | Partial | Fix name visibility, image/gallery paging, amenities, map action, price/reviews layout. |
| Files/email/drive cards | iOS Gmail/Drive cards | Partial | Ensure all supported payloads transform to meaningful rows; no over-truncation or empty large cards. |
| GitHub cards | iOS GitHub cards | Partial | Fixture-by-fixture parity for repo/search/issues/PRs/compare/activity/contributors. |
| Schedule cards | iOS schedule/Moltbook cards | Partial | Confirm create/update/status fields and states with real stream fixtures. |
| Approval/suspended tools | iOS suspended/approval flow | Render model + display-only card | `SuspendedToolRenderModel` now parses iOS `suspendPayload` variants and Android renders meaningful readonly details. Host approval callback still blocked on iOS `AIAgentProxy.updateMessage` / Android host wire shape. |
| Mention suggestions | `CompletionSuggestionService`, `ComposerToolbarViewModel` | Partial | Use enriched agent members everywhere, agent badges, `@room` gating, direct agent slash flow. |
| Skill picker | `ComposerToolbarViewModel` skill catalog flow | Partial | Match room-agent runtime skill catalog, legacy fallback, pinned picker behavior after mention tap/long press. |
| Attachment menu | `RoomAttachmentPicker` | Partial | Android `RoomMenuRenderModel.attachmentActions` now follows the iOS-supported subset order: game, text formatting, poll, location, files, gallery, camera photo/video. Remaining iOS-only gaps: ping and sketch actions, plus final visual/icon polish. |
| Game picker | `GamePickerViewModel`, `GamePickerSheet` | Partial | Android presenter now consumes `RoomGameApiServiceProvider`, so homeserver route resolution/service construction live outside UI. Create-game now waits for `sendGameInviteMessage` success before dismissing, failure keeps the sheet open with an error, my-playing rooms are grouped by app id, and enter-playing-room passes the matched remote URL/meet id. Remaining: pagination, preview layout, screenshot verification. |
| Message long press menu | iOS timeline item menu providers | Partial | Select text exists for copyable plain text events and AI stream/markdown events, and opens a selectable dialog. Still missing iOS floating preview/menu style, translate, save/unsave, share/save media parity, and final destructive/action ordering polish. |
| Reactions/read receipts | iOS timeline interaction views | Existing Element behavior | Audit visual and action parity after timeline layout stabilizes. |
| Pinned banner | `PinnedItemsBannerView` | Partial | Match floating blur/banner behavior and scroll visibility. |
| Room footer/security | `RoomScreenFooterView` | Partial | Align pin violation, identity violation, history visible, encrypted/unencrypted footer placement. |
| Remote terminal | `UnsealTerminalPanelView`, D2D terminal APIs | Partial bottom layer | Android now has Matrix D2D send API plus terminal panel/reducer/transport for `cmd.open/input/close`. Remaining: observe incoming D2D terminal events and wire presenter lifecycle before enabling as fully functional. |

## P0 Data Workflow

Detailed request-client migration contract: `docs/ios-room-data-client-workflow.md`. For each room feature, map iOS data owner -> route semantics -> Android facade -> domain model -> render model before changing Compose UI.

| Feature | iOS source | iOS input data | Android current source | Android target model | Gap |
|---|---|---|---|---|---|
| Room chatbot API routing | `ChatbotAPIClientFactory.makeClient(userSession:appSettings:)`, `ChatbotAPIClient.swift` | Logged-in user session, app settings, homeserver well-known | `ChatbotApiServiceFactory.createForHomeserver(matrixClient)` used in multiple presenters | `RoomUnsealDataClient` | Calls are scattered; room screen has no single API facade. |
| Room agents | `ClientProxy.loadRoomAgents(roomId:)`, `JoinedRoomProxy.updateMembers()` | `GET /chatbot/v1/rooms/{roomId}/agents` | `ChatbotApiService.getRoomAgents(roomId)` exists | `RoomUnsealContext.roomAgents` | Not loaded once for the room; composer/topbar cannot share enriched state. |
| Member enrichment | `RoomAgentMemberEnricher.swift` | Matrix `RoomMember` + `ChatbotRoomAgent` | Android uses raw `RoomMembersState`; schedule badge separately compares all agents | `RoomMemberRender`, `RoomAgentDescriptor` | Android does not consistently know which members are agents. |
| Agent-in-room detection | `RoomScreenViewModel.loadActiveScheduleCount()` | `listAgents()` + `roomProxy.membersPublisher` | `RoomScheduleBadgePresenter` repeats `listAgents()` + members | `RoomUnsealContext.hasAgentInRoom` | Logic duplicated and not available to composer/menu/timeline. |
| Device agent detection | `RoomScreenViewModel.loadActiveScheduleCount()` | Agent metadata `agent_kind=device`, `bound_device_id` | `RoomUnsealContext.deviceAgentInRoom` | `RoomUnsealContext.deviceAgentInRoom` | Implemented for shared room state. |
| Schedule count | `RoomScreenViewModel.loadActiveScheduleCount()` | `listSchedules(roomId)`, `schedule.isEnabled` | `RoomScheduleBadgePresenter` | `RoomUnsealContext.activeScheduleCount` | Badge owns network requests; should consume room context. |
| Room working memory | `ChatbotAPIClient.getRoomWorkingMemory(roomId:)` | `/chatbot/v1/rooms/{roomId}/working-memory` | API exists | `RoomUnsealContext.workingMemory` | Not surfaced in room context/menu. |
| Webhook triggers | `ChatbotAPIClient.listWebhookTriggers(... roomId ...)` | roomId-filtered triggers | API exists | `RoomUnsealContext.webhookTriggers` | Room menu cannot show trigger state from shared context. |

## P0 Composer Workflow

| Feature | iOS source | iOS behavior | Android current source | Android target model | Gap |
|---|---|---|---|---|---|
| Mention suggestions | `CompletionSuggestionService.swift` | `@` combines trigger + enriched joined members + agent IDs | `MessageComposerPresenter`, `SuggestionsProcessor`, `ResolvedSuggestion.Member` | `ComposerSuggestionRenderModel` | No agent badge; suggestions do not use room-agent enrichment. |
| `@room` suggestion | `CompletionSuggestionService.membersSuggestions` | Show only when power level allows and not direct one-to-one | Existing Matrix suggestion behavior | `ComposerSuggestionRenderModel(kind=AllUsers)` | Needs parity verification after model rewrite. |
| Agent descriptors | `ComposerToolbarViewModel.refreshRoomAgents()` | Uses enriched members first, fallback full agents + member IDs | No shared descriptor model | `RoomAgentDescriptor` | Skill picker and mentions cannot share agent identity. |
| Direct agent slash trigger | `ComposerToolbarViewModel.updateDirectAgentSkillPickerForSlashTrigger()` | Direct rooms resolve first active agent member | Existing slash command service | `ComposerAgentSkillState` | Android slash flow is not connected to room-agent skill state. |
| Runtime skill catalog | `loadRoomAgentSkillCatalogs` | Prefer `listRoomAgentSkills(roomId, agentId, runtimeOwnerUserId)` | API exists; feature screens use it elsewhere | `ComposerAgentSkillState.skillsByAgent` | Room composer needs the iOS loading/fallback order. |
| Legacy skill fallback | `legacyInstalledSkillCandidates` | Fallback to `listAgentSkills(botName)` | API exists | `ComposerAgentSkillState.legacyFallback` | Not wired into composer room flow. |
| Device-agent chat send | `RoomScreen.isAgentChatMode`, `AgentChatModeMemoryCache`, `TimelineViewModel.sendAgentChatMessage` | Toggle is process-memory per room; normal/reply sends use raw `m.room.message` with top-level `device_id`, plus optional `skills` | `MessagesPresenter`, `AgentChatModeMemoryCache`, `MessageComposerPresenter` | active target `boundDeviceId`, raw message `device_id` | Implemented and unit tested for normal/reply payload shape. |

## P0 Stream / Timeline Workflow

| Feature | iOS source | iOS behavior | Android current source | Android target model | Gap |
|---|---|---|---|---|---|
| Stream lifecycle | `unseal-agent-ios` stream views; Android SDK already owns SSE | Stream state patches become parts; UI reads parts | `libraries/agentstream`, `TimelineItemAiPresenter` | Stream SDK + `AiStreamRenderModel` | Must guarantee UI never bypasses SDK. |
| Completed cache | iOS keeps rendered/completed state in timeline; Android SDK has handle/store work | Completed stream should first-frame render final content | `AiStreamHandleStore`, `AiStreamContentCache` | SDK snapshot cache + Android render cache | Verify no recycled cell reloads completed stream. |
| AI timeline layout | iOS room timeline shows independent card/text blocks, no giant outer bubble | Avatar column and text/card alignment separated | `TimelineItemEventRow`, `MessageEventBubble`, `TimelineItemAiView` | `TimelinePresentationModel` | Android still needs full row-policy parity. |
| Edited label | iOS does not show edited for stream replacement messages | Hide edited label for stream content | `TimelineEventTimestampView` partially fixed | `TimelinePresentationModel.editedPolicy` | Keep this rule in data model, not one-off UI checks. |
| Markdown | iOS `MarkdownRenderView`, cached stable markdown, cursor while streaming | Links, lists, bold, code, quote | `MarkdownBody.kt` exists | `MarkdownRenderModel` | Verify clickable links and cache by completed content. |
| Root tool card insertion | iOS `ToolCallRootCard` groups tools at first tool position | Tool card appears once; tool parts not duplicated | `AndroidAgentStreamAdapters`, `TimelineItemAiView` | `ToolCallRootRenderModel` | Formalize adapter and tests. |
| Loading/cursor | iOS shows stream loading/cursor, not literal `Thinking...` as final content | Loading only while stream is unresolved | `AiLoadingIndicator`, stream state | `AiStreamRenderModel.cursorMode` | Completed cache must not flash loading. |
| Selected/active state tokens | iOS uses rounded capsule/pill selection across tabs/chips | Android has multiple local `background(color)` selected states | Shared selected-state component/token set | Tool root tabs, finance tabs, weather forecast pills, composer skill picker, room topbar/menu chips must not draw square selected backgrounds. |

## P0 Tool Cards

| Card | iOS expected content | Android current source | Android target | Gap |
|---|---|---|---|---|
| Weather | Location, date, current condition, temp, feels-like, wind, humidity, UV, forecast, selected day details | `toolcards` weather dispatcher/props | Typed weather props + card | Android style/content still differs from iOS screenshots. |
| Finance | Price, change color, chart, second-level tabs `News / Financials / Stats` | `toolcards` finance | Typed finance props + tabs | Android lacks full secondary tab parity. |
| News | Title, summary, domain/source, time, disclosure | `ComposioSearchCards`, dispatcher | Typed news/headline props | Android currently has spacing/color issues in screenshots. |
| Web Search | Search results list with title/domain/snippet/image when present | `ComposioSearchCards` | Typed search result props | Needs iOS row layout parity. |
| Shopping | Image, product name, brand/store, rating, green price | `ComposioSearchCards` | Typed shopping props | Android row compression and colors differ. |
| Places | Image/gallery, name, rating, reviews, address, category, map action | `ComposioSearchCards` | Typed place props | Android has image support but color/layout mismatch. |
| Hotels | Image/gallery, hotel name, rating, reviews, location, price, amenities, map action | `ComposioSearchCards` | Typed hotel props | Need full name visibility and gallery horizontal scroll. |
| Files | File icon/type, filename, size/date, disclosure | `GmailDriveCards` | Typed file props | Android truncates too aggressively compared with iOS. |
| Email/Drive | Meaningful extracted content, not raw JSON | `GmailDriveCards` | Typed email/drive props | Need confirm all payload forms. |
| GitHub | Repo, issues, PRs, activity, contributors, compare/search cards | GitHub card files | Typed GitHub props | Need fixture-by-fixture parity. |
| Schedule | Schedule action/status content | Schedule/Moltbook cards | Typed schedule props | Need manifest details from iOS card implementation. |
| Approval/suspended | Interactive approval or input state | `SuspendedToolCard` display-only | Suspended card render model + future host callback | Interaction parity is missing. |

## P0 Menus / Room Interactions

| Feature | iOS behavior | Android current source | Target model | Gap |
|---|---|---|---|---|
| Topbar actions | Call, video, schedules, terminal, device-agent chat, room settings | `MessagesView`, `RoomMenuRenderModel` | `RoomMenuRenderModel.topbarActions` + `RoomTopbarToolRenderModel` | Schedules, webhook summary, device-agent chat, and terminal are derived from shared `RoomUnsealContext`. Android now exposes the expanded vertical room tools from `topbarTools` in iOS-aligned order: terminal, device-agent chat, schedules. Webhook data stays in `webhookSummary` but is not shown in the iOS-parity topbar menu. This preserves the iOS core interaction (`ellipsis -> vertical floating buttons -> click collapses`). Terminal still needs incoming D2D observation and presenter lifecycle wiring before it is fully functional. |
| Remote terminal | `RoomScreen.unsealTerminalPanel`, `UnsealTerminalPanelView`, `ClientProxy.openUnsealRemoteTerminal/sendUnsealRemoteTerminalInput/resize/close`, `UnsealD2DTarget.deviceId` | iOS opens a panel and talks to the bound device agent over D2D terminal messages | Android has `MatrixDeviceAgentTerminalTransport` for `cmd.open/input/close`, `DeviceAgentTerminalReducer`, and a room panel shell | `RoomTerminalRenderModel` + D2D client facade | Missing incoming `cmd.ready/output/closed` observer, presenter lifecycle binding, resize handling, and final UI action wiring. |
| Attachment menu | iOS order/content/icons | Android bottom sheet | `RoomMenuRenderModel.attachmentActions` | Data order now matches the iOS-supported subset. Android still uses a bottom sheet and splits camera into photo/video; ping/sketch are not implemented. |
| Long press menu | `TimelineItemMenuActionProvider`, `TimelineItemMenuAction` | Existing `ActionListPresenter`, `MessageActionMenuRenderModel` | `MessageActionMenuRenderModel` | Android has a render model and existing reply/thread/forward/edit/copy/select-text/pin/report/source/remove actions. `SelectText` is functional for copyable text events and AI stream/markdown events via a selectable dialog; iOS menu chrome is still incomplete. Missing translate, saved messages, and media share/save. |

### Device Verification Notes

- 2026-06-15 true-device check on PHK110 after installing `:app:installGplayDebug`:
  - Room list launched after install: `/tmp/unseal-latest-launched-settled.png`.
  - `geminirayson` room entered by UIAutomator bounds click (`[0,1825][1240,2120]`): `/tmp/unseal-latest-geminirayson-room-uiauto.png`.
  - Expanded room tool menu: `/tmp/unseal-latest-geminirayson-menu-expanded.png`.
- 2026-06-15 attachment menu true-device check after reinstall:
  - Room list after launch: `/tmp/unseal-after-install-settled.png`.
  - `geminirayson` room: `/tmp/unseal-room-geminirayson-after-install.png`.
  - Attachment sheet after tapping composer plus: `/tmp/unseal-attachment-menu-after-order-fix.png`.
  - UIAutomator order: `Game`, `文本格式化`, `投票`, `附件`, `照片和视频库`, `拍摄照片`, `录制视频`.
- 2026-06-15 topbar title width true-device check:
  - Before fix, the room header capsule could collapse to `g...` when call/menu actions were present.
  - Android now gives the room header capsule a stable readable width and lets the text ellipsize inside the capsule.
  - Verification screenshot: `/tmp/unseal-room-topbar-title-after-width-fix.png`; UIAutomator exposes full `geminirayson` text in the topbar.
- 2026-06-15 room chrome/menu true-device check:
  - Current Android room state used for iOS comparison: `/tmp/unseal-current-room-for-ios-compare.png`.
  - Topbar tool menu expanded by tapping the UIAutomator `Room tools` bounds: `/tmp/unseal-current-room-tools-menu.png`.
  - Message long-press menu opened by adb long press: `/tmp/unseal-current-longpress-menu.png`.
  - The topbar ellipsis now animates like iOS and only shows the green status dot when `DeviceAgentChat` is visible and `isDeviceAgentChatActive == true`; device-agent presence or schedule-only rooms must not show the active indicator.
  - Post-fix true-device verification after reinstall: `/tmp/unseal-after-topbar-agent-badge-fix-room.png`, `/tmp/unseal-after-topbar-agent-badge-fix-expanded.png`.
- 2026-06-15 latest true-device projection check after reinstall:
  - Current Android room baseline from PHK110 after `:app:installGplayDebug`: `/tmp/unseal-android-room-after-latest-install.png`.
  - Current Android right topbar tool menu expanded by tapping the real `Room tools` bounds: `/tmp/unseal-android-room-menu-expanded-latest.png`.
  - Verified behavior: timeline is not pushed by the expanded menu; topbar remains a floating overlay; self and other text messages render as standalone left-flow text instead of ordinary right/left chat bubbles. Remaining visual gaps: header capsule sizing/opacity, composer chrome polish, and exact text gray/spacing parity.
- 2026-06-15 room tool render-model check after reinstall:
  - Reducer test: `./gradlew :features:messages:impl:testDebugUnitTest --tests 'io.element.android.features.messages.impl.roomdata.RoomMenuReducerTest'`.
  - True-device Home calibration: `/tmp/unseal-room-topbar-home-calibrate.png`; UIAutomator bounds for `geminirayson`: `[0,1825][1240,2120]`.
  - Correct `geminirayson` room entry: `/tmp/unseal-room-topbar-geminirayson-room-correct.png`.
  - Expanded topbar tools from PHK110 projection: `/tmp/unseal-room-topbar-geminirayson-tools-expanded.png`.
  - Verified behavior: call button and ellipsis remain independent floating buttons; expanded tools render downward as circular overlay controls without shifting the title capsule or timeline. Current visible Android extension: Webhook link button appears before schedules when webhook action is present.
- 2026-06-15 top chrome polish check after reinstall:
  - Targeted tests: `./gradlew :features:messages:impl:testDebugUnitTest --tests 'io.element.android.features.messages.impl.roomdata.RoomMenuReducerTest' --tests 'io.element.android.features.messages.impl.timeline.model.TimelinePresentationReducerTest'`.
  - Installed to PHK110 with `./gradlew :app:installGplayDebug`.
  - Room screenshot after settling: `/tmp/unseal-after-topchrome-polish-room-settled.png`.
  - Expanded room tools screenshot: `/tmp/unseal-after-topchrome-polish-menu-expanded.png`.
  - Verified behavior: self and other text messages still use standalone left-flow layout with no ordinary chat bubble; back/title/call/tools remain floating overlay controls; expanding tools rotates ellipsis and stacks the tool buttons without pushing the timeline. Remaining visual gap: Android glass color/opacity and title capsule weighting are closer but still not a pixel match to iOS.
- 2026-06-15 composer chrome projection/mock check after reinstall:
  - Compile: `./gradlew :features:messages:impl:compileDebugKotlin --no-daemon -Pkotlin.incremental=false --console=plain`.
  - Installed to PHK110 with `./gradlew :app:installGplayDebug --no-daemon -Pkotlin.incremental=false --console=plain`.
  - Room screenshot from PHK110 projection: `/tmp/unseal-after-composer-polish-room.png`.
  - Expanded room tools screenshot after tapping the real `Room tools` bounds: `/tmp/unseal-after-composer-polish-menu-expanded.png`.
  - Mock composer interaction after tapping `text_editor` and typing `mock_test`: `/tmp/unseal-after-composer-polish-input-focused.png`.
  - Verified behavior: bottom chrome is shorter and lighter than the previous full-width slab; `未加密` stays above the composer; attachment, text editor, and voice/send controls remain independent floating controls; keyboard focus switches the trailing control from voice to send without moving the topbar tool overlay. Remaining visual gap: `TextComposer` inner field/keyboard state still uses Android-native styling and needs a dedicated iOS parity pass in the text composer layer.
- iOS source parity: `RoomScreen.toolMenu` only renders when `deviceAgentInRoom != nil` or `hasAgentInRoom == true`. Android should therefore show the ellipsis menu in agent/device-agent rooms and hide it in ordinary rooms such as `London`.
- Current verified result: Android agent room shows floating back/title/call/ellipsis chrome, and ellipsis expands vertical tool actions without shifting the other floating topbar controls. This matches the iOS `roomTopOverlay` / `toolMenu` interaction model at the data/visibility level; webhook is no longer shown as a visible topbar extension and should move behind a secondary room/webhook surface if needed. Remaining work is visual polish and terminal incoming-event completion.
- 2026-06-15 latest PHK110 projection check:
  - Home UIAutomator row bounds for `geminirayson`: `[0,2122][1240,2417]`; room entry screenshot: `/tmp/unseal-gemini-room-bounds.png`.
  - `Room tools` UIAutomator bounds in the room: `[1023,160][1191,328]`; expanded screenshot: `/tmp/unseal-gemini-tools-expanded.png`.
  - Verified behavior: call and ellipsis are separate floating controls, the schedule tool expands downward from ellipsis without affecting title/timeline layout, and plain/self text is rendered as standalone left-flow content. Remaining visual gaps are header glass opacity/sizing, exact iOS composer chrome, and remaining room detail/menu pages.
- 2026-06-15 constrained-layout projection check:
  - Android layout change: standalone stream/markdown rows now calculate content margins from parent constraints (`BoxWithConstraints.maxWidth`) instead of global screen width; topbar control icons are explicitly `22.dp`.
  - Compile/install: `./gradlew :features:messages:impl:compileDebugKotlin --no-daemon -Pkotlin.incremental=false --console=plain`; `./gradlew :app:installGplayDebug --no-daemon -Pkotlin.incremental=false --console=plain`.
  - PHK110 screenshots: stable room `/tmp/unseal-room-verify-after-scroll.png`, expanded room tools `/tmp/unseal-room-verify-tools-expanded.png`, delayed settled state `/tmp/unseal-room-verify-delayed.png`.
  - Verified behavior: room tools overlay does not push content, AI root card is outside ordinary message bubbles, markdown text flows in the iOS-style content column with right gutter.
  - Remaining gap: immediate room-entry capture `/tmp/unseal-room-verify-stable.png` showed possible first-frame under-paint while UIAutomator already exposed timeline nodes. Investigate LazyColumn / stream-row render readiness and scroll performance before declaring room timeline projection complete.
- 2026-06-15 follow-up PHK110 projection/mock check:
  - Mock path: Home `geminirayson` row was opened via `adb input tap 620 2269`, then the real `Room tools` floating button was tapped to expand the right-side tool overlay.
  - Android layout change: compact standalone AI/markdown content now reserves a larger right gutter (`32.dp` on narrow widths) so stream cards/text do not run as far right as ordinary Android chat content.
  - Compile/install: `./gradlew :features:messages:impl:compileDebugKotlin --no-daemon -Pkotlin.incremental=false --console=plain`; `./gradlew :app:installGplayDebug --no-daemon -Pkotlin.incremental=false --console=plain`.
  - PHK110 screenshots: settled room `/tmp/unseal-room-after-standalone-margin.png`; expanded room tools `/tmp/unseal-room-menu-after-standalone-margin.png`.
  - UIAutomator evidence: standalone content right bound is about `1128` after the margin change, down from the previous wider bound around `1156`. Verified behavior remains: no outer AI bubble, menu is overlay-only, self/other plain text stays in left-flow standalone layout. Remaining gaps: iOS glass blur/opacity, title capsule weight, composer chrome, long-press chrome, and first-frame/timeline scroll performance.
- 2026-06-15 webhook-topbar filter verification:
  - Unit/compile: `./gradlew :features:messages:impl:compileDebugKotlin :features:messages:impl:testDebugUnitTest --tests 'io.element.android.features.messages.impl.roomdata.RoomMenuReducerTest' --no-daemon -Pkotlin.incremental=false --console=plain`
  - Device install: `./gradlew :app:installGplayDebug --no-daemon -Pkotlin.incremental=false --console=plain`
  - PHK110 screenshots: `/tmp/unseal-geminirayson-after-webhook-topbar-filter.png`, `/tmp/unseal-geminirayson-tools-after-webhook-topbar-filter.png`
  - UIAutomator evidence: `Room tools` exists; `Webhook triggers` and `Room AI Config` were absent before expansion, and the expanded menu screenshot shows only the schedule clock for this agent room.
- 2026-06-15 long-press action projection/mock check:
  - Unit/compile: `./gradlew :features:messages:impl:compileDebugKotlin :features:messages:impl:testDebugUnitTest --tests 'io.element.android.features.messages.impl.actionlist.*' --no-daemon -Pkotlin.incremental=false --console=plain`
  - Device install: `./gradlew :app:installGplayDebug --no-daemon -Pkotlin.incremental=false --console=plain`
  - PHK110 screenshot after adb long press and action-sheet scroll: `/tmp/unseal-action-menu-scrolled.png`; `Select text` appears before `复制文本`.
  - Latest post-install long-press screenshot: `/tmp/unseal-selecttext-menu-latest.png`; current Android still uses a bottom sheet rather than iOS' floating preview/action menu.
  - Mock composer attempt screenshot: `/tmp/unseal-selecttext-sent-message.png`; adb tapped voice recording instead of text send, so recording was discarded at `/tmp/unseal-after-discard-recording.png`.
- 2026-06-15 AI stream/markdown long-press check:
  - Code path: `TimelineItemEventContent.canBeCopied()` now includes non-empty `TimelineItemAiContent`; `MessagesPresenter.selectableText()` returns `TimelineItemAiContent.body`.
  - Unit/compile: `./gradlew :features:messages:impl:compileDebugKotlin :features:messages:impl:testDebugUnitTest --tests 'io.element.android.features.messages.impl.actionlist.*' --no-daemon -Pkotlin.incremental=false --console=plain`.
  - Device install: `./gradlew :app:installGplayDebug --no-daemon -Pkotlin.incremental=false --console=plain`.
  - PHK110 AI stream long-press menu screenshot: `/tmp/unseal-ai-longpress-menu.png`; menu contains `Select text` and `复制文本`.
  - PHK110 selectable dialog screenshot: `/tmp/unseal-ai-selecttext-dialog.png`; dialog contains the full AI markdown/body text (`https://keepsecret.io`, `Welcome to nginx!`, nginx paragraphs).
| Link handling | Links in markdown/cards open correctly | Mixed | Link action model | Ensure cards do not swallow URL taps. |
| Read receipts/reactions | iOS room behavior | Existing Element Android behavior | Menu/action model | P1 after stream/markdown. |

### Message Long-Press Action Parity

| iOS action | iOS file | Android action | Android status | Migration note |
|---|---|---|---|---|
| `selectText` | `ElementX/Sources/Screens/Timeline/View/ItemMenu/TimelineItemMenuAction.swift` | `SelectText` | Partial | Implemented for copyable plain text events and AI stream/markdown events with `MessagesState.selectableMessageText` and a `SelectionContainer` dialog. Still needs iOS-style floating menu chrome. |
| `copy` | same | `CopyText` | Implemented | Android uses `handleCopyContents`. |
| `translate` | same | none | Missing | Needs translation service + UI entry. |
| `copyCaption` | same | `CopyCaption` | Implemented | Android handles media captions. |
| `edit` | same | `Edit` | Implemented | Existing composer edit flow. |
| `addCaption` | same | `AddCaption` | Implemented | Existing attachment caption flow. |
| `editCaption` | same | `EditCaption` | Implemented | Existing attachment caption flow. |
| `removeCaption` | same | `RemoveCaption` | Implemented | Destructive action section. |
| `editPoll` | same | `EditPoll` | Implemented | Existing poll edit navigation. |
| `copyPermalink` | same | `CopyLink` | Implemented | Android action label matches iOS copy link. |
| `redact` | same | `Redact` | Implemented | Android remove action. |
| `reply(isThread:)` | same | `Reply` / `ReplyInThread` | Implemented | Ordering differs; keep reducer-controlled. |
| `forward` | same | `Forward` | Implemented | Existing forward flow. |
| `viewSource` | same | `ViewSource` | Implemented | Developer-mode gated on Android. |
| `report` | same | `ReportContent` | Implemented | Incoming only. |
| `react` / `toggleReaction` | same | reaction row + custom reaction sheet | Partial | Android uses separate reaction UI; audit visual parity later. |
| `endPoll` | same | `EndPoll` | Implemented | Existing confirmation flow. |
| `pin` / `unpin` | same | `Pin` / `Unpin` | Implemented | Uses pinned event permissions. |
| `saveMessage` / `unsaveMessage` | same | none | Missing | Requires saved-message backend/client equivalent. |
| `viewInRoomTimeline` | same | `ViewInTimeline` | Partial | Enum exists; handler is currently `Unit`, so pinned/saved/media jump flow is incomplete. |
| `share` | same | none | Missing | Needed for media/details parity. |
| `save` | same | none | Missing | Needed for media/details parity. |

## P1 Follow-up Features

| Feature | iOS source | Android target |
|---|---|---|
| Game picker | Composer toolbar game flow | Keep existing game picker but list parity in menu model. |
| Poll | Room composer/actions | Add to room menu manifest after P0. |
| Working memory editor | Chatbot room working memory APIs | Add room entry point once context exists. |
| Webhook management entry | Room webhook trigger screens | Add room menu entry driven by context triggers. |
| Voice/device-agent details | Room topbar and composer | Finish after topbar context parity. |
| JsonRender/data-ui-spec | iOS renderer stack | Separate renderer task after P0 stream/card data is stable. |

## Acceptance Fixtures

Manual acceptance must request real agent output for:

- weather in Shanghai
- Tesla finance
- latest AI news
- MacBook shopping
- Shanghai restaurant places
- Chengdu hotel travel plan
- recent files
- latest Gmail emails
- GitHub repository/search query
- schedule creation/check
- approval/suspended flow if available

For each fixture, capture:

- raw stream id
- SDK snapshot summary
- Android render model summary
- Android screenshot
- iOS screenshot or source-backed expected behavior

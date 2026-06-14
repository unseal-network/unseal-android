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

## Feature Completion Matrix

| Area | iOS source of truth | Android status | Remaining work |
|---|---|---|---|
| Room API routing | `ChatbotAPIClientFactory`, `ChatbotAPIClient` | Partial | Finish single room API facade coverage for agents, schedules, webhooks, working memory, game packages, skills. |
| Member/agent enrichment | `RoomAgentMemberEnricher`, `JoinedRoomProxy.updateMembers()` | Partial | Prove Android `RoomUnsealContext.members` matches iOS enriched member semantics, including direct-agent fallback. |
| Top floating chrome | `RoomScreen.roomTopOverlay`, `TopChromeBackdrop` | Partial | Match iOS floating capsule/header spacing, backdrop, and top safe-area behavior across scroll states. |
| Bottom composer chrome | `ComposerChromeBackdrop`, `ComposerToolbar` | Not complete | Add iOS-like floating composer backdrop, focused/disabled/reply/edit/voice/skill states. |
| Timeline presentation | iOS `TimelineView` + stream screenshots | Partial | Finish avatar column, sender grouping, right gutter, date divider, footer, receipt/reaction positioning, self/other direct-room parity. |
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
| Approval/suspended tools | iOS suspended/approval flow | Missing interaction | Add render model and host callback for approvals; current Android is display-only. |
| Mention suggestions | `CompletionSuggestionService`, `ComposerToolbarViewModel` | Partial | Use enriched agent members everywhere, agent badges, `@room` gating, direct agent slash flow. |
| Skill picker | `ComposerToolbarViewModel` skill catalog flow | Partial | Match room-agent runtime skill catalog, legacy fallback, pinned picker behavior after mention tap/long press. |
| Attachment menu | `RoomAttachmentPicker` | Partial | Align order, icons, enabled states, game/poll/media/location actions. |
| Game picker | `GamePickerViewModel`, `GamePickerSheet` | Partial | Match homeserver app package request flow, pagination, preview layout, insert/send behavior. |
| Message long press menu | iOS timeline item menu providers | Partial | Add/select text, translate, save/unsave, share/save media parity; align ordering and destructive styling. |
| Reactions/read receipts | iOS timeline interaction views | Existing Element behavior | Audit visual and action parity after timeline layout stabilizes. |
| Pinned banner | `PinnedItemsBannerView` | Partial | Match floating blur/banner behavior and scroll visibility. |
| Room footer/security | `RoomScreenFooterView` | Partial | Align pin violation, identity violation, history visible, encrypted/unencrypted footer placement. |
| Remote terminal | `UnsealTerminalPanelView`, D2D terminal APIs | Missing bottom layer | Implement Android D2D terminal client/panel before enabling terminal as functional. |

## P0 Data Workflow

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
| Topbar actions | Call, video, schedules, terminal, device-agent chat, room settings | `MessagesView`, `RoomMenuRenderModel` | `RoomMenuRenderModel.topbarActions` + active state | Schedules and device-agent chat are derived from room context; terminal has no Android D2D client yet and currently shows unsupported feedback. |
| Remote terminal | `RoomScreen.unsealTerminalPanel`, `UnsealTerminalPanelView`, `ClientProxy.openUnsealRemoteTerminal/sendUnsealRemoteTerminalInput/resize/close`, `UnsealD2DTarget.deviceId` | iOS opens a panel and talks to the bound device agent over D2D terminal messages | No Android D2D terminal client/entry point found | Future `RoomTerminalRenderModel` + D2D client facade | Missing bottom-layer client, panel, session lifecycle, resize/input/close handling. Do not pretend the button is functional until this is implemented. |
| Attachment menu | iOS order/content/icons | Android bottom sheet | `RoomMenuRenderModel.attachmentActions` | Keep bottom sheet if needed, but align data/order. |
| Long press menu | `TimelineItemMenuActionProvider`, `TimelineItemMenuAction` | Reply/thread/pin/report/view source/select/translate/save/share/save media | Existing `ActionListPresenter`, `MessageActionMenuRenderModel` | `MessageActionMenuRenderModel` | Android has a render model and existing reply/thread/forward/edit/copy/pin/report/source/remove actions. Missing bottom-layer support for iOS-only select text, translate, saved messages, and media share/save. |
| Link handling | Links in markdown/cards open correctly | Mixed | Link action model | Ensure cards do not swallow URL taps. |
| Read receipts/reactions | iOS room behavior | Existing Element Android behavior | Menu/action model | P1 after stream/markdown. |

### Message Long-Press Action Parity

| iOS action | iOS file | Android action | Android status | Migration note |
|---|---|---|---|---|
| `selectText` | `ElementX/Sources/Screens/Timeline/View/ItemMenu/TimelineItemMenuAction.swift` | none | Missing | Needs a text-selection screen/sheet before exposing. Do not map to copy. |
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

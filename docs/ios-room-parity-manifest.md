# iOS Room Parity Manifest

This document is the implementation checklist for Android room parity. iOS is the source of truth for data semantics; Android may use native Compose UI, but must consume equivalent data and state.

## P0 Data Workflow

| Feature | iOS source | iOS input data | Android current source | Android target model | Gap |
|---|---|---|---|---|---|
| Room chatbot API routing | `ChatbotAPIClientFactory.makeClient(userSession:appSettings:)`, `ChatbotAPIClient.swift` | Logged-in user session, app settings, homeserver well-known | `ChatbotApiServiceFactory.createForHomeserver(matrixClient)` used in multiple presenters | `RoomUnsealDataClient` | Calls are scattered; room screen has no single API facade. |
| Room agents | `ClientProxy.loadRoomAgents(roomId:)`, `JoinedRoomProxy.updateMembers()` | `GET /chatbot/v1/rooms/{roomId}/agents` | `ChatbotApiService.getRoomAgents(roomId)` exists | `RoomUnsealContext.roomAgents` | Not loaded once for the room; composer/topbar cannot share enriched state. |
| Member enrichment | `RoomAgentMemberEnricher.swift` | Matrix `RoomMember` + `ChatbotRoomAgent` | Android uses raw `RoomMembersState`; schedule badge separately compares all agents | `RoomMemberRender`, `RoomAgentDescriptor` | Android does not consistently know which members are agents. |
| Agent-in-room detection | `RoomScreenViewModel.loadActiveScheduleCount()` | `listAgents()` + `roomProxy.membersPublisher` | `RoomScheduleBadgePresenter` repeats `listAgents()` + members | `RoomUnsealContext.hasAgentInRoom` | Logic duplicated and not available to composer/menu/timeline. |
| Device agent detection | `RoomScreenViewModel.loadActiveScheduleCount()` | Agent metadata `agent_kind=device`, `bound_device_id` | No shared room state | `RoomUnsealContext.deviceAgentInRoom` | Terminal/device-agent topbar state cannot be derived centrally. |
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
| Topbar actions | Call, video, schedules, terminal, device-agent chat, room settings | `MessagesView`, navigation/menu code | `RoomMenuRenderModel.topbarActions` | Actions must be derived from room context. |
| Attachment menu | iOS order/content/icons | Android bottom sheet | `RoomMenuRenderModel.attachmentActions` | Keep bottom sheet if needed, but align data/order. |
| Long press menu | Reply/thread/pin/report/view source/select/translate/save/share/save media | Existing action list | `RoomMenuRenderModel.messageActions` | Missing iOS actions and ordering. |
| Link handling | Links in markdown/cards open correctly | Mixed | Link action model | Ensure cards do not swallow URL taps. |
| Read receipts/reactions | iOS room behavior | Existing Element Android behavior | Menu/action model | P1 after stream/markdown. |

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

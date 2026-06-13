# Android Room iOS Parity Data-First Migration Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate Android room, composer, stream render, tool cards, markdown, menus, and timeline behavior to match iOS by first aligning API clients, data structures, and business state flow.

**Architecture:** iOS is the factual reference for room data semantics and feature behavior. Android must introduce a room-level Unseal data context that reads the same logical resources as iOS, then derive stable render models consumed by Compose. Stream lifecycle remains owned by Stream SDK; Android consumes completed or live `StreamSnapshot.parts`.

**Tech Stack:** Kotlin, Jetpack Compose, Metro DI, Kotlin coroutines/Flow, Matrix Rust SDK room/member APIs, existing `libraries/chatbot`, existing `libraries/agentstream`.

---

## Current Checkpoint

- Android worktree: `/Users/Ruihan/.config/superpowers/worktrees/unseal-android/chatbot-api-service`
- Branch: `feature/agent-management`
- Safety checkpoint commit before this plan: `b5285d4075 chore: checkpoint room stream render work`
- Existing stream/card/timeline changes are part of the starting point. Do not revert them.

## Migration Workflow

This migration must happen in this order. Do not start by changing card UI.

1. **iOS fact manifest**
   - Create a manifest that lists every room/timeline/composer/menu feature and maps iOS source code, iOS input data, Android current state, Android target data model, and priority.
   - Treat iOS as the source of truth for how room data is fetched and combined.

2. **API client parity**
   - Create a room-scoped Android data client that wraps existing `ChatbotApiService`.
   - Use the logged-in homeserver and existing `.well-known` routing through `ChatbotApiServiceFactory.createForHomeserver(matrixClient)`.
   - Never hardcode `api.unseal.network` for room features.
   - Centralize room-level calls that are currently duplicated by schedule badge, composer, menu, and future UI.

3. **Room data context**
   - Build one `RoomUnsealContext` for the room screen.
   - Enrich Matrix members with room-agent metadata the same way iOS does.
   - Derive `hasAgentInRoom`, `deviceAgentInRoom`, `activeScheduleCount`, agent skill targets, webhook trigger state, and working memory from this context.

4. **Render models**
   - Add Android render models that are derived from data and are stable enough to test.
   - Compose must render these models only; it must not parse raw room-agent JSON or raw stream JSON.
   - Stream SDK remains the only owner of stream fetch, SSE parse, parts state machine, cache/store, and completed normalization.

5. **UI migration**
   - Only after the above models exist, migrate timeline layout, stream render, markdown, cards, topbar menus, attachment menu, mention picker, skill picker, and long-press menu.
   - UI can use Android-native Compose components, but data semantics, render order, card contents, and state transitions must match iOS.

6. **Performance pass**
   - Verify completed streams do not reload when cells recycle.
   - Verify markdown/tool card transforms are memoized by stream render version.
   - Verify visible listeners detach without cancelling background stream completion or store writes.

## Migration Workflow Deliverables

Every migrated feature must move through these gates. A feature is not considered migrated if it only has a Compose view.

| Gate | Required output | Why it matters |
|---|---|---|
| 1. iOS source mapping | File paths, method names, and state fields from iOS | Prevents guessing UI behavior without knowing how iOS gets data. |
| 2. Request/API mapping | Android client method, service endpoint, homeserver/unseal routing rule, failure behavior | Keeps Android from hardcoding endpoints or duplicating API calls. |
| 3. Domain model | Stable Kotlin data class that represents the feature before UI | Lets Android and iOS share semantics even when UI components differ. |
| 4. Reducer/adapter | Pure transform from API/SDK/domain data into render model | Keeps Compose from parsing JSON or making business decisions. |
| 5. State ownership | Presenter/state holder that owns loading, refresh, cache, and lifecycle | Avoids repeated network work when timeline rows recycle. |
| 6. UI | Compose component that consumes only render models | Makes UI replaceable without changing data logic. |
| 7. Tests | Unit tests for API mapping/domain/reducer; screenshot/manual tests for UI | Catches regressions before visual polish work. |

## iOS Data Flow To Android Architecture

### Room Data Request Client

iOS uses `ChatbotAPIClientFactory.makeClient(userSession:appSettings:)` from room and composer features. Android must use one room-scoped facade backed by `ChatbotApiServiceFactory.createForHomeserver(matrixClient)`.

The request client is responsible for:

- Selecting the correct base URL from the logged-in homeserver and `.well-known`.
- Keeping room feature calls on the same route family as iOS:
  - room agents: `getRoomAgents(roomId)`
  - global agents: `listAgents()`
  - schedules: `listSchedules(roomId)`
  - runtime room skills: `listRoomAgentSkills(roomId, agentId, runtimeOwnerUserId)`
  - legacy skills: `listAgentSkills(botName)`
  - webhook triggers: `listWebhookTriggers(roomId)`
  - working memory: `getRoomWorkingMemory(roomId)`
- Returning typed room domain models, not raw API responses.
- Returning partial results when one resource fails.

Android implementation checkpoint:

- `RoomUnsealDataClient` exists.
- `DefaultRoomUnsealDataClient` exists.
- `RoomUnsealDataSnapshot` carries partial `RoomUnsealResource<T>` failures.

Next required work:

- Replace duplicated schedule/topbar calls with `RoomUnsealContext`.
- Feed composer mention/skill state from `RoomUnsealContext`.
- Add refresh events so menus can request context reload without knowing request details.

### Members And Agent Enrichment

iOS data flow:

1. `JoinedRoomProxy.updateMembers()` loads local disk members with `membersNoSync()`.
2. It loads room agents through `roomAgentsProvider(id)`.
3. It enriches members with `RoomAgentMemberEnricher.enrich`.
4. It then repeats with live `members()` data.
5. Composer, mention suggestions, and room topbar all observe enriched `membersPublisher`.

Android target:

1. `RoomUnsealContextLoader` asks Matrix for room members if the current state is empty/unknown.
2. It loads `RoomUnsealDataSnapshot` through `RoomUnsealDataClient`.
3. It derives `RoomMemberRender` with the same rules as iOS:
   - only joined Matrix members can be agents
   - room-agent `membership` must be absent/empty/`join`
   - missing `userType` defaults to `agent`
   - agent types are `agent`, `bot`, `external_bot`, `trusted_external_bot`
4. `MessagesState.roomUnsealContext` exposes the result as `AsyncData<RoomUnsealContext>`.

Next required work:

- Use `roomUnsealContext.members` for mention suggestions.
- Use `roomUnsealContext.hasAgentInRoom` and `deviceAgentInRoom` for topbar/menu actions.
- Add reload triggers when room members change, app resumes, or a schedule/webhook mutation completes.

### Schedule, Device Agent, And Topbar

iOS data flow in `RoomScreenViewModel.loadActiveScheduleCount()`:

1. Create Chatbot API client from current user session.
2. Load `listSchedules(roomId)` and `listAgents()` concurrently.
3. Count enabled schedules.
4. Build `memberIDs` from `roomProxy.membersPublisher.value`.
5. Match global agents to members by `@localpart:serverName`.
6. Detect device agent when `agent.isDeviceAgent` and `boundDeviceID` exists.
7. Topbar uses those derived fields for schedules and device/terminal actions.

Android target:

- `RoomUnsealContext.activeScheduleCount`
- `RoomUnsealContext.hasAgentInRoom`
- `RoomUnsealContext.deviceAgentInRoom`
- `RoomMenuRenderModel.topbarActions`

Next required work:

- Stop `RoomScheduleBadgePresenter` from being the owner of requests.
- Derive `RoomScheduleBadgeState` from `RoomUnsealContext` inside messages.
- Build a topbar action reducer:
  - schedules visible when `hasAgentInRoom`
  - terminal/device-agent chat visible when `deviceAgentInRoom != null`
  - call/video/settings follow existing Matrix room rules

### Composer Mention And Skill Flow

iOS mention data flow:

1. `CompletionSuggestionService` combines suggestion trigger, enriched members, room list, and cached agent user IDs.
2. `@` suggestions include joined members except self.
3. Agent badge is based on enriched member `isAgent`.
4. `@room` is inserted only when power levels allow room notification and the room is not a direct one-to-one.
5. Member refresh is requested once when a user mention trigger first appears.

iOS skill data flow:

1. `ComposerToolbarViewModel` observes enriched members.
2. `refreshRoomAgents()` builds `ComposerAgentDescriptor` from agent members.
3. Direct rooms select the direct agent automatically.
4. Mentioned agent users become skill targets.
5. Runtime skills load through `listRoomAgentSkills(roomId, agentId, runtimeOwnerUserId)`.
6. If no runtime-visible skills are available or the request fails, iOS falls back to `listAgentSkills(botName)`.

Android target:

- `ComposerSuggestionRenderModel`
- `ComposerAgentDescriptor`
- `ComposerAgentSkillState`
- `ComposerAgentSkillCandidate`

Next required work:

- Add models first, without changing UI.
- Add reducers from `RoomUnsealContext + current composer text + permissions` to suggestions/skill state.
- Then replace Android suggestion view data source with the reducer output.

### Stream And Timeline

iOS stream/card data flow is not the request source for Android. Android must keep Stream SDK as the only lifecycle owner.

Android target:

1. Matrix event content gives a stream id.
2. Android asks Stream SDK for the stream snapshot.
3. Stream SDK handles fetch/dedupe/cache/store/SSE patch state.
4. Android reducer maps `StreamSnapshot.parts` to `AiStreamRenderModel`.
5. Timeline reducer maps Matrix event + stream render model to `TimelinePresentationModel`.
6. Compose renders only the render model.

Rules:

- Do not fetch stream directly from timeline cells.
- Do not parse raw stream JSON in Compose cards.
- Do not show completed stream as loading when cache/store has final snapshot.
- AI cards and markdown are independent content blocks, not nested in a normal message bubble.

## Feature Migration Matrix

### P0 Request / State Features

| Feature | iOS owner | Android target owner | Data model | Implementation status |
|---|---|---|---|---|
| Room API routing | `ChatbotAPIClientFactory`, `ChatbotAPIClient` | `RoomUnsealDataClient` | `RoomUnsealDataSnapshot` | Implemented initial facade. |
| Member refresh/enrichment | `JoinedRoomProxy.updateMembers`, `RoomAgentMemberEnricher` | `RoomUnsealContextLoader`, `RoomAgentMemberEnricher` | `RoomMemberRender` | Implemented initial parity. |
| Agent in room | `RoomScreenViewModel.loadActiveScheduleCount` | `RoomUnsealContext` + `RoomMenuReducer` | `hasAgentInRoom` | Implemented and consumed by topbar schedule action. |
| Device agent | `RoomScreenViewModel.loadActiveScheduleCount` | `RoomUnsealContext` + `RoomMenuReducer` | `RoomDeviceAgent` | Implemented and represented in menu model; UI actions still need parity wiring. |
| Schedule count | `RoomScreenViewModel.loadActiveScheduleCount` | messages state + room menu reducer | `activeScheduleCount` | Implemented from context and surfaced as `RoomScheduleMenuBadge`. |
| Working memory | `ChatbotAPIClient.getRoomWorkingMemory` | `RoomUnsealContext` | `workingMemory` | Loaded by client; no menu/UI consumer yet. |
| Webhook triggers | `ChatbotAPIClient.listWebhookTriggers` | `RoomUnsealContext` | `webhookTriggers` | Loaded by client; no menu/UI consumer yet. |

### P0 Composer Features

| Feature | iOS owner | Android target owner | Data model | Implementation status |
|---|---|---|---|---|
| Agent mention badge | `CompletionSuggestionService` | composer suggestion reducer + picker | `ComposerSuggestionRenderModel.isAgent` | Implemented; picker uses render model while insertion still uses `ResolvedSuggestion`. |
| Member suggestions | `CompletionSuggestionService.membersSuggestions` | composer suggestion reducer + shared room context store | `ComposerSuggestionRenderModel` | Implemented for current member/alias/command suggestions; agent skill side effects pending. |
| `@room` suggestion | `CompletionSuggestionService.membersSuggestions` | composer suggestion reducer | `kind=AllUsers` | Implemented in render model mapping; existing permission gate remains in `SuggestionsProcessor`. |
| Direct agent slash target | `ComposerToolbarViewModel.updateDirectAgentSkillPickerForSlashTrigger` | composer skill reducer | `ComposerAgentDescriptor` | Not started. |
| Mentioned agent targets | `ComposerToolbarViewModel.updateMentionedAgentTargets` | composer skill reducer | `ComposerAgentSkillState.agentTargets` | Not started. |
| Runtime skill catalog | `loadRoomAgentSkillCatalogs` | composer skill loader | `ComposerAgentSkillCandidate` | Not started. |
| Legacy skill fallback | `legacyInstalledSkillCandidates` | composer skill loader | `ComposerAgentSkillCandidate` | Not started. |

### P0 Timeline / Stream / Card Features

| Feature | iOS owner | Android target owner | Data model | Implementation status |
|---|---|---|---|---|
| Stream lifecycle | Stream renderer state | Stream SDK | `StreamSnapshot.parts` | Existing with presenter/cache invariant tests. |
| AI render model | `ToolCallRootCard` + text render flow | stream reducer | `AiStreamRenderModel` | Implemented with reducer tests; timeline content is derived from it. |
| Tool root grouping | `ToolGroupUtils` semantics | tool root adapter | `ToolCallRootRenderModel` | Implemented first version with reducer tests; card-specific parity still pending. |
| Timeline row policy | room timeline views | timeline presentation reducer | `TimelinePresentationModel` | Implemented with reducer tests; AI stream rows use standalone policy and adaptive right spacing. |
| Markdown render | iOS markdown view | markdown reducer/cache | `MarkdownRenderModel` | Existing partial; needs parity/cache tests. |
| Cards weather/finance/news/shopping/places/hotels/files/email/drive/github/schedule | iOS card implementations and screenshots | card adapters + Compose cards | typed card props | Existing partial; needs fixture-by-fixture parity. |

### P0 Menus / Room Interactions

| Feature | iOS owner | Android target owner | Data model | Implementation status |
|---|---|---|---|---|
| Topbar actions | `RoomScreenViewModel` + room view | room menu reducer | `RoomMenuRenderModel.topbarActions` | Implemented for threads, schedules, and device-agent actions; visual overlay parity still pending. |
| Attachment menu | composer attachment scope | room menu reducer | `RoomMenuRenderModel.attachmentActions` | Not started. |
| Long press menu | timeline action sheets | action menu reducer | `RoomMenuRenderModel.messageActions` | Not started. |
| Link handling | markdown/card link actions | link action reducer | `RoomLinkAction` | Existing mixed; needs card/markdown audit. |
| Read receipts/reactions | room timeline | existing Element state + menu model | action model | P1 after P0 render. |

## iOS Fact Sources

Use these files as reference before implementing each related Android task:

- Room topbar, schedule count, device-agent detection:
  - `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/RoomScreen/RoomScreenViewModel.swift`
- Room member enrichment:
  - `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Services/Room/JoinedRoomProxy.swift`
  - `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Services/Room/RoomAgentMemberEnricher.swift`
- Chatbot/Unseal room APIs:
  - `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Services/ChatbotAPI/ChatbotAPIClient.swift`
  - `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Services/ChatbotAPI/ChatbotAPIModels.swift`
- Composer, mention, agent skill picker:
  - `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/RoomScreen/ComposerToolbar/ComposerToolbarViewModel.swift`
  - `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/RoomScreen/ComposerToolbar/CompletionSuggestionService.swift`
- iOS stream/card visual parity:
  - `/Users/Ruihan/go/src/unseal-agent-ios/`

## Feature List

### P0: Room API Client

Android must expose a single room-level client interface for:

- `getRoomAgents(roomId)`
- `listAgents()`
- `listSchedules(roomId)`
- `listRoomAgentSkills(roomId, agentId, runtimeOwnerUserId)`
- `listAgentSkills(botName)` as legacy fallback
- `listWebhookTriggers(roomId)`
- `getRoomWorkingMemory(roomId)`

Rules:

- All calls use existing models in `libraries/chatbot/api` unless the model is missing required iOS fields.
- The client returns domain models for room presentation, not raw service responses.
- Failures are captured per resource so one failed call does not blank the whole room context.

### P0: Room Member / Agent Data

Implement the iOS member enrichment rules:

- Load Matrix members from the room.
- Load room agents from `getRoomAgents(roomId)`.
- Match room agent `user_id`/`mxid` to Matrix member user ID.
- Only joined Matrix members can become agents.
- If room-agent `membership` exists and is not `join`, ignore that agent marker.
- Missing `userType` defaults to `agent`.
- `isAgent` is true for `agent`, `bot`, `external_bot`, and `trusted_external_bot`.

Derived room fields:

- `roomAgents`
- `hasAgentInRoom`
- `deviceAgentInRoom`
- `activeScheduleCount`
- `agentMemberIds`
- `directAgentDescriptor`

Device-agent rule:

- Match iOS metadata semantics: `metadata["agent_kind"] == "device"` and `metadata["bound_device_id"]` exists.

### P0: Schedules / Topbar State

Android room topbar must use the same context as the rest of the room:

- Schedule button is visible only when an agent is in the room.
- Badge count is enabled schedules count.
- Terminal / device-agent chat actions show only when a device agent exists.
- Existing `RoomScheduleBadgePresenter` must stop duplicating agent/schedule requests and consume `RoomUnsealContext`.
- Room context is owned by `RoomUnsealContextStore` in RoomScope so presenter, topbar, and composer share one loaded snapshot.

### P0: Composer Mention Suggestions

Match iOS suggestion data behavior:

- `@` suggestions come from enriched joined members.
- Own user is excluded.
- Agent members show an Agent badge.
- `@room` appears only when power levels allow room notification and the room is not direct one-to-one.
- `#` room suggestions remain supported.
- Android may keep existing slash/emoji features, but they must not bypass agent mention logic.

Target model:

- `ComposerSuggestionRenderModel`
- Fields: `id`, `displayName`, `subtitle`, `avatar`, `kind`, `isAgent`, `insertPayload`.
- `MessageComposerState.suggestionRenderModels` is the UI-facing list.
- `MessageComposerState.suggestions` remains the insertion-facing list for the existing text composer.

### P0: Composer Agent Skill Flow

Match iOS skill target behavior:

- Mentioning an agent opens or updates the skill picker.
- In direct rooms, slash-trigger skill picker resolves the direct agent.
- Known room-agent descriptors come from `RoomUnsealContext`.
- Runtime skills load through `listRoomAgentSkills(roomId, agentId, runtimeOwnerUserId)`.
- If runtime-visible skills are unavailable, fallback to `listAgentSkills(botName)`.
- Sending a message preserves selected agent skills and target agent descriptors.

### P0: Stream SDK Lifecycle

Android UI must not own SSE logic.

- Same `streamId` reuses the same SDK handle.
- UI listener cancellation does not cancel background fetch/store.
- Completed stream in memory/store renders completed content on first frame.
- No `Thinking...` text for completed cached streams.
- No completed tool part may remain in running/input-only state.
- State changes emit immediately.
- Content-only patch changes can be throttled at 300-500ms.

If these invariants fail, fix Stream SDK or Android wrapper, not individual Compose cards.

### P0: Timeline Presentation Model

Add `TimelinePresentationModel` for row layout decisions:

- sender
- avatar
- sender label
- grouping
- alignment
- content max width
- bubble policy
- timestamp policy
- edited policy

iOS parity rules:

- AI stream and markdown are independent content blocks, not normal large chat bubbles.
- Avatar must live in its own column and must not overlap the card/bubble.
- Other-user AI messages preserve right-side breathing space.
- Self messages remain right-aligned.
- Edited label is hidden for stream messages and shown for normal edited messages.

### P0: AI Stream Render Model

Add `AiStreamRenderModel` derived from SDK snapshots:

- `streamId`
- `status`
- `updatedAt`
- `completedAt`
- `renderVersion`
- `visibleParts`
- `markdownBlocks`
- `toolCardEntries`
- `firstToolPartIndex`
- `isStreaming`
- `cursorMode`

Rules:

- Root tool card is inserted once at the first visible tool position.
- Hidden/ignored tool part rules must match iOS `ToolGroupUtils` semantics.
- Original tool parts are not rendered again below the root card.
- Raw stream events can remain available for diagnostics but are not normal UI input.

### P0: Tool Call Root Render Model

Add `ToolCallRootRenderModel`:

- `id`
- `name`
- `displayName`
- `state`
- `cardType`
- `props`
- `error`
- `selectedIndex`
- `counts`

Rules:

- Tool card props are transformed before Compose rendering.
- Compose must not parse raw JSON payloads.
- Empty or unsupported props must not create a large empty card.
- Unsupported tool types fall back to a compact diagnostic card, not raw JSON.

### P0: Markdown Render

Markdown render must match iOS behavior where possible:

- Links clickable.
- Lists, bold, headings, quotes, inline code, and code blocks supported.
- Completed markdown is cached with an LRU keyed by stable content.
- Streaming markdown can re-render, but heavy parsing must not happen on the main thread.
- Loading state uses cursor/loading animation, not a visible `Thinking...` message.

### P0: Tool Cards

All cards must be listed in the manifest and tested with real agent output.

Required first wave:

- Weather
- Finance
- News
- Web Search
- Shopping
- Places
- Hotels
- Files
- Email
- Drive
- GitHub
- Schedule
- Approval / suspended tool

Per-card rules:

- Weather: location, date, current conditions, feels-like, wind, humidity, UV, forecast, selected day details.
- Finance: price, change color, chart, second-level tabs `News / Financials / Stats`.
- News/Search: title, summary, source/domain, time, disclosure/link.
- Shopping: image, product name, brand/store, rating, price color, consistent row spacing.
- Places/Hotels: image, name, rating, reviews, location, price, amenity chips, map action, horizontal image/gallery support.
- Files: icon/type color, full enough filename, size/date, disclosure.
- Email/Drive: meaningful extracted content, not raw JSON.
- Approval/suspended: display state now; interactive host callback can be a separate task, but manifest must list missing parity.

### P0: Room Menus

Create `RoomMenuRenderModel` for:

- topbar actions
- attachment menu
- message long-press actions
- reactions
- read receipts
- link actions

Topbar actions:

- call
- video
- schedules
- terminal
- device-agent chat
- room info/settings

Long-press actions:

- reply
- thread
- pin/unpin
- report
- view source
- select text
- translate
- save/unsave
- share
- save media

### P1: Room UI Details

After P0 data parity:

- Mention picker floats above composer, with max height around 4.5 rows.
- Attachment menu order and icons match iOS manifest.
- Date divider, new message divider, typing indicator, encryption notice, read receipt behavior are compared and adjusted.
- Voice/game/poll interactions are listed and migrated after stream/markdown are stable.

## Implementation Tasks

### Task 1: Create iOS Parity Manifest

**Files:**

- Create: `docs/ios-room-parity-manifest.md`

Steps:

- [ ] Add all P0/P1 features listed above.
- [ ] For each feature, record iOS source file, Android source file, input data, target model, current gap, and test scenario.
- [ ] Commit with `docs: add ios room parity manifest`.

### Task 2: Add Room Unseal Data Client

**Files:**

- Create: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/roomdata/RoomUnsealDataClient.kt`
- Create: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/roomdata/DefaultRoomUnsealDataClient.kt`
- Test: `features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/roomdata/DefaultRoomUnsealDataClientTest.kt`

Steps:

- [ ] Write tests with `FakeChatbotApiService`.
- [ ] Implement the wrapper using `ChatbotApiServiceFactory.createForHomeserver(matrixClient)`.
- [ ] Return domain-level results and partial failures.
- [ ] Run `./gradlew :features:messages:impl:testDebugUnitTest --tests '*DefaultRoomUnsealDataClientTest'`.
- [ ] Commit with `feat(messages): add room unseal data client`.

### Task 3: Add Member Enrichment and Room Context

**Files:**

- Create: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/roomdata/RoomAgentMemberEnricher.kt`
- Create: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/roomdata/RoomUnsealContext.kt`
- Create: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/roomdata/RoomUnsealContextPresenter.kt`
- Test: `features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/roomdata/RoomAgentMemberEnricherTest.kt`
- Test: `features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/roomdata/RoomUnsealContextPresenterTest.kt`

Steps:

- [ ] Test iOS enrichment rules.
- [ ] Test schedule/device-agent derivation.
- [ ] Hook context into `MessagesPresenter`.
- [ ] Run roomdata unit tests.
- [ ] Commit with `feat(messages): derive room unseal context`.

### Task 4: Move Schedule Badge and Topbar State to Room Context

**Files:**

- Modify: `features/roomschedules/impl/src/main/kotlin/io/element/android/features/roomschedules/impl/room/RoomScheduleBadgePresenter.kt`
- Modify: messages topbar/menu presenter files discovered in Task 1.
- Test: existing schedule badge presenter tests plus new context-driven tests.

Steps:

- [x] Replace duplicated `listAgents/listSchedules` logic with context state for messages topbar.
- [x] Keep behavior identical when context loading or failed.
- [x] Verify schedules badge only appears when an agent is in the room.
- [x] Commit with `feat(messages): add room menu render model` (`399ead4a42`).

### Task 5: Composer Mention and Skill Picker Data Parity

**Files:**

- Modify: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/messagecomposer/MessageComposerPresenter.kt`
- Modify: suggestion processor/view files found in Task 1.
- Test: composer suggestion and skill picker tests.

Steps:

- [ ] Add `ComposerSuggestionRenderModel`.
- [ ] Add agent badge support.
- [ ] Use enriched room members for `@` suggestions.
- [ ] Use room-agent descriptors for skill target resolution.
- [ ] Prefer room-agent runtime skill API, fallback legacy agent skills.
- [ ] Commit with `feat(messages): align composer agent suggestions with ios`.

### Task 6: Stabilize Timeline and Stream Render Models

**Files:**

- Modify: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/AndroidAgentStreamAdapters.kt`
- Modify: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/TimelineItemAiPresenter.kt`
- Modify: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/model/event/TimelineItemAiContent.kt`
- Test: existing stream presenter/reducer tests.

Steps:

- [x] Add `AiStreamRenderModel`.
- [x] Add `ToolCallRootRenderModel`.
- [x] Ensure completed cached streams render completed state immediately.
- [ ] Ensure no Compose card parses raw stream JSON.
- [x] Run stream render tests.
- [x] Commit with `feat(messages): add ai stream render model` (`125bdae949`) and `fix(messages): avoid rebinding completed stream cache` (`2b9ea29e3d`).

### Task 7: Timeline Layout Parity

**Files:**

- Modify: `TimelineItemEventRow.kt`
- Modify: `MessageEventBubble.kt`
- Modify: `TimelineEventTimestampView.kt`
- Test: timeline presentation tests.

Steps:

- [x] Add `TimelinePresentationModel`.
- [x] Remove AI outer large bubble through standalone row policy. Markdown standalone parity remains pending.
- [x] Keep avatar column stable for AI stream rows.
- [x] Add adaptive right-side breathing space.
- [x] Keep edited label hidden for stream messages.
- [x] Commit with `feat(messages): add timeline presentation model` (`190027de90`).

### Task 8: Tool Card Parity Pass

**Files:**

- Modify: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/*`
- Test: card adapter/dispatcher tests.

Steps:

- [ ] Add fixtures for weather, finance, news, search, shopping, places, hotels, files, email, drive.
- [ ] Verify each fixture maps to typed props.
- [ ] Remove raw JSON and large empty fallback cards.
- [ ] Add finance second-level tabs.
- [ ] Fix gallery/horizontal image scrolling for hotels/places.
- [ ] Commit with `feat(messages): align ai tool cards with ios data`.

### Task 9: Performance and Cache Verification

**Files:**

- Modify stream handle/store/cache files only if tests prove gaps.
- Modify markdown cache and card memoization files as needed.

Steps:

- [ ] Add tests proving listener cancel does not cancel background store.
- [ ] Add tests proving completed stream is served from memory/store without fetch.
- [ ] Add memoization by `renderVersion` for tool props transforms.
- [ ] Run relevant unit tests.
- [ ] Commit with `fix(messages): prevent repeated stream loading in timeline`.

### Task 10: Manual Device Acceptance

Steps:

- [ ] Build with `./gradlew :app:assembleGplayDebug`.
- [ ] Install to device with adb.
- [ ] Use real agent to generate every P0 card type.
- [ ] Capture logs for stream handle reuse and completed cache hits.
- [ ] Compare screenshots against iOS for timeline spacing, markdown, and cards.
- [ ] Update `HANDOFF_AGENT_MANAGEMENT.md` with final architecture, known gaps, and commands.
- [ ] Commit docs with `docs: update room stream migration handoff`.

## Acceptance Criteria

- Android room features read the same logical data as iOS before rendering.
- `RoomUnsealContext` is the single room-level source for agent/schedule/device/skill/menu state.
- Stream SDK is the only stream lifecycle and SSE state-machine owner.
- Completed streams never flash loading/running when memory/store has final data.
- AI stream messages render as independent card/text content, not a large normal bubble.
- Tool cards do not show raw JSON to users.
- Mention suggestions identify agents.
- Schedules/device-agent topbar actions are derived from room context.
- Fast timeline scrolling does not trigger duplicate stream fetches for completed streams.

## Assumptions

- iOS is the visual and semantic source of truth.
- Android can keep platform-native Compose implementation details.
- Existing dirty work was checkpointed in `b5285d4075` and must be preserved.
- This plan does not migrate Agent Management, Credits, standalone Webhook pages, or standalone Vault pages except where their room entry points affect room behavior.

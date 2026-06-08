# Room Schedules Design

Date: 2026-06-09

## Feature Boundary

Feature name: `room-schedules`.

User-visible goal: Android users can open a room's AI configuration from the room screen, view and manage scheduled Agent tasks for that room, and view or edit the room working memory, matching the iOS Room Schedules flow.

Dependency class: native Android implementable.

Blocked by SDK artifact work: no.

Blocked by component library migration: no.

Out of scope:

- Webhook triggers. Those belong to `webhook-triggers`.
- Credits, connectors, and billing entry points.
- Agent creation/editing. Schedule creation may list Agents and validate room membership, but Agent CRUD belongs to `agent-management`.
- MiniApp execution, local Agent runtime, vault, sandbox, voice, and rich AI message rendering.
- Matrix Rust SDK verification, backup, or room-key recovery work.
- Background scheduling on the Android device. The schedule is created through the Chatbot API, as on iOS.

## iOS Source References

Primary iOS files:

- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/RoomSchedules/RoomSchedulesScreen/RoomSchedulesScreenCoordinator.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/RoomSchedules/RoomSchedulesScreen/RoomSchedulesScreenModels.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/RoomSchedules/RoomSchedulesScreen/RoomSchedulesScreenViewModel.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/RoomSchedules/RoomSchedulesScreen/View/RoomSchedulesScreen.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/RoomSchedules/RoomSchedulesScreen/View/ScheduleListRow.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/RoomSchedules/ScheduleEditScreen/ScheduleEditScreenCoordinator.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/RoomSchedules/ScheduleEditScreen/ScheduleEditScreenModels.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/RoomSchedules/ScheduleEditScreen/ScheduleEditScreenViewModel.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/RoomSchedules/ScheduleEditScreen/View/ScheduleEditScreen.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/RoomSchedules/ScheduleEditScreen/View/CronPickerView.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Other/CronParser.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/RoomScreen/RoomScreenModels.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/RoomScreen/RoomScreenViewModel.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/RoomScreen/View/RoomScreen.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/FlowCoordinators/RoomFlowCoordinator.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Services/ChatbotAPI/ChatbotAPIClient.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Services/ChatbotAPI/ChatbotAPIModels.swift`

Core Swift types:

- `RoomAIConfigTab`
- `RoomSchedulesScreenViewState`
- `RoomSchedulesScreenViewAction`
- `RoomSchedulesScreenViewModelAction`
- `RoomSchedulesScreenViewModel`
- `RoomSchedulesScreenCoordinator`
- `ScheduleEditMode`
- `ScheduleEditScreenViewState`
- `ScheduleEditScreenViewAction`
- `ScheduleEditScreenViewModelAction`
- `ScheduleEditScreenViewModel`
- `ScheduleEditScreenCoordinator`
- `CronParser`
- `CronPickerMode`
- `CronPickerModel`
- `ChatbotSchedule`
- `ChatbotCreateScheduleRequest`
- `ChatbotUpdateScheduleRequest`

Important iOS behaviors to preserve:

- The room screen loads schedules and agents together, sets `activeScheduleCount` to the number of enabled schedules, and sets `hasAgentInRoom` true only when at least one Agent Matrix ID is a joined member of the room.
- The schedules button is shown only when `hasAgentInRoom` is true.
- The schedules button shows a badge with `activeScheduleCount` when the count is greater than zero.
- Opening schedules presents a sheet with its own inner navigation stack.
- The sheet root is the Room AI configuration screen with two tabs: `schedules` and `workingMemory`.
- On first appear, the Room AI configuration screen starts three independent tasks: load schedules, load working memory, and check whether the user can edit working memory.
- Pull-to-refresh reloads schedules when the schedules tab is active and working memory when the working memory tab is active.
- Schedules display rules:
  - hide disabled schedules not created by the current user;
  - if "Mine" is enabled, show only schedules created by the current user;
  - sort enabled schedules before disabled schedules;
  - active count is the number of enabled schedules in the displayed list.
- Schedule row actions are owner-only, where owner means `schedule.creatorId == currentUserID`.
- Owner row actions are edit, enable/disable, and delete.
- Enable/disable is optimistic. It updates the local row status to `"enabled"` or `"disabled"` first, calls `updateScheduleStatus`, reloads schedules on failure, and shows an error.
- Delete asks for confirmation, calls `deleteSchedule`, and removes the deleted schedule locally on success.
- Creating or editing a schedule is pushed inside the sheet inner stack.
- After a schedule is saved, the edit screen pops, the schedule list reloads, and the room screen active schedule count reloads.
- After the schedules sheet is dismissed, the room screen active schedule count reloads.
- Create mode:
  - editable name field;
  - editable Agent picker;
  - selected Agent defaults to the first loaded Agent when none is selected;
  - warns when the selected Agent is not currently a joined room member;
  - submit blocks when the selected Agent is known not to be in the room;
  - submit builds full Matrix Agent ID from `localpart` and `serverName`, falling back to `botName`;
  - sends `agentId`, `name`, `cron`, `action`, `timezone`, and `roomId`.
- Edit mode:
  - name is displayed read-only;
  - Agent is displayed read-only;
  - only cron, action, and timezone are sent to update.
- Validation blocks empty trimmed name, empty trimmed action, and missing selected Agent.
- iOS treats Chatbot API HTTP 500 or HTTP 200 error wrappers during create as saved because of a server bug. Android should preserve this create-mode leniency if its existing error model can identify those cases.
- Working memory:
  - loads from `getRoomWorkingMemory(roomId)`;
  - displays empty, read-only, and edit states;
  - edit mode copies current memory into a text editor;
  - save calls `updateRoomWorkingMemory(roomId, content)`, updates the read-only content, and exits edit mode on success;
  - cancel exits edit mode and clears the editing text;
  - edit permission defaults to true when power-level data cannot be loaded, letting the server enforce permission errors.

Cron behavior to preserve:

- EventBridge cron format is `cron(min hour day month dow year)`.
- Default picker model is every day at 09:00, interval 2 hours, weekday Monday.
- Modes:
  - workdays: `cron(mm hh ? * 2-6 *)`;
  - every day: `cron(mm hh ? * * *)`;
  - every N hours: `cron(0 */N ? * * *)`;
  - every hour at minute: `cron(mm */1 ? * * *)`;
  - weekday: `cron(mm hh ? * dow *)`.
- Minute choices in the UI are 0, 5, 10, ..., 55.
- Hour choices are 0 through 23.
- Interval choices are 1, 2, 3, 4, 6, 8, and 12.
- Weekday UI order is Monday, Tuesday, Wednesday, Thursday, Friday, Saturday, Sunday, with iOS numeric values 2, 3, 4, 5, 6, 7, 1.
- Readable formatting:
  - `*/N` hour means `Every Nh` plus ` at :mm` when minute is not zero;
  - `*` or `?` day-of-week means `Every day at HH:mm`;
  - `2-6` means `Mon-Fri`;
  - comma lists render as day names joined by comma;
  - unknown or malformed expressions fall back to the original expression or default picker model.

## Android Existing State

Existing Android modules and files:

- `libraries/chatbot/api/src/main/kotlin/io/element/android/libraries/chatbot/api/ChatbotApiService.kt`
- `libraries/chatbot/api/src/main/kotlin/io/element/android/libraries/chatbot/api/model/schedules/ScheduleModels.kt`
- `libraries/chatbot/api/src/main/kotlin/io/element/android/libraries/chatbot/api/model/rooms/RoomModels.kt`
- `libraries/chatbot/impl/src/main/kotlin/io/element/android/libraries/chatbot/impl/DefaultChatbotApiService.kt`
- `libraries/chatbot/test/src/main/kotlin/io/element/android/libraries/chatbot/test/FakeChatbotApiService.kt`
- `libraries/chatbot/test/src/main/kotlin/io/element/android/libraries/chatbot/test/ChatbotFixtures.kt`
- `libraries/matrix/api/src/main/kotlin/io/element/android/libraries/matrix/api/MatrixClient.kt`
- `libraries/matrix/api/src/main/kotlin/io/element/android/libraries/matrix/api/room/JoinedRoom.kt`
- `libraries/matrix/api/src/main/kotlin/io/element/android/libraries/matrix/api/room/BaseRoom.kt`
- `libraries/matrix/api/src/main/kotlin/io/element/android/libraries/matrix/api/room/RoomMember.kt`
- `libraries/matrix/api/src/main/kotlin/io/element/android/libraries/matrix/api/room/powerlevels/RoomPermissions.kt`
- `libraries/matrix/test/src/main/kotlin/io/element/android/libraries/matrix/test/room/FakeJoinedRoom.kt`
- `features/messages/api/src/main/kotlin/io/element/android/features/messages/api/MessagesEntryPoint.kt`
- `features/roomdetailsedit/impl/src/main/kotlin/io/element/android/features/roomdetailsedit/impl/RoomDetailsEditPresenter.kt`
- `features/agentmanagement/api/src/main/kotlin/io/element/android/features/agentmanagement/api/AgentManagementEntryPoint.kt`
- `features/skills/api/src/main/kotlin/io/element/android/features/skills/api/SkillsEntryPoint.kt`

Existing aligned pieces:

- `ChatbotApiService` already exposes `listSchedules`, `createSchedule`, `updateSchedule`, `updateScheduleStatus`, `deleteSchedule`, `getRoomWorkingMemory`, `updateRoomWorkingMemory`, and `listAgents`.
- `DefaultChatbotApiService` already maps schedules to `/chatbot/v1/schedules`, schedule status to `/chatbot/v1/schedules/{schedule_id}/status`, and working memory to `/chatbot/v1/rooms/{roomId}/working-memory`.
- `FakeChatbotApiService` already exposes fake lambdas for schedule and working-memory operations.
- `ChatbotFixtures.aChatbotSchedule` already exists for presenter tests.
- `MatrixClient.sessionId` is the current user ID used to calculate owner-only schedule actions.
- `JoinedRoom` exposes `roomId`, `roomInfoFlow`, `membersStateFlow`, `updateMembers()`, `getMembers()`, and `roomPermissions()`.
- `RoomPermissions.permissionsAsState` shows the existing Android pattern for deriving permissions from room power-level changes.
- `FakeJoinedRoom` can be used in focused presenter tests.

Android gaps:

- No `features/roomschedules` modules exist.
- `MessagesEntryPoint.Callback` has no schedules navigation callback.
- The Android room screen has no schedule button, active schedule badge, or has-agent-in-room state.
- There is no Android cron picker/parser equivalent for the iOS schedule UI.
- There is no Android Room AI configuration screen with schedules and working-memory tabs.

## Design Decision

Recommended approach: add a new feature family `features/roomschedules/api`, `features/roomschedules/impl`, and `features/roomschedules/test`, then wire it from the room/messages feature through a narrow callback seam.

Alternatives considered:

- Put Room Schedules inside `features/messages`. This would make room integration easy but would mix Chatbot API screens, cron editing, and working-memory state into an already broad timeline feature.
- Put Room Schedules inside `features/agentmanagement`. This is misleading because the feature is room-scoped and uses Agents only for schedule creation.
- Add only a standalone entry point and defer room-screen integration. This would be easier to compile, but it would not match iOS because iOS exposes schedules from the room screen with a badge and has-agent gating.

The new feature family keeps ownership clear, keeps the first implementation subagent-bounded, and matches the existing `features/*/{api,impl,test}` pattern used by Agent Management and Skills.

## Target Android Behavior

### Entry Points And Navigation

Add `RoomSchedulesEntryPoint`.

Inputs:

- `roomId: RoomId`
- `roomName: String`
- `joinedRoom: JoinedRoom`

Callbacks:

- `onDone()`
- `onSchedulesChanged()`

Initial target:

- `RoomAiConfig`
- `EditSchedule(mode)` for internal flow only.

Navigation behavior:

- The feature opens at Room AI configuration.
- Create schedule pushes Schedule Edit in create mode.
- Edit schedule pushes Schedule Edit in edit mode.
- Save pops Schedule Edit and reloads schedules.
- Dismiss calls `onDone()` and `onSchedulesChanged()`.
- A successful create/edit/delete/status change calls or eventually leads to `onSchedulesChanged()` so the room badge can refresh.

### Room Screen Integration

The Android room/messages feature should expose a schedules navigation callback equivalent to iOS `displaySchedules`.

Room screen behavior:

- On room screen start, load schedules and agents in parallel through `ChatbotApiService`.
- `activeScheduleCount` is the count of schedules where `isEnabled` is true.
- Determine agents in room by building each Agent Matrix ID from `localpart` and `serverName`, then checking whether that user ID is a joined member of the room.
- Show the schedules button only when at least one Agent is joined in the room.
- Show a numeric badge only when `activeScheduleCount > 0`.
- Reload active schedule count and has-agent state after the Room Schedules sheet/screen is dismissed or after a schedule is saved.
- Failures while loading the badge state should not block the room timeline. They should hide schedules if agent presence cannot be proven and leave the active count at zero.

### Room AI Configuration Screen

State:

- room ID and room name
- current user ID
- selected tab: schedules or working memory
- schedules list
- loading schedules flag
- show-only-mine flag
- schedule error
- working memory content
- editing memory content
- loading memory flag
- saving memory flag
- can-edit-memory flag
- memory error
- delete confirmation target
- derived displayed schedules
- derived active count

Events:

- `OnAppear`
- `Refresh`
- `SelectTab`
- `ShowOnlyMineChanged`
- `CreateSchedule`
- `EditSchedule`
- `ToggleSchedule`
- `RequestDeleteSchedule`
- `ConfirmDeleteSchedule`
- `DismissDeleteConfirmation`
- `RefreshMemory`
- `StartEditingMemory`
- `EditingMemoryChanged`
- `CancelEditingMemory`
- `SaveMemory`
- `ClearError`
- `Dismiss`

Load behavior:

- On first appear, load schedules, load working memory, and check working-memory edit permission.
- Refresh reloads the active tab.
- Schedule loading failures preserve existing schedule data and surface a retryable error.
- Working-memory loading failures preserve existing content and surface a retryable error.
- Permission check defaults `canEditMemory = true` if room permissions cannot be loaded, matching iOS.

Schedules tab behavior:

- Displayed schedules are `schedules.filter { it.isEnabled || it.creatorId == currentUserId }`.
- When show-only-mine is true, further filter to `creatorId == currentUserId`.
- Sort enabled schedules before disabled schedules.
- Empty state is shown when the displayed list is empty.
- Row action controls are shown only for owner rows.
- Enable/disable uses optimistic local status update, calls `updateScheduleStatus(scheduleId, "enabled" | "disabled")`, reloads schedules on failure, and surfaces the failure.
- Delete requires confirmation, calls `deleteSchedule(scheduleId)`, and removes the matching schedule from local state on success.
- The schedule identity fallback is `schedule.scheduleId ?: schedule.name`, matching iOS.

Working-memory tab behavior:

- Read-only state shows monospaced content.
- Empty state allows adding working memory only when `canEditMemory` is true.
- Edit state copies `workingMemory` into `editingMemoryText`.
- Save calls `updateRoomWorkingMemory(roomId, editingMemoryText)`, sets `workingMemory = editingMemoryText`, exits edit state, and clears the edit buffer.
- Cancel exits edit state and clears the edit buffer.
- Save failures keep edit mode and preserve the edit buffer.

### Schedule Edit Screen

State:

- mode: create or edit
- room ID
- schedule being edited, if any
- loaded Agents
- joined room member IDs
- submitting flag
- editable name
- selected Agent bot name
- editable action
- cron picker model
- error
- derived title
- derived selected-agent-in-room flag

Events:

- `OnAppear`
- `NameChanged`
- `AgentChanged`
- `ActionChanged`
- `CronModelChanged`
- `Submit`
- `Cancel`
- `ClearError`

Load behavior:

- On first appear, load Agents through `listAgents()` and joined room members through `JoinedRoom`.
- If create mode has no selected Agent and agents are available, select the first Agent.
- Edit mode seeds name, selected Agent, action, and cron model from the schedule.

Validation:

- Trimmed name must not be empty in create mode.
- Trimmed action must not be empty.
- Selected Agent bot name must not be empty.
- If joined room member IDs are known and the selected Agent can be resolved to a Matrix ID, block submit when that Matrix ID is not joined.
- If room members are not loaded or Agent Matrix ID cannot be resolved, allow submit and let the server enforce errors, matching iOS's permissive unknown-members behavior.

Submit behavior:

- Create mode builds `ChatbotCreateScheduleRequest` with:
  - `agentId`: full Matrix ID `@localpart:serverName` when available, otherwise selected `botName`;
  - trimmed `name`;
  - cron from the picker model;
  - trimmed `action`;
  - `timezone`: Android system default time zone ID;
  - `roomId`.
- Edit mode builds `ChatbotUpdateScheduleRequest` with cron, trimmed action, and system time zone ID.
- Create mode should treat the known iOS server-bug cases as success when possible: HTTP 500 or HTTP 200 error wrappers from `ChatbotApiService` during create should navigate as saved. If the Android error type cannot expose both cases, preserve all identifiable leniency and document the remaining gap in the implementation plan.
- Save success emits a saved navigation action.
- Save failure keeps the user on the form and surfaces the error.

### Cron Parser And Picker

Add Android cron helpers that mirror iOS `CronParser`.

Required API:

- `CronPickerMode`
- `CronPickerModel`
- `CronParser.toReadable(expression: String): String`
- `CronParser.toCron(model: CronPickerModel): String`
- `CronParser.toPickerModel(expression: String): CronPickerModel`

The parser must preserve the iOS mappings listed in the iOS Source References section and include focused unit tests for each mode and malformed input.

## Data And API Mapping

| iOS | Android |
| --- | --- |
| `ChatbotAPIClient.listSchedules(roomId:)` | `ChatbotApiService.listSchedules(roomId)` |
| `ChatbotAPIClient.createSchedule(_:)` | `ChatbotApiService.createSchedule(request)` |
| `ChatbotAPIClient.updateSchedule(scheduleId:request:)` | `ChatbotApiService.updateSchedule(scheduleId, request)` |
| `ChatbotAPIClient.updateScheduleStatus(scheduleId:status:)` | `ChatbotApiService.updateScheduleStatus(scheduleId, status)` |
| `ChatbotAPIClient.deleteSchedule(scheduleId:)` | `ChatbotApiService.deleteSchedule(scheduleId)` |
| `ChatbotAPIClient.getRoomWorkingMemory(roomId:)` | `ChatbotApiService.getRoomWorkingMemory(roomId)` |
| `ChatbotAPIClient.updateRoomWorkingMemory(roomId:content:)` | `ChatbotApiService.updateRoomWorkingMemory(roomId, content)` |
| `ChatbotAPIClient.listAgents()` | `ChatbotApiService.listAgents()` |
| `RoomSchedulesScreenViewState.displayedSchedules` | `RoomSchedulesState.displayedSchedules` |
| `ScheduleEditScreenBindings.cronModel` | `ScheduleEditState.cronModel` |
| `CronParser` | Android `CronParser` helper |

Schedule enabled mapping:

- Android should add a local extension or formatter equivalent to iOS `ChatbotSchedule.isEnabled`.
- A schedule is enabled when `enabled == true` or `status == "enabled"`.
- A schedule is disabled when `enabled == false` or `status == "disabled"`.
- `status` takes precedence over `enabled`.
- If both `status` and `enabled` are missing, default to disabled, matching iOS `enabled ?? false`.

Room member mapping:

- `ChatbotAgent.localpart + serverName` maps to `@localpart:serverName`.
- Joined room members come from `JoinedRoom.membersStateFlow` after `updateMembers()` or from `getMembers()` if the current Android pattern makes that simpler.
- Only `RoomMembershipState.JOIN` counts as joined for Agent presence and schedule submit validation.

Persistence:

- No new local persistence.
- Presenter state is refreshed from Matrix room state and Chatbot API calls.

Pagination:

- Not applicable for schedules in this spec.

Error model:

- Use existing `Result` errors from `ChatbotApiService`.
- Preserve loaded data on reload failures.
- Separate schedule errors from memory errors where useful so a memory failure does not hide schedule data.
- Use explicit confirmation state for destructive delete.

## Android UI Requirements

The first Android UI should use native Compose and existing Element X components.

Room screen integration:

- Add an icon button for schedules in the same control cluster where iOS shows terminal/laptop/schedules room actions.
- Show the active count badge only when the count is greater than zero.
- Hide the button when no Agent is joined in the room.

Room AI configuration:

- Top title equivalent to `Room AI Config`, with room name as subtitle.
- Dismiss/back action.
- Segmented control or equivalent for `Schedules` and `Working Memory`.
- Schedules tab:
  - loading state;
  - empty state with add action;
  - active count text;
  - Mine toggle;
  - add schedule bottom action;
  - rows with status dot, name, readable cron, one-line action, and owner-only actions.
- Working memory tab:
  - loading state;
  - empty state;
  - monospaced read-only content;
  - monospaced edit field;
  - refresh action when not editing;
  - cancel/save actions when editing;
  - edit/add button only when `canEditMemory` is true.
- Schedule edit:
  - create title and edit title;
  - name editable only in create mode;
  - Agent picker editable only in create mode;
  - warning when selected Agent is not in room;
  - multiline action field;
  - cron controls for all five iOS modes;
  - submit button with loading state.

Text may use English literals for the first implementation pass. Localization is not part of this spec unless required by existing lint or compile rules.

## Testing Requirements

Focused unit tests should cover:

- displayed schedule filtering: hidden disabled schedules from other users, show-only-mine, enabled-first sorting;
- active count;
- owner-only action derivation;
- first appear loads schedules, working memory, edit permission, Agents, and members as appropriate;
- schedule toggle optimistic update and failure rollback/reload;
- delete confirmation and successful local removal;
- working memory edit, cancel, save success, and save failure preserving edit buffer;
- schedule create validation for empty name, empty action, and missing Agent;
- selected Agent room-membership warning and submit block when members are known;
- create request builds full Matrix Agent ID when possible and bot name fallback otherwise;
- edit request sends only cron/action/timezone;
- create-mode server-bug leniency for identifiable HTTP 500/HTTP 200 error wrappers;
- cron parser `toCron`, `toReadable`, and `toPickerModel` for every mode plus malformed input;
- room badge presenter/state: has-agent-in-room, active count, failure behavior.

Recommended verification commands after implementation:

```sh
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:roomschedules:impl:testDebugUnitTest
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:roomschedules:impl:compileDebugKotlin
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:messages:impl:compileDebugKotlin
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :libraries:chatbot:impl:testDebugUnitTest
```

Dependency scan:

```sh
rg -n "rustsdk|org\\.matrix\\.rust|voiceplayer|voicerecorder|vault|Vault|sandbox|Sandbox|UnsealUI|UnsealAgent|UnsealMiniApp|miniapp|MiniApp" features/roomschedules features/messages -S
```

Expected dependency scan result:

- `features/roomschedules` must not reference Rust SDK generated symbols, voice player/recorder, vault, sandbox, MiniApp, or Unseal component libraries.
- `features/messages` may keep existing unrelated dependencies, but any new schedules-related files must not introduce those forbidden dependencies.

## Implementation Boundary For Subagent

The implementation plan should split work into small commits:

1. Add `features/roomschedules/api`, `impl`, and `test` shells and entry point.
2. Add cron parser/model tests and implementation.
3. Add Room AI configuration presenter/state/events with schedule and working-memory behavior.
4. Add Schedule Edit presenter/state/events.
5. Add Compose screens and Appyx flow nodes.
6. Wire Messages/room screen entry point and badge state.
7. Run final verification and dependency scan.

Files that may be edited:

- `features/roomschedules/**`
- `features/messages/**`
- `appnav/**` only if required for entry-point wiring
- DI module lists or navigation files required to expose the new feature
- `docs/superpowers/plans/2026-06-09-room-schedules.md` after the spec is accepted and the writing-plans workflow starts

Files that should not be edited for this spec:

- Matrix Rust SDK source or Gradle artifact selection
- `libraries/rustsdk/**`
- Voice, vault, sandbox, MiniApp, and rich-renderer modules
- Chatbot API endpoint definitions unless implementation discovers a mismatch with the already existing schedule/working-memory methods

## Acceptance Criteria

1. `docs/superpowers/specs/2026-06-09-room-schedules-design.md` exists and is the source of truth for this feature.
2. A later implementation plan exists at `docs/superpowers/plans/2026-06-09-room-schedules.md` before implementation begins.
3. `RoomSchedulesEntryPoint` opens Room AI configuration for a specific joined room.
4. Room screen integration exposes the schedules action only when an Agent is joined in the room.
5. Room screen integration displays active schedule count when greater than zero and refreshes it after schedule changes or dismiss.
6. The schedules tab loads, refreshes, filters, sorts, toggles, deletes, and creates/edits schedules according to iOS behavior.
7. The working-memory tab loads, refreshes, edits, saves, cancels, and handles permission fallback according to iOS behavior.
8. Schedule create/edit validation and request construction match iOS.
9. Cron parser and picker behavior match iOS for every supported mode.
10. Schedule create preserves iOS server-bug leniency for identifiable HTTP 500/HTTP 200 error wrappers.
11. Focused unit tests cover presenter behavior, cron conversion, room badge state, and working memory.
12. `:features:roomschedules:impl:testDebugUnitTest` passes.
13. `:features:roomschedules:impl:compileDebugKotlin` passes.
14. `:features:messages:impl:compileDebugKotlin` passes after room integration.
15. `:libraries:chatbot:impl:testDebugUnitTest` passes.
16. No Rust SDK, voice, vault, sandbox, MiniApp, or Unseal component-library dependency is introduced by this feature.

## Next Spec

After `room-schedules` is implemented and verified, the next native Android implementable feature in the migration index is `webhook-triggers`.

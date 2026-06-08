# Webhook Triggers Migration Design

Feature name: `webhook-triggers`.

User-visible goal: Android users can manage Unseal webhook triggers from a global management screen or a room-scoped screen, matching the iOS list and edit flows for browsing, searching, filtering, creating, editing, enabling/disabling, drafting, and deleting triggers.

Dependency class: native Android implementable.

## Scope

In scope:

- Global trigger list with search, optional room filter, event catalog loading, create/edit navigation, enable/disable, and delete confirmation.
- Room trigger list with fixed room id, agent filter populated from `getRoomAgents(roomId)`, create/edit navigation, enable/disable, and delete confirmation.
- Trigger create/edit screen with event source selection, event type toggling, connected account selection, room selection, agent selection for create, draft generation, validation, create, update, and save completion.
- Feature entry point, fake entry point, Appyx flow, presenters, Compose views, and presenter tests.
- Direct use of existing `ChatbotApiService` webhook, connector, room-agent, and Matrix room-list APIs.

Out of scope:

- Dedicated connector management screen implementation. Android can expose connect URL and refresh connection state, but full Composio management remains the `connectors` feature.
- WebView/OAuth callback UI integration beyond a host callback seam.
- Webhook payload/config-schema editor.
- Voice, vault, sandbox, MiniApp, Rust SDK changes, or missing Unseal component-library dependencies.

## iOS Reference

Primary files:

- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/WebhookTriggers/WebhookTriggerListScreen/WebhookTriggerListScreenModels.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/WebhookTriggers/WebhookTriggerListScreen/WebhookTriggerListScreenViewModel.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/WebhookTriggers/WebhookTriggerEditScreen/WebhookTriggerEditScreenModels.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/WebhookTriggers/WebhookTriggerEditScreen/WebhookTriggerEditScreenViewModel.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Services/ChatbotAPI/ChatbotWebhookTriggerModels.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Services/ChatbotAPI/ChatbotAPIClient.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/FlowCoordinators/SettingsFlowCoordinator.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/FlowCoordinators/RoomFlowCoordinator.swift`

Relevant iOS behavior:

- `WebhookTriggerListMode.global` lists all triggers and can filter by selected room.
- `WebhookTriggerListMode.room(roomId, roomName)` always queries the given room and can locally filter by agent id.
- List load calls `listWebhookEventTypes()`, loads filter options, then calls `listWebhookTriggers(agentId:nil, source:nil, roomId, status:nil)`.
- List search trims whitespace and matches name, source, event types, agent id, action prompt, or room id.
- Enable/disable calls `updateWebhookTriggerStatus(triggerId, enabled: !trigger.isEnabled)` then reloads.
- Delete uses a confirmation binding, calls `deleteWebhookTrigger(triggerId)`, then reloads.
- Edit create mode can prefill room id; edit mode seeds name, description, action prompt, room id, selected event types, selected source, selected account, and selected agent.
- Event catalog comes from `listWebhookEventTypes()`.
- Connected accounts come from `listConnectedAccounts(toolkit: source.source, cursor:nil, limit:nil)`.
- Connect source calls `initiateConnection(toolkit, redirectUrl)` and shows a URL.
- Draft generation calls `draftWebhookTrigger(prompt)` and fills source, event types, name, and action prompt.
- Save create calls `createWebhookTrigger(agentId, name, description, connectionId, eventTypes, actionPrompt, roomId)`.
- Save edit calls `updateWebhookTrigger(triggerId, name, description, actionPrompt, roomId)`.

## Android Mapping

Use existing API foundation:

- `ChatbotApiService.listWebhookEventTypes()`
- `ChatbotApiService.listWebhookTriggers(agentId, source, roomId, status)`
- `ChatbotApiService.createWebhookTrigger(request)`
- `ChatbotApiService.updateWebhookTrigger(triggerId, request)`
- `ChatbotApiService.updateWebhookTriggerStatus(triggerId, enabled)`
- `ChatbotApiService.deleteWebhookTrigger(triggerId)`
- `ChatbotApiService.draftWebhookTrigger(prompt)`
- `ChatbotApiService.listConnectedAccounts(toolkit, cursor, limit)`
- `ChatbotApiService.initiateConnection(toolkit, redirectUrl)`
- `ChatbotApiService.getRoomAgents(roomId)`

Add:

- `features/webhooks/api`
- `features/webhooks/impl`
- `features/webhooks/test`

Entry point:

- `WebhookTriggersEntryPoint.InitialTarget.Global`
- `WebhookTriggersEntryPoint.InitialTarget.Room(roomId, roomName)`
- `WebhookTriggersEntryPoint.InitialTarget.Edit(mode)`

The implementation should follow the existing `features/roomschedules` and `features/skills` patterns: `FeatureEntryPoint`, `BaseFlowNode`, focused presenters, immutable state, Compose views, Metro assisted injection, and `FakeChatbotApiService` tests.

## Acceptance Criteria

- The list flow can load, search, filter, refresh, enable/disable, confirm delete, delete, and navigate to create/edit.
- The edit flow can load catalog/rooms, seed edit mode, select source/event/account/room/agent, generate draft, validate fields, create, update, and expose connect URL callback.
- Room mode queries only the given room id and loads room agents for filtering/create agent selection.
- Global mode can filter by room using Matrix `roomListService.allRooms.summaries`.
- All presenter tests pass.
- `:features:webhooks:impl:testDebugUnitTest` passes.
- `:features:webhooks:impl:compileDebugKotlin :features:webhooks:impl:compileDebugUnitTestKotlin` passes.
- `:libraries:chatbot:impl:testDebugUnitTest` passes.
- `features/webhooks` has no references to `rustsdk`, `org.matrix.rust`, `voiceplayer`, `voicerecorder`, `vault`, `Vault`, `sandbox`, `Sandbox`, `UnsealUI`, `UnsealAgent`, or `UnsealMiniApp`.

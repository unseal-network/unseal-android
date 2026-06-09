# Webhook Triggers Host Entrypoints Design

## Goal

Expose the already migrated Android Webhook Triggers feature from the same product locations as iOS:

- Global trigger management from Settings.
- Room-scoped trigger management from Room Details.

This spec is intentionally limited to host navigation and entrypoint wiring. It does not change the Webhook Trigger list/edit behavior implemented in `features/webhooks`.

## iOS Reference

- `ElementX/Sources/FlowCoordinators/SettingsFlowCoordinator.swift`
  - Presents `WebhookTriggerListScreenCoordinator` with mode `.global`.
  - Opens create/edit flows and reloads the trigger list after saves.
- `ElementX/Sources/Screens/RoomDetailsScreen/View/RoomDetailsScreen.swift`
  - Adds a `ListRow` titled `L10n.roomDetailsTriggers` with a notifications icon.
  - Sends `.processTapTriggers`.
- `ElementX/Sources/Screens/RoomDetailsScreen/RoomDetailsScreenViewModel.swift`
  - Handles `.processTapTriggers` by sending `.requestTriggersPresentation`.
- `ElementX/Sources/FlowCoordinators/RoomFlowCoordinator.swift`
  - Handles `.presentRoomTriggersScreen`.
  - Presents `WebhookTriggerListScreenCoordinator` with `.room(roomId: roomID, roomName: roomName)`.
  - Opens create with `.create(prefilledRoomId: roomID)`.
  - Reloads list after edit/create save.

## Android Target

### Settings Global Entry

Add a Settings row that opens:

```kotlin
WebhookTriggersEntryPoint.Params(
    initialTarget = WebhookTriggersEntryPoint.InitialTarget.Global,
)
```

The row should live in the Settings root, near other app-management items, and use existing `ListItem`/`PreferencePage` styling.

### Room Details Entry

Add a Room Details row for normal rooms only, matching iOS room-scoped behavior:

```kotlin
WebhookTriggersEntryPoint.Params(
    initialTarget = WebhookTriggersEntryPoint.InitialTarget.Room(
        roomId = state.roomId,
        roomName = state.roomName,
    ),
)
```

Do not show this row for DMs. iOS places this in the room detail action list, not DM profile controls.

### Connect URL Handling

`WebhookTriggersEntryPoint.Callback.onOpenConnectUrl(url)` must open the URL externally using the host module's existing browser/URL helpers. This is needed for Composio OAuth source connections launched from the edit screen.

### Save/Reload Handling

The existing Webhook feature already reloads its own list via `onTriggersChanged`. Host entrypoints should not duplicate trigger reload logic; they only need to keep the Webhook node alive and route callbacks.

## Dependencies

This spec depends on:

- `features:webhooks:api`
- Existing `features:webhooks:impl`
- Existing `features:webhooks:test` for unit tests

It must not introduce dependencies on unavailable iOS component libraries, miniapp libraries, vault/sandbox modules, or direct Matrix Rust SDK APIs.

## Acceptance Criteria

- Settings root has a visible "Webhook triggers" row that opens Webhook Triggers in global mode.
- Room Details for group rooms has a visible "Webhook triggers" row that opens Webhook Triggers in room mode with the current `RoomId` and room name.
- Room Details for DMs does not show the room-scoped row.
- Composio connect URLs emitted by the Webhook edit flow are opened externally by the host.
- Unit tests cover Settings navigation and Room Details navigation/visibility.
- The following commands pass:
  - `:features:preferences:impl:testDebugUnitTest`
  - `:features:roomdetails:impl:testDebugUnitTest`
  - `:features:preferences:impl:compileDebugKotlin`
  - `:features:roomdetails:impl:compileDebugKotlin`
  - `:app:assembleDebug`


# Encrypted Room Key Recovery Migration Design

Date: 2026-06-09

## Feature Boundary

Feature name: `encrypted-room-key-recovery`.

User-visible goal: Android should match the iOS behavior for ordinary encrypted room messages that are unable to decrypt: detect recoverable Megolm room-key failures, request missing keys from allowed sources, show recovery progress in the timeline, allow manual retry, and guide unverified users to device verification.

Out of scope:

- Agent/bot-device key recovery policy and custom agent target selection. That is `agent-room-key-recovery`.
- Secure backup setup, recovery key confirmation, and backup state management. That is `secure-backup-recovery`.
- A full visual redesign of Android timeline rows.
- Component-library-backed rich AI message rendering.
- MiniApp/runtime behavior.

Dependency class: Matrix Rust SDK dependent.

Blocked status: not blocked by missing component libraries. Matrix Rust SDK Android artifact `io.github.rayson-pagepeek.matrix.rustcomponents:sdk-android:26.06.5` exposes the required APIs: `Encryption.requestRoomKeyRecovery`, `setRoomKeyRequestsEnabled`, `setRoomKeyForwardingEnabled`, `setRoomKeyForwardingPolicy`, `RoomKeyRecoveryTarget`, `RoomKeyRecoveryScope`, `RoomKeyRecoveryProgress`, `RoomKeyRecoveryStage`, `RoomKeyForwardingPolicy`, and to-device listeners.

## iOS Source References

Primary iOS files:

- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Services/Encryption/RoomKeyRecovery.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Services/Client/ClientProxy.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Services/Client/ClientProxyProtocol.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Services/Timeline/TimelineItems/Items/Other/EncryptedRoomTimelineItem.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Services/Timeline/TimelineItems/RoomTimelineItemFactory.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Timeline/TimelineViewModel.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Timeline/View/TimelineItemViews/EncryptedRoomTimelineView.swift`

Core iOS types and behavior:

- `RoomKeyRecoveryRequest` parses event JSON for `m.megolm.v1.aes-sha2`, `room_id`, `sender`, `sender_key`, `device_id`, `session_id`, and `ciphertext`.
- `RoomKeyRecoveryTarget` identifies a user/device pair; the sender target drops `deviceId` when sender is from another homeserver or the original sender device is stale.
- `RoomKeyRecoveryDisplayStage`: `checkingDeviceVerification`, `deviceUnverified`, `backup`, `ownDevices`, `sender`, `members`, `resolved`, `failed`.
- `RoomKeyRecoveryPlan.build(isOwnMessage, canUseKeyBackup, hasMemberFallback)`:
  - Own message: `ownDevices`.
  - Other user message: `backup` if key backup can help, then `sender`.
  - Add `members` when room-member fallback targets exist.
- `RoomKeyRecoveryPendingStore` prevents automatic retry storms with a 30-minute per-request pending window.
- `RoomKeyRecoveryPlanProgressStore` persists active stage/deadline and failed state across timeline rebuilds.
- `AllowJoinedRoomKeyForwardingPolicy` allows forwarding only to requesters that are active members of the room; this is member-aware but not agent-specific.
- `TimelineViewModel.requestRoomKeysIfNeeded`:
  - Coalesces repeated UTD timeline items by room key identity and displays a count.
  - Retains only progress/pending records for currently visible recovery requests.
  - Requires verified session/device state before automatic recovery.
  - Shows `deviceUnverified` and a verify-device action when verification is unavailable.
  - Starts one active task per room-key identity.
  - Runs stages in order, waiting up to 60 seconds for each to decrypt the message.
  - Marks `resolved` when the message decrypts, and `failed` when all stages are exhausted.
  - Manual retry clears pending/progress state and restarts the plan.
- `ClientProxy.configureRoomKeyRecovery` enables room-key requests and forwarding, and installs the forwarding policy.
- `ClientProxy.requestRoomKeyRecovery` calls SDK `client.encryption().requestRoomKeyRecovery(...)`.
- `ClientProxy` observes `m.forwarded_room_key` to record the forwarded-key source for logging and resolution diagnostics.
- `EncryptedRoomTimelineView` renders a recovery card when a room-key recovery status exists, or when the cause is historical-message/device-unverified.

## Android Existing State

Existing Android modules and files:

- `libraries/matrix/api/src/main/kotlin/io/element/android/libraries/matrix/api/timeline/item/event/EventContent.kt`
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/model/event/TimelineItemEncryptedContent.kt`
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/model/event/TimelineItemEncryptedContentProvider.kt`
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/TimelineItemEncryptedView.kt`
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/factories/event/TimelineItemContentUTDFactory.kt`
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/factories/event/TimelineItemContentFactory.kt`
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/TimelineItemEventRowUtdPreview.kt`
- `libraries/matrix/impl/src/main/kotlin/io/element/android/libraries/matrix/impl/analytics/UtdTracker.kt`
- `libraries/matrix/impl/src/main/kotlin/io/element/android/libraries/matrix/impl/encryption/RustEncryptionService.kt`
- `libraries/matrix/impl/src/test/kotlin/io/element/android/libraries/matrix/impl/fixtures/fakes/FakeFfiEncryption.kt`
- `libraries/matrix/test/src/main/kotlin/io/element/android/libraries/matrix/test/encryption/FakeEncryptionService.kt`

Current Android behavior:

- Android maps UTD content into `UnableToDecryptContent.Data.MegolmV1AesSha2(sessionId, utdCause)` or unknown data.
- `TimelineItemEncryptedView` shows a simple informative row with static text/icon for each UTD cause.
- There is no Android domain model for room-key recovery request, target, stage, plan, pending state, progress state, or forwarded-key source.
- There is no Matrix API wrapper for `requestRoomKeyRecovery`, room-key request enablement, forwarding enablement, or forwarding policy.
- There is no timeline state/presenter integration that starts recovery attempts from UTD items.
- There is no manual retry or verify-device action from an encrypted timeline item.
- Android does track UTD analytics through `UtdTracker`, but that is diagnostic only.

## Target Android Behavior

User flows:

- When a timeline contains an ordinary recoverable Megolm UTD event, Android creates a room-key recovery request from the original event data.
- Repeated UTD events with the same `(roomId, sessionId, senderKey)` are coalesced in the timeline recovery UI and display a message count.
- If the current session/device verification state is unknown, show a checking-device-verification status and do not send key requests yet.
- If the current session/device is unverified, show a device-unverified recovery card with a verify-device action.
- If verified, automatically run the iOS recovery plan:
  - Own-message UTD: request from own devices.
  - Other-user UTD: quick-check backup when backup is usable, then request sender.
  - Member fallback: request active room members when member targets exist.
- Each key-request stage waits for the configured timeout before falling through to the next stage.
- If the event decrypts during a stage, show resolved and clear pending/progress state.
- If all stages fail, show failed with a retry action.
- Manual retry removes pending/progress state and restarts automatic recovery for that request.
- Android enables room-key requests and room-key forwarding when the Matrix client/session is ready.
- Android installs a member-aware room-key forwarding policy equivalent to iOS `AllowJoinedRoomKeyForwardingPolicy`.

Display states:

- `checkingDeviceVerification`: automatic status, no action.
- `deviceUnverified`: action opens existing session/device verification flow.
- `backup`: automatic status, shows progress stage bar.
- `ownDevices`: automatic status, shows progress stage bar and remaining wait time.
- `sender`: automatic status, shows progress stage bar and remaining wait time.
- `members`: automatic status, shows progress stage bar and remaining wait time.
- `resolved`: success status.
- `failed`: failed status with retry action.

## Data And API Mapping

New or extended Android Matrix API:

- Add a room-key recovery API under `libraries/matrix/api/encryption` or a narrowly scoped `RoomKeyRecoveryService` owned by the current Matrix session.
- Expose:
  - `configureRoomKeyRecovery()`
  - `requestRoomKeyRecovery(request, targets, scope): Result<RoomKeyRecoveryProgress>`
  - `setAllowedRoomKeyForwardingRequesters(roomId, userIds)`
  - optional forwarded-key-source flow/log hook if available through SDK to-device observation.
- Keep SDK-specific classes out of feature modules; map SDK types into Android API data classes.

Android data models should mirror iOS:

- `RoomKeyRecoveryRequest(roomId, senderUserId, senderDeviceId, senderKey, sessionId, ciphertext)`
- `RoomKeyRecoveryTarget(userId, deviceId)`
- `RoomKeyRecoveryScope`: `OwnDevices`, `Sender`, `RoomMember`
- `RoomKeyRecoveryStage`: `CheckingDeviceVerification`, `DeviceUnverified`, `Backup`, `OwnDevices`, `Sender`, `Members`, `Resolved`, `Failed`
- `RoomKeyRecoveryDisplayStatus(stage, title/detail string ids or semantic stage, isAutomatic, waitStartedAt, waitDuration, planStages)`
- `RoomKeyRecoveryPlan(stages)`
- `RoomKeyRecoveryProgressRecord`
- `RoomKeyRecoveryPendingRecord`

Request parsing:

- The request must come from the event original JSON/debug payload when available, matching iOS.
- If Android cannot currently access original JSON for timeline event content, the implementation plan must first add a Matrix timeline mapping seam that carries enough UTD metadata to build the request.
- Do not synthesize incomplete requests from only `sessionId`; missing `roomId`, `senderKey`, or sender should make the item non-recoverable and fall back to current static UTD display.

Verification mapping:

- Use existing session-verification state from `EncryptionService`, Matrix session security state, or the Android verification modules.
- Unknown verification state maps to `checkingDeviceVerification`.
- Unverified maps to `deviceUnverified`.
- Verified allows recovery attempts.

Backup mapping:

- The quick backup stage should retry/check decryption before requesting devices, as iOS does.
- Use existing backup/recovery state from `EncryptionService` to decide whether backup is usable.

Forwarding policy:

- Android must call SDK `Encryption.setRoomKeyRequestsEnabled(true)`.
- Android must call SDK `Encryption.setRoomKeyForwardingEnabled(true)`.
- Android must install a `RoomKeyForwardingPolicy` that allows requesters present in the room member set for the requested room.
- Agent/bot-specific exceptions are excluded here and reserved for `agent-room-key-recovery`.

## Android Implementation Notes

Likely implementation areas:

- Matrix layer:
  - `libraries/matrix/api/.../encryption` for room-key recovery API data types.
  - `libraries/matrix/impl/.../encryption` for SDK mapping and forwarding policy.
  - Fake services in `libraries/matrix/test` and SDK fakes in `libraries/matrix/impl/src/test`.
- Messages/timeline layer:
  - Extend UTD content model with optional room-key recovery request/status.
  - Add a recovery coordinator/presenter/service near `features/messages/impl/timeline`.
  - Add card rendering to `TimelineItemEncryptedView` while preserving existing static fallback.
  - Add event actions for verify-device and retry recovery.
- Persistence:
  - Store pending retry deadlines and stage progress in Android preferences/datastore or an existing lightweight store. The storage key should be feature-specific and prune stale entries to currently visible requests.

## Acceptance Criteria

1. Android exposes Matrix API wrappers for SDK room-key recovery request, forwarding enablement, and forwarding policy without leaking SDK types into feature modules.
2. Android configures room-key request receiving and forwarding when a session is active.
3. Member-aware forwarding policy allows active room members and refuses non-members.
4. Recoverable Megolm UTD events produce a `RoomKeyRecoveryRequest` only when all required fields are available.
5. Non-recoverable UTD events keep the current static encrypted timeline row.
6. Repeated UTD events with the same room-key identity are coalesced and expose the count to the UI.
7. Verified sessions automatically run the iOS plan order for own-message and other-user messages.
8. Unknown/unverified sessions do not send key requests and show checking/verify-device states.
9. Stage progress persists across timeline rebuilds and expires/falls through like iOS.
10. Pending automatic retries are suppressed for 30 minutes per room-key identity.
11. Manual retry clears pending/progress state and restarts recovery.
12. Resolved recovery clears pending/progress state.
13. Failed recovery displays retry affordance.
14. Existing UTD text/icon behavior remains unchanged when no recovery status exists.
15. Unit tests cover request parsing, plan building, pending/progress stores, SDK mapping, forwarding policy, timeline coalescing, verified/unverified state handling, manual retry, and UI fallback.
16. `:libraries:matrix:impl:testDebugUnitTest` passes.
17. `:features:messages:impl:testDebugUnitTest` passes for the targeted timeline tests.
18. `:features:messages:impl:assembleDebug` passes.

## Subagent Handoff Notes

- Start from iOS `RoomKeyRecovery.swift` and `TimelineViewModel.recoverRoomKey`; do not infer stage order from Android's current static UTD UI.
- Keep `agent-room-key-recovery` out of this implementation; the member-aware forwarding policy here should be general room membership only.
- If original event JSON is unavailable in Android timeline models, add that mapping first and explicitly test that missing JSON falls back safely.
- Prefer small commits by layer: Matrix API/wrapper, recovery planner/stores, timeline integration, UI card, verification.

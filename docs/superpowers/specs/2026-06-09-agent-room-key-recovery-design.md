# Agent Room Key Recovery Migration Design

Date: 2026-06-09

## Feature Boundary

Feature name: `agent-room-key-recovery`.

User-visible goal: Android should match the iOS behavior for encrypted messages sent by Unseal agent/bot devices that are unable to decrypt: detect agent-origin Megolm UTD events, send a direct `m.room_key_request` to the originating agent device, suppress retry storms, and reuse the encrypted timeline recovery UI from ordinary room-key recovery.

Out of scope:

- Ordinary room-key recovery from own devices, backup, sender, and room members. That is `encrypted-room-key-recovery`.
- Secure backup setup and recovery-key/passphrase flows. That is `secure-backup-recovery`.
- Agent management, room-agent CRUD, skills management, vault, or local agent runtime.
- Generic to-device event UI or incoming to-device event processing.
- Rich AI message rendering and MiniApp runtime behavior.

Dependency class: Matrix Rust SDK dependent plus Unseal custom policy.

Blocked status: not blocked by missing component libraries. Matrix Rust SDK Android artifact `io.github.rayson-pagepeek.matrix.rustcomponents:sdk-android:26.06.5` exposes `Client.sendToDeviceEvent(eventType, userId, deviceId, content)` and `Client.deviceId()`, which are sufficient for iOS-equivalent direct agent key requests.

## iOS Source References

Primary iOS files:

- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Services/Encryption/AgentRoomKeyRecovery.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Services/Client/ClientProxy.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Services/Client/ClientProxyProtocol.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Timeline/TimelineViewModel.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Timeline/View/TimelineItemViews/EncryptedRoomTimelineView.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Services/Encryption/RoomKeyRecovery.swift`

Core iOS types and behavior:

- `AgentRoomKeyRecoveryRequest` parses original event JSON and requires:
  - `content.algorithm == "m.megolm.v1.aes-sha2"`
  - `content.sender_key`
  - `content.device_id`
  - `room_id`, falling back to the room ID passed by the timeline.
  - `sender`, falling back to the event sender passed by the timeline.
  - `content.session_id`, falling back to the UTD session ID passed by the timeline.
- Agent sender detection in `AgentRoomKeyRecoveryRequest.isAgentSender(userID:deviceID:)` returns true when:
  - device ID uppercased starts with `BOT_`; or
  - Matrix user localpart is exactly `agent`; or
  - Matrix user localpart starts with `agent-`; or
  - Matrix user localpart starts with `jelf-`; or
  - Matrix user localpart contains `-jelf`.
- `AgentRoomKeyRecoveryRequest.target` is always the original agent sender user ID plus exact sender device ID.
- `roomKeyRequestContent(requestingDeviceID:requestID:)` builds the to-device payload:

```json
{
  "action": "request",
  "request_id": "<lowercase uuid>",
  "requesting_device_id": "<current device id>",
  "body": {
    "algorithm": "m.megolm.v1.aes-sha2",
    "room_id": "<room id>",
    "sender_key": "<sender key>",
    "session_id": "<session id>"
  }
}
```

- `encodedRoomKeyRequestContent(requestingDeviceID:)` serializes the payload as sorted JSON.
- `AgentRoomKeyRecoveryPendingStore` stores a timestamp per `(roomID, senderUserID, senderDeviceID, senderKey, sessionID)` and suppresses automatic retries for 60 seconds.
- `ClientProxy.requestAgentRoomKeyRecovery`:
  - Fails when the current device ID is missing.
  - Encodes request content using the current device ID.
  - Calls SDK `client.sendToDeviceEvent(eventType: "m.room_key_request", userId: target.userID, deviceId: target.deviceID, content: content)`.
  - Treats non-empty send failures as invalid response.
- `TimelineViewModel.roomMemberRecoveryTargets` excludes room agents from ordinary member fallback unless the missing-key sender itself is a room agent.
- `TimelineViewModel.roomAgentUserIDs` loads agent MXIDs from Chatbot API `getRoomAgents(roomId:)` and caches them by active room-member signature.

## Android Existing State

Existing Android modules and files:

- `libraries/matrix/api/src/main/kotlin/io/element/android/libraries/matrix/api/MatrixClient.kt`
- `libraries/matrix/impl/src/main/kotlin/io/element/android/libraries/matrix/impl/RustMatrixClient.kt`
- `libraries/matrix/impl/src/test/kotlin/io/element/android/libraries/matrix/impl/fixtures/fakes/FakeFfiClient.kt`
- `libraries/matrix/test/src/main/kotlin/io/element/android/libraries/matrix/test/FakeMatrixClient.kt`
- `libraries/chatbot/api/src/main/kotlin/io/element/android/libraries/chatbot/api/ChatbotApiService.kt`
- `libraries/chatbot/api/src/main/kotlin/io/element/android/libraries/chatbot/api/model/rooms/RoomModels.kt`
- `libraries/chatbot/test/src/main/kotlin/io/element/android/libraries/chatbot/test/FakeChatbotApiService.kt`
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/roomkey/RoomKeyRecoveryTimelineRunner.kt`
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/roomkey/RoomKeyRecoveryCoordinator.kt`
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/roomkey/RoomKeyRecoveryRequestParser.kt`
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/TimelinePresenter.kt`
- `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/factories/event/TimelineItemContentFactory.kt`

Current Android behavior:

- Ordinary encrypted room-key recovery already parses Megolm UTD original JSON, coalesces timeline statuses, renders recovery timeline card states, and configures a member-aware forwarding policy.
- `MatrixClient` exposes `deviceId`; `RustMatrixClient` holds the Rust SDK `Client`.
- Matrix API does not yet expose a method for sending direct to-device events or requesting agent room-key recovery.
- Chatbot API already exposes `getRoomAgents(roomId): Result<ChatbotGetRoomAgentsResponse>`.
- `ChatbotRoomAgent.mxid` contains the Matrix user ID needed to exclude room agents from ordinary member fallback.
- `RoomKeyRecoveryTimelineRunner` currently includes every active non-own member as ordinary member fallback, so agent users can be included in ordinary fallback unless filtered.
- There is no Android parser, pending store, coordinator, or runner integration for `AgentRoomKeyRecoveryRequest`.

## Target Android Behavior

User flows:

- When a visible timeline UTD event is a Megolm event sent by an agent/bot device, Android creates an agent room-key recovery request from original JSON plus timeline fallback fields.
- Android sends a direct to-device `m.room_key_request` to the exact originating agent device.
- Automatic agent room-key retries are suppressed for 60 seconds per full agent request identity.
- Multiple visible UTD events with the same ordinary room-key identity and/or agent request identity continue to use one timeline recovery card per missing key identity.
- Agent-origin requests should not run ordinary sender/member fallback first; the first agent implementation sends the direct agent request and then lets the existing recovery card show active/failed/retry semantics.
- Manual retry for a recovery card clears both ordinary pending/progress state and matching agent pending state, then resends the agent to-device request when the request is agent-origin.
- Missing original JSON, missing sender key, missing sender device ID, non-Megolm algorithm, or non-agent sender keeps the existing ordinary recovery/fallback behavior.
- Ordinary member fallback must exclude active room agents returned by `ChatbotApiService.getRoomAgents(roomId)` unless the original missing-key sender is that room agent, matching iOS.

Display states:

- Agent request pending/active can reuse the existing encrypted timeline card stage vocabulary, but the underlying action must be the direct agent to-device request.
- If the current session verification state is unknown or unverified, Android should preserve the existing ordinary recovery gating behavior and not send agent requests until verified.
- Failed agent direct request should surface the existing retry affordance.
- Existing static UTD fallback remains unchanged when neither ordinary nor agent recovery can build a request.

## Data And API Mapping

New Android Matrix API:

- Add `AgentRoomKeyRecoveryRequest` and `AgentRoomKeyRecoveryTarget` under `libraries/matrix/api/encryption/roomkey`.
- Add `suspend fun requestAgentRoomKeyRecovery(request: AgentRoomKeyRecoveryRequest): Result<Unit>` to `MatrixClient` or a narrowly scoped session service.
- Android request payload must match iOS:
  - event type: `m.room_key_request`
  - JSON keys: `action`, `request_id`, `requesting_device_id`, `body.algorithm`, `body.room_id`, `body.sender_key`, `body.session_id`
  - target: `request.senderUserId` plus `request.senderDeviceId`
  - requesting device ID: current Matrix client device ID
- Rust implementation should call SDK `Client.sendToDeviceEvent(...)` and return failure if SDK result contains failures.
- Tests must verify JSON shape with deterministic request ID where possible.

New Android feature-layer models:

- Add `AgentRoomKeyRecoveryRequestParser` near ordinary room-key recovery parser.
- Add `AgentRoomKeyRecoveryPendingStore` with a 60-second retry interval.
- Add `AgentRoomKeyRecoveryCoordinator` or integrate narrowly into `RoomKeyRecoveryTimelineRunner` while keeping the ordinary coordinator's stage behavior intact.
- Add a room-agent resolver/cache in the messages room scope that uses `ChatbotApiServiceFactory.createForUnsealApi(matrixClient).getRoomAgents(roomId.value)`.

Request parsing mapping:

- `roomId`: original JSON `room_id`, fallback timeline room ID.
- `senderUserId`: original JSON `sender`, fallback timeline event sender.
- `senderDeviceId`: required `content.device_id`.
- `senderKey`: required `content.sender_key`.
- `sessionId`: `content.session_id`, fallback UTD session ID.
- `algorithm`: must be `m.megolm.v1.aes-sha2`.
- `isAgentSender`: exact iOS localpart/device rules.

Room-agent filtering:

- Resolve room agents from Chatbot API and use their non-null `mxid` values.
- Exclude those MXIDs from ordinary room-member fallback targets when the original missing-key sender is not one of those agents.
- If API lookup fails, fall back to no exclusions and preserve ordinary recovery behavior.
- Cache by active room-member signature to avoid repeated network calls for the same visible membership state.

## Acceptance Criteria

1. Android has a standalone `agent-room-key-recovery` spec and implementation plan before code implementation.
2. Android exposes a Matrix API wrapper for direct agent room-key recovery without leaking Rust SDK types into feature modules.
3. Rust implementation calls SDK `Client.sendToDeviceEvent("m.room_key_request", targetUserId, targetDeviceId, content)`.
4. The direct to-device JSON payload matches iOS shape and includes the current Android device ID as `requesting_device_id`.
5. Agent request parsing accepts only Megolm events from iOS-defined agent senders.
6. Parser uses timeline fallback room ID, sender ID, and session ID exactly where iOS does.
7. Parser rejects non-agent senders, missing device ID, missing sender key, wrong algorithm, and invalid JSON.
8. Agent pending store suppresses automatic retry for 60 seconds per full request key.
9. Verified timeline recovery sends direct agent requests for visible agent-origin UTD events.
10. Unknown/unverified session states do not send agent requests.
11. Manual retry clears agent pending state and resends direct agent request.
12. Ordinary member fallback excludes room agents returned by Chatbot API, unless the UTD sender is that agent.
13. Existing ordinary room-key recovery behavior remains intact for non-agent UTD events.
14. Existing static UTD fallback remains unchanged when no valid recovery request exists.
15. Unit tests cover parser rules, pending store, Matrix API SDK mapping, timeline runner direct send, verification gating, manual retry, and room-agent member fallback filtering.
16. `:libraries:matrix:impl:testDebugUnitTest` passes for agent room-key recovery tests.
17. `:features:messages:impl:testDebugUnitTest` passes for room-key/agent room-key recovery tests.
18. `:features:messages:impl:assembleDebug` passes.
19. `:app:assembleFdroidDebug` passes before handing over a runnable build.

## Subagent Handoff Notes

- Start from iOS `AgentRoomKeyRecovery.swift`; do not infer agent detection rules from Android agent-management names.
- Reuse ordinary room-key recovery UI and status model where possible, but keep direct agent request sending distinct from SDK `Encryption.requestRoomKeyRecovery`.
- Keep the first Android implementation conservative: direct request to the exact agent device only.
- Do not implement room-agent CRUD, skills, vault, or local runtime as part of this spec.

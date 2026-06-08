# Encrypted Room Key Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Do not start `agent-room-key-recovery` in this plan.

**Goal:** Migrate iOS ordinary encrypted room key recovery behavior to Android for unable-to-decrypt Megolm timeline items.

**Architecture:** Add Android Matrix API abstractions for room-key recovery, then build a timeline-facing recovery coordinator that mirrors iOS request parsing, plan stages, pending/progress persistence, and encrypted timeline card behavior. Keep agent/bot-specific policy out of scope.

**Tech Stack:** Kotlin, Compose, Matrix Rust SDK 26.06.5, Turbine, Truth, Android unit tests, Gradle.

---

## File Structure

- Add/modify `libraries/matrix/api/src/main/kotlin/io/element/android/libraries/matrix/api/encryption/roomkey/`
  - API data types for request, target, scope, progress, forwarding policy inputs.
- Modify `libraries/matrix/api/src/main/kotlin/io/element/android/libraries/matrix/api/encryption/EncryptionService.kt` or add a scoped service interface.
  - Expose room-key recovery configuration and request methods.
- Modify `libraries/matrix/impl/src/main/kotlin/io/element/android/libraries/matrix/impl/encryption/RustEncryptionService.kt`
  - Map Android API types to SDK `Encryption.requestRoomKeyRecovery` and forwarding APIs.
- Add/modify `libraries/matrix/impl/src/test/kotlin/io/element/android/libraries/matrix/impl/encryption/*RoomKeyRecovery*Test.kt`
  - Verify SDK mapping and forwarding policy.
- Modify `libraries/matrix/test/src/main/kotlin/io/element/android/libraries/matrix/test/encryption/FakeEncryptionService.kt`
  - Add test seams for room-key recovery.
- Add `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/roomkey/`
  - Recovery request parser, plan builder, pending/progress stores, coordinator.
- Modify `libraries/matrix/api/src/main/kotlin/io/element/android/libraries/matrix/api/timeline/item/event/EventContent.kt`
  - Carry optional recovery metadata/status for UTD content or introduce a narrowly scoped companion model.
- Modify `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/model/event/TimelineItemEncryptedContent.kt`
  - Include recovery status/count/request for rendering and retry.
- Modify `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/TimelineItemEncryptedView.kt`
  - Render recovery card states when present; preserve current static fallback.
- Modify timeline factories/presenters under `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/`
  - Create requests from UTD events, coalesce duplicates, wire retry and verify-device actions.

## iOS Reference Summary

Use these files as behavioral source of truth:

- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Services/Encryption/RoomKeyRecovery.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Services/Client/ClientProxy.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Timeline/TimelineViewModel.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Timeline/View/TimelineItemViews/EncryptedRoomTimelineView.swift`

Required iOS behaviors:

- Parse request from original event JSON only when `algorithm == m.megolm.v1.aes-sha2` and `room_id`, `sender_key`, and `session_id` are available.
- Suppress automatic retry for 30 minutes per `(roomId, sessionId, senderKey)`.
- Preserve active stage/deadline/failed state across timeline rebuilds.
- Gate recovery by verification state: unknown = checking, unverified = verify-device, verified = run stages.
- Recovery plan order:
  - Own message: `ownDevices`.
  - Other user: `backup` if usable, then `sender`.
  - Append `members` when room-member fallback targets exist.
- Each key-request stage waits 60 seconds for decryption before falling through.
- Manual retry clears pending/progress and restarts.

### Task 1: Matrix API Room-Key Recovery Wrapper

**Files:**
- Add: `libraries/matrix/api/src/main/kotlin/io/element/android/libraries/matrix/api/encryption/roomkey/RoomKeyRecovery.kt`
- Modify: `libraries/matrix/api/src/main/kotlin/io/element/android/libraries/matrix/api/encryption/EncryptionService.kt`
- Modify: `libraries/matrix/impl/src/main/kotlin/io/element/android/libraries/matrix/impl/encryption/RustEncryptionService.kt`
- Modify: `libraries/matrix/impl/src/test/kotlin/io/element/android/libraries/matrix/impl/fixtures/fakes/FakeFfiEncryption.kt`
- Add: `libraries/matrix/impl/src/test/kotlin/io/element/android/libraries/matrix/impl/encryption/RustRoomKeyRecoveryTest.kt`
- Modify: `libraries/matrix/test/src/main/kotlin/io/element/android/libraries/matrix/test/encryption/FakeEncryptionService.kt`

- [x] **Step 1: Add API models**

Add Kotlin data classes/enums:

```kotlin
data class RoomKeyRecoveryRequest(...)
data class RoomKeyRecoveryTarget(...)
enum class RoomKeyRecoveryScope { OwnDevices, Sender, RoomMember }
enum class RoomKeyRecoveryStage { BackupRequested, BackupMissed, SenderRequested, SenderTimedOut, MembersRequested, MembersUnavailable, Resolved, Failed }
data class RoomKeyRecoveryProgress(...)
data class RoomKeyForwardingAuthorization(...)
data class RoomKeyForwardingDecision(...)
fun interface RoomKeyForwardingPolicy { fun allowForwarding(...): RoomKeyForwardingDecision }
```

Use Android API `RoomId`/`UserId` value classes where practical, but keep sender key/session IDs as strings.

- [x] **Step 2: Extend EncryptionService**

Add methods:

```kotlin
suspend fun configureRoomKeyRecovery(policy: RoomKeyForwardingPolicy): Result<Unit>
suspend fun requestRoomKeyRecovery(
    request: RoomKeyRecoveryRequest,
    targets: List<RoomKeyRecoveryTarget>,
    scope: RoomKeyRecoveryScope,
): Result<RoomKeyRecoveryProgress>
```

- [x] **Step 3: Implement Rust mapping**

In `RustEncryptionService`, call:

```kotlin
inner.setRoomKeyRequestsEnabled(true)
inner.setRoomKeyForwardingEnabled(true)
inner.setRoomKeyForwardingPolicy(...)
inner.requestRoomKeyRecovery(roomId, sessionId, senderKey, targets, scope, ciphertext)
```

Map SDK stages into API stages.

- [x] **Step 4: Test SDK mapping and forwarding policy bridge**

Use `FakeFfiEncryption` recorders to assert:

- Configure calls requests enabled, forwarding enabled, and installs policy.
- Request maps room ID, session ID, sender key, ciphertext, scope, and targets.
- SDK progress maps back into API progress.
- Policy returns allow/refuse decisions with reason.

- [x] **Step 5: Verify Matrix API layer**

Run:

```bash
env -u http_proxy -u https_proxy -u all_proxy -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u NO_PROXY -u no_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :libraries:matrix:impl:testDebugUnitTest --tests 'io.element.android.libraries.matrix.impl.encryption.*RoomKeyRecovery*'
```

- [x] **Step 6: Commit Task 1**

```bash
git add libraries/matrix/api libraries/matrix/impl/src/main/kotlin/io/element/android/libraries/matrix/impl/encryption libraries/matrix/impl/src/test/kotlin/io/element/android/libraries/matrix/impl libraries/matrix/test/src/main/kotlin/io/element/android/libraries/matrix/test/encryption
git commit -m "feat: expose room key recovery sdk wrapper"
```

Verification:

```text
:libraries:matrix:impl:testDebugUnitTest --tests 'io.element.android.libraries.matrix.impl.encryption.*RoomKeyRecovery*' PASS
git diff --check PASS
```

### Task 2: Recovery Request Parsing And Planner

**Files:**
- Add: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/roomkey/RoomKeyRecoveryRequestParser.kt`
- Add: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/roomkey/RoomKeyRecoveryPlanner.kt`
- Add: `features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/roomkey/RoomKeyRecoveryRequestParserTest.kt`
- Add: `features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/roomkey/RoomKeyRecoveryPlannerTest.kt`
- Modify: `features/messages/impl/build.gradle.kts`

- [x] **Step 1: Implement request parser**

Parse original event JSON for:

- `room_id`
- `sender`
- `content.algorithm == m.megolm.v1.aes-sha2`
- `content.sender_key`
- `content.device_id`
- `content.session_id`
- `content.ciphertext`

Return `null` when required fields are missing or algorithm is not Megolm.

- [x] **Step 2: Implement sender target rules**

Match iOS:

- If sender and own user are on different homeservers, drop sender device ID.
- If latest sender devices are known and do not contain original sender device ID, drop sender device ID.
- Otherwise keep original sender device ID.

- [x] **Step 3: Implement plan builder**

Mirror iOS `RoomKeyRecoveryPlan.build`.

- [x] **Step 4: Test parser and planner**

Cover full JSON, missing JSON, missing required fields, wrong algorithm, same/different homeserver sender target, stale sender device, own-message plan, other-user plan with backup, and member fallback.

- [x] **Step 5: Commit Task 2**

```bash
git add features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/roomkey features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/roomkey
git commit -m "feat: add room key recovery parser and planner"
```

Verification:

```text
:features:messages:impl:testDebugUnitTest --tests 'io.element.android.features.messages.impl.roomkey.*' PASS
git diff --check PASS
```

### Task 3: Pending And Progress Stores

**Files:**
- Add: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/roomkey/RoomKeyRecoveryPendingStore.kt`
- Add: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/roomkey/RoomKeyRecoveryProgressStore.kt`
- Add tests for both stores.

- [x] **Step 1: Implement pending store**

Persist 30-minute retry deadlines per recovery identity key. Provide:

- `markPendingIfNeeded(request, force = false)`
- `removePending(request)`
- `remainingInterval(request)`
- `retainOnly(requests)`
- `pruneExpired()`

- [x] **Step 2: Implement progress store**

Persist current stage, plan stages, start time, deadline, and failed timestamp. Provide:

- `record(request)`
- `startStage(stage, request, planStages, duration)`
- `markFailed(request, planStages)`
- `remove(request)`
- `retainOnly(requests)`

- [x] **Step 3: Test persistence semantics**

Use fake clock/storage. Cover retry suppression, forced retry, pruning, failed record display, active record remaining time, and retain-only behavior.

- [x] **Step 4: Commit Task 3**

```bash
git add features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/roomkey features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/roomkey
git commit -m "feat: persist room key recovery progress"
```

Verification:

```text
:features:messages:impl:testDebugUnitTest --tests 'io.element.android.features.messages.impl.roomkey.*' PASS
git diff --check PASS
```

### Task 4: Timeline Recovery Coordinator

**Files:**
- Add: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/roomkey/RoomKeyRecoveryCoordinator.kt`
- Add: `features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/roomkey/RoomKeyRecoveryCoordinatorTest.kt`
- Timeline model/UI wiring remains in Task 5 and session wiring remains in Task 6.

- [x] **Step 1: Define coordinator inputs and outputs**

Inputs should include visible UTD items, own user ID, room ID, verification state, backup usability, member targets, latest sender device IDs, and decryption retry/check callback.

Outputs should include recovery status by identity key, coalesced counts, and effects for sending SDK requests.

- [x] **Step 2: Implement verification gating**

Unknown -> checking status. Unverified -> device-unverified status and no request. Verified -> proceed.

- [x] **Step 3: Implement stage execution**

Run stages in plan order:

- `backup`: retry/check decryption before device requests.
- `ownDevices`: request own devices, wait 60 seconds.
- `sender`: request sender target, wait 60 seconds.
- `members`: request room member targets, wait 60 seconds.

Mark resolved/failed and update stores.

- [x] **Step 4: Implement manual retry**

Manual retry clears pending/progress, forces pending mark, sets checking status, and restarts.

- [x] **Step 5: Test coordinator**

Cover verified/unverified/unknown, active-task suppression, pending suppression, stage fallthrough, resolved cleanup, failed status, manual retry, and coalesced counts.

- [x] **Step 6: Commit Task 4**

```bash
git add features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/roomkey features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/roomkey
git commit -m "feat: coordinate encrypted room key recovery"
```

Verification:

```text
:features:messages:impl:testDebugUnitTest --tests 'io.element.android.features.messages.impl.roomkey.*' PASS
git diff --check PASS
```

### Task 5: Timeline Model And UI Card

**Files:**
- Deferred to Task 6 if needed: `libraries/matrix/api/src/main/kotlin/io/element/android/libraries/matrix/api/timeline/item/event/EventContent.kt`
- Modify: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/model/event/TimelineItemEncryptedContent.kt`
- Modify: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/model/event/TimelineItemEncryptedContentProvider.kt`
- Modify: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/TimelineItemEncryptedView.kt`
- Modify: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/TimelineEvent.kt`
- Modify: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/TimelinePresenter.kt`
- Modify: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/TimelineItemEventContentView.kt`
- Modify/add relevant UI tests/previews.

- [x] **Step 1: Add timeline-facing recovery model**

Add status, request, event count, wait timing, and plan stages to encrypted content model. Keep nullable so current static fallback remains unchanged.

- [x] **Step 2: Render recovery card**

Match iOS behavior:

- Message count.
- Stage title/detail.
- Stage bar.
- Remaining seconds for active wait.
- Verify-device action for `deviceUnverified`.
- Retry action for `failed`.

- [x] **Step 3: Preserve static fallback**

When no recovery status exists, `TimelineItemEncryptedView` must render the same text/icon mapping as today.

- [x] **Step 4: Test UI states**

Cover timeline recovery display mapping, device-unverified action, failed retry action, active stage display, and count text with unit tests. Preserve fallback through nullable model and unchanged fallback branch; screenshot-level Compose coverage is deferred.

- [x] **Step 5: Commit Task 5**

```bash
git add libraries/matrix/api/src/main/kotlin/io/element/android/libraries/matrix/api/timeline/item/event/EventContent.kt features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/timeline
git commit -m "feat: render room key recovery timeline card"
```

Verification:

```text
:features:messages:impl:testDebugUnitTest --tests 'io.element.android.features.messages.impl.timeline.components.event.TimelineItemRoomKeyRecoveryDisplayTest' PASS
:features:messages:impl:testDebugUnitTest --tests 'io.element.android.features.messages.impl.roomkey.*' PASS
:app:assembleFdroidDebug PASS
git diff --check PASS
```

### Task 6: Session Wiring And Verification

**Files:**
- Add: `libraries/matrix/api/src/main/kotlin/io/element/android/libraries/matrix/api/encryption/roomkey/MemberAwareRoomKeyForwardingPolicy.kt`
- Modify: `libraries/matrix/impl/src/main/kotlin/io/element/android/libraries/matrix/impl/di/SessionMatrixModule.kt`
- Modify: `appnav/src/main/kotlin/io/element/android/appnav/loggedin/LoggedInPresenter.kt`
- Add: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/roomkey/RoomKeyRecoveryTimelineRunner.kt`
- Modify: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/roomkey/RoomKeyRecoveryCoordinator.kt`
- Modify: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/TimelinePresenter.kt`
- Modify: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/factories/TimelineItemsFactory.kt`
- Modify: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/factories/event/TimelineItemEventFactory.kt`
- Modify: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/factories/event/TimelineItemContentFactory.kt`
- Modify: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/factories/event/TimelineItemContentUTDFactory.kt`
- Modify/add tests in `features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/timeline`, `features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/roomkey`, and `appnav/src/test/kotlin/io/element/android/appnav/loggedin`.

- [x] **Step 1: Configure room-key recovery on session startup**

Add a session-scoped `MemberAwareRoomKeyForwardingPolicy`, install it from `LoggedInPresenter` with `EncryptionService.configureRoomKeyRecovery(policy)`, and keep the policy denied by default until a room publishes active members.

- [x] **Step 2: Wire timeline retry and verify actions**

Parse UTD `originalJson` values into `RoomKeyRecoveryRequest`, run `RoomKeyRecoveryTimelineRunner`, pass `RoomKeyRecoveryStatus` into the encrypted timeline content model, make retry call coordinator manual retry, and make verify call `SessionVerificationService.requestDeviceVerification()`.

- [x] **Step 3: Ensure member-aware policy has active room members**

On every visible room membership update, publish `activeRoomMembers()` to `MemberAwareRoomKeyForwardingPolicy.updateRoomMembers(roomId, userIds)`. The policy must allow only matching room/requester pairs and deny unknown rooms or inactive users.

- [x] **Step 4: Run feature verification**

Run:

```bash
env -u http_proxy -u https_proxy -u all_proxy -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u NO_PROXY -u no_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:messages:impl:testDebugUnitTest
env -u http_proxy -u https_proxy -u all_proxy -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u NO_PROXY -u no_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :libraries:matrix:impl:testDebugUnitTest
env -u http_proxy -u https_proxy -u all_proxy -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u NO_PROXY -u no_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:messages:impl:assembleDebug
```

- [ ] **Step 5: Commit Task 6**

```bash
git add features/messages libraries/matrix
git commit -m "feat: wire encrypted room key recovery"
```

Verification:

```text
:features:messages:impl:compileDebugKotlin :appnav:compileDebugKotlin PASS
:features:messages:impl:testDebugUnitTest --tests 'io.element.android.features.messages.impl.roomkey.RoomKeyRecoveryTimelineRunnerTest' PASS
:appnav:testDebugUnitTest --tests 'io.element.android.appnav.loggedin.LoggedInPresenterTest.present - configures room key recovery' PASS
:features:messages:impl:testDebugUnitTest PASS
:libraries:matrix:impl:testDebugUnitTest PASS
:features:messages:impl:assembleDebug PASS
git diff --check PASS
```

### Task 7: Final Spec Verification

- [ ] **Step 1: Run acceptance commands**

Run:

```bash
env -u http_proxy -u https_proxy -u all_proxy -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u NO_PROXY -u no_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:messages:impl:testDebugUnitTest :libraries:matrix:impl:testDebugUnitTest :features:messages:impl:assembleDebug
```

- [ ] **Step 2: Check diff**

Run:

```bash
git diff --check
git status --short
```

- [ ] **Step 3: Mark plan complete**

Update this plan's checkboxes and verification evidence after the commands pass.

## Self-Review

Spec coverage: This plan covers `encrypted-room-key-recovery` only. It intentionally excludes `agent-room-key-recovery`.

Risk: This feature spans Matrix SDK wiring, timeline models, persistence, and UI. Implement task-by-task and commit each task before moving on.

Fallback rule: Missing original JSON or missing required Megolm fields must keep current Android static UTD display and must not send room-key recovery requests.

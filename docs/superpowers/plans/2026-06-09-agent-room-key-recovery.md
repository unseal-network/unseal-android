# Agent Room Key Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate iOS direct agent/bot device room-key recovery to Android for agent-origin Megolm unable-to-decrypt timeline items.

**Architecture:** Add a narrow Matrix API wrapper for direct `m.room_key_request` to-device sends, then add feature-layer parsing, 60-second pending suppression, room-agent filtering, and timeline runner integration. Reuse the ordinary encrypted room-key recovery timeline card/status model where possible, but keep the direct agent request path separate from SDK `Encryption.requestRoomKeyRecovery`.

**Tech Stack:** Kotlin, Matrix Rust SDK 26.06.5, Compose timeline model, Chatbot API service, Metro DI, Turbine, Truth, Android unit tests, Gradle.

---

## File Structure

- Add/modify `libraries/matrix/api/src/main/kotlin/io/element/android/libraries/matrix/api/encryption/roomkey/`
  - `AgentRoomKeyRecovery.kt`: API request/target models and deterministic JSON payload encoder.
- Modify `libraries/matrix/api/src/main/kotlin/io/element/android/libraries/matrix/api/MatrixClient.kt`
  - Add `requestAgentRoomKeyRecovery(request): Result<Unit>`.
- Modify `libraries/matrix/impl/src/main/kotlin/io/element/android/libraries/matrix/impl/RustMatrixClient.kt`
  - Implement wrapper through SDK `Client.sendToDeviceEvent`.
- Modify `libraries/matrix/impl/src/test/kotlin/io/element/android/libraries/matrix/impl/fixtures/fakes/FakeFfiClient.kt`
  - Record to-device event sends.
- Add `libraries/matrix/impl/src/test/kotlin/io/element/android/libraries/matrix/impl/encryption/RustAgentRoomKeyRecoveryTest.kt`
  - Verify SDK mapping, JSON payload, current device ID, and failure handling.
- Modify `libraries/matrix/test/src/main/kotlin/io/element/android/libraries/matrix/test/FakeMatrixClient.kt`
  - Add fake seam for feature tests.
- Add `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/roomkey/AgentRoomKeyRecoveryRequestParser.kt`
  - Parse iOS-equivalent agent-origin request from original JSON plus timeline fallbacks.
- Add `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/roomkey/AgentRoomKeyRecoveryPendingStore.kt`
  - Suppress direct agent automatic retries for 60 seconds.
- Add `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/roomkey/RoomAgentResolver.kt`
  - Resolve and cache room-agent MXIDs from `ChatbotApiService.getRoomAgents`.
- Modify `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/roomkey/RoomKeyRecoveryTimelineRunner.kt`
  - Detect agent-origin UTD events, send direct agent requests when verified, clear agent pending state on retry, and exclude room agents from ordinary member fallback.
- Modify `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/TimelinePresenter.kt`
  - Inject `MatrixClient` and `ChatbotApiServiceFactory` into the runner if not already available from the room graph.
- Add/modify tests under `features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/roomkey/`.

## iOS Reference Summary

Use these files as source of truth:

- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Services/Encryption/AgentRoomKeyRecovery.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Services/Client/ClientProxy.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Timeline/TimelineViewModel.swift`

Required iOS behaviors:

- Parse only Megolm events with `content.algorithm == "m.megolm.v1.aes-sha2"`.
- Require `content.device_id` and `content.sender_key`.
- Use `root.room_id`, `root.sender`, and `content.session_id`; fall back to timeline room ID, event sender, and UTD session ID only for those fields.
- Accept agent senders only when device ID uppercased starts with `BOT_`, or user localpart is `agent`, starts with `agent-`, starts with `jelf-`, or contains `-jelf`.
- Send `m.room_key_request` directly to the original agent sender user/device.
- Payload body must include algorithm, room ID, sender key, and session ID; root payload must include `action=request`, `request_id`, and current device ID as `requesting_device_id`.
- Suppress automatic retry for 60 seconds per `(roomID, senderUserID, senderDeviceID, senderKey, sessionID)`.
- Exclude room agents from ordinary member fallback targets unless the missing-key sender is that agent.

### Task 1: Matrix API Direct Agent Room-Key Request

**Files:**
- Add: `libraries/matrix/api/src/main/kotlin/io/element/android/libraries/matrix/api/encryption/roomkey/AgentRoomKeyRecovery.kt`
- Modify: `libraries/matrix/api/src/main/kotlin/io/element/android/libraries/matrix/api/MatrixClient.kt`
- Modify: `libraries/matrix/impl/src/main/kotlin/io/element/android/libraries/matrix/impl/RustMatrixClient.kt`
- Modify: `libraries/matrix/impl/src/test/kotlin/io/element/android/libraries/matrix/impl/fixtures/fakes/FakeFfiClient.kt`
- Add: `libraries/matrix/impl/src/test/kotlin/io/element/android/libraries/matrix/impl/encryption/RustAgentRoomKeyRecoveryTest.kt`
- Modify: `libraries/matrix/test/src/main/kotlin/io/element/android/libraries/matrix/test/FakeMatrixClient.kt`

- [x] **Step 1: Add failing Matrix wrapper test**

Add `RustAgentRoomKeyRecoveryTest` with tests equivalent to:

```kotlin
@Test
fun `requestAgentRoomKeyRecovery - sends to-device room key request to agent device`() = runTest {
    val inner = FakeFfiClient(deviceId = "OWNDEVICE")
    val client = createRustMatrixClient(inner)
    val request = AgentRoomKeyRecoveryRequest(
        roomId = RoomId("!room:example.org"),
        senderUserId = UserId("@agent:example.org"),
        senderDeviceId = "BOT_DEVICE",
        senderKey = "SENDER_KEY",
        sessionId = "SESSION",
    )

    assertThat(client.requestAgentRoomKeyRecovery(request).isSuccess).isTrue()

    val call = inner.sendToDeviceEventCall!!
    assertThat(call.eventType).isEqualTo("m.room_key_request")
    assertThat(call.userId).isEqualTo("@agent:example.org")
    assertThat(call.deviceId).isEqualTo("BOT_DEVICE")
    assertThat(call.content).contains("\"requesting_device_id\":\"OWNDEVICE\"")
    assertThat(call.content).contains("\"algorithm\":\"m.megolm.v1.aes-sha2\"")
    assertThat(call.content).contains("\"room_id\":\"!room:example.org\"")
    assertThat(call.content).contains("\"sender_key\":\"SENDER_KEY\"")
    assertThat(call.content).contains("\"session_id\":\"SESSION\"")
}
```

Also add a failure test where `FakeFfiClient` returns `ToDeviceSendResult(failures = listOf(...))` or throws, and assert the result is failure.

- [x] **Step 2: Run test to verify it fails**

Run:

```bash
env -u http_proxy -u https_proxy -u all_proxy -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u NO_PROXY -u no_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :libraries:matrix:impl:testDebugUnitTest --tests 'io.element.android.libraries.matrix.impl.encryption.RustAgentRoomKeyRecoveryTest'
```

Expected: FAIL because `AgentRoomKeyRecoveryRequest` and `MatrixClient.requestAgentRoomKeyRecovery` do not exist.

- [x] **Step 3: Add API model and JSON encoder**

Create `AgentRoomKeyRecovery.kt` with:

```kotlin
data class AgentRoomKeyRecoveryTarget(
    val userId: UserId,
    val deviceId: String,
)

data class AgentRoomKeyRecoveryRequest(
    val roomId: RoomId,
    val senderUserId: UserId,
    val senderDeviceId: String,
    val senderKey: String,
    val sessionId: String,
) {
    val target = AgentRoomKeyRecoveryTarget(senderUserId, senderDeviceId)
    val identityKey = listOf(roomId.value, senderUserId.value, senderDeviceId, senderKey, sessionId).joinToString("|")

    fun encodedRoomKeyRequestContent(requestingDeviceId: String, requestId: String = UUID.randomUUID().toString().lowercase()): String {
        return buildJsonObject {
            put("action", "request")
            put("request_id", requestId)
            put("requesting_device_id", requestingDeviceId)
            putJsonObject("body") {
                put("algorithm", ALGORITHM)
                put("room_id", roomId.value)
                put("sender_key", senderKey)
                put("session_id", sessionId)
            }
        }.toString()
    }

    companion object {
        const val EVENT_TYPE = "m.room_key_request"
        const val ALGORITHM = "m.megolm.v1.aes-sha2"
    }
}
```

Use `kotlinx.serialization.json.buildJsonObject` and `putJsonObject`.

- [x] **Step 4: Add MatrixClient API and Rust implementation**

Add to `MatrixClient`:

```kotlin
suspend fun requestAgentRoomKeyRecovery(request: AgentRoomKeyRecoveryRequest): Result<Unit>
```

In `RustMatrixClient`, implement:

```kotlin
override suspend fun requestAgentRoomKeyRecovery(request: AgentRoomKeyRecoveryRequest): Result<Unit> = withContext(sessionDispatcher) {
    runCatching {
        val result = innerClient.sendToDeviceEvent(
            eventType = AgentRoomKeyRecoveryRequest.EVENT_TYPE,
            userId = request.target.userId.value,
            deviceId = request.target.deviceId,
            content = request.encodedRoomKeyRequestContent(requestingDeviceId = deviceId.value),
        )
        check(result.failures.isEmpty()) { "Failed to send agent room key request: ${result.failures}" }
    }
}
```

- [x] **Step 5: Extend fakes**

In `FakeFfiClient`, add:

```kotlin
private val sendToDeviceEventResult: () -> ToDeviceSendResult = { ToDeviceSendResult(emptyList()) }
var sendToDeviceEventCall: SendToDeviceEventCall? = null

override suspend fun sendToDeviceEvent(eventType: String, userId: String, deviceId: String, content: String): ToDeviceSendResult {
    sendToDeviceEventCall = SendToDeviceEventCall(eventType, userId, deviceId, content)
    return sendToDeviceEventResult()
}

data class SendToDeviceEventCall(
    val eventType: String,
    val userId: String,
    val deviceId: String,
    val content: String,
)
```

In `FakeMatrixClient`, add constructor lambda and override:

```kotlin
private val requestAgentRoomKeyRecoveryLambda: (AgentRoomKeyRecoveryRequest) -> Result<Unit> = { lambdaError() }

override suspend fun requestAgentRoomKeyRecovery(request: AgentRoomKeyRecoveryRequest): Result<Unit> =
    simulateLongTask { requestAgentRoomKeyRecoveryLambda(request) }
```

- [x] **Step 6: Run and pass Matrix tests**

Run:

```bash
env -u http_proxy -u https_proxy -u all_proxy -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u NO_PROXY -u no_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :libraries:matrix:impl:testDebugUnitTest --tests 'io.element.android.libraries.matrix.impl.encryption.RustAgentRoomKeyRecoveryTest'
git diff --check
```

Expected: PASS.

- [ ] **Step 7: Commit Task 1**

```bash
git add libraries/matrix/api libraries/matrix/impl/src/main/kotlin/io/element/android/libraries/matrix/impl/RustMatrixClient.kt libraries/matrix/impl/src/test/kotlin/io/element/android/libraries/matrix/impl libraries/matrix/test/src/main/kotlin/io/element/android/libraries/matrix/test/FakeMatrixClient.kt
git commit -m "feat: add agent room key recovery matrix request"
```

Verification:

```text
:libraries:matrix:impl:testDebugUnitTest --tests 'io.element.android.libraries.matrix.impl.encryption.RustAgentRoomKeyRecoveryTest' PASS
git diff --check PASS
```

### Task 2: Agent Parser And Pending Store

**Files:**
- Add: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/roomkey/AgentRoomKeyRecoveryRequestParser.kt`
- Add: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/roomkey/AgentRoomKeyRecoveryPendingStore.kt`
- Add: `features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/roomkey/AgentRoomKeyRecoveryRequestParserTest.kt`
- Add: `features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/roomkey/AgentRoomKeyRecoveryPendingStoreTest.kt`

- [ ] **Step 1: Add failing parser tests**

Test cases:

- Parses agent event when device ID starts with `BOT_`.
- Parses agent event when localpart is `agent`, `agent-*`, `jelf-*`, or contains `-jelf`.
- Uses fallback room ID, sender ID, and session ID when those specific JSON fields are absent.
- Rejects non-agent sender/device.
- Rejects missing `device_id`, missing `sender_key`, wrong algorithm, and invalid JSON.

Use this input shape:

```kotlin
parser.parse(
    originalJson = """
        {
          "room_id": "!room:example.org",
          "sender": "@agent:example.org",
          "content": {
            "algorithm": "m.megolm.v1.aes-sha2",
            "sender_key": "SENDER_KEY",
            "session_id": "SESSION",
            "device_id": "BOT_DEVICE"
          }
        }
    """.trimIndent(),
    fallbackRoomId = RoomId("!fallback:example.org"),
    fallbackSenderId = UserId("@fallback:example.org"),
    fallbackSessionId = "FALLBACK_SESSION",
)
```

- [ ] **Step 2: Add failing pending store tests**

Use a fake clock and verify:

- First `markPendingIfNeeded(request)` returns true.
- Second call before 60 seconds returns false.
- Call at 60 seconds returns true.
- `removePending(request)` allows immediate retry.
- `retainOnly(listOf(request))` removes unrelated request keys.

- [ ] **Step 3: Run tests to verify failure**

Run:

```bash
env -u http_proxy -u https_proxy -u all_proxy -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u NO_PROXY -u no_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:messages:impl:testDebugUnitTest --tests 'io.element.android.features.messages.impl.roomkey.AgentRoomKeyRecovery*Test'
```

Expected: FAIL because parser and store do not exist.

- [ ] **Step 4: Implement parser**

Implement `AgentRoomKeyRecoveryRequestParser` with:

```kotlin
fun parse(originalJson: String?, fallbackRoomId: RoomId?, fallbackSenderId: UserId, fallbackSessionId: String): AgentRoomKeyRecoveryRequest?
```

Rules:

- Parse JSON with `Json.parseToJsonElement`.
- Read `root["content"].jsonObject`.
- `senderUserId = root["sender"] ?: fallbackSenderId.value`.
- `senderDeviceId = content["device_id"]` required.
- `roomId = root["room_id"] ?: fallbackRoomId?.value` required.
- `senderKey = content["sender_key"]` required.
- `algorithm == AgentRoomKeyRecoveryRequest.ALGORITHM`.
- `sessionId = content["session_id"] ?: fallbackSessionId`.
- `isAgentSender(userId, deviceId)` must exactly match iOS localpart/device rules.

- [ ] **Step 5: Implement pending store**

Implement `AgentRoomKeyRecoveryPendingStore`:

```kotlin
class AgentRoomKeyRecoveryPendingStore(
    private val retryInterval: Duration = 60.seconds,
    private val now: () -> Instant = Clock.System::now,
) {
    private val pendingSinceByIdentityKey = mutableMapOf<String, Instant>()

    fun markPendingIfNeeded(request: AgentRoomKeyRecoveryRequest): Boolean
    fun removePending(request: AgentRoomKeyRecoveryRequest)
    fun retainOnly(requests: Collection<AgentRoomKeyRecoveryRequest>)
}
```

Use `request.identityKey` as the key.

- [ ] **Step 6: Run and pass parser/store tests**

Run:

```bash
env -u http_proxy -u https_proxy -u all_proxy -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u NO_PROXY -u no_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:messages:impl:testDebugUnitTest --tests 'io.element.android.features.messages.impl.roomkey.AgentRoomKeyRecovery*Test'
git diff --check
```

Expected: PASS.

- [ ] **Step 7: Commit Task 2**

```bash
git add features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/roomkey features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/roomkey
git commit -m "feat: parse agent room key recovery requests"
```

### Task 3: Room Agent Resolver And Ordinary Member Filtering

**Files:**
- Add: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/roomkey/RoomAgentResolver.kt`
- Add: `features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/roomkey/RoomAgentResolverTest.kt`
- Modify: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/roomkey/RoomKeyRecoveryTimelineRunner.kt`
- Modify: `features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/roomkey/RoomKeyRecoveryTimelineRunnerTest.kt`

- [ ] **Step 1: Add failing resolver tests**

Cover:

- `getRoomAgents` response with `mxid` values returns a set of `UserId`.
- Agents with null `mxid` are ignored.
- API failure returns an empty set.
- Same active member signature uses cached result.
- Changed active member signature refreshes API call.

- [ ] **Step 2: Implement resolver**

Create `RoomAgentResolver`:

```kotlin
class RoomAgentResolver(
    private val matrixClient: MatrixClient,
    private val chatbotApiServiceFactory: ChatbotApiServiceFactory,
) {
    private var cache: Cache? = null

    suspend fun roomAgentUserIds(roomId: RoomId, activeMemberIds: Set<UserId>): Set<UserId> {
        val signature = activeMemberIds.map { it.value }.sorted().joinToString("|")
        cache?.takeIf { it.roomId == roomId && it.memberSignature == signature }?.let { return it.userIds }
        val service = chatbotApiServiceFactory.createForUnsealApi(matrixClient)
        val userIds = service.getRoomAgents(roomId.value)
            .getOrElse { return emptySet() }
            .agents
            .mapNotNull { it.mxid }
            .map(::UserId)
            .toSet()
        cache = Cache(roomId, signature, userIds)
        return userIds
    }

    private data class Cache(val roomId: RoomId, val memberSignature: String, val userIds: Set<UserId>)
}
```

- [ ] **Step 3: Filter ordinary room member targets**

In `RoomKeyRecoveryTimelineRunner.recoverVisibleItems`, compute room agents and filter ordinary member targets:

```kotlin
val roomAgentUserIds = roomAgentResolver.roomAgentUserIds(roomId, activeMemberIds)
val ordinaryMemberTargets = roomMembers
    .filter { it.userId != sessionId }
    .filter { member ->
        requests.any { it.senderUserId == member.userId } || member.userId !in roomAgentUserIds
    }
    .map { RoomKeyRecoveryTarget(userId = it.userId, deviceId = null) }
```

Keep behavior unchanged when resolver returns empty set.

- [ ] **Step 4: Run and pass resolver/filter tests**

Run:

```bash
env -u http_proxy -u https_proxy -u all_proxy -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u NO_PROXY -u no_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:messages:impl:testDebugUnitTest --tests 'io.element.android.features.messages.impl.roomkey.RoomAgentResolverTest' --tests 'io.element.android.features.messages.impl.roomkey.RoomKeyRecoveryTimelineRunnerTest'
git diff --check
```

Expected: PASS.

- [ ] **Step 5: Commit Task 3**

```bash
git add features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/roomkey features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/roomkey
git commit -m "feat: filter room agents from member key recovery"
```

### Task 4: Timeline Direct Agent Recovery Integration

**Files:**
- Modify: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/roomkey/RoomKeyRecoveryTimelineRunner.kt`
- Modify: `features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/roomkey/RoomKeyRecoveryTimelineRunnerTest.kt`
- Modify: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/TimelinePresenter.kt`
- Modify affected factory or DI construction only if required by constructor changes.

- [ ] **Step 1: Add failing timeline runner tests**

Add tests:

- Verified agent UTD calls `MatrixClient.requestAgentRoomKeyRecovery` exactly once.
- NotVerified agent UTD does not call direct agent request.
- Repeated `recoverVisibleItems` within 60 seconds does not send again.
- `retry(roomKeyRequest)` clears matching agent pending and sends direct request again.
- Non-agent UTD still uses ordinary `EncryptionService.requestRoomKeyRecovery`.

- [ ] **Step 2: Extend runner constructor**

Add dependencies:

```kotlin
private val matrixClient: MatrixClient,
private val roomAgentResolver: RoomAgentResolver,
```

Keep `EncryptionService` for ordinary recovery. Use `matrixClient.requestAgentRoomKeyRecovery(agentRequest)` for direct agent recovery.

- [ ] **Step 3: Parse agent requests from timeline items**

Inside `MatrixTimelineItem.roomKeyRecoveryRequest`, also build an agent request from:

- `event.timelineItemDebugInfoProvider().originalJson`
- current room ID fallback
- `event.sender`
- `UnableToDecryptContent.Data.MegolmV1AesSha2.sessionId`

The agent parser should return null for non-agent events.

- [ ] **Step 4: Send direct agent requests when verified**

In `recoverVisibleItems`:

- If `sessionVerifiedStatus != SessionVerifiedStatus.Verified`, do not send agent requests.
- Retain only visible agent requests in `AgentRoomKeyRecoveryPendingStore`.
- For each visible agent request where `markPendingIfNeeded` returns true, call `matrixClient.requestAgentRoomKeyRecovery`.
- If direct send fails, keep ordinary recovery status failed/retry semantics by allowing the existing ordinary status map to show failed after recovery plan exhaustion.

- [ ] **Step 5: Clear agent pending on manual retry**

In `retry(request)`:

- Find matching visible agent request by ordinary identity key `(roomId, sessionId, senderKey)`.
- Remove it from `AgentRoomKeyRecoveryPendingStore`.
- Call direct agent request again after ordinary retry is scheduled.

- [ ] **Step 6: Run and pass timeline integration tests**

Run:

```bash
env -u http_proxy -u https_proxy -u all_proxy -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u NO_PROXY -u no_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:messages:impl:testDebugUnitTest --tests 'io.element.android.features.messages.impl.roomkey.RoomKeyRecoveryTimelineRunnerTest'
git diff --check
```

Expected: PASS.

- [ ] **Step 7: Commit Task 4**

```bash
git add features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/roomkey features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/roomkey features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline
git commit -m "feat: request agent room keys from timeline"
```

### Task 5: Final Verification And Runnable Build

- [ ] **Step 1: Run feature acceptance commands**

Run:

```bash
env -u http_proxy -u https_proxy -u all_proxy -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u NO_PROXY -u no_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :libraries:matrix:impl:testDebugUnitTest :features:messages:impl:testDebugUnitTest :features:messages:impl:assembleDebug
```

Expected: PASS.

- [ ] **Step 2: Run full runnable APK build**

Run:

```bash
env -u http_proxy -u https_proxy -u all_proxy -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u NO_PROXY -u no_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :app:assembleFdroidDebug
```

Expected: PASS and APKs under `app/build/outputs/apk/fdroid/debug/`.

- [ ] **Step 3: Record APK checksum**

Run:

```bash
shasum -a 256 app/build/outputs/apk/fdroid/debug/app-fdroid-universal-debug.apk
ls -lh app/build/outputs/apk/fdroid/debug/app-fdroid-universal-debug.apk
```

- [ ] **Step 4: Check diff**

Run:

```bash
git diff --check
git status --short
```

- [ ] **Step 5: Mark plan complete and commit verification**

Update this plan with verification evidence and the APK checksum, then commit:

```bash
git add docs/superpowers/plans/2026-06-09-agent-room-key-recovery.md
git commit -m "docs: verify agent room key recovery"
```

## Self-Review

Spec coverage: This plan covers `agent-room-key-recovery` only. It depends on the already implemented ordinary `encrypted-room-key-recovery` timeline card and status model, and it does not implement unrelated agent management, vault, skills, local runtime, or rich rendering.

Placeholder scan: No task uses `TBD`, deferred placeholders, or unspecified test commands. Each task has exact files, test expectations, and commit commands.

Type consistency: `AgentRoomKeyRecoveryRequest`, `AgentRoomKeyRecoveryTarget`, `AgentRoomKeyRecoveryRequestParser`, `AgentRoomKeyRecoveryPendingStore`, and `RoomAgentResolver` names are consistent across tasks.

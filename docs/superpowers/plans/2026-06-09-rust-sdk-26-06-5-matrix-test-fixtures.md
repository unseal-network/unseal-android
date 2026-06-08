# Rust SDK 26.06.5 Matrix Test Fixtures Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore `:libraries:matrix:impl` unit-test compilation after moving Android to Matrix Rust SDK `26.06.5`.

**Architecture:** Keep runtime behavior unchanged. Adapt only Matrix impl tests and test fixtures to the public API present in `io.github.rayson-pagepeek.matrix.rustcomponents:sdk-android:26.06.5`; when the runtime wrapper is already degraded because an FFI surface disappeared, update tests to assert that current degraded contract instead of recreating removed SDK types.

**Tech Stack:** Kotlin, Gradle, Matrix Rust Components Kotlin AAR `26.06.5`, `javap`, Truth, Turbine.

---

## File Structure

- Modify `libraries/matrix/impl/src/test/kotlin/io/element/android/libraries/matrix/impl/RustClientSessionDelegateTest.kt`
  - Remove tests for removed `ClientSessionDelegate.onBackgroundTaskErrorReport`.
- Modify `libraries/matrix/impl/src/test/kotlin/io/element/android/libraries/matrix/impl/RustHomeserverCapabilitiesProviderTest.kt`
  - Align tests with the conservative no-FFI provider.
- Modify `libraries/matrix/impl/src/test/kotlin/io/element/android/libraries/matrix/impl/auth/AuthenticationExceptionMappingTest.kt`
  - Replace removed `OAuthException` cases with current `OidcException`/available exception coverage, or remove obsolete OAuth-specific assertions if no constructible subtype exists.
- Modify `libraries/matrix/impl/src/test/kotlin/io/element/android/libraries/matrix/impl/auth/qrlogin/QrErrorMapperTest.kt`
  - Replace removed `OAuthMetadataInvalid` expectation with the current SDK error type.
- Modify `libraries/matrix/impl/src/test/kotlin/io/element/android/libraries/matrix/impl/oauth/AccountManagementActionKtTest.kt`
  - Update expected Rust action names to `SessionEnd`, `SessionView`, and `SessionsList`.
- Modify `libraries/matrix/impl/src/test/kotlin/io/element/android/libraries/matrix/impl/fixtures/factories/*.kt`
  - Remove constructor arguments removed from SDK `26.06.5` and add required arguments such as `RoomInfo.isPinned` and `Session.oidcData`.
- Modify `libraries/matrix/impl/src/test/kotlin/io/element/android/libraries/matrix/impl/fixtures/fakes/*.kt`
  - Update fake overrides to SDK `26.06.5` signatures and remove fakes for removed SDK classes.
- Modify `libraries/matrix/impl/src/test/kotlin/io/element/android/libraries/matrix/impl/room/threads/RustThreadsListServiceTest.kt`
  - Assert the current degraded no-op `RustThreadsListService` contract.
- Modify `libraries/matrix/impl/src/test/kotlin/io/element/android/libraries/matrix/impl/timeline/RustTimelineTest.kt`
  - Use `RoomPaginationStatus` instead of removed `PaginationStatus`.
- Modify `libraries/matrix/impl/src/test/kotlin/io/element/android/libraries/matrix/impl/room/RoomInfoMapperTest.kt`
  - Remove assertions that depend on removed RTC consensus FFI fields.

Do not modify production code unless a compile failure proves a test-only change cannot express the existing runtime contract.

### Task 1: Remove Removed Client Session Background Error Tests

**Files:**
- Modify: `libraries/matrix/impl/src/test/kotlin/io/element/android/libraries/matrix/impl/RustClientSessionDelegateTest.kt`

- [x] **Step 1: Delete obsolete imports and tests**

Remove these imports:

```kotlin
import io.element.android.libraries.matrix.impl.core.SdkBackgroundTaskError
import io.element.android.services.analytics.api.AnalyticsService
import io.element.android.services.analytics.test.FakeAnalyticsService
import uniffi.matrix_sdk_common.BackgroundTaskFailureReason
```

Delete the two tests named:

```kotlin
fun `onBackgroundTaskErrorReport reports the error to analytics if recoverable`() = runTest
fun `onBackgroundTaskErrorReport reports the error to analytics and throws it if it's a panic`() = runTest
```

Change helper signature from:

```kotlin
fun TestScope.aRustClientSessionDelegate(
    sessionStore: SessionStore = InMemorySessionStore(),
    analyticsService: AnalyticsService = FakeAnalyticsService(),
) = RustClientSessionDelegate(
    sessionStore = sessionStore,
    appCoroutineScope = this,
    analyticsService = analyticsService,
)
```

to:

```kotlin
fun TestScope.aRustClientSessionDelegate(
    sessionStore: SessionStore = InMemorySessionStore(),
) = RustClientSessionDelegate(
    sessionStore = sessionStore,
    appCoroutineScope = this,
)
```

- [x] **Step 2: Run targeted compilation**

Run:

```bash
env -u http_proxy -u https_proxy -u all_proxy -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u NO_PROXY -u no_proxy \
  JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools \
  ./gradlew --no-daemon --no-configuration-cache :libraries:matrix:impl:compileDebugUnitTestKotlin
```

Expected: this may still FAIL, but there must be no remaining errors in `RustClientSessionDelegateTest.kt`.

### Task 2: Align Conservative Homeserver Capabilities Tests

**Files:**
- Modify: `libraries/matrix/impl/src/test/kotlin/io/element/android/libraries/matrix/impl/RustHomeserverCapabilitiesProviderTest.kt`
- Delete if unused by the test tree: `libraries/matrix/impl/src/test/kotlin/io/element/android/libraries/matrix/impl/fixtures/fakes/FakeFfiHomeserverCapabilities.kt`

- [x] **Step 1: Replace FFI-backed tests**

Replace the test file body with tests for the current provider contract:

```kotlin
class RustHomeserverCapabilitiesProviderTest {
    @Test
    fun `refresh succeeds without FFI capabilities`() = runTest {
        val provider = RustHomeserverCapabilitiesProvider()

        assertThat(provider.refresh().isSuccess).isTrue()
    }

    @Test
    fun `canChangeDisplayName returns conservative true`() = runTest {
        val provider = RustHomeserverCapabilitiesProvider()

        assertThat(provider.canChangeDisplayName().getOrNull()).isTrue()
    }

    @Test
    fun `canChangeAvatarUrl returns conservative true`() = runTest {
        val provider = RustHomeserverCapabilitiesProvider()

        assertThat(provider.canChangeAvatarUrl().getOrNull()).isTrue()
    }
}
```

- [x] **Step 2: Remove obsolete fake if unused**

Run:

```bash
rg -n "FakeFfiHomeserverCapabilities" libraries/matrix/impl/src/test/kotlin
```

If the only match is `fixtures/fakes/FakeFfiHomeserverCapabilities.kt`, delete that file.

### Task 3: Update Fixture Constructors To SDK 26.06.5

**Files:**
- Modify fixture factory files under `libraries/matrix/impl/src/test/kotlin/io/element/android/libraries/matrix/impl/fixtures/factories/`
- Modify related fake files under `libraries/matrix/impl/src/test/kotlin/io/element/android/libraries/matrix/impl/fixtures/fakes/`

- [x] **Step 1: Remove constructor arguments no longer in `26.06.5`**

Apply these mechanical updates:

```text
EventTimelineItem.kt: remove `eventTypeRaw = ...`
NotificationItem.kt: remove `rawEvent`, `isDm`, `serviceMembers`, and `activeServiceMembersCount`
RoomInfo.kt: remove `serviceMembers`, `isLowPriority`, `activeRoomCallConsensusIntent`, `activeServiceMembersCount`, `isDm`, and `fullyReadEventId`; add `isPinned = false`
RoomMember.kt: remove `isServiceMember`
RoomPowerLevelsValues.kt and FakeFfiRoomPowerLevels.kt: remove `beacon` and `beaconInfo`
Session.kt: replace `oauthData = ...` with `oidcData = ...`
SpaceRoom.kt: replace `isDm = ...` with `isDirect = ...`
```

- [x] **Step 2: Update fake client signatures**

In `FakeFfiClient.kt`:

```kotlin
// Remove HomeserverCapabilities imports, property, and override.
override suspend fun setPusher(
    identifiers: PusherIdentifiers,
    kind: PusherKind,
    appDisplayName: String,
    deviceDisplayName: String,
    profileTag: String?,
    lang: String,
) = Unit
```

In `FakeFfiSpaceRoomList.kt`, remove `suspend` from:

```kotlin
override fun rooms(): List<SpaceRoom> = rooms.invoke()
override fun subscribeToRoomUpdate(listener: SpaceRoomListEntriesListener): TaskHandle
```

- [x] **Step 3: Run targeted compilation**

Run the same `:libraries:matrix:impl:compileDebugUnitTestKotlin` command.

Expected: this may still FAIL, but the files changed in this task should no longer appear in compiler errors.

### Task 4: Align Timeline, OAuth, Account Management, And Threads Tests

**Files:**
- Modify: `libraries/matrix/impl/src/test/kotlin/io/element/android/libraries/matrix/impl/timeline/RustTimelineTest.kt`
- Modify: `libraries/matrix/impl/src/test/kotlin/io/element/android/libraries/matrix/impl/fixtures/fakes/FakeFfiTimeline.kt`
- Delete or adapt: `libraries/matrix/impl/src/test/kotlin/io/element/android/libraries/matrix/impl/fixtures/fakes/FakeFfiTimelineEventFilter.kt`
- Modify: `libraries/matrix/impl/src/test/kotlin/io/element/android/libraries/matrix/impl/room/FakeTimelineEventFilterFactory.kt`
- Modify: `libraries/matrix/impl/src/test/kotlin/io/element/android/libraries/matrix/impl/oauth/AccountManagementActionKtTest.kt`
- Modify: `libraries/matrix/impl/src/test/kotlin/io/element/android/libraries/matrix/impl/room/threads/RustThreadsListServiceTest.kt`

- [x] **Step 1: Replace pagination status type**

Replace:

```kotlin
import uniffi.matrix_sdk.PaginationStatus
```

with:

```kotlin
import uniffi.matrix_sdk.RoomPaginationStatus
```

and replace test values:

```kotlin
PaginationStatus.Paginating
PaginationStatus.Idle(hitTimelineStart = false)
```

with:

```kotlin
RoomPaginationStatus.Paginating
RoomPaginationStatus.Idle(hitTimelineStart = false)
```

- [x] **Step 2: Update account management expected actions**

Replace expected Rust actions:

```kotlin
RustAccountManagementAction.DeviceDelete(A_DEVICE_ID.value)
RustAccountManagementAction.DeviceView(A_DEVICE_ID.value)
RustAccountManagementAction.DevicesList
```

with:

```kotlin
RustAccountManagementAction.SessionEnd(A_DEVICE_ID.value)
RustAccountManagementAction.SessionView(A_DEVICE_ID.value)
RustAccountManagementAction.SessionsList
```

- [x] **Step 3: Replace threads tests with no-op contract tests**

Replace `RustThreadsListServiceTest` with tests that construct `RustThreadsListService()` and assert:

```kotlin
service.subscribeToItemUpdates().test {
    assertThat(awaitItem()).isEmpty()
}
service.subscribeToPaginationUpdates().test {
    assertThat(awaitItem()).isEqualTo(ThreadListPaginationStatus.Idle(hasMoreToLoad = false))
}
assertThat(service.paginate().isSuccess).isTrue()
assertThat(service.reset().isSuccess).isTrue()
service.destroy()
```

Delete `FakeFfiThreadListService.kt` if no other test references it.

### Task 5: Final Matrix Impl Verification

**Files:**
- All files changed in previous tasks.

- [x] **Step 1: Compile Matrix impl unit tests**

Run:

```bash
env -u http_proxy -u https_proxy -u all_proxy -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u NO_PROXY -u no_proxy \
  JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools \
  ./gradlew --no-daemon --no-configuration-cache :libraries:matrix:impl:compileDebugUnitTestKotlin
```

Expected: PASS.

- [x] **Step 2: Run Matrix impl unit tests**

Run:

```bash
env -u http_proxy -u https_proxy -u all_proxy -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u NO_PROXY -u no_proxy \
  JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools \
  ./gradlew --no-daemon --no-configuration-cache :libraries:matrix:impl:testDebugUnitTest
```

Expected: PASS.

- [x] **Step 3: Re-run runnable APK build**

Run:

```bash
env -u http_proxy -u https_proxy -u all_proxy -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u NO_PROXY -u no_proxy \
  JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools \
  ./gradlew --no-daemon --no-configuration-cache :app:assembleFdroidDebug
```

Expected: PASS.

- [x] **Step 4: Commit the fixture adaptation**

Run:

```bash
git add libraries/matrix/impl/src/test/kotlin docs/superpowers/plans/2026-06-09-rust-sdk-26-06-5-matrix-test-fixtures.md
git commit -m "test: adapt matrix fixtures to rust sdk 26.06.5"
```

Expected: one commit containing only test/fixture changes and this plan.

Verification performed:

```text
:libraries:matrix:impl:compileDebugUnitTestKotlin PASS
:libraries:matrix:impl:testDebugUnitTest PASS
:app:assembleFdroidDebug PASS
Generated APKs:
app/build/outputs/apk/fdroid/debug/app-fdroid-arm64-v8a-debug.apk
app/build/outputs/apk/fdroid/debug/app-fdroid-universal-debug.apk
```

## Self-Review

Spec coverage: This plan is a verification completion for `rust-sdk-26-06-5-build`; it does not implement a new migrated feature and therefore does not advance past `session-verification` in the feature queue.

Placeholder scan: No `TBD`, `TODO`, `FIXME`, or placeholder steps remain.

Type consistency: All mentioned SDK replacement names were verified from the `26.06.5` AAR with `javap` or from current production code.

# Secure Backup Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate Android secure backup and recovery behavior to match the iOS secure backup controller and recovery key flows.

**Architecture:** Keep Android's existing `features/securebackup` Compose presenters and `EncryptionService` abstraction. Align behavior by tightening presenter state transitions and test seams, not by introducing a new controller layer.

**Tech Stack:** Kotlin, Compose presenter tests with Molecule/Turbine, Matrix Rust SDK wrapper through `EncryptionService`, Gradle unit tests.

---

## File Structure

- Modify: `features/securebackup/impl/src/main/kotlin/io/element/android/features/securebackup/impl/root/SecureBackupRootPresenter.kt`
  - Responsibility: handle root screen key backup enable and remote backup state actions.
- Modify: `features/securebackup/impl/src/test/kotlin/io/element/android/features/securebackup/impl/root/SecureBackupRootPresenterTest.kt`
  - Responsibility: verify root presenter success/error behavior against iOS semantics.
- Modify: `features/securebackup/impl/src/main/kotlin/io/element/android/features/securebackup/impl/setup/SecureBackupSetupPresenter.kt`
  - Responsibility: handle setup/change recovery key SDK progress and failures.
- Modify: `features/securebackup/impl/src/test/kotlin/io/element/android/features/securebackup/impl/setup/SecureBackupSetupPresenterTest.kt`
  - Responsibility: verify recovery setup progress/error behavior against iOS semantics.
- Modify: `libraries/matrix/test/src/main/kotlin/io/element/android/libraries/matrix/test/encryption/FakeEncryptionService.kt`
  - Responsibility: allow tests to inject `resetRecoveryKey()` failures.
- Verify only: `features/securebackup/impl/src/main/kotlin/io/element/android/features/securebackup/impl/enter/SecureBackupEnterRecoveryKeyPresenter.kt`
  - Responsibility: existing recovery confirmation behavior should keep passing.

## iOS Reference Summary

Use these iOS files as the behavioral source of truth:

- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Services/SecureBackup/SecureBackupController.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/SecureBackup/SecureBackupScreen/SecureBackupScreenViewModel.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/SecureBackup/SecureBackupRecoveryKeyScreen/SecureBackupRecoveryKeyScreenViewModel.swift`

Required iOS behaviors for this plan:

- `enableBackups()` failure `BackupExistsOnServer` is treated as success.
- `enableRecovery()` progress `RoomKeyUploadError` fails recovery key generation.
- `enableRecovery()` failure `BackupExistsOnServer` remains distinguishable so the UI can point to identity reset.
- `resetRecoveryKey()` failure is handled as a recovery key generation error.

### Task 1: Root Key Backup Enable Semantics

**Files:**
- Modify: `features/securebackup/impl/src/test/kotlin/io/element/android/features/securebackup/impl/root/SecureBackupRootPresenterTest.kt`
- Modify: `features/securebackup/impl/src/main/kotlin/io/element/android/features/securebackup/impl/root/SecureBackupRootPresenter.kt`

- [ ] **Step 1: Write failing test for BackupExistsOnServer-as-success**

Add this import to `SecureBackupRootPresenterTest.kt`:

```kotlin
import io.element.android.libraries.matrix.api.encryption.RecoveryException
```

Add this test inside `SecureBackupRootPresenterTest`:

```kotlin
    @Test
    fun `present - enable key storage treats backup exists on server as success`() = runTest {
        val encryptionService = FakeEncryptionService().apply {
            givenEnableBackupsFailure(RecoveryException.BackupExistsOnServer)
        }
        val presenter = createSecureBackupRootPresenter(
            encryptionService = encryptionService,
        )
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            skipItems(2)
            val initialState = awaitItem()
            initialState.eventSink(SecureBackupRootEvents.EnableKeyStorage)
            assertThat(awaitItem().enableAction.isLoading()).isTrue()
            assertThat(awaitItem().enableAction.isSuccess()).isTrue()
        }
    }
```

- [ ] **Step 2: Write failing test for generic enable failure**

Add this test inside `SecureBackupRootPresenterTest`:

```kotlin
    @Test
    fun `present - enable key storage keeps generic failures visible`() = runTest {
        val encryptionService = FakeEncryptionService().apply {
            givenEnableBackupsFailure(AN_EXCEPTION)
        }
        val presenter = createSecureBackupRootPresenter(
            encryptionService = encryptionService,
        )
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            skipItems(2)
            val initialState = awaitItem()
            initialState.eventSink(SecureBackupRootEvents.EnableKeyStorage)
            assertThat(awaitItem().enableAction.isLoading()).isTrue()
            assertThat(awaitItem().enableAction).isEqualTo(AsyncAction.Failure(AN_EXCEPTION))
        }
    }
```

- [ ] **Step 3: Run root tests and verify failure**

Run:

```bash
env -u http_proxy -u https_proxy -u all_proxy -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u NO_PROXY -u no_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:securebackup:impl:testDebugUnitTest --tests io.element.android.features.securebackup.impl.root.SecureBackupRootPresenterTest
```

Expected: the new `BackupExistsOnServer` test fails because `enableBackup()` currently propagates all failures.

- [ ] **Step 4: Implement iOS-compatible enable handling**

In `SecureBackupRootPresenter.kt`, add this import:

```kotlin
import io.element.android.libraries.matrix.api.encryption.RecoveryException
```

Replace `enableBackup` with:

```kotlin
    private fun CoroutineScope.enableBackup(action: MutableState<AsyncAction<Unit>>) = launch {
        suspend {
            Timber.tag(loggerTagDisable.value).d("Calling encryptionService.enableBackups()")
            encryptionService.enableBackups()
                .recoverCatching { exception ->
                    if (exception is RecoveryException.BackupExistsOnServer) {
                        Timber.tag(loggerTagDisable.value).i("Backup already exists on server; treating key storage as enabled.")
                        Unit
                    } else {
                        throw exception
                    }
                }
                .getOrThrow()
        }.runCatchingUpdatingState(action)
    }
```

- [ ] **Step 5: Run root tests and verify pass**

Run the command from Step 3.

Expected: `SecureBackupRootPresenterTest` passes.

- [ ] **Step 6: Commit Task 1**

```bash
git add features/securebackup/impl/src/main/kotlin/io/element/android/features/securebackup/impl/root/SecureBackupRootPresenter.kt features/securebackup/impl/src/test/kotlin/io/element/android/features/securebackup/impl/root/SecureBackupRootPresenterTest.kt
git commit -m "fix: align secure backup enable retry state"
```

### Task 2: Recovery Setup Progress And Error Semantics

**Files:**
- Modify: `features/securebackup/impl/src/test/kotlin/io/element/android/features/securebackup/impl/setup/SecureBackupSetupPresenterTest.kt`
- Modify: `features/securebackup/impl/src/main/kotlin/io/element/android/features/securebackup/impl/setup/SecureBackupSetupPresenter.kt`

- [ ] **Step 1: Write failing test for room key upload error**

Add this test inside `SecureBackupSetupPresenterTest`:

```kotlin
    @Test
    fun `present - room key upload error fails recovery setup`() = runTest {
        val encryptionService = FakeEncryptionService(
            enableRecoveryLambda = { Result.success(Unit) },
        )
        val presenter = createSecureBackupSetupPresenter(
            encryptionService = encryptionService
        )
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            val initialState = awaitItem()
            initialState.eventSink.invoke(SecureBackupSetupEvents.CreateRecoveryKey)
            val creatingState = awaitItem()
            assertThat(creatingState.setupState).isEqualTo(SetupState.Creating)
            encryptionService.emitEnableRecoveryProgress(EnableRecoveryProgress.RoomKeyUploadError)
            val failedState = awaitItem()
            assertThat(failedState.setupState).isInstanceOf(SetupState.Error::class.java)
        }
    }
```

- [ ] **Step 2: Write failing test for BackupExistsOnServer setup error**

Add this import:

```kotlin
import io.element.android.libraries.matrix.api.encryption.RecoveryException
```

Add this test inside `SecureBackupSetupPresenterTest`:

```kotlin
    @Test
    fun `present - backup exists on server remains distinguishable while setting up recovery`() = runTest {
        val encryptionService = FakeEncryptionService(
            enableRecoveryLambda = { Result.failure(RecoveryException.BackupExistsOnServer) }
        )
        val presenter = createSecureBackupSetupPresenter(
            isChangeRecoveryKeyUserStory = false,
            encryptionService = encryptionService
        )
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            val initialState = awaitItem()
            initialState.eventSink(SecureBackupSetupEvents.CreateRecoveryKey)
            assertThat(awaitItem().setupState).isEqualTo(SetupState.Creating)
            val failedState = awaitItem()
            assertThat((failedState.setupState as SetupState.Error).exception).isEqualTo(RecoveryException.BackupExistsOnServer)
        }
    }
```

- [ ] **Step 3: Run setup tests and verify failure**

Run:

```bash
env -u http_proxy -u https_proxy -u all_proxy -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u NO_PROXY -u no_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:securebackup:impl:testDebugUnitTest --tests io.element.android.features.securebackup.impl.setup.SecureBackupSetupPresenterTest
```

Expected: the room-key-upload-error test fails because `RoomKeyUploadError` is currently ignored. The `BackupExistsOnServer` test may already pass; keep it to lock iOS-specific error identity.

- [ ] **Step 4: Implement room key upload error transition**

In `SecureBackupSetupPresenter.kt`, replace the `EnableRecoveryProgress` `when` branch in `observeEncryptionService` with:

```kotlin
            when (enableRecoveryProgress) {
                is EnableRecoveryProgress.Starting,
                is EnableRecoveryProgress.CreatingBackup,
                is EnableRecoveryProgress.CreatingRecoveryKey,
                is EnableRecoveryProgress.BackingUp -> Unit
                is EnableRecoveryProgress.RoomKeyUploadError ->
                    stateAndDispatch.dispatchAction(
                        SecureBackupSetupStateMachine.Event.SdkError(
                            IllegalStateException("Room key upload failed while enabling recovery")
                        )
                    )
                is EnableRecoveryProgress.Done ->
                    stateAndDispatch.dispatchAction(SecureBackupSetupStateMachine.Event.SdkHasCreatedKey(enableRecoveryProgress.recoveryKey))
            }
```

- [ ] **Step 5: Run setup tests and verify pass**

Run the command from Step 3.

Expected: `SecureBackupSetupPresenterTest` passes.

- [ ] **Step 6: Commit Task 2**

```bash
git add features/securebackup/impl/src/main/kotlin/io/element/android/features/securebackup/impl/setup/SecureBackupSetupPresenter.kt features/securebackup/impl/src/test/kotlin/io/element/android/features/securebackup/impl/setup/SecureBackupSetupPresenterTest.kt
git commit -m "fix: align recovery setup upload errors"
```

### Task 3: Change Recovery Key Failure Test Seam

**Files:**
- Modify: `libraries/matrix/test/src/main/kotlin/io/element/android/libraries/matrix/test/encryption/FakeEncryptionService.kt`
- Modify: `features/securebackup/impl/src/test/kotlin/io/element/android/features/securebackup/impl/setup/SecureBackupSetupPresenterTest.kt`

- [ ] **Step 1: Add configurable resetRecoveryKey failure seam**

In `FakeEncryptionService.kt`, add a private field near `recoverFailure`:

```kotlin
    private var resetRecoveryKeyFailure: Exception? = null
```

Add this helper near `givenRecoverFailure`:

```kotlin
    fun givenResetRecoveryKeyFailure(exception: Exception?) {
        resetRecoveryKeyFailure = exception
    }
```

Replace `resetRecoveryKey()` with:

```kotlin
    override suspend fun resetRecoveryKey(): Result<String> = simulateLongTask {
        resetRecoveryKeyFailure?.let { return Result.failure(it) }
        return Result.success(FAKE_RECOVERY_KEY)
    }
```

- [ ] **Step 2: Add change recovery key failure test**

Add this test inside `SecureBackupSetupPresenterTest`:

```kotlin
    @Test
    fun `present - change recovery key failure returns to initial after dismiss`() = runTest {
        val encryptionService = FakeEncryptionService().apply {
            givenResetRecoveryKeyFailure(IllegalStateException("Reset failed"))
        }
        val presenter = createSecureBackupSetupPresenter(
            isChangeRecoveryKeyUserStory = true,
            encryptionService = encryptionService
        )
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            val initialState = awaitItem()
            initialState.eventSink.invoke(SecureBackupSetupEvents.CreateRecoveryKey)
            assertThat(awaitItem().setupState).isEqualTo(SetupState.Creating)
            val failedState = awaitItem()
            assertThat(failedState.setupState).isInstanceOf(SetupState.Error::class.java)
            failedState.eventSink.invoke(SecureBackupSetupEvents.DismissDialog)
            assertThat(awaitItem().setupState).isEqualTo(SetupState.Init)
        }
    }
```

- [ ] **Step 3: Run setup tests and verify pass**

Run:

```bash
env -u http_proxy -u https_proxy -u all_proxy -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u NO_PROXY -u no_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:securebackup:impl:testDebugUnitTest --tests io.element.android.features.securebackup.impl.setup.SecureBackupSetupPresenterTest
```

Expected: `SecureBackupSetupPresenterTest` passes.

- [ ] **Step 4: Commit Task 3**

```bash
git add libraries/matrix/test/src/main/kotlin/io/element/android/libraries/matrix/test/encryption/FakeEncryptionService.kt features/securebackup/impl/src/test/kotlin/io/element/android/features/securebackup/impl/setup/SecureBackupSetupPresenterTest.kt
git commit -m "test: cover recovery key reset failures"
```

### Task 4: Full Verification

**Files:**
- Verify: secure backup implementation and Matrix wrapper tests.

- [ ] **Step 1: Run securebackup module tests**

Run:

```bash
env -u http_proxy -u https_proxy -u all_proxy -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u NO_PROXY -u no_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:securebackup:impl:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run Matrix implementation encryption tests**

Run:

```bash
env -u http_proxy -u https_proxy -u all_proxy -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u NO_PROXY -u no_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :libraries:matrix:impl:testDebugUnitTest --tests 'io.element.android.libraries.matrix.impl.encryption.*'
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Assemble securebackup debug**

Run:

```bash
env -u http_proxy -u https_proxy -u all_proxy -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u NO_PROXY -u no_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:securebackup:impl:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Check whitespace**

Run:

```bash
git diff --check
```

Expected: no output.

- [ ] **Step 5: Commit docs/spec/plan if not already committed**

```bash
git add docs/superpowers/specs/2026-06-08-secure-backup-recovery-design.md docs/superpowers/plans/2026-06-08-secure-backup-recovery.md
git commit -m "docs: plan secure backup recovery migration"
```

If the docs were included in an earlier commit, this command should be skipped after confirming `git status --short` has no doc changes.

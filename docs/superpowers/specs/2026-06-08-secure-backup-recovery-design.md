# Secure Backup Recovery Migration Design

Date: 2026-06-08

## Feature Boundary

Feature name: `secure-backup-recovery`.

User-visible goal: Android secure backup and recovery screens should preserve the iOS behavior for recovery state, key backup state, recovery key setup/change/confirmation, and broken server-backup recovery paths.

Out of scope:

- Encrypted timeline unable-to-decrypt room key recovery cards. That is `encrypted-room-key-recovery`.
- Agent/bot device room key recovery policy. That is `agent-room-key-recovery`.
- New UI design beyond matching the existing Android secure backup screens to iOS behavior.
- Component-library-backed rich rendering or MiniApp behavior.
- New Matrix Rust SDK APIs beyond APIs already exposed through `EncryptionService`.

Dependency class: Matrix Rust SDK dependent.

Blocked status: not blocked by missing component libraries. It depends on the Matrix Rust SDK Android artifact already integrated by `matrix-rust-sdk-artifact-integration`.

## iOS Source References

Primary iOS files:

- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Services/SecureBackup/SecureBackupControllerProtocol.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Services/SecureBackup/SecureBackupController.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Services/Session/UserSession.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/SecureBackup/SecureBackupScreen/SecureBackupScreenViewModel.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/SecureBackup/SecureBackupRecoveryKeyScreen/SecureBackupRecoveryKeyScreenViewModel.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/SecureBackup/SecureBackupKeyBackupScreen/SecureBackupKeyBackupScreenViewModel.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Onboarding/IdentityConfirmationScreen/IdentityConfirmationScreenViewModel.swift`

Core iOS types and states:

- `SecureBackupRecoveryState`: `unknown`, `disabled`, `enabled`, `incomplete`, `settingUp`.
- `SecureBackupKeyBackupState`: `unknown`, `enabling`, `enabled`, `disabling`.
- `SecureBackupControllerError.backupExistsOnServer`: server has a stale/remote backup while local recovery is disabled; user must reset identity instead of silently deleting server state.
- `SecureBackupSteadyState`: logout upload wait state, not part of this first Android pass except preserving existing `waitForBackupUploadSteadyState` mapping.

Important iOS behavior to preserve:

- SDK backup states map to local key backup states:
  - `unknown` -> `unknown`, then iOS performs `backupExistsOnServer()`.
  - `creating` / `enabling` -> `enabling`.
  - `resuming` / `enabled` / `downloading` -> `enabled`.
  - `disabling` -> `disabling`.
- SDK recovery states map to local recovery states:
  - `unknown` -> `unknown`.
  - `enabled` -> `enabled`.
  - `disabled` -> `disabled`.
  - `incomplete` -> `incomplete`.
- When backup state is `unknown`, iOS checks `backupExistsOnServer()`. If true, local key backup is treated as enabled so the UI can offer recovery instead of treating key storage as off.
- Enabling key backup treats SDK `BackupExistsOnServer` as success and refreshes remote backup state. The user should not see a generic enable failure for this account shape.
- Generating a recovery key:
  - If recovery is disabled, iOS calls `enableRecovery(waitForBackupsToUpload: false, passphrase: nil, progressListener:)`.
  - If recovery is not disabled, iOS calls `resetRecoveryKey()`.
  - `starting`, `creatingBackup`, `creatingRecoveryKey`, and `backingUp` mean setup is in progress.
  - `done` means recovery is enabled and the generated key is available.
  - `roomKeyUploadError` means recovery key generation failed.
  - `BackupExistsOnServer` while enabling recovery is surfaced as a specific broken-account path that should point to identity reset, not a generic failure.
- Confirming recovery key calls SDK `recover(recoveryKey:)`; failures keep the user on the confirmation flow with an error.

## Android Existing State

Existing Android modules and files:

- `features/securebackup/api/src/main/kotlin/io/element/android/features/securebackup/api/SecureBackupEntryPoint.kt`
- `features/securebackup/impl/src/main/kotlin/io/element/android/features/securebackup/impl/SecureBackupFlowNode.kt`
- `features/securebackup/impl/src/main/kotlin/io/element/android/features/securebackup/impl/root/SecureBackupRootPresenter.kt`
- `features/securebackup/impl/src/main/kotlin/io/element/android/features/securebackup/impl/root/SecureBackupRootState.kt`
- `features/securebackup/impl/src/main/kotlin/io/element/android/features/securebackup/impl/setup/SecureBackupSetupPresenter.kt`
- `features/securebackup/impl/src/main/kotlin/io/element/android/features/securebackup/impl/setup/SecureBackupSetupStateMachine.kt`
- `features/securebackup/impl/src/main/kotlin/io/element/android/features/securebackup/impl/enter/SecureBackupEnterRecoveryKeyPresenter.kt`
- `features/securebackup/impl/src/main/kotlin/io/element/android/features/securebackup/impl/disable/SecureBackupDisablePresenter.kt`
- `features/securebackup/impl/src/main/kotlin/io/element/android/features/securebackup/impl/reset/ResetIdentityFlowManager.kt`
- `libraries/matrix/api/src/main/kotlin/io/element/android/libraries/matrix/api/encryption/EncryptionService.kt`
- `libraries/matrix/api/src/main/kotlin/io/element/android/libraries/matrix/api/encryption/RecoveryException.kt`
- `libraries/matrix/api/src/main/kotlin/io/element/android/libraries/matrix/api/encryption/BackupState.kt`
- `libraries/matrix/api/src/main/kotlin/io/element/android/libraries/matrix/api/encryption/RecoveryState.kt`
- `libraries/matrix/api/src/main/kotlin/io/element/android/libraries/matrix/api/encryption/EnableRecoveryProgress.kt`
- `libraries/matrix/impl/src/main/kotlin/io/element/android/libraries/matrix/impl/encryption/RustEncryptionService.kt`
- `libraries/matrix/impl/src/main/kotlin/io/element/android/libraries/matrix/impl/encryption/RecoveryExceptionMapper.kt`
- `libraries/matrix/test/src/main/kotlin/io/element/android/libraries/matrix/test/encryption/FakeEncryptionService.kt`

Existing Android behavior already aligned:

- Matrix API exposes `BackupState`, `RecoveryState`, `EnableRecoveryProgress`, and `RecoveryException.BackupExistsOnServer`.
- `SecureBackupRootState.isKeyStorageEnabled` treats `BackupState.UNKNOWN` plus `doesBackupExistOnServer == true` as enabled.
- Recovery setup, change recovery key, enter recovery key, disable key backup, and reset identity flows already exist.
- `RustEncryptionService.recover()` calls `recoverAndFixBackup()` and ignores SDK import errors, matching the recovery/fix intent.

Android gaps to migrate:

- `SecureBackupRootPresenter.enableBackup()` currently treats all `enableBackups()` failures as generic failures. It should treat `RecoveryException.BackupExistsOnServer` as success, matching iOS.
- `SecureBackupSetupPresenter` currently ignores `EnableRecoveryProgress.RoomKeyUploadError`. It should dispatch setup error so the user does not get stuck in `Creating`.
- `SecureBackupSetupPresenter` currently maps every `enableRecovery()` failure to a generic setup error. It should preserve `RecoveryException.BackupExistsOnServer` as a specific broken-account state for UI/tests.
- `FakeEncryptionService.resetRecoveryKey()` cannot currently be configured to fail, making it hard to verify change-recovery error behavior.

## Target Android Behavior

User flows:

- Root secure backup screen:
  - Show key storage as enabled when SDK backup is enabled/resuming/downloading or remote `doesBackupExistOnServer()` returns true while local backup state is unknown.
  - Retry remote backup detection after failure.
  - Tapping enable key storage calls `EncryptionService.enableBackups()`.
  - If `enableBackups()` fails with `RecoveryException.BackupExistsOnServer`, treat the enable action as success.
  - Other enable failures remain user-visible errors.
- Setup recovery:
  - Initial state is idle.
  - Tapping create recovery key enters loading.
  - SDK progress `Starting`, `CreatingBackup`, `CreatingRecoveryKey`, and `BackingUp` keep loading.
  - SDK progress `Done(key)` shows the generated key.
  - SDK progress `RoomKeyUploadError` transitions to error.
  - `RecoveryException.BackupExistsOnServer` while enabling recovery transitions to a specific account-needs-reset error.
- Change recovery key:
  - Calls `EncryptionService.resetRecoveryKey()` and shows generated key on success.
  - Failure transitions to error and can be dismissed back to initial.
- Enter recovery key:
  - Existing recovery key input, validation, visibility toggle, submit, and error behavior should remain.
- Identity reset:
  - Existing reset flow remains the escalation path for the broken server backup state.

Screen states:

- Loading: setup key creation and root enable action.
- Success: generated key shown, root enable action success, recover action success callback.
- Error: generic setup error, generic enable key backup error, and specific `BackupExistsOnServer` setup error.
- Empty/offline/permission denied: no new states in this spec. Existing retry/error states remain.

Navigation entry points:

- Existing root navigation remains through `SecureBackupEntryPoint.InitialTarget.Root`.
- Existing recovery-key confirmation remains through `SecureBackupEntryPoint.InitialTarget.EnterRecoveryKey`.
- Existing reset identity entry remains through `SecureBackupEntryPoint.InitialTarget.ResetIdentity`.

## Data And API Mapping

iOS to Android state mapping:

- `SecureBackupRecoveryState.unknown` -> `RecoveryState.UNKNOWN`.
- `SecureBackupRecoveryState.disabled` -> `RecoveryState.DISABLED`.
- `SecureBackupRecoveryState.enabled` -> `RecoveryState.ENABLED`.
- `SecureBackupRecoveryState.incomplete` -> `RecoveryState.INCOMPLETE`.
- `SecureBackupRecoveryState.settingUp` -> Android setup presenter `SetupState.Creating`.
- `SecureBackupKeyBackupState.unknown` -> `BackupState.UNKNOWN` plus `doesBackupExistOnServer()` fallback.
- `SecureBackupKeyBackupState.enabling` -> `BackupState.CREATING` / `BackupState.ENABLING`.
- `SecureBackupKeyBackupState.enabled` -> `BackupState.RESUMING` / `BackupState.ENABLED` / `BackupState.DOWNLOADING`.
- `SecureBackupKeyBackupState.disabling` -> `BackupState.DISABLING`.

API mapping:

- iOS `secureBackupController.enable()` -> Android `EncryptionService.enableBackups()`.
- iOS `secureBackupController.disable()` -> Android `EncryptionService.disableRecovery()`.
- iOS `secureBackupController.generateRecoveryKey()` with disabled recovery -> Android `EncryptionService.enableRecovery(waitForBackupsToUpload = false)` plus `enableRecoveryProgressStateFlow`.
- iOS `secureBackupController.generateRecoveryKey()` with enabled/incomplete recovery -> Android `EncryptionService.resetRecoveryKey()`.
- iOS `secureBackupController.confirmRecoveryKey(_:)` -> Android `EncryptionService.recover(recoveryKey)`.
- iOS `encryption.backupExistsOnServer()` -> Android `EncryptionService.doesBackupExistOnServer()`.

Error model:

- Preserve `RecoveryException.BackupExistsOnServer` as a first-class error for setup/generate recovery key.
- Treat `RecoveryException.BackupExistsOnServer` from `enableBackups()` as success.
- Treat `EnableRecoveryProgress.RoomKeyUploadError` as setup error.
- Keep other exceptions as generic failures in existing Android dialogs.

Persistence:

- No new local persistence in this spec.

Pagination/streaming:

- No pagination.
- Recovery setup uses the existing `enableRecoveryProgressStateFlow` stream.

## Acceptance Criteria

1. Root presenter treats `enableBackups()` `RecoveryException.BackupExistsOnServer` as success.
2. Root presenter keeps generic failure behavior for non-`BackupExistsOnServer` enable errors.
3. Setup presenter maps `EnableRecoveryProgress.RoomKeyUploadError` to `SetupState.Error`.
4. Setup presenter preserves `RecoveryException.BackupExistsOnServer` as a specific error state that tests can assert.
5. Change recovery key failure is testable through `FakeEncryptionService`.
6. Existing enter recovery key behavior remains passing.
7. `:features:securebackup:impl:testDebugUnitTest` passes.
8. `:libraries:matrix:impl:testDebugUnitTest` passes.
9. `:features:securebackup:impl:assembleDebug` passes.

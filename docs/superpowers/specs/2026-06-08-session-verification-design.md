# Session Verification Design

Date: 2026-06-08

## Feature Boundary

Feature name: `session-verification`.

User-visible goal: Android session verification must match the iOS device/user verification behavior for new-login onboarding, incoming verification requests, outgoing verification requests, SAS challenge comparison, cancellation, failure, retry, and completion.

Dependency class: Matrix Rust SDK dependent.

Blocked by SDK artifact work: no. The `matrix-rust-sdk-artifact-integration` spec is implemented and `:libraries:matrix:impl:assembleDebug` has passed with Android SDK 36.

Blocked by component library migration: no. Android already has native Compose verification modules and Matrix abstractions.

Out of scope:

- Secure backup setup, recovery key entry, recovery key confirmation, and backup upload progress. These belong to `secure-backup-recovery`.
- Encrypted room key recovery and unable-to-decrypt timeline recovery. These belong to `encrypted-room-key-recovery`.
- Agent or bot room key recovery policy. This belongs to `agent-room-key-recovery`.
- Visual redesign of verification screens beyond state and copy needed to match behavior.
- Replacing Android's existing FlowRedux/Presenter architecture with an iOS-style single SwiftState clone.

## iOS Source References

The iOS implementation is the behavioral source of truth for this spec.

Primary verification flow:

- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Onboarding/SessionVerificationScreen/SessionVerificationScreenStateMachine.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Onboarding/SessionVerificationScreen/SessionVerificationScreenViewModel.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Onboarding/SessionVerificationScreen/SessionVerificationScreenModels.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Onboarding/SessionVerificationScreen/SessionVerificationScreenCoordinator.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Onboarding/SessionVerificationScreen/View/SessionVerificationScreen.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Onboarding/SessionVerificationScreen/View/SessionVerificationRequestDetailsView.swift`

Matrix Rust SDK proxy:

- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Services/SessionVerification/SessionVerificationControllerProxy.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Services/SessionVerification/SessionVerificationControllerProxyProtocol.swift`

Session security state and navigation:

- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Services/Session/UserSession.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Services/Session/UserSessionProtocol.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/FlowCoordinators/OnboardingFlowCoordinator.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/FlowCoordinators/UserSessionFlowCoordinator.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Onboarding/IdentityConfirmationScreen/IdentityConfirmationScreenViewModel.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/HomeScreen/HomeScreenViewModel.swift`

Important iOS types and behavior:

- `SessionVerificationScreenFlow`: supports `deviceInitiator`, `deviceResponder`, `userInitiator(userID:)`, and `userResponder(requestDetails:)`.
- `SessionVerificationScreenStateMachine.State`: `initial`, `acceptingVerificationRequest`, `requestingVerification`, `verificationRequestAccepted`, `startingSasVerification`, `sasVerificationStarted`, `showingChallenge`, `acceptingChallenge`, `decliningChallenge`, `verified`, `cancelling`, and `cancelled`.
- `SessionVerificationScreenStateMachine.Event`: accepts, requests, starts SAS, receives challenge, accepts challenge, declines challenge, cancels, fails, and restarts.
- `SessionVerificationScreenViewModel`: acknowledges responder requests immediately, ignores incoming request callbacks inside the screen because higher-level flow handles them, calls Matrix Rust SDK proxy methods only on state transitions, and sends `.finished` when the user ignores, cancels, or completes.
- `SessionVerificationControllerProxy`: maps Matrix Rust SDK delegate callbacks to app actions, converts request details into app models, supports device/user verification requests, acknowledges incoming requests, accepts requests, starts SAS, approves, declines, and cancels.
- iOS currently handles `MatrixRustSDK.SessionVerificationData.emojis`; non-emoji verification data is ignored by the proxy.
- `OnboardingFlowCoordinator`: launches device verification from identity confirmation, marks identity confirmation as run after verification completes, and advances onboarding.
- `UserSessionFlowCoordinator`: presents incoming/outgoing session verification as a sheet when triggered after login.

## Android Existing State

Android already contains a substantial session verification stack.

Entry points and feature modules:

- `/Users/Ruihan/go/src/unseal-android/features/verifysession/api/src/main/kotlin/io/element/android/features/verifysession/api/IncomingVerificationEntryPoint.kt`
- `/Users/Ruihan/go/src/unseal-android/features/verifysession/api/src/main/kotlin/io/element/android/features/verifysession/api/OutgoingVerificationEntryPoint.kt`
- `/Users/Ruihan/go/src/unseal-android/features/verifysession/impl/src/main/kotlin/io/element/android/features/verifysession/impl/incoming/IncomingVerificationPresenter.kt`
- `/Users/Ruihan/go/src/unseal-android/features/verifysession/impl/src/main/kotlin/io/element/android/features/verifysession/impl/incoming/IncomingVerificationStateMachine.kt`
- `/Users/Ruihan/go/src/unseal-android/features/verifysession/impl/src/main/kotlin/io/element/android/features/verifysession/impl/outgoing/OutgoingVerificationPresenter.kt`
- `/Users/Ruihan/go/src/unseal-android/features/verifysession/impl/src/main/kotlin/io/element/android/features/verifysession/impl/outgoing/OutgoingVerificationStateMachine.kt`

Matrix abstraction and Rust SDK adapter:

- `/Users/Ruihan/go/src/unseal-android/libraries/matrix/api/src/main/kotlin/io/element/android/libraries/matrix/api/verification/SessionVerificationService.kt`
- `/Users/Ruihan/go/src/unseal-android/libraries/matrix/api/src/main/kotlin/io/element/android/libraries/matrix/api/verification/SessionVerificationData.kt`
- `/Users/Ruihan/go/src/unseal-android/libraries/matrix/api/src/main/kotlin/io/element/android/libraries/matrix/api/verification/VerificationRequest.kt`
- `/Users/Ruihan/go/src/unseal-android/libraries/matrix/impl/src/main/kotlin/io/element/android/libraries/matrix/impl/verification/RustSessionVerificationService.kt`
- `/Users/Ruihan/go/src/unseal-android/libraries/matrix/test/src/main/kotlin/io/element/android/libraries/matrix/test/verification/FakeSessionVerificationService.kt`

FTUE integration:

- `/Users/Ruihan/go/src/unseal-android/features/ftue/impl/src/main/kotlin/io/element/android/features/ftue/impl/state/DefaultFtueService.kt`
- `/Users/Ruihan/go/src/unseal-android/features/ftue/impl/src/main/kotlin/io/element/android/features/ftue/impl/FtueFlowNode.kt`
- `/Users/Ruihan/go/src/unseal-android/features/ftue/impl/src/main/kotlin/io/element/android/features/ftue/impl/sessionverification/FtueSessionVerificationFlowNode.kt`
- `/Users/Ruihan/go/src/unseal-android/features/ftue/impl/src/main/kotlin/io/element/android/features/ftue/impl/sessionverification/choosemode/ChooseSelfVerificationModePresenter.kt`

Existing tests:

- `/Users/Ruihan/go/src/unseal-android/features/verifysession/impl/src/test/kotlin/io/element/android/features/verifysession/impl/incoming/IncomingVerificationPresenterTest.kt`
- `/Users/Ruihan/go/src/unseal-android/features/verifysession/impl/src/test/kotlin/io/element/android/features/verifysession/impl/incoming/DefaultIncomingVerificationEntryPointTest.kt`
- `/Users/Ruihan/go/src/unseal-android/features/verifysession/impl/src/test/kotlin/io/element/android/features/verifysession/impl/outgoing/OutgoingVerificationPresenterTest.kt`
- `/Users/Ruihan/go/src/unseal-android/features/verifysession/impl/src/test/kotlin/io/element/android/features/verifysession/impl/outgoing/DefaultOutgoingVerificationEntryPointTest.kt`

Reusable Android patterns:

- FlowRedux state machines for incoming and outgoing verification.
- Presenter state projection to Compose UI models.
- `SessionVerificationService` as the Matrix API seam.
- `FakeSessionVerificationService` for deterministic state-machine tests.
- FTUE progression through `DefaultFtueService`.

Observed Android/iOS behavior differences to resolve or explicitly preserve:

- iOS `didFail` while requesting or accepting returns the state to `initial`; Android outgoing currently maps `DidFail` to `Canceled`.
- iOS challenge approve failure returns from `acceptingChallenge` to `showingChallenge`; Android outgoing currently maps `DidFail` to `Canceled`.
- iOS decline success becomes `cancelled`; incoming Android currently treats a `DidCancel` while `RejectingChallenge` as `Failure`.
- iOS ignores incoming request callbacks within the verification screen because higher-level flow handles them; Android listener and entry-point behavior must preserve the same one-active-flow assumption.
- Android supports decimal SAS data and should keep that support. iOS currently ignores non-emoji data, but Android already has UI strings and rendering for decimal comparison; removing it would reduce Android's existing capability without improving iOS parity.

## Target Android Behavior

### User Flows

Device initiator:

1. FTUE or security UI starts outgoing current-session verification.
2. Android resets stale verification state and requests device verification.
3. The screen waits for the other session to accept.
4. After acceptance, the user starts SAS verification.
5. Android displays emoji or decimal challenge data.
6. If the user confirms matching challenge data, Android approves verification and waits for Rust SDK completion.
7. On verified session status, Android shows completion and lets FTUE advance only after the user completes the success step.

User initiator:

1. Android starts outgoing verification for a specific user.
2. The flow uses `requestUserVerification(userId)`.
3. The SAS, cancel, failure, and completion behavior is the same as device initiator, except completion copy uses user-verification language.

Device responder:

1. Higher-level listener receives an incoming verification request.
2. Android presents incoming verification and immediately acknowledges the specific request.
3. If the user ignores the request before accepting, Android exits without cancelling the remote request.
4. If the user accepts, Android calls `acceptVerificationRequest()`.
5. Android waits for challenge data, displays it, and handles confirm/decline/cancel exactly like iOS responder flow.

User responder:

1. Same as device responder, but copy and identity presentation use user-verification language.
2. Android must show sender profile and request details where available.

### State Handling

Android must expose states that preserve the iOS state-machine semantics:

- Initial: ready to request or accept verification.
- Requesting verification: outgoing request in flight.
- Accepting verification request: incoming accept call in flight.
- Verification request accepted: ready to start SAS.
- Starting SAS verification: start call in flight.
- SAS verification started: waiting for challenge data.
- Showing challenge: challenge data visible and user can match or reject.
- Accepting challenge: approval call in flight; UI shows waiting/loading but keeps the challenge visible.
- Declining challenge: decline call in flight; UI shows waiting/loading but keeps the challenge visible.
- Verified/completed: verification completed successfully.
- Cancelling: cancel call in flight.
- Cancelled: local or remote cancellation.
- Failure/retry: request/accept/start/approve/decline failures must land in a state where the user can retry or exit according to the iOS transition.

Required alignment:

- Requesting verification failure returns to initial/retry state, not a terminal cancelled state.
- Accepting incoming verification failure returns to initial/retry state.
- Approve failure while showing challenge returns to showing challenge with the same challenge data.
- Decline failure while showing challenge returns to showing challenge with the same challenge data.
- Decline success is treated as cancellation, not an unrecoverable failure.
- Local cancel from active states calls `cancelVerification()` and exits to cancelled/finished.
- Incoming ignore before accepting exits without cancelling.
- Duplicate challenge data while already verifying is ignored.
- `reset(cancelAnyPendingVerificationAttempt = true)` is used only when an active pending attempt should be cancelled; initial ignore and normal cleanup should not accidentally cancel unrelated higher-level incoming requests.

### Screen States

Android screens must continue to handle:

- loading: unknown session verification status and in-flight request/accept/start/approve/decline/cancel actions.
- empty: not applicable.
- error: recoverable request/action failure with retry or back action.
- success: completed session or user verification.
- offline/network failure: service call failure should land in the retry/failure state described above.
- permission denied: not applicable.
- retry: restart after cancel/fail must reset service flow and state machine without leaving stale Matrix Rust SDK state.

### Navigation Entry Points

Android must keep these entry points:

- FTUE session verification step from `DefaultFtueService`.
- Incoming verification entry point for requests emitted by `SessionVerificationServiceListener`.
- Outgoing verification entry point for current-session and user verification.

This spec does not require adding a new Home security banner entry point. If Android already has one outside the inspected modules, the implementation plan may wire it to the same outgoing verification entry point; otherwise that belongs to a later security-banner spec.

## Data And API Mapping

### iOS To Android Model Mapping

| iOS | Android |
| --- | --- |
| `SessionVerificationScreenFlow.deviceInitiator` | `VerificationRequest.Outgoing.CurrentSession` |
| `SessionVerificationScreenFlow.userInitiator(userID:)` | `VerificationRequest.Outgoing.User(UserId)` |
| `SessionVerificationScreenFlow.deviceResponder(requestDetails:)` | `VerificationRequest.Incoming.OtherSession` |
| `SessionVerificationScreenFlow.userResponder(requestDetails:)` | `VerificationRequest.Incoming.User` |
| `SessionVerificationRequestDetails.senderProfile` | `SessionVerificationRequestDetails.senderProfile` |
| `SessionVerificationRequestDetails.flowID` | `SessionVerificationRequestDetails.flowId` |
| `SessionVerificationRequestDetails.deviceID` | `SessionVerificationRequestDetails.deviceId` |
| `SessionVerificationRequestDetails.deviceDisplayName` | `SessionVerificationRequestDetails.deviceDisplayName` |
| `SessionVerificationRequestDetails.firstSeenDate` | `SessionVerificationRequestDetails.firstSeenTimestamp` |
| `SessionVerificationEmoji(symbol, description)` | `VerificationEmoji(number)` rendered through Android resource mapping |
| `SessionVerificationControllerProxyAction.finished` | `VerificationFlowState.DidFinish` |
| `SessionVerificationControllerProxyAction.cancelled` | `VerificationFlowState.DidCancel` |
| `SessionVerificationControllerProxyAction.failed` | `VerificationFlowState.DidFail` |

### Matrix Rust SDK Calls

Android must route through `SessionVerificationService`:

- `requestDeviceVerification()`
- `requestUserVerification(userId)`
- `acknowledgeVerificationRequest(verificationRequest)`
- `acceptVerificationRequest()`
- `startSasVerification()`
- `approveVerification()`
- `declineVerification()`
- `cancelVerification()`
- `reset(cancelAnyPendingVerificationAttempt)`

The Rust adapter must continue to:

- wait for E2EE initialization before verification APIs.
- create and delegate a `SessionVerificationController`.
- map Rust verification status to `SessionVerifiedStatus.Unknown`, `NotVerified`, and `Verified`.
- emit `DidAcceptVerificationRequest`, `DidStartSasVerification`, `DidReceiveVerificationData`, `DidFinish`, `DidCancel`, and `DidFail`.
- wait for session verified status before emitting `DidFinish`.
- keep decimal SAS support.

### Persistence

No new local database persistence is required.

Existing session preferences remain relevant:

- FTUE can skip verification through `SessionPreferencesStore.isSessionVerificationSkipped()`.
- `DefaultFtueService.onUserCompletedSessionVerification()` must still clear the local "success screen must be acknowledged" flag after the user confirms completion.

### Error Model

Android should keep service APIs throwing/recording failures internally, but presenters/state machines must project failures into explicit UI states:

- request/accept failure: retry from initial.
- start SAS failure: retry from request accepted or exit via cancel.
- approve/decline failure: return to challenge view with same data.
- remote cancel: cancelled.
- local decline success: cancelled.
- local decline failure: challenge remains visible.
- Rust SDK finished but session status never becomes verified within timeout: failure/cancelled state with retry.

## Testing And Acceptance Criteria

Implementation must include focused tests before behavior changes.

Required unit tests:

- `OutgoingVerificationPresenterTest`: request failure returns to initial/retry instead of cancelled.
- `OutgoingVerificationPresenterTest`: approve failure preserves the current challenge data and allows the user to retry.
- `OutgoingVerificationPresenterTest`: decline failure preserves the current challenge data and allows the user to retry.
- `OutgoingVerificationPresenterTest`: verified status before any user-started current-session flow exits automatically unless `showDeviceVerifiedScreen` is true.
- `IncomingVerificationPresenterTest`: ignore before accept exits without calling `cancelVerification()`.
- `IncomingVerificationPresenterTest`: decline success results in canceled/finished behavior, not failure.
- `IncomingVerificationPresenterTest`: accept failure returns to initial/retry state.
- `RustSessionVerificationService` tests or fakes: incoming request details are acknowledged with sender ID and flow ID, and delegate events map to `VerificationFlowState`.
- `DefaultFtueService` tests: not verified status enters `SessionVerification`; completed verification waits for user acknowledgement before proceeding; `onUserCompletedSessionVerification()` clears the acknowledgement gate.

Required build checks:

```bash
JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools \
  ./gradlew --no-daemon --no-configuration-cache :features:verifysession:impl:testDebugUnitTest

JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools \
  ./gradlew --no-daemon --no-configuration-cache :features:ftue:impl:testDebugUnitTest

JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools \
  ./gradlew --no-daemon --no-configuration-cache :libraries:matrix:impl:testDebugUnitTest

JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools \
  ./gradlew --no-daemon --no-configuration-cache :features:verifysession:impl:assembleDebug
```

Network/dependency note:

- If dependency downloads are slow, run Gradle with proxy variables cleared, matching the successful SDK setup:

```bash
env -u http_proxy -u https_proxy -u all_proxy -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u NO_PROXY -u no_proxy \
  JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools \
  ./gradlew --no-daemon --no-configuration-cache :features:verifysession:impl:assembleDebug
```

## Subagent Handoff Notes

This spec is intentionally implementation-ready but not an implementation plan.

The implementation plan must:

1. Start from failing tests in `features/verifysession/impl`.
2. Keep changes scoped to verification state machines, presenters, fakes, and FTUE service tests unless a Matrix service adapter gap is proven.
3. Avoid touching secure backup code.
4. Preserve existing UI resources unless a state needs a missing retry/failure action.
5. Commit after each focused behavior batch.
6. Run the required build checks before finishing.

The first implementation task should audit current Android tests against the iOS state-machine transitions and add failing tests for the behavior differences listed in this spec.

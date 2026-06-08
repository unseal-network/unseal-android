# Session Verification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Align Android session verification behavior with the iOS migration sample while preserving Android's existing verification architecture and decimal SAS support.

**Architecture:** Keep `SessionVerificationService` as the Matrix Rust SDK seam and keep the existing FlowRedux state machines in `features/verifysession/impl`. Add failing presenter tests that encode the iOS state-machine behavior, then adjust outgoing and incoming state-machine failure transitions to return to retryable states instead of terminal cancellation/failure where iOS does. FTUE behavior is already close to the iOS onboarding sample, so the plan adds focused regression tests rather than replacing the service.

**Tech Stack:** Kotlin, FlowRedux, Compose presenters, Turbine, Truth, Matrix Rust SDK Android bindings, Gradle.

---

## File Structure

- Modify `features/verifysession/impl/src/test/kotlin/io/element/android/features/verifysession/impl/outgoing/OutgoingVerificationPresenterTest.kt`
  - Adds regression tests for outgoing request failure, start-SAS failure, approve failure, decline failure, and already-verified current-session behavior.
- Modify `features/verifysession/impl/src/main/kotlin/io/element/android/features/verifysession/impl/outgoing/OutgoingVerificationStateMachine.kt`
  - Changes `DidFail` handling to match iOS retry semantics.
- Modify `features/verifysession/impl/src/test/kotlin/io/element/android/features/verifysession/impl/incoming/IncomingVerificationPresenterTest.kt`
  - Adds regression tests for incoming ignore, accept failure, decline success, and approve/decline failure preserving challenge data.
- Modify `features/verifysession/impl/src/main/kotlin/io/element/android/features/verifysession/impl/incoming/IncomingVerificationStateMachine.kt`
  - Changes `DidCancel` and `DidFail` handling for responder challenge states.
- Modify `features/ftue/impl/src/test/kotlin/io/element/android/features/ftue/impl/DefaultFtueServiceTest.kt`
  - Adds explicit verification-success acknowledgement gate coverage.

No production files outside `features/verifysession/impl` should change unless a failing test proves a Matrix service or FTUE gap.

### Task 1: Add Outgoing Verification Regression Tests

**Files:**
- Modify: `features/verifysession/impl/src/test/kotlin/io/element/android/features/verifysession/impl/outgoing/OutgoingVerificationPresenterTest.kt`

- [ ] **Step 1: Replace the request-failure expectation with the iOS retry behavior**

In `OutgoingVerificationPresenterTest`, replace the existing test named:

```kotlin
fun `present - A fail when requesting verification resets the state to the canceled one`() = runTest {
```

with this test:

```kotlin
@Test
fun `present - A fail when requesting verification resets the state to initial`() = runTest {
    val service = unverifiedSessionService(
        requestDeviceVerificationLambda = { },
    )
    val presenter = createOutgoingVerificationPresenter(service)
    presenter.test {
        awaitItem().eventSink(OutgoingVerificationViewEvents.RequestVerification)
        service.emitVerificationFlowState(VerificationFlowState.DidFail)
        assertThat(awaitItem().step).isInstanceOf(Step.AwaitingOtherDeviceResponse::class.java)
        assertThat(awaitItem().step).isEqualTo(Step.Initial)
    }
}
```

- [ ] **Step 2: Add approve-failure retry coverage**

Add this test after `present - A failure when verifying cancels it`:

```kotlin
@Test
fun `present - A fail when approving verification keeps the challenge visible`() = runTest {
    val emojis = listOf(VerificationEmoji(number = 30))
    val service = unverifiedSessionService(
        requestDeviceVerificationLambda = { },
        startSasVerificationLambda = { },
        approveVerificationLambda = { },
    )
    val presenter = createOutgoingVerificationPresenter(service)
    presenter.test {
        val state = requestVerificationAndAwaitVerifyingState(
            fakeService = service,
            sessionVerificationData = SessionVerificationData.Emojis(emojis),
        )
        state.eventSink(OutgoingVerificationViewEvents.ConfirmVerification)
        assertThat(awaitItem().step).isEqualTo(
            Step.Verifying(
                data = SessionVerificationData.Emojis(emojis),
                submitAction = AsyncData.Loading(),
            )
        )
        service.emitVerificationFlowState(VerificationFlowState.DidFail)
        assertThat(awaitItem().step).isEqualTo(
            Step.Verifying(
                data = SessionVerificationData.Emojis(emojis),
                submitAction = AsyncData.Uninitialized,
            )
        )
    }
}
```

- [ ] **Step 3: Add decline-failure retry coverage**

Add this test after the approve-failure test:

```kotlin
@Test
fun `present - A fail when declining verification keeps the challenge visible`() = runTest {
    val decimals = SessionVerificationData.Decimals(listOf(1234, 5678, 9012))
    val service = unverifiedSessionService(
        requestDeviceVerificationLambda = { },
        startSasVerificationLambda = { },
        declineVerificationLambda = { },
    )
    val presenter = createOutgoingVerificationPresenter(service)
    presenter.test {
        val state = requestVerificationAndAwaitVerifyingState(
            fakeService = service,
            sessionVerificationData = decimals,
        )
        state.eventSink(OutgoingVerificationViewEvents.DeclineVerification)
        assertThat(awaitItem().step).isEqualTo(
            Step.Verifying(
                data = decimals,
                submitAction = AsyncData.Loading(),
            )
        )
        service.emitVerificationFlowState(VerificationFlowState.DidFail)
        assertThat(awaitItem().step).isEqualTo(
            Step.Verifying(
                data = decimals,
                submitAction = AsyncData.Uninitialized,
            )
        )
    }
}
```

- [ ] **Step 4: Add start-SAS failure retry coverage**

Add this test after the request-failure test:

```kotlin
@Test
fun `present - A fail when starting SAS returns to ready state`() = runTest {
    val service = unverifiedSessionService(
        requestDeviceVerificationLambda = { },
        startSasVerificationLambda = { },
    )
    val presenter = createOutgoingVerificationPresenter(service)
    presenter.test {
        var state = awaitItem()
        assertThat(state.step).isEqualTo(Step.Initial)
        state.eventSink(OutgoingVerificationViewEvents.RequestVerification)
        advanceUntilIdle()
        service.emitVerificationFlowState(VerificationFlowState.DidAcceptVerificationRequest)
        assertThat(awaitItem().step).isEqualTo(Step.AwaitingOtherDeviceResponse)
        state = awaitItem()
        assertThat(state.step).isEqualTo(Step.Ready)

        state.eventSink(OutgoingVerificationViewEvents.StartSasVerification)
        service.emitVerificationFlowState(VerificationFlowState.DidFail)

        assertThat(awaitItem().step).isEqualTo(Step.AwaitingOtherDeviceResponse)
        assertThat(awaitItem().step).isEqualTo(Step.Ready)
    }
}
```

- [ ] **Step 5: Run the outgoing presenter test and verify the new tests fail**

Run:

```bash
env -u http_proxy -u https_proxy -u all_proxy -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u NO_PROXY -u no_proxy \
  JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools \
  ./gradlew --no-daemon --no-configuration-cache :features:verifysession:impl:testDebugUnitTest \
  --tests io.element.android.features.verifysession.impl.outgoing.OutgoingVerificationPresenterTest
```

Expected: FAIL. The failing assertions should show the current Android behavior still reports `Canceled` instead of `Initial`, `Ready`, or a challenge `Step.Verifying`.

### Task 2: Align Outgoing Verification State Transitions

**Files:**
- Modify: `features/verifysession/impl/src/main/kotlin/io/element/android/features/verifysession/impl/outgoing/OutgoingVerificationStateMachine.kt`

- [ ] **Step 1: Replace generic `DidFail` handling**

In the generic `inState` block, replace:

```kotlin
on<Event.DidFail> { event, state: MachineState<State> ->
    state.override { State.Canceled.andLogStateChange() }
}
```

with:

```kotlin
on<Event.DidFail> { _, state: MachineState<State> ->
    when (val snapshot = state.snapshot) {
        is State.RequestingVerification -> {
            sessionVerificationService.reset(cancelAnyPendingVerificationAttempt = false)
            state.override { State.Initial.andLogStateChange() }
        }
        State.StartingSasVerification -> {
            state.override { State.VerificationRequestAccepted.andLogStateChange() }
        }
        is State.Verifying.Replying -> {
            state.override { State.Verifying.ChallengeReceived(snapshot.data).andLogStateChange() }
        }
        State.Completed,
        State.Exit,
        is State.Canceled -> state.noChange()
        State.Initial,
        State.VerificationRequestAccepted,
        State.SasVerificationStarted,
        is State.Verifying.ChallengeReceived -> {
            state.override { State.Canceled.andLogStateChange() }
        }
    }
}
```

- [ ] **Step 2: Run outgoing presenter tests**

Run:

```bash
env -u http_proxy -u https_proxy -u all_proxy -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u NO_PROXY -u no_proxy \
  JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools \
  ./gradlew --no-daemon --no-configuration-cache :features:verifysession:impl:testDebugUnitTest \
  --tests io.element.android.features.verifysession.impl.outgoing.OutgoingVerificationPresenterTest
```

Expected: PASS.

- [ ] **Step 3: Commit outgoing alignment**

Run:

```bash
git add features/verifysession/impl/src/test/kotlin/io/element/android/features/verifysession/impl/outgoing/OutgoingVerificationPresenterTest.kt \
  features/verifysession/impl/src/main/kotlin/io/element/android/features/verifysession/impl/outgoing/OutgoingVerificationStateMachine.kt
git commit -m "fix: align outgoing verification retry states"
```

Expected: one commit containing only the outgoing test and state-machine files.

### Task 3: Add Incoming Verification Regression Tests

**Files:**
- Modify: `features/verifysession/impl/src/test/kotlin/io/element/android/features/verifysession/impl/incoming/IncomingVerificationPresenterTest.kt`

- [ ] **Step 1: Strengthen ignore-before-accept coverage**

In `present - user ignores incoming request`, add a cancel recorder and assert it is never called:

```kotlin
val cancelVerificationLambda = lambdaRecorder<Unit> { }
val fakeSessionVerificationService = FakeSessionVerificationService(
    acknowledgeVerificationRequestLambda = acknowledgeVerificationRequestLambda,
    acceptVerificationRequestLambda = acceptVerificationRequestLambda,
    cancelVerificationLambda = cancelVerificationLambda,
    resetLambda = resetLambda,
)
```

Then add after `navigatorLambda.assertions().isCalledOnce()`:

```kotlin
cancelVerificationLambda.assertions().isNeverCalled()
acceptVerificationRequestLambda.assertions().isNeverCalled()
```

- [ ] **Step 2: Add accept-failure retry coverage**

Add this test after the ignore test:

```kotlin
@Test
fun `present - accept failure returns to initial state`() = runTest {
    val acknowledgeVerificationRequestLambda = lambdaRecorder<VerificationRequest.Incoming, Unit> { _ -> }
    val acceptVerificationRequestLambda = lambdaRecorder<Unit> { }
    val resetLambda = lambdaRecorder<Boolean, Unit> { }
    val fakeSessionVerificationService = FakeSessionVerificationService(
        acknowledgeVerificationRequestLambda = acknowledgeVerificationRequestLambda,
        acceptVerificationRequestLambda = acceptVerificationRequestLambda,
        resetLambda = resetLambda,
    )
    createPresenter(service = fakeSessionVerificationService).test {
        val initialState = awaitItem()
        initialState.eventSink(IncomingVerificationViewEvents.StartVerification)
        skipItems(1)
        val waitingState = awaitItem()
        assertThat((waitingState.step as IncomingVerificationState.Step.Initial).isWaiting).isTrue()

        fakeSessionVerificationService.emitVerificationFlowState(VerificationFlowState.DidFail)

        val retryState = awaitItem()
        assertThat(retryState.step).isEqualTo(
            IncomingVerificationState.Step.Initial(
                deviceDisplayName = "a device name",
                deviceId = A_DEVICE_ID,
                formattedSignInTime = "567 TimeOrDate false",
                isWaiting = false,
            )
        )
    }
}
```

- [ ] **Step 3: Add decline-success cancellation coverage**

Add this test after the existing emoji-not-matching test:

```kotlin
@Test
fun `present - declined challenge success results in canceled state`() = runTest {
    val declineVerificationLambda = lambdaRecorder<Unit> { }
    val fakeSessionVerificationService = FakeSessionVerificationService(
        acknowledgeVerificationRequestLambda = { },
        acceptVerificationRequestLambda = { },
        declineVerificationLambda = declineVerificationLambda,
        resetLambda = { },
    )
    createPresenter(service = fakeSessionVerificationService).test {
        val emojiState = acceptIncomingRequestAndAwaitEmojiState(fakeSessionVerificationService)
        emojiState.eventSink(IncomingVerificationViewEvents.DeclineVerification)
        val waitingItem = awaitItem()
        assertThat((waitingItem.step as IncomingVerificationState.Step.Verifying).isWaiting).isTrue()
        advanceUntilIdle()
        declineVerificationLambda.assertions().isCalledOnce()

        fakeSessionVerificationService.emitVerificationFlowState(VerificationFlowState.DidCancel)

        assertThat(awaitItem().step).isEqualTo(IncomingVerificationState.Step.Canceled)
    }
}
```

- [ ] **Step 4: Add approve/decline failure challenge-preservation coverage**

Add these two tests after the decline-success test:

```kotlin
@Test
fun `present - approve failure keeps challenge visible`() = runTest {
    val fakeSessionVerificationService = FakeSessionVerificationService(
        acknowledgeVerificationRequestLambda = { },
        acceptVerificationRequestLambda = { },
        approveVerificationLambda = { },
        resetLambda = { },
    )
    createPresenter(service = fakeSessionVerificationService).test {
        val emojiState = acceptIncomingRequestAndAwaitEmojiState(fakeSessionVerificationService)
        emojiState.eventSink(IncomingVerificationViewEvents.ConfirmVerification)
        val waitingItem = awaitItem()
        assertThat((waitingItem.step as IncomingVerificationState.Step.Verifying).isWaiting).isTrue()

        fakeSessionVerificationService.emitVerificationFlowState(VerificationFlowState.DidFail)

        assertThat(awaitItem().step).isEqualTo(
            IncomingVerificationState.Step.Verifying(
                data = aEmojisSessionVerificationData(),
                isWaiting = false,
            )
        )
    }
}

@Test
fun `present - decline failure keeps challenge visible`() = runTest {
    val fakeSessionVerificationService = FakeSessionVerificationService(
        acknowledgeVerificationRequestLambda = { },
        acceptVerificationRequestLambda = { },
        declineVerificationLambda = { },
        resetLambda = { },
    )
    createPresenter(service = fakeSessionVerificationService).test {
        val emojiState = acceptIncomingRequestAndAwaitEmojiState(fakeSessionVerificationService)
        emojiState.eventSink(IncomingVerificationViewEvents.DeclineVerification)
        val waitingItem = awaitItem()
        assertThat((waitingItem.step as IncomingVerificationState.Step.Verifying).isWaiting).isTrue()

        fakeSessionVerificationService.emitVerificationFlowState(VerificationFlowState.DidFail)

        assertThat(awaitItem().step).isEqualTo(
            IncomingVerificationState.Step.Verifying(
                data = aEmojisSessionVerificationData(),
                isWaiting = false,
            )
        )
    }
}
```

- [ ] **Step 5: Add a helper for accepting incoming requests**

Add this helper above `createPresenter`:

```kotlin
context(testScope: TestScope)
private suspend fun ReceiveTurbine<IncomingVerificationState>.acceptIncomingRequestAndAwaitEmojiState(
    fakeSessionVerificationService: FakeSessionVerificationService,
): IncomingVerificationState {
    val initialState = awaitItem()
    initialState.eventSink(IncomingVerificationViewEvents.StartVerification)
    skipItems(1)
    assertThat((awaitItem().step as IncomingVerificationState.Step.Initial).isWaiting).isTrue()
    testScope.advanceUntilIdle()
    fakeSessionVerificationService.emitVerificationFlowState(VerificationFlowState.DidAcceptVerificationRequest)
    fakeSessionVerificationService.emitVerificationFlowState(VerificationFlowState.DidStartSasVerification)
    fakeSessionVerificationService.emitVerificationFlowState(
        VerificationFlowState.DidReceiveVerificationData(
            data = aEmojisSessionVerificationData()
        )
    )
    val emojiState = awaitItem()
    assertThat(emojiState.step).isEqualTo(
        IncomingVerificationState.Step.Verifying(
            data = aEmojisSessionVerificationData(),
            isWaiting = false,
        )
    )
    return emojiState
}
```

Also add this import at the top:

```kotlin
import app.cash.turbine.ReceiveTurbine
```

- [ ] **Step 6: Run incoming presenter tests and verify failures**

Run:

```bash
env -u http_proxy -u https_proxy -u all_proxy -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u NO_PROXY -u no_proxy \
  JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools \
  ./gradlew --no-daemon --no-configuration-cache :features:verifysession:impl:testDebugUnitTest \
  --tests io.element.android.features.verifysession.impl.incoming.IncomingVerificationPresenterTest
```

Expected: FAIL. The failing assertions should show accept failure, decline success, or challenge failure behavior still differs from iOS.

### Task 4: Align Incoming Verification State Transitions

**Files:**
- Modify: `features/verifysession/impl/src/main/kotlin/io/element/android/features/verifysession/impl/incoming/IncomingVerificationStateMachine.kt`

- [ ] **Step 1: Replace `DidCancel` handling for rejecting challenge**

In the generic `inState` block, replace the `DidCancel` branch that maps `State.RejectingChallenge` to `State.Failure` with:

```kotlin
on<Event.DidCancel> { _, state: MachineState<State> ->
    when (state.snapshot) {
        is State.Initial -> state.mutate { State.Initial(isCancelled = true).andLogStateChange() }
        State.AcceptingIncomingVerification,
        State.RejectingIncomingVerification,
        is State.ChallengeReceived,
        is State.AcceptingChallenge,
        is State.RejectingChallenge,
        State.Canceling -> state.override { State.Canceled.andLogStateChange() }
        State.Canceled,
        State.Completed,
        State.Failure -> state.noChange()
    }
}
```

- [ ] **Step 2: Replace generic `DidFail` handling**

Replace:

```kotlin
on<Event.DidFail> { _, state: MachineState<State> ->
    state.override { State.Failure.andLogStateChange() }
}
```

with:

```kotlin
on<Event.DidFail> { _, state: MachineState<State> ->
    when (val snapshot = state.snapshot) {
        State.AcceptingIncomingVerification -> {
            sessionVerificationService.reset(cancelAnyPendingVerificationAttempt = false)
            state.override { State.Initial(isCancelled = false).andLogStateChange() }
        }
        is State.AcceptingChallenge -> {
            state.override { State.ChallengeReceived(snapshot.data).andLogStateChange() }
        }
        is State.RejectingChallenge -> {
            state.override { State.ChallengeReceived(snapshot.data).andLogStateChange() }
        }
        is State.Initial,
        State.RejectingIncomingVerification,
        is State.ChallengeReceived,
        State.Canceling -> state.override { State.Failure.andLogStateChange() }
        State.Canceled,
        State.Completed,
        State.Failure -> state.noChange()
    }
}
```

- [ ] **Step 3: Run incoming presenter tests**

Run:

```bash
env -u http_proxy -u https_proxy -u all_proxy -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u NO_PROXY -u no_proxy \
  JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools \
  ./gradlew --no-daemon --no-configuration-cache :features:verifysession:impl:testDebugUnitTest \
  --tests io.element.android.features.verifysession.impl.incoming.IncomingVerificationPresenterTest
```

Expected: PASS.

- [ ] **Step 4: Commit incoming alignment**

Run:

```bash
git add features/verifysession/impl/src/test/kotlin/io/element/android/features/verifysession/impl/incoming/IncomingVerificationPresenterTest.kt \
  features/verifysession/impl/src/main/kotlin/io/element/android/features/verifysession/impl/incoming/IncomingVerificationStateMachine.kt
git commit -m "fix: align incoming verification retry states"
```

Expected: one commit containing only incoming verification files.

### Task 5: Add FTUE Verification Gate Regression Tests

**Files:**
- Modify: `features/ftue/impl/src/test/kotlin/io/element/android/features/ftue/impl/DefaultFtueServiceTest.kt`

- [ ] **Step 1: Add explicit success-acknowledgement gate test**

Add this test after `traverse flow`:

```kotlin
@Test
fun `session verification success waits for user acknowledgement before advancing`() = runTest {
    val sessionVerificationService = FakeSessionVerificationService().apply {
        emitVerifiedStatus(SessionVerifiedStatus.NotVerified)
    }
    val analyticsService = FakeAnalyticsService()
    val permissionStateProvider = FakePermissionStateProvider(permissionGranted = false)
    val lockScreenService = FakeLockScreenService()
    val service = createDefaultFtueService(
        sessionVerificationService = sessionVerificationService,
        analyticsService = analyticsService,
        permissionStateProvider = permissionStateProvider,
        lockScreenService = lockScreenService,
    )

    service.ftueStepStateFlow.test {
        assertThat(awaitItem()).isEqualTo(InternalFtueState.Unknown)
        assertThat(awaitItem()).isEqualTo(InternalFtueState.Incomplete(FtueStep.SessionVerification))

        sessionVerificationService.emitVerifiedStatus(SessionVerifiedStatus.Verified)
        service.updateFtueStep()

        expectNoEvents()

        service.onUserCompletedSessionVerification()

        assertThat(awaitItem()).isEqualTo(InternalFtueState.Incomplete(FtueStep.NotificationsOptIn))
    }
}
```

- [ ] **Step 2: Add skip preference coverage**

Add this test after the acknowledgement test:

```kotlin
@Test
fun `skipped session verification advances when session is not verified`() = runTest {
    val sessionVerificationService = FakeSessionVerificationService().apply {
        emitVerifiedStatus(SessionVerifiedStatus.NotVerified)
    }
    val sessionPreferencesStore = InMemorySessionPreferencesStore()
    sessionPreferencesStore.setSessionVerificationSkipped(true)
    val service = createDefaultFtueService(
        sessionVerificationService = sessionVerificationService,
        sessionPreferencesStore = sessionPreferencesStore,
    )

    service.ftueStepStateFlow.test {
        assertThat(awaitItem()).isEqualTo(InternalFtueState.Unknown)
        assertThat(awaitItem()).isEqualTo(InternalFtueState.Incomplete(FtueStep.NotificationsOptIn))
    }
}
```

- [ ] **Step 3: Run FTUE tests**

Run:

```bash
env -u http_proxy -u https_proxy -u all_proxy -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u NO_PROXY -u no_proxy \
  JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools \
  ./gradlew --no-daemon --no-configuration-cache :features:ftue:impl:testDebugUnitTest \
  --tests io.element.android.features.ftue.impl.DefaultFtueServiceTest
```

Expected: PASS. If the acknowledgement test fails because `expectNoEvents()` sees a duplicate session-verification state, replace that assertion with:

```kotlin
assertThat(awaitItem()).isEqualTo(InternalFtueState.Incomplete(FtueStep.SessionVerification))
expectNoEvents()
```

and keep the user-acknowledgement assertion.

- [ ] **Step 4: Commit FTUE tests**

Run:

```bash
git add features/ftue/impl/src/test/kotlin/io/element/android/features/ftue/impl/DefaultFtueServiceTest.kt
git commit -m "test: cover FTUE verification acknowledgement"
```

Expected: one commit containing only the FTUE test file.

### Task 6: Final Verification

**Files:**
- No file changes expected.

- [ ] **Step 1: Run all session verification implementation tests**

Run:

```bash
env -u http_proxy -u https_proxy -u all_proxy -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u NO_PROXY -u no_proxy \
  JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools \
  ./gradlew --no-daemon --no-configuration-cache :features:verifysession:impl:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 2: Run FTUE unit tests**

Run:

```bash
env -u http_proxy -u https_proxy -u all_proxy -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u NO_PROXY -u no_proxy \
  JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools \
  ./gradlew --no-daemon --no-configuration-cache :features:ftue:impl:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 3: Run Matrix implementation tests**

Run:

```bash
env -u http_proxy -u https_proxy -u all_proxy -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u NO_PROXY -u no_proxy \
  JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools \
  ./gradlew --no-daemon --no-configuration-cache :libraries:matrix:impl:testDebugUnitTest
```

Expected: PASS. This proves the Matrix Rust SDK service adapter still compiles and existing Matrix verification-related tests remain green.

- [ ] **Step 4: Run session verification assemble**

Run:

```bash
env -u http_proxy -u https_proxy -u all_proxy -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u NO_PROXY -u no_proxy \
  JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools \
  ./gradlew --no-daemon --no-configuration-cache :features:verifysession:impl:assembleDebug
```

Expected: PASS.

- [ ] **Step 5: Run diff and status checks**

Run:

```bash
git diff --check
git status --short --branch
```

Expected:

- `git diff --check` prints no output.
- `git status --short --branch` shows no uncommitted tracked changes. The existing untracked `.codegraph/` directory may remain and must not be committed.

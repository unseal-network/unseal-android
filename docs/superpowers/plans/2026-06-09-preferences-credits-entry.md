# Preferences Credits Entry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the iOS-equivalent Credits balance card and Billing/Usage navigation entry points to the Android Preferences root screen.

**Architecture:** Reuse the existing Android `features/credits` entry point and `ChatbotApiServiceFactory.createForUnsealApi(matrixClient)` so Preferences only loads the small balance summary and delegates full billing/usage screens to Credits. Mirror iOS `SettingsScreen` behavior: initial loading, loaded `balanceUsd` with `$` prefix, unavailable `$0.00`, and separate `Recharge`, `Billing`, and `Usage` actions. Keep Stripe/top-up implementation out of scope; wire `Recharge` to a no-op callback seam until the top-up spec.

**Tech Stack:** Kotlin, Jetpack Compose, Appyx `BaseFlowNode`, Metro injection, existing `ChatbotApiService`, existing `CreditsEntryPoint`, Robolectric/Compose unit tests.

---

## iOS Reference

- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Settings/SettingsScreen/View/SettingsScreen.swift`
  - `creditBalanceCard(loadState:)` shows `settingsCreditBalance`, a large balance, `creditsActionRecharge`, `creditsActionBilling`, and `creditsActionUsage`.
  - `case .loaded(let balanceUsd)` displays `balanceUsd.hasPrefix("$") ? balanceUsd : "$\(balanceUsd)"`.
  - `case .unavailable` displays `$0.00`.
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Settings/SettingsScreen/SettingsScreenModels.swift`
  - `CreditBalanceLoadState` has `loading`, `loaded(String)`, and `unavailable`.
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Settings/SettingsScreen/SettingsScreenViewModel.swift`
  - `updateCreditBalance(_:)` maps nil or empty `balanceUsd` to `.unavailable`, otherwise `.loaded(balance.balanceUsd)`.
  - `.creditBilling`, `.creditUsage`, and `.recharge` are separate view actions.

---

## File Structure

- Modify `features/preferences/impl/build.gradle.kts`: add Credits and Chatbot API/test dependencies.
- Modify `features/preferences/impl/src/main/kotlin/io/element/android/features/preferences/impl/PreferencesFlowNode.kt`: inject `CreditsEntryPoint`, add `NavTarget.Credits(initialTab)`, create Credits nodes, and route Preferences root callbacks.
- Modify `features/preferences/impl/src/main/kotlin/io/element/android/features/preferences/impl/root/PreferencesRootState.kt`: add `CreditBalanceLoadState`.
- Modify `features/preferences/impl/src/main/kotlin/io/element/android/features/preferences/impl/root/PreferencesRootPresenter.kt`: load credit balance once from Unseal API and expose load state.
- Modify `features/preferences/impl/src/main/kotlin/io/element/android/features/preferences/impl/root/PreferencesRootNode.kt`: add callback methods and pass them to the view.
- Modify `features/preferences/impl/src/main/kotlin/io/element/android/features/preferences/impl/root/PreferencesRootView.kt`: render the Credits card in Preferences and dispatch Recharge/Billing/Usage callbacks.
- Modify `features/preferences/impl/src/test/kotlin/io/element/android/features/preferences/impl/DefaultPreferencesEntryPointTest.kt`: cover Credits nav target creation.
- Modify `features/preferences/impl/src/test/kotlin/io/element/android/features/preferences/impl/root/PreferencesRootPresenterTest.kt`: cover balance loading and failure.
- Modify `features/preferences/impl/src/test/kotlin/io/element/android/features/preferences/impl/root/PreferencesRootViewTest.kt`: cover Credits card rendering and clicks.

---

### Task 1: Write Failing Preferences Credits Tests

**Files:**
- Modify: `features/preferences/impl/src/test/kotlin/io/element/android/features/preferences/impl/DefaultPreferencesEntryPointTest.kt`
- Modify: `features/preferences/impl/src/test/kotlin/io/element/android/features/preferences/impl/root/PreferencesRootPresenterTest.kt`
- Modify: `features/preferences/impl/src/test/kotlin/io/element/android/features/preferences/impl/root/PreferencesRootViewTest.kt`

- [x] **Step 1: Add flow node tests**

In `DefaultPreferencesEntryPointTest.kt`, import:

```kotlin
import io.element.android.features.credits.api.CreditsEntryPoint
import io.element.android.features.credits.test.FakeCreditsEntryPoint
```

Update every `PreferencesFlowNode(...)` construction in this test file to pass:

```kotlin
creditsEntryPoint = FakeCreditsEntryPoint(),
```

Add these tests:

```kotlin
@Test
fun `test credits billing nav target creates balance credits node`() {
    var capturedParams: CreditsEntryPoint.Params? = null
    val node = aPreferencesFlowNode(
        creditsEntryPoint = FakeCreditsEntryPoint { parentNode, _, params, _ ->
            capturedParams = params
            parentNode
        },
    )

    node.resolve(
        PreferencesFlowNode.NavTarget.Credits(CreditsEntryPoint.CreditsTab.Balance),
        BuildContext.root(null),
    )

    assertThat(capturedParams).isEqualTo(
        CreditsEntryPoint.Params(initialTab = CreditsEntryPoint.CreditsTab.Balance)
    )
}

@Test
fun `test credits usage nav target creates daily usage credits node`() {
    var capturedParams: CreditsEntryPoint.Params? = null
    val node = aPreferencesFlowNode(
        creditsEntryPoint = FakeCreditsEntryPoint { parentNode, _, params, _ ->
            capturedParams = params
            parentNode
        },
    )

    node.resolve(
        PreferencesFlowNode.NavTarget.Credits(CreditsEntryPoint.CreditsTab.DailyUsage),
        BuildContext.root(null),
    )

    assertThat(capturedParams).isEqualTo(
        CreditsEntryPoint.Params(initialTab = CreditsEntryPoint.CreditsTab.DailyUsage)
    )
}
```

Add a private helper in the same file to keep future constructor changes localized:

```kotlin
private fun aPreferencesFlowNode(
    creditsEntryPoint: CreditsEntryPoint = FakeCreditsEntryPoint(),
    webhookTriggersEntryPoint: WebhookTriggersEntryPoint = FakeWebhookTriggersEntryPoint(),
) = PreferencesFlowNode(
    buildContext = BuildContext.root(null),
    plugins = listOf(
        PreferencesEntryPoint.Params(
            initialElement = PreferencesEntryPoint.InitialTarget.Root,
        ),
        object : PreferencesEntryPoint.Callback {
            override fun navigateToAddAccount() = lambdaError()
            override fun navigateToLinkNewDevice() = lambdaError()
            override fun navigateToBugReport() = lambdaError()
            override fun navigateToSecureBackup() = lambdaError()
            override fun navigateToRoomNotificationSettings(roomId: RoomId) = lambdaError()
            override fun navigateToEvent(roomId: RoomId, eventId: EventId) = lambdaError()
        }
    ),
    lockScreenEntryPoint = FakeLockScreenEntryPoint(),
    notificationTroubleShootEntryPoint = FakeNotificationTroubleShootEntryPoint(),
    pushHistoryEntryPoint = FakePushHistoryEntryPoint(),
    logoutEntryPoint = FakeLogoutEntryPoint(),
    openSourceLicensesEntryPoint = FakeOpenSourceLicensesEntryPoint(),
    accountDeactivationEntryPoint = FakeAccountDeactivationEntryPoint(),
    webhookTriggersEntryPoint = webhookTriggersEntryPoint,
    creditsEntryPoint = creditsEntryPoint,
)
```

- [x] **Step 2: Add presenter tests**

In `PreferencesRootPresenterTest.kt`, import:

```kotlin
import io.element.android.libraries.chatbot.test.FakeChatbotApiService
import io.element.android.libraries.chatbot.test.FakeChatbotApiServiceFactory
import io.element.android.libraries.chatbot.test.aCreditBalance
```

Update `createPresenter(...)` to accept and pass:

```kotlin
chatbotApiServiceFactory: FakeChatbotApiServiceFactory = FakeChatbotApiServiceFactory(),
```

Add these tests:

```kotlin
@Test
fun `present - credit balance starts loading then becomes loaded`() = runTest {
    val chatbotApiService = FakeChatbotApiService().apply {
        getBalanceResult = {
            Result.success(aCreditBalance(balanceMicros = "12500000").copy(balanceUsd = "12.50"))
        }
    }
    createPresenter(
        matrixClient = FakeMatrixClient(
            canDeactivateAccountResult = { true },
            accountManagementUrlResult = { Result.success(null) },
        ),
        chatbotApiServiceFactory = FakeChatbotApiServiceFactory(chatbotApiService),
    ).test {
        assertThat(awaitItem().creditBalanceLoadState).isEqualTo(CreditBalanceLoadState.Loading)
        val loaded = awaitStateWhere { it.creditBalanceLoadState == CreditBalanceLoadState.Loaded("12.50") }
        assertThat(loaded.creditBalanceLoadState).isEqualTo(CreditBalanceLoadState.Loaded("12.50"))
    }
}

@Test
fun `present - credit balance failure becomes unavailable`() = runTest {
    val chatbotApiService = FakeChatbotApiService().apply {
        getBalanceResult = { Result.failure(IllegalStateException("No credits")) }
    }
    createPresenter(
        matrixClient = FakeMatrixClient(
            canDeactivateAccountResult = { true },
            accountManagementUrlResult = { Result.success(null) },
        ),
        chatbotApiServiceFactory = FakeChatbotApiServiceFactory(chatbotApiService),
    ).test {
        assertThat(awaitItem().creditBalanceLoadState).isEqualTo(CreditBalanceLoadState.Loading)
        val unavailable = awaitStateWhere { it.creditBalanceLoadState == CreditBalanceLoadState.Unavailable }
        assertThat(unavailable.creditBalanceLoadState).isEqualTo(CreditBalanceLoadState.Unavailable)
    }
}
```

Add the helper:

```kotlin
private suspend fun ReceiveTurbine<PreferencesRootState>.awaitStateWhere(
    predicate: (PreferencesRootState) -> Boolean,
): PreferencesRootState {
    repeat(20) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
    error("State matching predicate was not emitted")
}
```

- [x] **Step 3: Add view tests**

In `PreferencesRootViewTest.kt`, add tests:

```kotlin
@Test
fun `credit balance card shows loaded prefixed balance`() = runAndroidComposeUiTest {
    val eventsRecorder = EventsRecorder<PreferencesRootEvent>(expectEvents = false)
    setView(
        aPreferencesRootState(
            creditBalanceLoadState = CreditBalanceLoadState.Loaded("12.50"),
            eventSink = eventsRecorder,
        ),
    )
    onNodeWithText("Credit Balance").assertExists()
    onNodeWithText("$12.50").assertExists()
}

@Test
fun `credit balance card shows unavailable zero balance`() = runAndroidComposeUiTest {
    val eventsRecorder = EventsRecorder<PreferencesRootEvent>(expectEvents = false)
    setView(
        aPreferencesRootState(
            creditBalanceLoadState = CreditBalanceLoadState.Unavailable,
            eventSink = eventsRecorder,
        ),
    )
    onNodeWithText("$0.00").assertExists()
}

@Test
fun `click on Recharge invokes the expected callback`() = runAndroidComposeUiTest {
    val eventsRecorder = EventsRecorder<PreferencesRootEvent>(expectEvents = false)
    ensureCalledOnce { callback ->
        setView(
            aPreferencesRootState(eventSink = eventsRecorder),
            onOpenCreditsTopUp = callback,
        )
        onNodeWithText("Recharge")
            .performScrollTo()
            .performClick()
    }
}

@Test
fun `click on Billing invokes the expected callback`() = runAndroidComposeUiTest {
    val eventsRecorder = EventsRecorder<PreferencesRootEvent>(expectEvents = false)
    ensureCalledOnce { callback ->
        setView(
            aPreferencesRootState(eventSink = eventsRecorder),
            onOpenCreditsBilling = callback,
        )
        onNodeWithText("Billing")
            .performScrollTo()
            .performClick()
    }
}

@Test
fun `click on Usage invokes the expected callback`() = runAndroidComposeUiTest {
    val eventsRecorder = EventsRecorder<PreferencesRootEvent>(expectEvents = false)
    ensureCalledOnce { callback ->
        setView(
            aPreferencesRootState(eventSink = eventsRecorder),
            onOpenCreditsUsage = callback,
        )
        onNodeWithText("Usage")
            .performScrollTo()
            .performClick()
    }
}
```

Update the `setView(...)` helper to accept and pass these callbacks:

```kotlin
onOpenCreditsTopUp: () -> Unit = EnsureNeverCalled(),
onOpenCreditsBilling: () -> Unit = EnsureNeverCalled(),
onOpenCreditsUsage: () -> Unit = EnsureNeverCalled(),
```

- [x] **Step 4: Run focused tests and confirm failure**

Run:

```bash
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:preferences:impl:testDebugUnitTest --tests '*PreferencesRootPresenterTest' --tests '*PreferencesRootViewTest' --tests '*DefaultPreferencesEntryPointTest'
```

Expected: fails because `CreditBalanceLoadState`, Credits callbacks, Credits dependency, and `NavTarget.Credits` are not implemented yet.

---

### Task 2: Implement Preferences Credits State And Presenter Loading

**Files:**
- Modify: `features/preferences/impl/build.gradle.kts`
- Modify: `features/preferences/impl/src/main/kotlin/io/element/android/features/preferences/impl/root/PreferencesRootState.kt`
- Modify: `features/preferences/impl/src/main/kotlin/io/element/android/features/preferences/impl/root/PreferencesRootPresenter.kt`

- [x] **Step 1: Add dependencies**

In `features/preferences/impl/build.gradle.kts`, add:

```kotlin
implementation(projects.features.credits.api)
implementation(projects.libraries.chatbot.api)
testImplementation(projects.features.credits.test)
testImplementation(projects.libraries.chatbot.test)
```

- [x] **Step 2: Add balance load state**

In `PreferencesRootState.kt`, add before `data class PreferencesRootState`:

```kotlin
sealed interface CreditBalanceLoadState {
    data object Loading : CreditBalanceLoadState
    data class Loaded(val balanceUsd: String) : CreditBalanceLoadState
    data object Unavailable : CreditBalanceLoadState
}
```

Add this property to `PreferencesRootState`:

```kotlin
val creditBalanceLoadState: CreditBalanceLoadState,
```

- [x] **Step 3: Load balance in presenter**

In `PreferencesRootPresenter.kt`, import:

```kotlin
import io.element.android.libraries.chatbot.api.ChatbotApiServiceFactory
```

Add constructor dependency:

```kotlin
private val chatbotApiServiceFactory: ChatbotApiServiceFactory,
```

Add state in `present()`:

```kotlin
var creditBalanceLoadState by remember {
    mutableStateOf<CreditBalanceLoadState>(CreditBalanceLoadState.Loading)
}
```

Add a `LaunchedEffect(Unit)` after existing one-shot load effects:

```kotlin
LaunchedEffect(Unit) {
    creditBalanceLoadState = runCatching {
        chatbotApiServiceFactory.createForUnsealApi(matrixClient).getBalance()
    }.getOrElse {
        Result.failure(it)
    }.fold(
        onSuccess = { balance ->
            if (balance.balanceUsd.isBlank()) {
                CreditBalanceLoadState.Unavailable
            } else {
                CreditBalanceLoadState.Loaded(balance.balanceUsd)
            }
        },
        onFailure = {
            CreditBalanceLoadState.Unavailable
        },
    )
}
```

Pass `creditBalanceLoadState = creditBalanceLoadState` into the returned state.

- [x] **Step 4: Run presenter tests**

Run:

```bash
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:preferences:impl:testDebugUnitTest --tests '*PreferencesRootPresenterTest'
```

Expected: presenter tests pass.

---

### Task 3: Implement Preferences Credits View

**Files:**
- Modify: `features/preferences/impl/src/main/kotlin/io/element/android/features/preferences/impl/root/PreferencesRootNode.kt`
- Modify: `features/preferences/impl/src/main/kotlin/io/element/android/features/preferences/impl/root/PreferencesRootView.kt`
- Modify: `features/preferences/impl/src/test/kotlin/io/element/android/features/preferences/impl/root/PreferencesRootViewTest.kt`

- [x] **Step 1: Extend node callback and view wiring**

In `PreferencesRootNode.Callback`, add:

```kotlin
fun navigateToCreditsBilling()
fun navigateToCreditsUsage()
fun navigateToCreditsTopUp()
```

In `PreferencesRootNode.View`, pass the callbacks to `PreferencesRootView`:

```kotlin
onOpenCreditsBilling = callback::navigateToCreditsBilling,
onOpenCreditsUsage = callback::navigateToCreditsUsage,
onOpenCreditsTopUp = callback::navigateToCreditsTopUp,
```

- [x] **Step 2: Add view callbacks**

In `PreferencesRootView(...)`, add parameters:

```kotlin
onOpenCreditsTopUp: () -> Unit,
onOpenCreditsBilling: () -> Unit,
onOpenCreditsUsage: () -> Unit,
```

Pass them into `ManageAccountSection(...)`.

- [x] **Step 3: Render the iOS-equivalent card**

Add helper:

```kotlin
private fun String.prefixedDollar(): String {
    return when {
        isBlank() -> "$0.00"
        startsWith("$") -> this
        else -> "$$this"
    }
}
```

Add a composable near `ManageAccountSection`:

```kotlin
@Composable
private fun ColumnScope.CreditBalanceCard(
    loadState: CreditBalanceLoadState,
    onOpenCreditsTopUp: () -> Unit,
    onOpenCreditsBilling: () -> Unit,
    onOpenCreditsUsage: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text("Credit Balance")
        },
        supportingContent = {
            Text(
                text = when (loadState) {
                    CreditBalanceLoadState.Loading -> "$0.00"
                    is CreditBalanceLoadState.Loaded -> loadState.balanceUsd.prefixedDollar()
                    CreditBalanceLoadState.Unavailable -> "$0.00"
                },
                style = ElementTheme.typography.fontHeadingLgBold,
            )
        },
        trailingContent = ListItemContent.Text("Recharge"),
        onClick = onOpenCreditsTopUp,
    )
    ListItem(
        headlineContent = { Text("Billing") },
        leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.CreditCard())),
        onClick = onOpenCreditsBilling,
    )
    ListItem(
        headlineContent = { Text("Usage") },
        leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Chart())) ,
        onClick = onOpenCreditsUsage,
    )
}
```

Call it near the top of `ManageAccountSection`, before account management/device rows:

```kotlin
CreditBalanceCard(
    loadState = state.creditBalanceLoadState,
    onOpenCreditsTopUp = onOpenCreditsTopUp,
    onOpenCreditsBilling = onOpenCreditsBilling,
    onOpenCreditsUsage = onOpenCreditsUsage,
)
```

If `CompoundIcons.CreditCard()` is unavailable, use `CompoundIcons.UserProfile()` for Billing; do not add new icon assets.

- [x] **Step 4: Update test fixtures and run view tests**

Update `aPreferencesRootState(...)` fixture to default:

```kotlin
creditBalanceLoadState: CreditBalanceLoadState = CreditBalanceLoadState.Loading,
```

Run:

```bash
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:preferences:impl:testDebugUnitTest --tests '*PreferencesRootViewTest'
```

Expected: view tests pass.

---

### Task 4: Implement Credits Navigation

**Files:**
- Modify: `features/preferences/impl/src/main/kotlin/io/element/android/features/preferences/impl/PreferencesFlowNode.kt`
- Modify: `features/preferences/impl/src/test/kotlin/io/element/android/features/preferences/impl/DefaultPreferencesEntryPointTest.kt`

- [x] **Step 1: Inject entry point and add nav target**

In `PreferencesFlowNode.kt`, import:

```kotlin
import io.element.android.features.credits.api.CreditsEntryPoint
import io.element.android.libraries.chatbot.api.model.credits.CreditBalance
```

Add constructor dependency:

```kotlin
private val creditsEntryPoint: CreditsEntryPoint,
```

Add nav target:

```kotlin
@Parcelize
data class Credits(val initialTab: CreditsEntryPoint.CreditsTab) : NavTarget
```

- [x] **Step 2: Route root callbacks**

Inside the root callback object, add:

```kotlin
override fun navigateToCreditsBilling() {
    backstack.push(NavTarget.Credits(CreditsEntryPoint.CreditsTab.Balance))
}

override fun navigateToCreditsUsage() {
    backstack.push(NavTarget.Credits(CreditsEntryPoint.CreditsTab.DailyUsage))
}

override fun navigateToCreditsTopUp() {
    backstack.push(NavTarget.Credits(CreditsEntryPoint.CreditsTab.Balance))
}
```

- [x] **Step 3: Resolve Credits target**

Add to the `when (navTarget)`:

```kotlin
is NavTarget.Credits -> {
    creditsEntryPoint.createNode(
        parentNode = this,
        buildContext = buildContext,
        params = CreditsEntryPoint.Params(initialTab = navTarget.initialTab),
        callback = object : CreditsEntryPoint.Callback {
            override fun onDone() {
                if (backstack.canPop()) {
                    backstack.pop()
                } else {
                    navigateUp()
                }
            }

            override fun onTopUpRequested(balance: CreditBalance?) = Unit
        },
    )
}
```

The top-up callback is intentionally no-op for this spec.

- [x] **Step 4: Run flow node tests**

Run:

```bash
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:preferences:impl:testDebugUnitTest --tests '*DefaultPreferencesEntryPointTest'
```

Expected: flow node tests pass.

---

### Task 5: Verify And Commit Preferences Credits Entry

**Files:**
- Inspect: `features/preferences/impl`

- [x] **Step 1: Run focused Preferences tests**

Run:

```bash
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:preferences:impl:testDebugUnitTest --tests '*PreferencesRootPresenterTest' --tests '*PreferencesRootViewTest' --tests '*DefaultPreferencesEntryPointTest'
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 2: Run compile checks**

Run:

```bash
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:preferences:impl:compileDebugKotlin :features:preferences:impl:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 3: Check for formatting and unrelated changes**

Run:

```bash
git diff --check
git status --short
```

Expected: no whitespace errors; only Preferences files and this plan should be modified.

- [x] **Step 4: Update parent plan Task 5 checkboxes**

In `docs/superpowers/plans/2026-06-09-credits-dashboard.md`, mark Task 5 Step 1-5 as complete.

- [x] **Step 5: Commit**

Run:

```bash
git add docs/superpowers/plans/2026-06-09-credits-dashboard.md docs/superpowers/plans/2026-06-09-preferences-credits-entry.md features/preferences/impl
git commit -m "feat: add credits settings entry"
```

Expected: commit succeeds.

---

## Self-Review

- Spec coverage: This plan covers iOS Settings credit balance load state, `$` prefix behavior, unavailable `$0.00`, Recharge/Billing/Usage actions, Android Preferences UI, presenter loading through Unseal API, and Credits navigation. Stripe/top-up payment is intentionally excluded and left as a callback seam, matching the current migration scope.
- Placeholder scan: No TBD/TODO/fill-in-later placeholders remain. The only conditional icon note has a concrete fallback.
- Type consistency: `CreditBalanceLoadState`, `CreditsEntryPoint.CreditsTab.Balance`, `CreditsEntryPoint.CreditsTab.DailyUsage`, and `ChatbotApiServiceFactory.createForUnsealApi(matrixClient)` match the existing Credits implementation.

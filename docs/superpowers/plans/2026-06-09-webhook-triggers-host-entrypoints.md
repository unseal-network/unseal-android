# Webhook Triggers Host Entrypoints Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire the existing Webhook Triggers feature into Android Settings and Room Details so users can open global and room-scoped trigger management from the app.

**Architecture:** Reuse `WebhookTriggersEntryPoint` from host flow nodes rather than coupling host modules to Webhook implementation screens. Settings pushes a new `NavTarget.WebhookTriggers`; Room Details pushes a new `NavTarget.WebhookTriggers`. Each host owns only navigation, external URL opening, and row visibility.

**Tech Stack:** Kotlin, Compose, Appyx `BaseFlowNode`, Metro DI, Element Android design system, existing Webhook feature API.

---

## File Structure

- Modify `features/preferences/impl/build.gradle.kts`
  - Add `features.webhooks.api` and `features.webhooks.test` dependencies.
- Modify `features/preferences/impl/src/main/kotlin/io/element/android/features/preferences/impl/PreferencesFlowNode.kt`
  - Inject `WebhookTriggersEntryPoint`.
  - Add `NavTarget.WebhookTriggers`.
  - Route root callback to the new nav target.
  - Create the Webhook node in global mode.
  - Add a local URL state rendered through `OpenUrlInTabView` for Composio connect URLs.
- Modify `features/preferences/impl/src/main/kotlin/io/element/android/features/preferences/impl/root/PreferencesRootNode.kt`
  - Add callback method `navigateToWebhookTriggers`.
  - Pass `onOpenWebhookTriggers` to the view.
- Modify `features/preferences/impl/src/main/kotlin/io/element/android/features/preferences/impl/root/PreferencesRootView.kt`
  - Add a "Webhook triggers" row in the Manage app section.
- Modify `features/preferences/impl/src/main/res/values/localazy.xml`
  - Add `screen_preferences_webhook_triggers_title`.
- Modify preferences tests:
  - `features/preferences/impl/src/test/kotlin/io/element/android/features/preferences/impl/DefaultPreferencesEntryPointTest.kt`
  - `features/preferences/impl/src/test/kotlin/io/element/android/features/preferences/impl/root/PreferencesRootViewTest.kt`
- Modify `features/roomdetails/impl/build.gradle.kts`
  - Add `features.webhooks.api` and `features.webhooks.test` dependencies.
- Modify `features/roomdetails/impl/src/main/kotlin/io/element/android/features/roomdetails/impl/RoomDetailsFlowNode.kt`
  - Inject `WebhookTriggersEntryPoint`.
  - Add `NavTarget.WebhookTriggers`.
  - Route room details callback to the new nav target.
  - Create the Webhook node in room mode.
- Modify `features/roomdetails/impl/src/main/kotlin/io/element/android/features/roomdetails/impl/RoomDetailsNode.kt`
  - Add callback method `navigateToWebhookTriggers`.
  - Pass `onWebhookTriggersClick` to the view.
- Modify `features/roomdetails/impl/src/main/kotlin/io/element/android/features/roomdetails/impl/RoomDetailsView.kt`
  - Add a group-room-only "Webhook triggers" row.
- Modify `features/roomdetails/impl/src/main/res/values/localazy.xml`
  - Add `screen_room_details_webhook_triggers_title`.
- Modify room details tests:
  - `features/roomdetails/impl/src/test/kotlin/io/element/android/features/roomdetails/impl/DefaultRoomDetailsEntryPointTest.kt`
  - `features/roomdetails/impl/src/test/kotlin/io/element/android/features/roomdetails/impl/RoomDetailsViewTest.kt`

## Task 1: Settings Global Webhook Entry

**Files:**
- Modify: `features/preferences/impl/build.gradle.kts`
- Modify: `features/preferences/impl/src/main/kotlin/io/element/android/features/preferences/impl/PreferencesFlowNode.kt`
- Modify: `features/preferences/impl/src/main/kotlin/io/element/android/features/preferences/impl/root/PreferencesRootNode.kt`
- Modify: `features/preferences/impl/src/main/kotlin/io/element/android/features/preferences/impl/root/PreferencesRootView.kt`
- Modify: `features/preferences/impl/src/main/res/values/localazy.xml`
- Test: `features/preferences/impl/src/test/kotlin/io/element/android/features/preferences/impl/DefaultPreferencesEntryPointTest.kt`
- Test: `features/preferences/impl/src/test/kotlin/io/element/android/features/preferences/impl/root/PreferencesRootViewTest.kt`

- [ ] **Step 1: Add the feature API/test dependencies**

Add these dependencies:

```kotlin
implementation(projects.features.webhooks.api)
testImplementation(projects.features.webhooks.test)
```

- [ ] **Step 2: Add the settings string**

Add this string to `features/preferences/impl/src/main/res/values/localazy.xml`:

```xml
<string name="screen_preferences_webhook_triggers_title">"Webhook triggers"</string>
```

- [ ] **Step 3: Add root callback and view parameter**

Add to `PreferencesRootNode.Callback`:

```kotlin
fun navigateToWebhookTriggers()
```

Add to `PreferencesRootView` parameters:

```kotlin
onOpenWebhookTriggers: () -> Unit,
```

Pass it from `PreferencesRootNode.View`:

```kotlin
onOpenWebhookTriggers = callback::navigateToWebhookTriggers,
```

- [ ] **Step 4: Add the row to ManageAppSection**

Extend `ManageAppSection` parameters:

```kotlin
onOpenWebhookTriggers: () -> Unit,
```

Add this row after notification settings and before lock screen:

```kotlin
ListItem(
    headlineContent = { Text(stringResource(id = R.string.screen_preferences_webhook_triggers_title)) },
    leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Notifications())),
    onClick = onOpenWebhookTriggers,
)
```

- [ ] **Step 5: Wire `PreferencesFlowNode` to Webhook entrypoint**

Add imports and constructor dependency:

```kotlin
import androidx.compose.runtime.mutableStateOf
import io.element.android.libraries.designsystem.utils.OpenUrlInTabView
import io.element.android.features.webhooks.api.WebhookTriggersEntryPoint
```

```kotlin
private val webhookTriggersEntryPoint: WebhookTriggersEntryPoint,
```

Add nav target:

```kotlin
@Parcelize
data object WebhookTriggers : NavTarget
```

Add root callback method:

```kotlin
override fun navigateToWebhookTriggers() {
    backstack.push(NavTarget.WebhookTriggers)
}
```

Resolve it with global mode:

```kotlin
NavTarget.WebhookTriggers -> {
    webhookTriggersEntryPoint.createNode(
        parentNode = this,
        buildContext = buildContext,
        params = WebhookTriggersEntryPoint.Params(
            initialTarget = WebhookTriggersEntryPoint.InitialTarget.Global,
        ),
        callback = object : WebhookTriggersEntryPoint.Callback {
            override fun onDone() {
                if (backstack.canPop()) {
                    backstack.pop()
                } else {
                    navigateUp()
                }
            }

            override fun onTriggersChanged() = Unit

            override fun onOpenConnectUrl(url: String) {
                connectUrl.value = url
            }
        },
    )
}
```

Add a URL state and render it from `View`:

```kotlin
private val connectUrl = mutableStateOf<String?>(null)

@Composable
override fun View(modifier: Modifier) {
    BackstackView(modifier)
    OpenUrlInTabView(connectUrl)
}
```

If `PreferencesFlowNode` already overrides `View`, merge `OpenUrlInTabView(connectUrl)` into the existing override without changing the backstack rendering.

- [ ] **Step 6: Add/adjust tests**

Add a view test that clicks the new row and expects the view callback to fire:

```kotlin
@Test
fun `clicking webhook triggers opens webhook triggers`() = runAndroidComposeUiTest {
    val callback = lambdaRecorder<Unit> {}
    setContent {
        PreferencesRootView(
            state = aPreferencesRootState(),
            onBackClick = {},
            onAddAccountClick = {},
            onSecureBackupClick = {},
            onManageAccountClick = {},
            onLinkNewDeviceClick = {},
            onOpenAnalytics = {},
            onOpenRageShake = {},
            onOpenLockScreenSettings = {},
            onOpenWebhookTriggers = callback,
            onOpenAbout = {},
            onOpenDeveloperSettings = {},
            onOpenAdvancedSettings = {},
            onOpenLabs = {},
            onOpenNotificationSettings = {},
            onOpenUserProfile = {},
            onOpenBlockedUsers = {},
            onSignOutClick = {},
            onDeactivateClick = {},
        )
    }
    onNodeWithText("Webhook triggers").performClick()
    callback.assertions().isCalledOnce()
}
```

Add or update an entrypoint/flow test so creating `NavTarget.WebhookTriggers` uses `WebhookTriggersEntryPoint.Params(initialTarget = Global)`.

- [ ] **Step 7: Verify and commit**

Run:

```bash
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:preferences:impl:testDebugUnitTest --tests '*PreferencesRootViewTest*' --tests '*DefaultPreferencesEntryPointTest*' :features:preferences:impl:compileDebugKotlin
```

Expected: build successful.

Commit:

```bash
git add features/preferences/impl
git commit -m "feat: add webhook triggers settings entry"
```

## Task 2: Room Details Webhook Entry

**Files:**
- Modify: `features/roomdetails/impl/build.gradle.kts`
- Modify: `features/roomdetails/impl/src/main/kotlin/io/element/android/features/roomdetails/impl/RoomDetailsFlowNode.kt`
- Modify: `features/roomdetails/impl/src/main/kotlin/io/element/android/features/roomdetails/impl/RoomDetailsNode.kt`
- Modify: `features/roomdetails/impl/src/main/kotlin/io/element/android/features/roomdetails/impl/RoomDetailsView.kt`
- Modify: `features/roomdetails/impl/src/main/res/values/localazy.xml`
- Test: `features/roomdetails/impl/src/test/kotlin/io/element/android/features/roomdetails/impl/DefaultRoomDetailsEntryPointTest.kt`
- Test: `features/roomdetails/impl/src/test/kotlin/io/element/android/features/roomdetails/impl/RoomDetailsViewTest.kt`

- [ ] **Step 1: Add the feature API/test dependencies**

Add these dependencies:

```kotlin
implementation(projects.features.webhooks.api)
testImplementation(projects.features.webhooks.test)
```

- [ ] **Step 2: Add the room details string**

Add this string to `features/roomdetails/impl/src/main/res/values/localazy.xml`:

```xml
<string name="screen_room_details_webhook_triggers_title">"Webhook triggers"</string>
```

- [ ] **Step 3: Add node callback and view parameter**

Add to `RoomDetailsNode.Callback`:

```kotlin
fun navigateToWebhookTriggers()
```

Add to `RoomDetailsView` parameters:

```kotlin
onWebhookTriggersClick: () -> Unit,
```

Pass it from `RoomDetailsNode.View`:

```kotlin
onWebhookTriggersClick = callback::navigateToWebhookTriggers,
```

- [ ] **Step 4: Add the room-only row**

Inside the `RoomDetailsType.Room` content section, add this row in the room settings/actions category near polls/pinned content or before members:

```kotlin
ListItem(
    headlineContent = { Text(stringResource(R.string.screen_room_details_webhook_triggers_title)) },
    leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Notifications())),
    onClick = onWebhookTriggersClick,
)
```

Do not add the row inside the `RoomDetailsType.Dm` branch.

- [ ] **Step 5: Wire `RoomDetailsFlowNode` to Webhook entrypoint**

Add import and constructor dependency:

```kotlin
import io.element.android.features.webhooks.api.WebhookTriggersEntryPoint
```

```kotlin
private val webhookTriggersEntryPoint: WebhookTriggersEntryPoint,
```

Add nav target:

```kotlin
@Parcelize
data object WebhookTriggers : NavTarget
```

Add room details callback method:

```kotlin
override fun navigateToWebhookTriggers() {
    backstack.push(NavTarget.WebhookTriggers)
}
```

Resolve it with room mode:

```kotlin
NavTarget.WebhookTriggers -> {
    webhookTriggersEntryPoint.createNode(
        parentNode = this,
        buildContext = buildContext,
        params = WebhookTriggersEntryPoint.Params(
            initialTarget = WebhookTriggersEntryPoint.InitialTarget.Room(
                roomId = room.roomId,
                roomName = room.info().name ?: room.roomId.value,
            ),
        ),
        callback = object : WebhookTriggersEntryPoint.Callback {
            override fun onDone() {
                if (backstack.canPop()) {
                    backstack.pop()
                } else {
                    navigateUp()
                }
            }

            override fun onTriggersChanged() = Unit

            override fun onOpenConnectUrl(url: String) {
                learnMoreUrl.value = url
            }
        },
    )
}
```

This deliberately reuses the existing `learnMoreUrl` state and `OpenUrlInTabView(learnMoreUrl)` already present in `RoomDetailsFlowNode`.

- [ ] **Step 6: Add/adjust tests**

Add a RoomDetails view test that uses `aRoomDetailsState(roomType = RoomDetailsType.Room)` and verifies clicking "Webhook triggers" invokes `onWebhookTriggersClick`.

Add a DM view test that uses `RoomDetailsType.Dm` and verifies "Webhook triggers" is not displayed.

Add or update a flow/entrypoint test so navigating to `NavTarget.WebhookTriggers` creates Webhook entrypoint params with `InitialTarget.Room(roomId, roomName)`.

- [ ] **Step 7: Verify and commit**

Run:

```bash
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:roomdetails:impl:testDebugUnitTest --tests '*RoomDetailsViewTest*' --tests '*DefaultRoomDetailsEntryPointTest*' :features:roomdetails:impl:compileDebugKotlin
```

Expected: build successful.

Commit:

```bash
git add features/roomdetails/impl
git commit -m "feat: add webhook triggers room entry"
```

## Task 3: Final Verification

**Files:**
- No source edits expected.

- [ ] **Step 1: Run focused Webhook host tests**

Run:

```bash
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:preferences:impl:testDebugUnitTest :features:roomdetails:impl:testDebugUnitTest :features:webhooks:impl:testDebugUnitTest
```

Expected: build successful.

- [ ] **Step 2: Run host compile checks**

Run:

```bash
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:preferences:impl:compileDebugKotlin :features:roomdetails:impl:compileDebugKotlin :app:assembleDebug
```

Expected: build successful and APKs generated under `app/build/outputs/apk`.

- [ ] **Step 3: Scan for forbidden dependencies/placeholders**

Run:

```bash
rg -n "rustsdk|org\\.matrix\\.rust|voiceplayer|voicerecorder|vault|Vault|sandbox|Sandbox|UnsealUI|UnsealAgent|UnsealMiniApp" features/preferences/impl features/roomdetails/impl -g '!**/build/**'
rg -n "TODO|FIXME|TBD|implement later|fill in details|Not implemented" docs/superpowers/plans/2026-06-09-webhook-triggers-host-entrypoints.md features/preferences/impl features/roomdetails/impl -g '!**/build/**'
```

Expected: no new forbidden dependency usage. The placeholder scan may report pre-existing comments outside touched code; inspect and confirm none were introduced by this spec.

- [ ] **Step 4: Record APK checksums**

Run:

```bash
ls -lh app/build/outputs/apk/fdroid/debug/*.apk app/build/outputs/apk/gplay/debug/*.apk
shasum -a 256 app/build/outputs/apk/fdroid/debug/*.apk app/build/outputs/apk/gplay/debug/*.apk
```

Expected: APK paths, sizes, and hashes are recorded in the final report.

- [ ] **Step 5: Commit verification docs**

If test/build outputs change no source files, no commit is required. If spec status docs are updated, commit them:

```bash
git add docs/superpowers/specs/2026-06-09-webhook-triggers-host-entrypoints-design.md docs/superpowers/plans/2026-06-09-webhook-triggers-host-entrypoints.md
git commit -m "docs: plan webhook trigger host entrypoints"
```

## Self-Review

- Spec coverage: Settings global entry, Room Details room entry, DM exclusion, external connect URL callback, tests, and full build verification are covered.
- Placeholder scan: The plan contains no TBD/FIXME/fill-in placeholders. Notes that require local verification are paired with exact fallback rules and commands.
- Type consistency: `WebhookTriggersEntryPoint.Params`, `InitialTarget.Global`, and `InitialTarget.Room(RoomId, String)` match the API created by the prior Webhook Triggers spec.

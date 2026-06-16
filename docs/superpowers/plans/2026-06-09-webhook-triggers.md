# Webhook Triggers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build native Android Webhook Triggers management matching iOS global and room-scoped list/edit flows.

**Architecture:** Add `features/webhooks` with `api`, `impl`, and `test` modules. Use the same Appyx + Presenter + Metro assisted-injection patterns as `features/roomschedules`, with API calls isolated behind the already completed `ChatbotApiService`.

**Tech Stack:** Kotlin, Compose Material3, Appyx, Metro DI, coroutines, immutable collections, Matrix room-list APIs, `libraries/chatbot/api`, `libraries/chatbot/test`, `libraries/matrix/test`.

---

## File Structure

Create:

- `features/webhooks/api/build.gradle.kts`: public feature module.
- `features/webhooks/api/src/main/kotlin/io/element/android/features/webhooks/api/WebhookTriggersEntryPoint.kt`: initial targets and host callbacks.
- `features/webhooks/api/src/main/kotlin/io/element/android/features/webhooks/api/WebhookTriggerEditMode.kt`: public create/edit target mode shared by entry point and implementation.
- `features/webhooks/impl/build.gradle.kts`: implementation module.
- `features/webhooks/impl/src/main/kotlin/io/element/android/features/webhooks/impl/DefaultWebhookTriggersEntryPoint.kt`: AppScope binding.
- `features/webhooks/impl/src/main/kotlin/io/element/android/features/webhooks/impl/WebhookTriggersFlowNode.kt`: Appyx list/edit flow.
- `features/webhooks/impl/src/main/kotlin/io/element/android/features/webhooks/impl/shared/WebhookFormatters.kt`: trigger status, search, source/event helpers, room summaries.
- `features/webhooks/impl/src/main/kotlin/io/element/android/features/webhooks/impl/list/*`: list state/events/navigator/presenter/node/view.
- `features/webhooks/impl/src/main/kotlin/io/element/android/features/webhooks/impl/edit/*`: edit state/events/navigator/presenter/node/view.
- `features/webhooks/impl/src/test/kotlin/io/element/android/features/webhooks/impl/list/WebhookTriggerListPresenterTest.kt`: list presenter tests.
- `features/webhooks/impl/src/test/kotlin/io/element/android/features/webhooks/impl/edit/WebhookTriggerEditPresenterTest.kt`: edit presenter tests.
- `features/webhooks/test/build.gradle.kts`: fake entry point module.
- `features/webhooks/test/src/main/kotlin/io/element/android/features/webhooks/test/FakeWebhookTriggersEntryPoint.kt`: test fake.

Do not create:

- Connector management screens.
- OAuth/WebView host implementation beyond `onOpenConnectUrl(url)`.
- Rust SDK, voice, vault, sandbox, MiniApp, or Unseal component-library dependencies.

---

### Task 1: Feature Module And Flow Shell

**Files:**
- Create: `features/webhooks/api/build.gradle.kts`
- Create: `features/webhooks/api/src/main/kotlin/io/element/android/features/webhooks/api/WebhookTriggersEntryPoint.kt`
- Create: `features/webhooks/api/src/main/kotlin/io/element/android/features/webhooks/api/WebhookTriggerEditMode.kt`
- Create: `features/webhooks/impl/build.gradle.kts`
- Create: `features/webhooks/impl/src/main/kotlin/io/element/android/features/webhooks/impl/DefaultWebhookTriggersEntryPoint.kt`
- Create: `features/webhooks/impl/src/main/kotlin/io/element/android/features/webhooks/impl/WebhookTriggersFlowNode.kt`
- Create: `features/webhooks/test/build.gradle.kts`
- Create: `features/webhooks/test/src/main/kotlin/io/element/android/features/webhooks/test/FakeWebhookTriggersEntryPoint.kt`

- [x] **Step 1: Add Gradle modules**

Create `features/webhooks/api/build.gradle.kts` with `io.element.android-library`, `kotlin-parcelize`, `projects.libraries.architecture`, `projects.libraries.matrix.api`, and `projects.libraries.chatbot.api`.

Create `features/webhooks/impl/build.gradle.kts` using `io.element.android-compose-library`, `kotlin-parcelize`, `setupDependencyInjection()`, `testCommonDependencies(libs, true)`, implementation dependencies matching `features/roomschedules/impl`, `api(projects.features.webhooks.api)`, `testImplementation(projects.features.webhooks.test)`, `testImplementation(projects.libraries.chatbot.test)`, and `testImplementation(projects.libraries.matrix.test)`.

Create `features/webhooks/test/build.gradle.kts` using `io.element.android-library`, namespace `io.element.android.features.webhooks.test`, `implementation(projects.features.webhooks.api)`, `implementation(projects.libraries.architecture)`, and `implementation(projects.tests.testutils)`.

- [x] **Step 2: Add public entry point**

Create `WebhookTriggersEntryPoint`:

```kotlin
interface WebhookTriggersEntryPoint : FeatureEntryPoint {
    sealed interface InitialTarget : Parcelable {
        @Parcelize data object Global : InitialTarget
        @Parcelize data class Room(val roomId: RoomId, val roomName: String) : InitialTarget
        @Parcelize data class Edit(val mode: WebhookTriggerEditMode) : InitialTarget
    }

    data class Params(val initialTarget: InitialTarget = InitialTarget.Global) : NodeInputs

    fun createNode(parentNode: Node, buildContext: BuildContext, params: Params, callback: Callback): Node

    interface Callback : Plugin {
        fun onDone()
        fun onTriggersChanged()
        fun onOpenConnectUrl(url: String)
    }
}
```

Create `WebhookTriggerEditMode` in the API module, not in implementation:

```kotlin
sealed interface WebhookTriggerEditMode : Parcelable {
    @Parcelize data class Create(val prefilledRoomId: RoomId? = null) : WebhookTriggerEditMode
    @Parcelize data class Edit(val trigger: @RawValue ChatbotWebhookTrigger) : WebhookTriggerEditMode
}
```

This keeps `WebhookTriggersEntryPoint.InitialTarget.Edit` usable by hosts and avoids any API-to-implementation dependency.

- [x] **Step 3: Add default and fake entry points**

Add `DefaultWebhookTriggersEntryPoint` with `@ContributesBinding(AppScope::class)` and `parentNode.createNode<WebhookTriggersFlowNode>(buildContext, plugins = listOf(params, callback))`.

Add `FakeWebhookTriggersEntryPoint` equivalent to `FakeRoomSchedulesEntryPoint`, with a `createNodeResult` lambda defaulting to `lambdaError()`.

- [x] **Step 4: Add flow node shell**

Create `WebhookTriggersFlowNode` as a `BaseFlowNode<NavTarget>` with targets:

```kotlin
sealed interface NavTarget : Parcelable {
    @Parcelize data class List(val mode: WebhookTriggerListMode) : NavTarget
    @Parcelize data class Edit(val mode: WebhookTriggerEditMode) : NavTarget
}
```

Map `InitialTarget.Global` to `List(Global)`, `InitialTarget.Room(roomId, roomName)` to `List(Room(roomId, roomName))`, and `InitialTarget.Edit(mode)` to `Edit(mode)`.

For this task, resolve both targets to a temporary `Node` that renders `Text("Webhook Triggers")`, then later tasks replace it with real nodes.

- [x] **Step 5: Compile module shell**

Run:

```bash
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:webhooks:impl:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 6: Commit shell**

```bash
git add features/webhooks docs/superpowers/specs/2026-06-09-webhook-triggers-design.md docs/superpowers/plans/2026-06-09-webhook-triggers.md
git commit -m "feat: add webhook triggers feature shell"
```

---

### Task 2: Shared Helpers And List Presenter

**Files:**
- Create: `features/webhooks/impl/src/main/kotlin/io/element/android/features/webhooks/impl/shared/WebhookFormatters.kt`
- Create: `features/webhooks/impl/src/main/kotlin/io/element/android/features/webhooks/impl/list/WebhookTriggerListEvents.kt`
- Create: `features/webhooks/impl/src/main/kotlin/io/element/android/features/webhooks/impl/list/WebhookTriggerListMode.kt`
- Create: `features/webhooks/impl/src/main/kotlin/io/element/android/features/webhooks/impl/list/WebhookTriggerListNavigator.kt`
- Create: `features/webhooks/impl/src/main/kotlin/io/element/android/features/webhooks/impl/list/WebhookTriggerListState.kt`
- Create: `features/webhooks/impl/src/main/kotlin/io/element/android/features/webhooks/impl/list/WebhookTriggerListPresenter.kt`
- Create: `features/webhooks/impl/src/test/kotlin/io/element/android/features/webhooks/impl/list/WebhookTriggerListPresenterTest.kt`

- [x] **Step 1: Write presenter tests**

Cover these tests:

```kotlin
`present - global loads event catalog rooms and triggers once`
`present - room mode loads fixed room triggers and room agents`
`event - search trims and matches iOS fields`
`event - room filter reloads global triggers with selected room`
`event - agent filter filters room triggers locally`
`event - toggle status calls api with inverse enabled value and reloads`
`event - delete requires confirmation then deletes and reloads`
`present - load failure preserves previous data and exposes error`
```

Use `FakeChatbotApiService`, `FakeChatbotApiServiceFactory`, `FakeMatrixClient`, `FakeDynamicRoomList`, `aRoomSummary`, `aChatbotWebhookTrigger`, `WarmUpRule`, and `Presenter.test`.

- [x] **Step 2: Implement shared helpers**

Create helpers:

```kotlin
fun ChatbotWebhookTrigger.isEnabled(): Boolean = status == ChatbotWebhookTriggerStatus.Enabled
fun ChatbotWebhookTrigger.matchesWebhookQuery(query: String): Boolean
fun ChatbotWebhookTrigger.withStatus(enabled: Boolean): ChatbotWebhookTrigger
fun ChatbotWebhookEventCatalogResponse.sourceSlugFor(trigger: ChatbotWebhookTrigger): String?
fun ChatbotWebhookTrigger.displaySource(fallback: String? = null): String
```

`matchesWebhookQuery` must trim query and match name, source, joined event types, agent id, action prompt, and room id case-insensitively, exactly like iOS.

- [x] **Step 3: Implement list state/events/navigator**

Create `WebhookTriggerListMode.Global` and `WebhookTriggerListMode.Room(roomId: RoomId, roomName: String)`.

Create events matching iOS: `OnAppear`, `Refresh`, `SearchChanged`, `SelectRoomFilter`, `SelectAgentFilter`, `CreateTrigger`, `EditTrigger`, `ToggleStatus`, `RequestDelete`, `CancelDelete`, `ConfirmDelete`, `ClearError`, `Dismiss`.

State must contain mode, triggers, filteredTriggers, eventSources, availableRooms, selectedRoomId, availableAgents, selectedAgentId, searchQuery, isLoading, error, togglingTriggerId, deletingTriggerId, deleteConfirmationTriggerId, and eventSink.

- [x] **Step 4: Implement list presenter**

Use `@AssistedInject`, assisted `mode`, assisted `navigator`, injected `MatrixClient`, and injected `ChatbotApiServiceFactory`.

Rules:

- `OnAppear` loads event catalog, filter options, and triggers once.
- Global mode loads rooms from `matrixClient.roomListService.allRooms.summaries`.
- Room mode loads agents via `getRoomAgents(roomId.value)`.
- Global trigger query uses selected room id; room mode always uses fixed room id.
- Agent filter is local and applies only in room mode.
- Search applies locally.
- Toggle calls `updateWebhookTriggerStatus(triggerId, !trigger.isEnabled())`, then reloads.
- Delete requires `RequestDelete` then `ConfirmDelete`, calls `deleteWebhookTrigger`, then reloads.
- Failures preserve old data and expose an error string.

- [x] **Step 5: Run focused list tests**

```bash
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:webhooks:impl:testDebugUnitTest --tests '*WebhookTriggerListPresenterTest'
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 6: Commit list presenter**

```bash
git add features/webhooks/impl/src/main/kotlin/io/element/android/features/webhooks/impl/shared features/webhooks/impl/src/main/kotlin/io/element/android/features/webhooks/impl/list features/webhooks/impl/src/test/kotlin/io/element/android/features/webhooks/impl/list
git commit -m "feat: add webhook trigger list presenter"
```

---

### Task 3: Edit Presenter

**Files:**
- Create: `features/webhooks/impl/src/main/kotlin/io/element/android/features/webhooks/impl/edit/WebhookTriggerEditEvents.kt`
- Create: `features/webhooks/impl/src/main/kotlin/io/element/android/features/webhooks/impl/edit/WebhookTriggerEditNavigator.kt`
- Create: `features/webhooks/impl/src/main/kotlin/io/element/android/features/webhooks/impl/edit/WebhookTriggerEditState.kt`
- Create: `features/webhooks/impl/src/main/kotlin/io/element/android/features/webhooks/impl/edit/WebhookTriggerEditPresenter.kt`
- Create: `features/webhooks/impl/src/test/kotlin/io/element/android/features/webhooks/impl/edit/WebhookTriggerEditPresenterTest.kt`

- [x] **Step 1: Write edit presenter tests**

Cover:

```kotlin
`present - create mode loads catalog and rooms and preselects room`
`present - edit mode seeds fields source event types account room and agent`
`event - select source clears event types account and loads connected accounts`
`event - select room clears agent and loads room agents`
`event - generate draft fills source events name and action`
`state - can save requires name event type prompt room and create agent`
`event - create sends iOS request body and emits saved`
`event - update sends metadata request and emits saved`
`event - connect source opens connect url through navigator`
`present - failure preserves editable state and exposes error`
```

- [x] **Step 2: Implement edit state/events/navigator**

Use the API module `WebhookTriggerEditMode.Create(prefilledRoomId: RoomId?)` and `WebhookTriggerEditMode.Edit(trigger: ChatbotWebhookTrigger)` in the edit presenter and flow.

State must mirror iOS: mode, eventSources, selectedSource, selectedEventTypes, connectedAccounts, selectedAccount, availableRooms, selectedRoomId, availableAgents, selectedAgentId, name, description, actionPrompt, draftPrompt, loading flags, error, and derived `canSave`.

Navigator must expose `onSaved(trigger)`, `onCancelled()`, and `onOpenConnectUrl(url)`.

- [x] **Step 3: Implement edit presenter**

Rules:

- `OnAppear` loads event catalog and rooms once, then seeds create/edit mode.
- Create mode uses `prefilledRoomId` and loads agents for that room.
- Edit mode seeds name, description, action prompt, room id, selected event types, source resolved from catalog, connected accounts, account from provider trigger connection id, room agents, and selected agent id.
- `SelectSource` resets event types/account/account list and loads connected accounts for source.
- `ToggleEventType` toggles ids.
- `SelectRoom` clears agent and loads agents.
- `GenerateDraft` trims prompt, calls `draftWebhookTrigger`, fills source, event types, name, and action prompt.
- `ConnectSource` calls `initiateConnection(source.source, "io.element.android.x://composio-callback?toolkit=<slug>")` and sends URL to navigator.
- Create request trims name/action, uses null description when blank, selected account id as `connectionId`, selected event types as a sorted list, selected room id, and selected agent id.
- Update request sends name, description, action prompt, and room id only.

- [x] **Step 4: Run focused edit tests**

```bash
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:webhooks:impl:testDebugUnitTest --tests '*WebhookTriggerEditPresenterTest'
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 5: Commit edit presenter**

```bash
git add features/webhooks/impl/src/main/kotlin/io/element/android/features/webhooks/impl/edit features/webhooks/impl/src/test/kotlin/io/element/android/features/webhooks/impl/edit
git commit -m "feat: add webhook trigger edit presenter"
```

---

### Task 4: Nodes, Compose UI, And Final Verification

**Files:**
- Create: `features/webhooks/impl/src/main/kotlin/io/element/android/features/webhooks/impl/list/WebhookTriggerListNode.kt`
- Create: `features/webhooks/impl/src/main/kotlin/io/element/android/features/webhooks/impl/list/WebhookTriggerListView.kt`
- Create: `features/webhooks/impl/src/main/kotlin/io/element/android/features/webhooks/impl/edit/WebhookTriggerEditNode.kt`
- Create: `features/webhooks/impl/src/main/kotlin/io/element/android/features/webhooks/impl/edit/WebhookTriggerEditView.kt`
- Modify: `features/webhooks/impl/src/main/kotlin/io/element/android/features/webhooks/impl/WebhookTriggersFlowNode.kt`

- [x] **Step 1: Wire flow to real nodes**

Resolve list targets to `WebhookTriggerListNode` and edit targets to `WebhookTriggerEditNode`.

List `CreateTrigger` pushes `Edit(Create(prefilledRoomId))`, where room mode passes its fixed room id and global mode passes null. List `EditTrigger` pushes `Edit(Edit(trigger))`. Edit saved calls `callback.onTriggersChanged()`, emits reload to list, and pops.

- [x] **Step 2: Add Compose views**

List UI: Back, title, Create, Refresh, search, room filter in global mode, agent filter in room mode, rows with name/source/events/agent/room/status, enable/disable button, edit button, delete confirmation controls, and loading/error text.

Edit UI: Back/Cancel, title, draft prompt/generate, name, description, source picker, event type toggles, account picker, connect button, room picker, agent picker in create mode, action prompt, Save, loading/error text.

Use simple Material3 controls and existing project design dependencies. Do not add WebView, connector management, vault, voice, sandbox, or MiniApp UI.

- [x] **Step 3: Run feature tests and compiles**

```bash
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:webhooks:impl:testDebugUnitTest :features:webhooks:impl:compileDebugKotlin :features:webhooks:impl:compileDebugUnitTestKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 4: Run regression and scans**

```bash
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :libraries:chatbot:impl:testDebugUnitTest
rg -n "rustsdk|org\\.matrix\\.rust|voiceplayer|voicerecorder|vault|Vault|sandbox|Sandbox|UnsealUI|UnsealAgent|UnsealMiniApp" features/webhooks -g '!**/build/**'
rg -n "TODO|FIXME|TBD|implement later|fill in details|Not implemented|error\\(" features/webhooks -g '!**/build/**'
```

Expected: Gradle passes; both `rg` scans produce no production-source output.

- [x] **Step 5: Commit UI and verification**

```bash
git add features/webhooks docs/superpowers/plans/2026-06-09-webhook-triggers.md
git commit -m "feat: add webhook triggers screens"
```

---

## Self-Review

Spec coverage:

- Global/room list modes are covered by Task 2 and Task 4.
- Search/filter/load/toggle/delete are covered by Task 2.
- Edit create/update/draft/connect/account/event/room/agent behavior is covered by Task 3.
- Appyx entry point and UI are covered by Task 1 and Task 4.
- Forbidden dependencies are covered by Task 4 scans.

Placeholder scan:

- No task contains TBD, TODO, implement later, or fill-in-only instructions.

Type consistency:

- `WebhookTriggerListMode`, `WebhookTriggerEditMode`, flow targets, presenter states, and entry-point targets use the same room id and trigger model shapes across tasks.

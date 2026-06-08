# Skills Marketplace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the native Android Skills feature matching iOS Skills Home, public marketplace pagination/search, and skill detail metadata edit/delete flows without Rust SDK or missing Unseal component-library dependencies.

**Architecture:** Add standalone `features/skills` `api`, `impl`, and `test` modules. The implementation follows the existing Agent Management feature structure: public `FeatureEntryPoint`, Appyx `BaseFlowNode`, focused nodes per screen, `Presenter<State>` classes that call `ChatbotApiServiceFactory.createForUnsealApi(matrixClient)`, and presenter unit tests using `FakeChatbotApiService`.

**Tech Stack:** Kotlin, Compose Material3, Appyx, Metro DI, coroutines, immutable collections, `libraries/chatbot/api`, `libraries/chatbot/test`, `libraries/matrix/api`, Element X Android architecture/test utilities.

---

## File Structure

Create:

- `features/skills/api/build.gradle.kts`: public module for the Skills entry point.
- `features/skills/api/src/main/kotlin/io/element/android/features/skills/api/SkillsEntryPoint.kt`: initial targets and host callbacks.
- `features/skills/impl/build.gradle.kts`: implementation module.
- `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/DefaultSkillsEntryPoint.kt`: AppScope binding.
- `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/SkillsFlowNode.kt`: Appyx backstack for home, marketplace, detail, and create seam.
- `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/shared/SkillFormatters.kt`: title, visibility labels, date formatting, search helpers, JSON visibility serialization.
- `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/home/SkillsHomeEvents.kt`: home events.
- `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/home/SkillsHomeNavigator.kt`: home navigation seam.
- `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/home/SkillsHomeState.kt`: home state and derived fields.
- `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/home/SkillsHomePresenter.kt`: iOS-aligned home/list/marketplace state machine.
- `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/home/SkillsHomeNode.kt`: Appyx node for home.
- `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/home/SkillsHomeView.kt`: Compose home UI.
- `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/detail/SkillDetailEvents.kt`: detail events.
- `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/detail/SkillDetailNavigator.kt`: detail navigation seam.
- `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/detail/SkillDetailState.kt`: detail state and derived fields.
- `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/detail/SkillDetailPresenter.kt`: detail load/edit/save/delete state machine.
- `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/detail/SkillDetailNode.kt`: Appyx node for detail.
- `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/detail/SkillDetailView.kt`: Compose detail UI.
- `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/marketplace/SkillMarketplaceEvents.kt`: marketplace-only events.
- `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/marketplace/SkillMarketplaceNavigator.kt`: marketplace-only navigation seam.
- `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/marketplace/SkillMarketplaceState.kt`: marketplace-only state.
- `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/marketplace/SkillMarketplacePresenter.kt`: marketplace-only pagination/search state machine.
- `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/marketplace/SkillMarketplaceNode.kt`: Appyx node for marketplace-only screen.
- `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/marketplace/SkillMarketplaceView.kt`: Compose marketplace-only UI.
- `features/skills/test/build.gradle.kts`: fake entry point module.
- `features/skills/test/src/main/kotlin/io/element/android/features/skills/test/FakeSkillsEntryPoint.kt`: test fake.
- `features/skills/impl/src/test/kotlin/io/element/android/features/skills/impl/home/SkillsHomePresenterTest.kt`: home tests.
- `features/skills/impl/src/test/kotlin/io/element/android/features/skills/impl/detail/SkillDetailPresenterTest.kt`: detail tests.
- `features/skills/impl/src/test/kotlin/io/element/android/features/skills/impl/marketplace/SkillMarketplacePresenterTest.kt`: marketplace-only tests.

Modify only if required by compile:

- `settings.gradle.kts`: add module includes only if auto-include does not discover `features/skills/*`.

Do not create:

- Skill create/import/upload implementation.
- Agent skill attach/detach implementation.
- File viewer/downloader implementation.
- Any dependency on `libraries/rustsdk`, `voiceplayer`, `voicerecorder`, vault modules, `UnsealUI`, `UnsealAgent`, or `UnsealMiniApp`.

---

### Task 1: Feature Module And Flow Shell

**Files:**
- Create: `features/skills/api/build.gradle.kts`
- Create: `features/skills/api/src/main/kotlin/io/element/android/features/skills/api/SkillsEntryPoint.kt`
- Create: `features/skills/impl/build.gradle.kts`
- Create: `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/DefaultSkillsEntryPoint.kt`
- Create: `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/SkillsFlowNode.kt`
- Create: `features/skills/test/build.gradle.kts`
- Create: `features/skills/test/src/main/kotlin/io/element/android/features/skills/test/FakeSkillsEntryPoint.kt`

- [ ] **Step 1: Add the API module**

Create `features/skills/api/build.gradle.kts` matching the Agent Management API module:

```kotlin
plugins {
    id("io.element.android-library")
    id("kotlin-parcelize")
}

android {
    namespace = "io.element.android.features.skills.api"
}

dependencies {
    implementation(projects.libraries.architecture)
}
```

- [ ] **Step 2: Add the public entry point**

Create `SkillsEntryPoint` with these exact public types:

```kotlin
interface SkillsEntryPoint : FeatureEntryPoint {
    sealed interface InitialTarget : Parcelable {
        @Parcelize data object Home : InitialTarget
        @Parcelize data object Marketplace : InitialTarget
        @Parcelize data class Detail(val id: String, val isOwner: Boolean) : InitialTarget
        @Parcelize data object Create : InitialTarget
    }

    data class Params(val initialTarget: InitialTarget = InitialTarget.Home) : NodeInputs

    fun createNode(parentNode: Node, buildContext: BuildContext, params: Params, callback: Callback): Node

    interface Callback : Plugin {
        fun onDone()
        fun onCreateSkill()
        fun onSkillDeleted(id: String)
    }
}
```

Include imports for `Parcelable`, Appyx `BuildContext`, `Node`, `Plugin`, `FeatureEntryPoint`, `NodeInputs`, and `kotlinx.parcelize.Parcelize`.

- [ ] **Step 3: Add implementation and test build files**

Create `features/skills/impl/build.gradle.kts` with the same plugin/dependency shape as `features/agentmanagement/impl/build.gradle.kts`, replacing the API dependency with `api(projects.features.skills.api)` and test fake dependency with `testImplementation(projects.features.skills.test)`.

Create `features/skills/test/build.gradle.kts` with:

```kotlin
plugins {
    id("io.element.android-compose-library")
}

android {
    namespace = "io.element.android.features.skills.test"
}

dependencies {
    api(projects.features.skills.api)
    implementation(projects.libraries.architecture)
    implementation(projects.tests.testutils)
}
```

- [ ] **Step 4: Add entry point binding and flow shell**

Create `DefaultSkillsEntryPoint` using `@ContributesBinding(AppScope::class)` and `parentNode.createNode<SkillsFlowNode>(buildContext, plugins = listOf(params, callback))`.

Create `SkillsFlowNode` with nav targets `Home`, `Marketplace`, `Detail(id, isOwner)`, and `Create`. `Create` must call `callback.onCreateSkill()` and return to the previous node if possible. Home must push detail for selected skills and call the create seam. Detail deletion must call `callback.onSkillDeleted(id)` and pop. Marketplace-only selection must push non-owner detail.

- [ ] **Step 5: Add fake entry point**

Create `FakeSkillsEntryPoint` that implements `SkillsEntryPoint` and returns a lightweight `Node` whose `View` is empty. Store the last `Params` and `Callback` in mutable properties so host tests can inspect them later.

- [ ] **Step 6: Verify module shell compiles**

Run:

```bash
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:skills:impl:compileDebugKotlin
```

Expected: build reaches compile and either succeeds after placeholder nodes are added or fails only because screen node classes from later tasks are still missing. If it fails because Gradle does not discover the new modules, add the `features/skills/*` includes using the same convention as existing feature modules, then rerun.

- [ ] **Step 7: Commit module shell**

```bash
git add features/skills docs/superpowers/specs/2026-06-08-skills-marketplace-design.md docs/superpowers/plans/2026-06-08-skills-marketplace.md
git commit -m "feat: add skills feature shell"
```

---

### Task 2: Shared Skill Formatters

**Files:**
- Create: `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/shared/SkillFormatters.kt`
- Test through presenter tests in Tasks 3-5.

- [ ] **Step 1: Implement shared helpers**

Create helpers with these signatures:

```kotlin
fun ChatbotUserSkill.matchesSkillQuery(query: String): Boolean
fun ChatbotSkillVisibility.displayName(): String
fun ChatbotSkillVisibility.apiValue(): String
fun ChatbotUserSkill.createdDateLabel(): String?
fun List<ChatbotUserSkill>.sortedBySkillName(): List<ChatbotUserSkill>
```

Required behavior:

- `matchesSkillQuery` trims query, returns true for blank query, and matches `name` or `description` with `ignoreCase = true`.
- `displayName` returns `Private`, `Public`, `Shared`.
- `apiValue` returns `private`, `public`, `shared`.
- `createdDateLabel` supports ISO-8601 strings and numeric Unix timestamp strings; return `null` if parsing fails.
- `sortedBySkillName` sorts by `name`.

- [ ] **Step 2: Commit helpers with first presenter task or shell**

Do not make a separate commit unless no presenter work is ready; otherwise stage this with Task 3.

---

### Task 3: Skills Home Presenter

**Files:**
- Create: `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/home/SkillsHomeEvents.kt`
- Create: `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/home/SkillsHomeNavigator.kt`
- Create: `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/home/SkillsHomeState.kt`
- Create: `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/home/SkillsHomePresenter.kt`
- Create: `features/skills/impl/src/test/kotlin/io/element/android/features/skills/impl/home/SkillsHomePresenterTest.kt`

- [ ] **Step 1: Write failing presenter tests**

Cover these test names:

```kotlin
`present - loads user skills once and sorts by name`
`event - refresh reloads user skills and marketplace only when selected`
`event - local search trims and matches name or description case insensitively`
`event - selecting marketplace clears search and loads first page once`
`event - marketplace search debounces and sends trimmed nullable query`
`event - next marketplace page appends and stops when total reached`
`event - selecting marketplace skill computes ownership from user skills`
```

Use `FakeChatbotApiService`, `FakeChatbotApiServiceFactory`, `FakeMatrixClient`, `WarmUpRule`, and the `Presenter.test` Turbine helper exactly like `AgentListPresenterTest`.

- [ ] **Step 2: Implement state/events/navigator**

Required public names:

```kotlin
enum class SkillsHomeTab { Mine, Marketplace }

sealed interface SkillsHomeEvents {
    data object OnAppear : SkillsHomeEvents
    data object Refresh : SkillsHomeEvents
    data object CreateSkill : SkillsHomeEvents
    data class SelectSkill(val id: String) : SkillsHomeEvents
    data class SelectMarketplaceSkill(val id: String) : SkillsHomeEvents
    data class SelectTab(val tab: SkillsHomeTab) : SkillsHomeEvents
    data class SearchQueryChanged(val query: String) : SkillsHomeEvents
    data object LoadNextMarketplacePage : SkillsHomeEvents
    data object ClearError : SkillsHomeEvents
}

interface SkillsHomeNavigator {
    fun onCreateSkill()
    fun onOpenSkill(id: String, isOwner: Boolean)
}
```

`SkillsHomeState` must expose user skills, filtered user skills, selected tab, marketplace skills, marketplace total/page/page size, loading flags, search query, error, `marketplaceHasMore`, and event sink.

- [ ] **Step 3: Implement presenter**

Use `@AssistedInject`, assisted `SkillsHomeNavigator`, injected `MatrixClient`, and `ChatbotApiServiceFactory`.

Behavior:

- `OnAppear` loads user skills only once.
- `Refresh` reloads user skills; if selected tab is marketplace, reload page 1.
- User skills are sorted by name.
- `SearchQueryChanged` updates query immediately; if selected tab is marketplace, debounce 450 ms and reload page 1.
- `SelectTab` clears search and loads marketplace page 1 only if marketplace list is empty.
- `LoadNextMarketplacePage` guards loading and `marketplaceHasMore`.
- `SelectSkill` opens owner detail.
- `SelectMarketplaceSkill` opens owner detail when the selected ID exists in current user skills.
- Failures preserve previous data and set `error`.

- [ ] **Step 4: Run focused home tests**

```bash
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:skills:impl:testDebugUnitTest --tests '*SkillsHomePresenterTest'
```

Expected: all `SkillsHomePresenterTest` tests pass.

- [ ] **Step 5: Commit home presenter**

```bash
git add features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/shared features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/home features/skills/impl/src/test/kotlin/io/element/android/features/skills/impl/home
git commit -m "feat: add skills home flow"
```

---

### Task 4: Skill Detail Presenter

**Files:**
- Create: `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/detail/SkillDetailEvents.kt`
- Create: `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/detail/SkillDetailNavigator.kt`
- Create: `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/detail/SkillDetailState.kt`
- Create: `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/detail/SkillDetailPresenter.kt`
- Create: `features/skills/impl/src/test/kotlin/io/element/android/features/skills/impl/detail/SkillDetailPresenterTest.kt`

- [ ] **Step 1: Write failing presenter tests**

Cover these test names:

```kotlin
`present - loads detail once and refreshes on demand`
`present - load failure preserves previous response and exposes error`
`event - owner start edit seeds fields and cancel exits editing`
`event - owner save sends metadata body updates skill and exits editing`
`event - non owner edit save and delete are ignored`
`event - delete emits deleted action on success`
`event - delete failure preserves state and exposes error`
```

- [ ] **Step 2: Implement detail state/events/navigator**

Required public names:

```kotlin
sealed interface SkillDetailEvents {
    data object OnAppear : SkillDetailEvents
    data object Refresh : SkillDetailEvents
    data object StartEditing : SkillDetailEvents
    data object CancelEditing : SkillDetailEvents
    data class EditNameChanged(val value: String) : SkillDetailEvents
    data class EditDescriptionChanged(val value: String) : SkillDetailEvents
    data class EditVisibilityChanged(val value: ChatbotSkillVisibility) : SkillDetailEvents
    data object SaveEditing : SkillDetailEvents
    data object Delete : SkillDetailEvents
    data object ClearError : SkillDetailEvents
}

interface SkillDetailNavigator {
    fun onDeleted(id: String)
}
```

`SkillDetailState` must include id, isOwner, response, isLoading, isSaving, isEditing, edit fields, error, derived skill/title/canEdit.

- [ ] **Step 3: Implement detail presenter**

Use the same DI shape as home. Build update body with:

```kotlin
buildJsonObject {
    put("name", editName)
    put("description", editDescription)
    put("visibility", editVisibility.apiValue())
}
```

Behavior:

- `OnAppear` loads once.
- `Refresh` reloads.
- Start editing does nothing without a loaded skill or when not owner.
- Start editing seeds name, description, and visibility defaulting to `Private`.
- Save does nothing when not owner or not editing.
- Successful save replaces `response` with `ChatbotGetUserSkillResponse(skill = updatedSkill)` and exits edit mode.
- Delete does nothing when not owner; successful delete calls `navigator.onDeleted(id)`.
- Failures preserve existing response and set error.

- [ ] **Step 4: Run focused detail tests**

```bash
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:skills:impl:testDebugUnitTest --tests '*SkillDetailPresenterTest'
```

Expected: all detail tests pass.

- [ ] **Step 5: Commit detail presenter**

```bash
git add features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/detail features/skills/impl/src/test/kotlin/io/element/android/features/skills/impl/detail
git commit -m "feat: add skill detail flow"
```

---

### Task 5: Marketplace-Only Presenter

**Files:**
- Create: `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/marketplace/SkillMarketplaceEvents.kt`
- Create: `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/marketplace/SkillMarketplaceNavigator.kt`
- Create: `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/marketplace/SkillMarketplaceState.kt`
- Create: `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/marketplace/SkillMarketplacePresenter.kt`
- Create: `features/skills/impl/src/test/kotlin/io/element/android/features/skills/impl/marketplace/SkillMarketplacePresenterTest.kt`

- [ ] **Step 1: Write failing presenter tests**

Cover:

```kotlin
`present - loads first page once`
`event - refresh replaces first page`
`event - search debounces and sends trimmed nullable query`
`event - load next page appends results`
`event - select skill opens detail`
`present - failure preserves existing skills and exposes error`
```

- [ ] **Step 2: Implement marketplace presenter**

Mirror iOS `SkillMarketplaceScreenViewModel`:

- page starts at 1;
- page size is 20;
- empty search becomes null;
- `hasMore` uses `total` when present, otherwise `skills.size >= pageSize`;
- load next page is ignored while loading next page or when no more pages.

- [ ] **Step 3: Run focused marketplace tests**

```bash
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:skills:impl:testDebugUnitTest --tests '*SkillMarketplacePresenterTest'
```

Expected: all marketplace tests pass.

- [ ] **Step 4: Commit marketplace presenter**

```bash
git add features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/marketplace features/skills/impl/src/test/kotlin/io/element/android/features/skills/impl/marketplace
git commit -m "feat: add skill marketplace flow"
```

---

### Task 6: Compose Views And Node Wiring

**Files:**
- Create: `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/home/SkillsHomeNode.kt`
- Create: `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/home/SkillsHomeView.kt`
- Create: `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/detail/SkillDetailNode.kt`
- Create: `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/detail/SkillDetailView.kt`
- Create: `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/marketplace/SkillMarketplaceNode.kt`
- Create: `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/marketplace/SkillMarketplaceView.kt`
- Modify: `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/SkillsFlowNode.kt`

- [ ] **Step 1: Wire nodes to presenters**

Each node must use the same pattern as `AgentListNode`, `AgentDetailNode`, and `AgentEditNode`: assisted `BuildContext`, assisted plugins, injected presenter factory, local callback/navigator adapter, and a `View` method that calls the Compose view with `presenter.present()`.

- [ ] **Step 2: Add Compose UI**

Use simple Material3 Compose controls:

- Home: Back, Create, Refresh, search field, two buttons/tabs for Mine/Marketplace, loading indicator, empty text, rows, load-more button.
- Detail: Back, Refresh, read/edit sections, Save/Cancel, Delete only when owner, loading/error text.
- Marketplace-only: Back, Refresh, search, list, load-more.

Do not add file picker, upload, file viewer, agent attach, voice, vault, sandbox, or MiniApp UI.

- [ ] **Step 3: Run full skills impl tests and compile**

```bash
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:skills:impl:testDebugUnitTest
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:skills:impl:compileDebugKotlin :features:skills:impl:compileDebugUnitTestKotlin
```

Expected: both commands pass.

- [ ] **Step 4: Run dependency scan**

```bash
rg "rustsdk|org\\.matrix\\.rust|voiceplayer|voicerecorder|vault|UnsealUI|UnsealAgent|UnsealMiniApp" features/skills -n
```

Expected: no output.

- [ ] **Step 5: Commit UI and node wiring**

```bash
git add features/skills
git commit -m "feat: add skills ui wiring"
```

---

### Task 7: Final Verification

**Files:**
- No code changes expected.

- [ ] **Step 1: Run chatbot regression tests**

```bash
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :libraries:chatbot:impl:testDebugUnitTest
```

Expected: pass.

- [ ] **Step 2: Check final git state**

```bash
git status --short --branch
git log --oneline -8
```

Expected: worktree clean after all commits; recent commits include the Skills feature chunks.

- [ ] **Step 3: Document any deferred integration**

If the host app is not wired to `SkillsEntryPoint`, leave it as an explicit follow-up in the final response. Do not add settings/start-chat integration in this spec unless required by compile.

---

## Self-Review

Spec coverage:

- Home list/search/refresh/sort: Task 3.
- Marketplace pagination/search/has-more: Tasks 3 and 5.
- Detail load/edit/delete/owner behavior: Task 4.
- Entry points/navigation seams: Tasks 1 and 6.
- Native-only dependency boundary: Task 6 dependency scan and Task 7 regression.
- Create/import/upload excluded but exposed as seam: Tasks 1 and 6.

Placeholder scan:

- No `TBD`, `TODO`, or undefined "implement later" steps remain.
- Each task has exact files, behavior, commands, and expected results.

Type consistency:

- `SkillsEntryPoint.InitialTarget.Detail(id, isOwner)` maps to `SkillsFlowNode.NavTarget.Detail(id, isOwner)`.
- Home, marketplace, and detail navigators use the same `id` and `isOwner` ownership shape as the iOS coordinator actions.
- Visibility serialization is centralized in `ChatbotSkillVisibility.apiValue()` and reused by detail update.

# Agent Skills Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Android Agent Skills management matching iOS Agent skill picker semantics: load attached skills, browse Mine/Public skills, select skills, and add only newly selected skills to a specific Agent.

**Architecture:** Extend the existing `features/skills` flow instead of creating a separate feature module. Add a focused `agentskills` presenter/state/view/node and a simple `managementhub` node, reuse `ChatbotApiServiceFactory.createForUnsealApi(matrixClient)`, and keep host callbacks in `SkillsEntryPoint` for Agent Management and Skill creation seams.

**Tech Stack:** Kotlin, Compose Material3, Appyx, Metro assisted injection, coroutines, immutable collections, `libraries/chatbot/api`, `libraries/chatbot/test`, `libraries/matrix/api`, existing Element X presenter test utilities.

---

## File Structure

Modify:

- `features/skills/api/src/main/kotlin/io/element/android/features/skills/api/SkillsEntryPoint.kt`: add `AgentSkills(botName)` and `ManagementHub` initial targets plus `onOpenAgentManagement()`.
- `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/SkillsFlowNode.kt`: add nav targets and callbacks for Agent Skills and Management Hub.
- `features/skills/test/src/main/kotlin/io/element/android/features/skills/test/FakeSkillsEntryPoint.kt`: implement new callback method when required by compile.

Create:

- `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/agentskills/AgentSkillsEvents.kt`: event definitions.
- `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/agentskills/AgentSkillsNavigator.kt`: completion callback seam.
- `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/agentskills/AgentSkillsState.kt`: UI state and derived fields.
- `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/agentskills/AgentSkillsPresenter.kt`: iOS-aligned load/search/pagination/save state machine.
- `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/agentskills/AgentSkillsNode.kt`: Appyx node with `botName` input.
- `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/agentskills/AgentSkillsView.kt`: Compose screen.
- `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/managementhub/SkillsManagementHubNode.kt`: hub node.
- `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/managementhub/SkillsManagementHubView.kt`: two-row Compose hub.
- `features/skills/impl/src/test/kotlin/io/element/android/features/skills/impl/agentskills/AgentSkillsPresenterTest.kt`: presenter behavior tests.

Do not create or modify:

- Any remove/detach Agent skill API.
- Room composer skill picker or timeline metadata.
- Rust SDK, voice, vault, sandbox, MiniApp, or component-library dependencies.

---

### Task 1: Entry Targets And Flow Wiring

**Files:**
- Modify: `features/skills/api/src/main/kotlin/io/element/android/features/skills/api/SkillsEntryPoint.kt`
- Modify: `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/SkillsFlowNode.kt`
- Modify: `features/skills/test/src/main/kotlin/io/element/android/features/skills/test/FakeSkillsEntryPoint.kt`
- Create: `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/managementhub/SkillsManagementHubNode.kt`
- Create: `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/managementhub/SkillsManagementHubView.kt`
- Create placeholder: `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/agentskills/AgentSkillsNode.kt`
- Create placeholder: `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/agentskills/AgentSkillsView.kt`

- [x] **Step 1: Extend the public entry point**

Add two initial targets to `SkillsEntryPoint.InitialTarget`:

```kotlin
@Parcelize
data class AgentSkills(val botName: String) : InitialTarget

@Parcelize
data object ManagementHub : InitialTarget
```

Add a callback method:

```kotlin
fun onOpenAgentManagement()
```

- [x] **Step 2: Add flow nav targets**

In `SkillsFlowNode.NavTarget`, add:

```kotlin
@Parcelize
data class AgentSkills(val botName: String) : NavTarget

@Parcelize
data object ManagementHub : NavTarget
```

Update `toNavTarget()`:

```kotlin
is SkillsEntryPoint.InitialTarget.AgentSkills -> SkillsFlowNode.NavTarget.AgentSkills(botName)
SkillsEntryPoint.InitialTarget.ManagementHub -> SkillsFlowNode.NavTarget.ManagementHub
```

- [x] **Step 3: Wire the hub target**

Create `SkillsManagementHubNode` with callback:

```kotlin
interface Callback : Plugin {
    fun onDone()
    fun onOpenAgentManagement()
    fun onOpenSkillsHome()
}
```

Its `View` must call `SkillsManagementHubView(onBackClick = callback::onDone, onOpenAgentManagement = callback::onOpenAgentManagement, onOpenSkills = callback::onOpenSkillsHome)`.

Create `SkillsManagementHubView` with title `Agent & Skills`, a Back button, and two full-width buttons: `Agent Management` and `Skills Management`.

In `SkillsFlowNode.resolve`, map `ManagementHub` to the node. Hub `onOpenAgentManagement()` calls `callback.onOpenAgentManagement()`. Hub `onOpenSkillsHome()` pushes `NavTarget.Home`.

- [x] **Step 4: Add temporary Agent Skills node shell**

Create `AgentSkillsNode` with:

```kotlin
@Parcelize
data class Inputs(val botName: String) : Parcelable

interface Callback : Plugin {
    fun onDone()
}
```

For Task 1 only, render `AgentSkillsView(botName = inputs.botName, onBackClick = callback::onDone)`.

Create temporary `AgentSkillsView`:

```kotlin
@Composable
fun AgentSkillsView(botName: String, onBackClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onBackClick) { Text("Back") }
        Text("Agent Skills", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(botName)
    }
}
```

- [x] **Step 5: Wire Agent Skills target**

In `SkillsFlowNode.resolve`, map `NavTarget.AgentSkills(botName)` to `AgentSkillsNode` with `AgentSkillsNode.Inputs(botName)` and a callback whose `onDone()` calls `closeOrPop()`.

- [x] **Step 6: Update fake entry point**

If compile requires it, add an empty `override fun onOpenAgentManagement() = Unit` to any callback fake or test callback affected by the `SkillsEntryPoint.Callback` change.

- [x] **Step 7: Verify flow compiles**

Run:

```bash
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:skills:impl:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 8: Commit flow shell**

```bash
git add features/skills docs/superpowers/specs/2026-06-09-agent-skills-management-design.md docs/superpowers/plans/2026-06-09-agent-skills-management.md
git commit -m "feat: add agent skills entry points"
```

---

### Task 2: Agent Skills Presenter

**Files:**
- Create: `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/agentskills/AgentSkillsEvents.kt`
- Create: `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/agentskills/AgentSkillsNavigator.kt`
- Create: `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/agentskills/AgentSkillsState.kt`
- Create: `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/agentskills/AgentSkillsPresenter.kt`
- Create: `features/skills/impl/src/test/kotlin/io/element/android/features/skills/impl/agentskills/AgentSkillsPresenterTest.kt`

- [x] **Step 1: Write presenter tests first**

Create tests covering these names:

```kotlin
`present - loads attached and user skills once`
`event - mine search trims and matches name or description`
`event - selecting public tab loads first page once`
`event - public search debounces and sends trimmed nullable query`
`event - next public page appends and stops when total reached`
`event - save adds only newly selected skills in sorted order`
`event - save with no new skills completes without api call`
`event - partial save failure keeps failed skill selected and merges successful additions`
`event - load failure preserves previously loaded state`
```

Use `FakeChatbotApiService`, `FakeChatbotApiServiceFactory`, `FakeMatrixClient`, `WarmUpRule`, and the existing `Presenter.test` helper in the same style as `SkillsHomePresenterTest`.

- [x] **Step 2: Add state and events**

Create:

```kotlin
enum class AgentSkillsTab { Mine, Public }

sealed interface AgentSkillsEvents {
    data object OnAppear : AgentSkillsEvents
    data object Refresh : AgentSkillsEvents
    data class SelectTab(val tab: AgentSkillsTab) : AgentSkillsEvents
    data class SearchQueryChanged(val query: String) : AgentSkillsEvents
    data class ToggleSkill(val skill: ChatbotUserSkill) : AgentSkillsEvents
    data object LoadNextPublicPage : AgentSkillsEvents
    data object Save : AgentSkillsEvents
    data object ClearError : AgentSkillsEvents
}
```

Create `AgentSkillsNavigator`:

```kotlin
interface AgentSkillsNavigator {
    fun onSaved()
}
```

Create `AgentSkillSaveFailure`:

```kotlin
data class AgentSkillSaveFailure(val skillId: String, val message: String)
```

Create `AgentSkillsState` with fields:

```kotlin
val botName: String
val attachedSkills: ImmutableList<ChatbotUserSkill>
val userSkills: ImmutableList<ChatbotUserSkill>
val selectedSkills: ImmutableList<ChatbotUserSkill>
val originalSkillIds: ImmutableSet<String>
val selectedSkillIds: ImmutableSet<String>
val selectedTab: AgentSkillsTab
val searchQuery: String
val publicSkills: ImmutableList<ChatbotUserSkill>
val publicTotal: Int?
val publicPage: Int
val publicPageSize: Int
val isLoading: Boolean
val isLoadingPublic: Boolean
val isLoadingPublicNextPage: Boolean
val isSaving: Boolean
val saveFailures: ImmutableList<AgentSkillSaveFailure>
val error: String?
val eventSink: (AgentSkillsEvents) -> Unit
```

Add derived fields:

```kotlin
val filteredUserSkills = userSkills.filter { it.matchesSkillQuery(searchQuery) }.toImmutableList()
val publicHasMore = publicTotal?.let { publicSkills.size < it } ?: (publicSkills.size >= publicPageSize)
val newSelectedSkillIds = selectedSkillIds - originalSkillIds
val deselectedOriginalCount = originalSkillIds.count { it !in selectedSkillIds }
```

- [x] **Step 3: Implement presenter load behavior**

`AgentSkillsPresenter` must use `@AssistedInject`, assisted `botName`, assisted `AgentSkillsNavigator`, injected `MatrixClient`, and injected `ChatbotApiServiceFactory`.

Load rules:

- `OnAppear` loads once.
- `Refresh` reloads attached skills and user skills.
- Attached load calls `listAgentSkills(botName)`.
- User skill load calls `listUserSkills(null)` and sorts by skill name.
- First successful attached load sets `originalSkillIds` and `selectedSkillIds` to attached ids and `selectedSkills` to attached skills.
- Later refreshes should update `attachedSkills` and merge attached ids into `originalSkillIds` without clearing newly selected ids.
- Failures set `error = message ?: simpleName ?: "Failed to load agent skills"` and preserve existing lists.

- [x] **Step 4: Implement tab/search/pagination**

Rules:

- `SelectTab` changes tab and clears `searchQuery`.
- First selection of Public loads page 1 if `publicSkills` is empty.
- `SearchQueryChanged` updates query; when tab is Public, debounce 450 ms then load page 1 with trimmed query or null.
- `LoadNextPublicPage` does nothing when already loading or `publicHasMore` is false.
- Public page size is `20`.
- Public load calls `listPublicSkills(page, 20, search)`, replaces on page 1, appends otherwise, and sets page to `response.page ?: requestedPage`.

- [x] **Step 5: Implement selection and save**

Rules:

- `ToggleSkill` selects or deselects by id.
- When selecting, add full metadata to `selectedSkills` if not already present.
- When deselecting, remove metadata from `selectedSkills`.
- `Save` computes `selectedSkillIds - originalSkillIds`, sorts ids, and calls `addAgentSkill(botName, skillId, null)`.
- If no new ids exist, call `navigator.onSaved()` without API calls.
- On success for a skill id, add that id to `originalSkillIds`.
- On failure for a skill id, append `AgentSkillSaveFailure(skillId, message)` and keep it in `selectedSkillIds`.
- If all new ids succeed, call `navigator.onSaved()`.
- If any fail, stay on screen with `isSaving = false` and an error like `Failed to add 1 skill`.

- [x] **Step 6: Run focused tests**

Run:

```bash
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:skills:impl:testDebugUnitTest --tests '*AgentSkillsPresenterTest'
```

Expected: all `AgentSkillsPresenterTest` tests pass.

- [x] **Step 7: Commit presenter**

```bash
git add features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/agentskills features/skills/impl/src/test/kotlin/io/element/android/features/skills/impl/agentskills
git commit -m "feat: add agent skills presenter"
```

---

### Task 3: Agent Skills UI And Node

**Files:**
- Modify: `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/agentskills/AgentSkillsNode.kt`
- Modify: `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/agentskills/AgentSkillsView.kt`

- [x] **Step 1: Wire node to presenter**

Update `AgentSkillsNode` to create `AgentSkillsPresenter(botName = inputs.botName, navigator = object : AgentSkillsNavigator { override fun onSaved() = callback.onDone() })`.

Render:

```kotlin
AgentSkillsView(
    state = presenter.present(),
    onBackClick = callback::onDone,
    modifier = modifier,
)
```

- [x] **Step 2: Replace temporary view with full UI**

`AgentSkillsView` must:

- call `state.eventSink(AgentSkillsEvents.OnAppear)` in `LaunchedEffect(Unit)`;
- show Back, title `Agent Skills`, and `state.botName`;
- show Save button disabled while saving;
- show Mine/Public tab buttons;
- show search field with placeholder `Search skills`;
- show Refresh button and dismissible error button;
- show text warning when `state.deselectedOriginalCount > 0`: `Existing attached skills cannot be removed yet`;
- show save failures as text rows;
- show Mine tab list from `state.filteredUserSkills`;
- show Public tab list from `state.publicSkills`;
- show `Load more` button when `state.publicHasMore`;
- render rows with `SkillListRow`, selection indicator text `Selected`, and toggle through `AgentSkillsEvents.ToggleSkill(skill)`.

- [x] **Step 3: Verify compile and full skills tests**

Run:

```bash
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:skills:impl:compileDebugKotlin :features:skills:impl:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 4: Commit UI**

```bash
git add features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/agentskills
git commit -m "feat: add agent skills screen"
```

---

### Task 4: Final Verification

**Files:**
- No source changes expected.

- [x] **Step 1: Run feature tests**

```bash
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:skills:impl:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 2: Run feature compile**

```bash
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:skills:impl:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 3: Run chatbot implementation tests**

```bash
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :libraries:chatbot:impl:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 4: Scan dependencies**

```bash
rg -n "rustsdk|org\\.matrix\\.rust|voiceplayer|voicerecorder|vault|Vault|sandbox|Sandbox|UnsealUI|UnsealAgent|UnsealMiniApp|removeAgentSkill|deleteAgentSkill|detachAgentSkill" features/skills -S
```

Expected: no output for forbidden dependencies or fake detach APIs. Existing spec/plan matches are acceptable only under `docs/`, not `features/skills`.

- [x] **Step 5: Inspect status**

```bash
git status --short --branch
git log --oneline -6
```

Expected: clean working tree on `feature/agent-management`.

---

## Self-Review

Spec coverage:

- Agent-specific attached-skill management is covered by Tasks 1-3.
- The iOS management hub is covered by Task 1.
- Mine/Public tabs, local search, public search debounce, and pagination are covered by Task 2 and Task 3.
- Add-only save behavior is covered by Task 2 tests and implementation.
- Missing detach/remove support is explicitly excluded and scanned in Task 4.
- Rust SDK and missing component libraries are explicitly excluded and scanned in Task 4.

Placeholder scan:

- No task contains TBD, TODO, implement later, or unspecified error handling.

Type consistency:

- `AgentSkillsTab`, `AgentSkillsEvents`, `AgentSkillsState`, `AgentSkillsPresenter`, `AgentSkillsNode`, and `AgentSkillsNavigator` names match across tasks.
- `SkillsEntryPoint.InitialTarget.AgentSkills` and `SkillsFlowNode.NavTarget.AgentSkills` use the same `botName: String` input.

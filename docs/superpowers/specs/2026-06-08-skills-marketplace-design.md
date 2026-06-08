# Skills Marketplace Design

Date: 2026-06-08

## Feature Boundary

Feature name: `skills-marketplace`.

User-visible goal: Android users can open Skills, browse their own skills, browse the public skill marketplace, search both lists, page through marketplace results, inspect a skill detail, edit/delete owned skill metadata, and open a create-skill navigation seam.

Dependency class: native Android implementable.

Blocked by SDK artifact work: no.

Blocked by component library migration: no for list, marketplace, detail metadata editing, and delete flows.

Out of scope:

- `agent-skills-management`: attaching/detaching skills to an agent, conflict reconciliation, and per-agent skill persistence.
- Skill package creation/import/upload, zip parsing, S3/ST S upload flows, file editor, and file picker. The create action must be exposed as a navigation seam only.
- Full skill file viewing/downloading. Detail can display file counts or raw URL names only if already returned by the API model; opening file contents is out of scope for this first Android spec.
- Composer skill picker and timeline skill execution.
- Any Rust SDK, Matrix verification, room key recovery, voice, vault, sandbox runtime, `UnsealUI`, `UnsealAgent`, or `UnsealMiniApp` dependency.

## iOS Source References

Core iOS files:

- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/FlowCoordinators/SettingsFlowCoordinator.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/FlowCoordinators/StartChatFlowCoordinator.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Skills/SkillsHomeScreen/SkillsHomeScreenCoordinator.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Skills/SkillsHomeScreen/SkillsHomeScreenModels.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Skills/SkillsHomeScreen/SkillsHomeScreenViewModel.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Skills/SkillsHomeScreen/View/SkillsHomeScreen.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Skills/SkillsHomeScreen/View/SkillCardRow.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Skills/SkillMarketplaceScreen/SkillMarketplaceScreenCoordinator.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Skills/SkillMarketplaceScreen/SkillMarketplaceScreenModels.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Skills/SkillMarketplaceScreen/SkillMarketplaceScreenViewModel.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Skills/SkillMarketplaceScreen/View/SkillMarketplaceScreen.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Skills/SkillDetailScreen/SkillDetailScreenCoordinator.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Skills/SkillDetailScreen/SkillDetailScreenModels.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Skills/SkillDetailScreen/SkillDetailScreenViewModel.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Skills/SkillDetailScreen/View/SkillDetailScreen.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Services/ChatbotAPI/ChatbotAPIClient.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Services/ChatbotAPI/ChatbotAPIModels.swift`

Core Swift types:

- `SkillsTab`
- `SkillsHomeScreenViewState`, `SkillsHomeScreenViewAction`, `SkillsHomeScreenViewModelAction`, `SkillsHomeScreenViewModel`
- `SkillMarketplaceScreenViewState`, `SkillMarketplaceScreenViewAction`, `SkillMarketplaceScreenViewModelAction`, `SkillMarketplaceScreenViewModel`
- `SkillDetailScreenViewState`, `SkillDetailScreenViewAction`, `SkillDetailScreenViewModelAction`, `SkillDetailScreenViewModel`
- `ChatbotSkillVisibility`, `ChatbotUserSkill`, `ChatbotListUserSkillsResponse`, `ChatbotListPublicSkillsResponse`, `ChatbotGetUserSkillResponse`, `ChatbotUpdateUserSkillResponse`, `ChatbotDeleteUserSkillResponse`

Important iOS behaviors to preserve:

- Skills home loads the user's skills on first appear.
- Pull-to-refresh reloads the user's skills and also reloads marketplace first page when the selected tab is marketplace.
- User skills are sorted by `name` ascending.
- The user's skills tab searches locally by trimmed query against `name` and `description`, case-insensitively.
- The selected tab is either `mine` or `marketplace`; switching tabs clears the shared search query.
- Selecting marketplace for the first time loads marketplace page 1.
- Marketplace search is debounced by 450 ms and sends the trimmed query as `nil`/absent when empty.
- Marketplace page size is 20.
- Marketplace `hasMore` is true when `skills.count < total` if total is available; otherwise it is true when the current result count is at least `pageSize`.
- Loading next marketplace page is ignored while already loading a next page or when `hasMore` is false.
- Selecting a user skill opens detail with `isOwner = true`.
- Selecting a marketplace skill opens detail with `isOwner = true` only when the same skill ID exists in the user's skills; otherwise `isOwner = false`.
- Detail loads `getUserSkill(id)` on first appear and refresh.
- Detail owner mode can enter editing, seed edit fields from the loaded skill, save name/description/visibility via `updateUserSkill`, and leave editing on success.
- Detail non-owner mode does not expose edit or delete actions.
- Detail delete calls `deleteUserSkill(id)` and emits a deleted action so navigation pops back to the list.
- Detail errors are surfaced for load, update, and delete failures while preserving any previously loaded detail response.

## Android Existing State

Existing Android foundations:

- `libraries/chatbot/api/src/main/kotlin/io/element/android/libraries/chatbot/api/ChatbotApiService.kt`
- `libraries/chatbot/api/src/main/kotlin/io/element/android/libraries/chatbot/api/model/skills/SkillModels.kt`
- `libraries/chatbot/impl/src/main/kotlin/io/element/android/libraries/chatbot/impl/DefaultChatbotApiService.kt`
- `libraries/chatbot/test/src/main/kotlin/io/element/android/libraries/chatbot/test/FakeChatbotApiService.kt`
- `features/agentmanagement/api/src/main/kotlin/io/element/android/features/agentmanagement/api/AgentManagementEntryPoint.kt`
- `features/agentmanagement/impl/src/main/kotlin/io/element/android/features/agentmanagement/impl/AgentManagementFlowNode.kt`

Android already has the required HTTP service methods:

- `listUserSkills(visibility)`
- `listPublicSkills(page, pageSize, search)`
- `getUserSkill(id)`
- `updateUserSkill(id, body)`
- `deleteUserSkill(id)`

Missing Android feature modules:

- `features/skills/api`
- `features/skills/impl`
- `features/skills/test`

Reusable architecture patterns:

- `FeatureEntryPoint` API modules with Appyx node creation.
- `BaseFlowNode` for list/detail navigation.
- `Presenter<State>` with Compose `remember` state and event sinks.
- `libraries/chatbot/test` fake API service for focused presenter tests.

## Target Android Behavior

### Entry Points And Navigation

- Add a Skills feature entry point with params that can open:
  - skills home,
  - public marketplace-only list,
  - skill detail for an ID with `isOwner`,
  - create seam.
- Provide callbacks for:
  - done/back,
  - open skill detail,
  - create skill seam,
  - skill deleted.
- Existing Agent Management `onOpenSkills(botName: String?)` can keep delegating to the host app until an integration spec wires it to this entry point.

### Skills Home

State:

- user skills
- selected tab
- marketplace skills
- marketplace total/page/page size
- loading flags for user skills, marketplace first page, and marketplace next page
- search query
- error
- derived filtered user skills and marketplace has-more

Events:

- `OnAppear`
- `Refresh`
- `CreateSkill`
- `SelectSkill`
- `SelectTab`
- `SearchQueryChanged`
- `LoadNextMarketplacePage`

Presenter:

- Load user skills once on first appear.
- Refresh reloads user skills and, if marketplace tab is selected, marketplace first page.
- Sort user skills by `name`.
- Local search for user skills uses trimmed case-insensitive name/description matching.
- Switching tabs clears search.
- Switching to marketplace loads page 1 if marketplace list is empty.
- Marketplace search debounces 450 ms.
- Next page appends results and updates page from response `page ?: requestedPage`.
- On failure preserve existing data and expose user-visible error.

### Marketplace-Only List

State and behavior should match iOS `SkillMarketplaceScreen`:

- page/page size/total/skills
- search query
- loading first page and next page flags
- load on appear, refresh, debounced search, load more, select skill.

This screen may share presenter logic with the marketplace part of Skills Home if the Android design keeps boundaries clear.

### Skill Detail

State:

- id
- isOwner
- loaded `ChatbotGetUserSkillResponse`
- loading/saving flags
- edit mode
- edit name/description/visibility fields
- error
- derived skill, title, visibility label, canEdit

Events:

- `OnAppear`
- `Refresh`
- `StartEditing`
- `CancelEditing`
- `EditNameChanged`
- `EditDescriptionChanged`
- `EditVisibilityChanged`
- `SaveEditing`
- `Delete`

Presenter:

- Load detail once on first appear.
- Refresh reloads detail.
- Start editing copies loaded skill values into edit fields; default missing visibility to `Private`.
- Save sends JSON body with `name`, `description`, and `visibility`.
- Successful update replaces the loaded skill with the updated response skill and exits edit mode.
- Delete calls `deleteUserSkill`; success emits a deleted navigation action.
- Non-owner mode ignores edit/delete events.
- Errors preserve existing detail data.

## Android UI Requirements

The first Android UI should be native Compose and consistent with existing Element X screen structure.

- Skills Home:
  - title "Skills";
  - create action button;
  - two-tab segmented control for Mine and Marketplace;
  - search field with tab-specific placeholder;
  - loading rows;
  - empty states;
  - skill rows with letter avatar, name, optional description, optional formatted created date, and visibility badge on the Mine tab;
  - marketplace total header when total is available;
  - load-more row/button for marketplace pagination.
- Marketplace-only:
  - title "Skill 市场" or "Skill Marketplace";
  - search field, loading/empty/list/load-more states.
- Detail:
  - title uses loaded skill name, fallback "Skill";
  - read mode shows name, description when non-empty, visibility, created-at;
  - owner read mode exposes Edit and Delete actions;
  - edit mode exposes editable name, description, visibility picker, Save and Cancel;
  - non-owner detail hides Edit/Delete.

## Data And API Mapping

| iOS | Android |
| --- | --- |
| `ChatbotSkillVisibility.private` | `ChatbotSkillVisibility.Private` serialized as `private` |
| `ChatbotSkillVisibility.public` | `ChatbotSkillVisibility.Public` serialized as `public` |
| `ChatbotSkillVisibility.shared` | `ChatbotSkillVisibility.Shared` serialized as `shared` |
| `ChatbotUserSkill.id` | `ChatbotUserSkill.id` |
| `ChatbotUserSkill.original_skill_id` | `ChatbotUserSkill.originalSkillId` |
| `ChatbotListPublicSkillsResponse.page_size` | `ChatbotListPublicSkillsResponse.pageSize` |
| `ChatbotGetUserSkillResponse.skill` | `ChatbotGetUserSkillResponse.skill` |

Network endpoints already exist in `ChatbotApiService` and should be consumed through `ChatbotApiServiceFactory.createForUnsealApi(matrixClient)`.

The update body must be a `ChatbotJsonObject` with:

- `"name"`: current edit name string
- `"description"`: current edit description string
- `"visibility"`: serialized visibility value (`private`, `public`, `shared`)

## Test Requirements

Unit tests must cover:

- home loads user skills once and sorts by name;
- home refresh reloads user skills and reloads marketplace only when marketplace tab is selected;
- home local search matches name/description after trimming and is case-insensitive;
- switching to marketplace clears search and loads first marketplace page once;
- marketplace search debounces 450 ms and sends trimmed query or null when blank;
- marketplace next page appends results and respects has-more/loading guards;
- selecting marketplace skill computes `isOwner` from existing user skills;
- detail loads once, refreshes, and preserves prior data on load error;
- owner start-edit seeds fields from loaded skill;
- owner save sends name/description/visibility JSON, updates loaded skill, and exits edit mode;
- non-owner edit/delete events are ignored;
- delete emits deleted action on success and preserves state with error on failure.

Compile verification:

- `./gradlew --no-daemon --no-configuration-cache :features:skills:impl:testDebugUnitTest`
- `./gradlew --no-daemon --no-configuration-cache :features:skills:impl:compileDebugKotlin :features:skills:impl:compileDebugUnitTestKotlin`
- `./gradlew --no-daemon --no-configuration-cache :libraries:chatbot:impl:testDebugUnitTest`

Dependency verification:

- `features/skills` must not reference `rustsdk`, `org.matrix.rust`, `voiceplayer`, `voicerecorder`, `vault`, `UnsealUI`, `UnsealAgent`, or `UnsealMiniApp`.

## Acceptance Criteria

- A standalone Android Skills feature exists and compiles.
- It can list/search/refresh user skills.
- It can list/search/page public marketplace skills.
- It can open skill detail in owner or non-owner mode.
- Owner detail can edit metadata and delete a skill using `ChatbotApiService`.
- Create skill is exposed as a navigation seam without implementing creation/upload.
- It preserves iOS behavior for sorting, filtering, tab switching, debounced marketplace search, pagination, ownership detection, detail edit/save/delete, and error preservation.
- It has focused unit tests and fake test fixtures.
- It introduces no Rust SDK, voice, vault, sandbox runtime, or Unseal component-library dependency.

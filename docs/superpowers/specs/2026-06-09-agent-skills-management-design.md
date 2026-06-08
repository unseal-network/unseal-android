# Agent Skills Management Design

Date: 2026-06-09

## Feature Boundary

Feature name: `agent-skills-management`.

User-visible goal: Android should let a user manage the Skills attached to a specific Agent using the same iOS add-only selection behavior, and should expose the same high-level Agent/Skills management hub used by iOS settings/start-chat entry points.

Dependency class: native Android implementable.

Blocked by SDK artifact work: no.

Blocked by component library migration: no.

Out of scope:

- Removing or replacing already-attached Agent skills. The iOS API and UI currently only add newly selected skills; deselecting an existing skill in edit mode does not call a remove endpoint.
- Creating, editing, deleting, uploading, or importing Skills. Those belong to `skills-marketplace`.
- Agent create/edit form migration. That belongs to `agent-management`; this spec may reuse its existing callbacks.
- Room composer skill picker, selected skill metadata in timeline events, and room-agent runtime skill catalogs. Those are later room/composer specs.
- Vault, sandbox, voice, MiniApp, rich renderer, and Matrix Rust SDK behavior.

## iOS Source References

Primary iOS files:

- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/AgentSkillsManagementScreen/AgentSkillsManagementScreenModels.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/AgentSkillsManagementScreen/AgentSkillsManagementScreenViewModel.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/AgentSkillsManagementScreen/View/AgentSkillsManagementScreen.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Agents/AgentEditScreen/View/AgentSkillPickerSheet.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Agents/AgentEditScreen/AgentEditScreenViewModel.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Agents/AgentEditScreen/AgentEditScreenModels.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Agents/AgentDetailScreen/AgentDetailScreenViewModel.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Agents/AgentDetailScreen/View/AgentDetailScreen.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Services/ChatbotAPI/ChatbotAPIClient.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Services/ChatbotAPI/ChatbotAPIModels.swift`

Core Swift types and behavior:

- `AgentSkillsManagementScreenViewAction.openAgents` and `.openSkills` navigate from a simple settings hub to Agent Management and Skills Management.
- `AgentSkillPickerSheet` owns the selection UI for attaching skills to an Agent.
- `SkillTab.mine` and `.public` show two segmented tabs: current user's skills and public marketplace skills.
- `selectedSkillIDs: Set<String>` is the source of truth for checked rows.
- `selectedSkills: [ChatbotUserSkill]` preserves selected metadata for display after picker dismissal.
- `loadMySkills()` calls `listUserSkills(visibility: nil)` once, sorts by `name`, and filters locally by name or description.
- Public skills load lazily when the public tab is selected, call `listPublicSkills(page:pageSize:search:)`, reset on search changes, append on load-more, and stop when `publicSkills.count >= publicTotal`.
- Tapping a skill toggles it in `selectedSkillIDs` and `selectedSkills`.
- iOS `AgentEditScreenViewModel.submit()` adds skills after agent creation by calling `addAgentSkill` for every selected id.
- iOS edit mode calculates `selectedSkillIDs.subtracting(originalSkillIDs)` and calls `addAgentSkill` only for newly selected skills. Removed/deselected existing skills are not sent to the backend.
- Add failures during create are collected into `AgentSkillAddFailure` for the success summary; edit-mode add failures are intentionally best-effort through `try?`.

API behavior to preserve:

- `listAgentSkills(botName:)` GET `/chatbot/v1/agents/{botName}/skills`.
- iOS accepts both wrapped `{ "skills": [...] }` and direct array responses, then maps `ChatbotAgentSkillItem` into `ChatbotUserSkill`.
- `addAgentSkill(botName:skillId:name:)` POST `/chatbot/v1/agents/{botName}/skills/add` with `skill_id` and optional non-empty `name`.
- There is no iOS remove/detach call for an Agent skill.

## Android Existing State

Existing Android files and modules:

- `features/agentmanagement/api/src/main/kotlin/io/element/android/features/agentmanagement/api/AgentManagementEntryPoint.kt`
- `features/agentmanagement/impl/src/main/kotlin/io/element/android/features/agentmanagement/impl/AgentManagementFlowNode.kt`
- `features/agentmanagement/impl/src/main/kotlin/io/element/android/features/agentmanagement/impl/detail/AgentDetailPresenter.kt`
- `features/agentmanagement/impl/src/main/kotlin/io/element/android/features/agentmanagement/impl/detail/AgentDetailView.kt`
- `features/agentmanagement/impl/src/main/kotlin/io/element/android/features/agentmanagement/impl/edit/AgentEditPresenter.kt`
- `features/skills/api/src/main/kotlin/io/element/android/features/skills/api/SkillsEntryPoint.kt`
- `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/home/SkillsHomePresenter.kt`
- `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/marketplace/SkillMarketplacePresenter.kt`
- `features/skills/impl/src/main/kotlin/io/element/android/features/skills/impl/shared/SkillFormatters.kt`
- `libraries/chatbot/api/src/main/kotlin/io/element/android/libraries/chatbot/api/ChatbotApiService.kt`
- `libraries/chatbot/api/src/main/kotlin/io/element/android/libraries/chatbot/api/model/skills/SkillModels.kt`
- `libraries/chatbot/impl/src/main/kotlin/io/element/android/libraries/chatbot/impl/DefaultChatbotApiService.kt`
- `libraries/chatbot/test/src/main/kotlin/io/element/android/libraries/chatbot/test/FakeChatbotApiService.kt`

Existing aligned pieces:

- `ChatbotApiService` already exposes `listAgentSkills(botName)`, `addAgentSkill(botName, skillId, name)`, `listUserSkills`, and `listPublicSkills`.
- `DefaultChatbotApiService` already mirrors the iOS response fallback for `listAgentSkills`.
- `FakeChatbotApiService` already provides configurable fake lambdas for `listAgentSkills` and `addAgentSkill`.
- `AgentDetailPresenter` already loads Agent skills and exposes `ManageSkills`.
- `AgentManagementEntryPoint.Callback.onOpenSkills(botName: String?)` already exists as the host seam from Agent list/detail.
- `SkillsHomePresenter` and `SkillMarketplacePresenter` already implement most of the iOS skill list/search/pagination behavior that a picker can reuse conceptually.

Android gaps:

- No native screen exists for selecting Skills to attach to a specific Agent.
- `SkillsEntryPoint.InitialTarget` has no target for Agent skill attachment.
- `SkillsFlowNode` cannot currently open an Agent-specific picker/manager.
- Agent Management delegates `onOpenSkills(botName)` to the host, so direct in-flow Agent detail to Agent Skills management still depends on host wiring.
- There is no Android equivalent of the iOS simple Agent/Skills management hub.

## Target Android Behavior

### Agent-Specific Skill Management Flow

Entry:

- `SkillsEntryPoint.InitialTarget.AgentSkills(botName: String)` opens a native Agent Skills management screen for that Agent.
- `AgentManagementEntryPoint.Callback.onOpenSkills(botName)` may be wired by a host integration to open this entry point; inside this spec, the `SkillsEntryPoint` target must exist and be directly testable.

Load behavior:

- On first appear, load currently attached Agent skills with `listAgentSkills(botName)`.
- Load current user's skills with `listUserSkills(visibility = null)` once and sort by name.
- Preserve `originalSkillIds` from the first successful Agent skill load.
- The selected set initially contains all attached Agent skill ids.
- If one request fails, preserve any data that was already loaded and surface a retryable error message.

Tabs and filtering:

- Provide two tabs: `Mine` and `Public`.
- Mine tab filters loaded user skills locally by trimmed query against name or description, case-insensitive.
- Public tab loads first page only when selected for the first time.
- Public search is debounced by 450 ms and sends a trimmed query or `null` when blank.
- Public pagination appends pages and stops when total is reached or `hasMore == false`.
- Switching tabs clears the search query, matching the existing Skills Home behavior and keeping presenter state simple.

Selection:

- Tapping an unchecked row selects it and stores the full `ChatbotUserSkill` metadata.
- Tapping a newly selected row deselects it and removes its metadata.
- Tapping a row that was originally attached may visually deselect it locally before save, but save must not call a remove endpoint. The state must expose a warning/count for unchanged original ids so the UI can avoid implying backend detach support.
- The save set is `selectedSkillIds - originalSkillIds`, matching iOS edit mode.

Save:

- Save calls `addAgentSkill(botName, skillId, name = null)` for each newly selected skill id in sorted order.
- If no new skill ids exist, save succeeds immediately and navigates/dismisses.
- Partial failures are preserved in state as per-skill failures; successful adds are merged into `originalSkillIds` and attached skill list.
- Retry save should only retry still-failed/new ids.
- After full success, navigate back through the node callback.

### Management Hub Flow

Entry:

- `SkillsEntryPoint.InitialTarget.ManagementHub` opens a simple two-row hub equivalent to iOS `AgentSkillsManagementScreen`.

Behavior:

- Row `Agent Management` calls a host callback `onOpenAgentManagement()`.
- Row `Skills Management` opens the existing Skills Home screen inside the Skills flow.
- No network calls are made by the hub.

### Screen States

- Loading: first Agent skills/user skills load and in-flight save.
- Empty: no skills in the current tab.
- Error: load or save failure, dismissible and retryable.
- Success: save completed and screen closes.
- Offline/network failure: surfaced through the same error state from `Result.failure`.
- Permission denied: no special state in this spec.

## Data And API Mapping

iOS to Android mapping:

| iOS | Android |
| --- | --- |
| `AgentSkillPickerSheet.selectedSkillIDs` | `AgentSkillsState.selectedSkillIds` |
| `AgentSkillPickerSheet.selectedSkills` | `AgentSkillsState.selectedSkills` |
| `AgentSkillPickerSheet.mySkills` | `AgentSkillsState.userSkills` |
| `AgentSkillPickerSheet.publicSkills` | `AgentSkillsState.publicSkills` |
| `AgentSkillPickerSheet.publicPage` | `AgentSkillsState.publicPage` |
| `AgentEditScreenViewState.originalSkillIDs` | `AgentSkillsState.originalSkillIds` |
| `selectedSkillIDs.subtracting(originalSkillIDs)` | `selectedSkillIds - originalSkillIds` |
| `ChatbotAPIClient.listAgentSkills` | `ChatbotApiService.listAgentSkills` |
| `ChatbotAPIClient.addAgentSkill` | `ChatbotApiService.addAgentSkill` |

Persistence:

- No local persistence.
- State exists only in the presenter and is refreshed from Chatbot API.

Pagination:

- Public skills use page size 20, 1-based pages, append on load-more, and support `total`/`hasMore`.

Error model:

- Load failures set a readable error and keep prior loaded state.
- Save failures are represented per skill id so the user can retry.
- The feature does not invent a detach error because no detach request is sent.

## Acceptance Criteria

1. A subagent-ready implementation plan exists at `docs/superpowers/plans/2026-06-09-agent-skills-management.md`.
2. `SkillsEntryPoint.InitialTarget.AgentSkills(botName)` exists and opens an Agent-specific skills screen.
3. `SkillsEntryPoint.InitialTarget.ManagementHub` exists and opens a two-row Agent/Skills management hub.
4. Agent-specific presenter loads attached Agent skills and user skills on first appear.
5. Agent-specific presenter preserves original Agent skill ids and computes save additions as `selectedSkillIds - originalSkillIds`.
6. Mine tab local search trims and matches skill name/description case-insensitively.
7. Public tab lazy-loads first page, debounces search by 450 ms, paginates, and appends results.
8. Save calls `addAgentSkill(botName, skillId, null)` only for newly selected skill ids.
9. Save handles partial failures without losing successful additions.
10. No Rust SDK, voice, vault, sandbox, MiniApp, or Unseal component-library dependency is introduced.
11. `:features:skills:impl:testDebugUnitTest` passes.
12. `:features:skills:impl:compileDebugKotlin` passes.
13. `:libraries:chatbot:impl:testDebugUnitTest` passes.

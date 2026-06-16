# Agent Management Design

Date: 2026-06-08

## Feature Boundary

Feature name: `agent-management`.

User-visible goal: Android users can open Agent Management, view their agents, search the list, inspect an agent profile, create a basic agent, edit a basic agent, copy the agent Matrix ID, and start or open a direct chat with the agent.

Dependency class: native Android implementable.

Blocked by SDK artifact work: no.

Blocked by component library migration: no for the basic list/detail/create/edit flows in this spec.

Out of scope:

- `agent-skills-management`: skill marketplace browsing, skill picker, skill attach/detach editing, and post-save skill reconciliation.
- `vault-management`: agent vault entries, manual vault entry editing, cloning owner vault keys, and vault key picker.
- `local-agent-runtime`: sandbox creation, sandbox clone/reclone, local runtime state, and environment setup beyond preserving already-supported API fields where needed.
- `voice-library`: voice catalog loading, personal voice profiles, voice selection, set/delete voice config, and voice warnings.
- `ai-message-rich-renderer`: rich AI timeline rendering after chat opens.
- `miniapp-runtime`: any embedded miniapp launch/runtime.
- Rust SDK key recovery, device verification, or encrypted room key recovery.
- Avatar photo picking and Matrix media upload in the first Android implementation. The UI may display an existing `avatar_url`; create/edit can leave avatar unchanged or accept only a pre-existing URL through state/test seams.

## iOS Source References

Core iOS files:

- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/FlowCoordinators/SettingsFlowCoordinator.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/FlowCoordinators/StartChatFlowCoordinator.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/FlowCoordinators/RoomFlowCoordinator.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Agents/AgentListScreen/AgentListScreenCoordinator.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Agents/AgentListScreen/AgentListScreenModels.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Agents/AgentListScreen/AgentListScreenViewModel.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Agents/AgentListScreen/View/AgentListScreen.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Agents/AgentListScreen/View/AgentCardRow.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Agents/AgentDetailScreen/AgentDetailScreenCoordinator.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Agents/AgentDetailScreen/AgentDetailScreenModels.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Agents/AgentDetailScreen/AgentDetailScreenViewModel.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Agents/AgentDetailScreen/View/AgentDetailScreen.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Agents/AgentEditScreen/AgentEditScreenCoordinator.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Agents/AgentEditScreen/AgentEditScreenModels.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Agents/AgentEditScreen/AgentEditScreenViewModel.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Agents/AgentEditScreen/View/AgentEditScreen.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Services/ChatbotAPI/ChatbotAPIClient.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Services/ChatbotAPI/ChatbotAPIModels.swift`

Core Swift types:

- `AgentListScreenViewState`, `AgentListScreenViewAction`, `AgentListScreenViewModelAction`, `AgentListScreenViewModel`
- `AgentDetailScreenViewState`, `AgentDetailScreenViewAction`, `AgentDetailScreenViewModelAction`, `AgentDetailScreenViewModel`
- `AgentEditScreenMode`, `AgentEditScreenViewState`, `AgentEditScreenViewAction`, `AgentEditPhase`, `AgentNameAvailability`, `AgentEditSubmittingStep`, `AgentCreateSuccessSummary`, `AgentEditScreenViewModel`
- `ChatbotAgent`, `ChatbotAgentProvider`, `ChatbotProviderModel`, `ChatbotAgentSettings`, `ChatbotCreateAgentRequest`, `ChatbotUpdateAgentRequest`, `ChatbotAgentRoom`

Important iOS behaviors to preserve:

- Agent list loads once on first appear and supports explicit refresh.
- Agent list search trims whitespace and filters by `botName`, `displayName`, or `description`, case-insensitively.
- Agent list sorts by `displayName ?? botName`.
- Agent list has actions for create, open agent detail, and open skills. Android must expose the open-skills seam, but the actual skills feature remains out of scope.
- Detail screen accepts a partial `ChatbotAgent` from the list, then loads fresh agent details, rooms, and skills. Android must load fresh agent details and rooms; skills can be represented as a navigation seam/empty first version because `agent-skills-management` owns skill editing.
- Detail computes Matrix ID from `localpart` and `serverName`, falling back for DM target to a valid `providerAgentId` MXID.
- Detail copy action copies `agentMatrixUserID ?? matrixID ?? botName`.
- Detail start-chat checks for an existing direct room, opens it when present, otherwise creates a direct room with the agent MXID and display name.
- Detail room rows open existing rooms. Leaving an agent room calls `agentLeaveRoom` and reloads room data; a first Android version may expose this as an event/API seam if destructive confirmation is not yet wired.
- Create mode initializes `isPublic = false` and `autoJoin = true`.
- Edit mode pre-fills display name, description, public flag, auto-join from `settings.autoJoin ?? config.autoJoin ?? true`, provider, model, API key, base URL, soul, and avatar URL.
- Providers load on appear; provider `unseal` is moved to the first position; if no provider is selected, select the first provider.
- When selected provider exposes models, auto-select the first model if the current model is empty or no longer present.
- API key is required in UI only when selected provider ID is not `unseal`.
- Base URL field is shown only when provider info says it supports base URL.
- Create mode checks agent name availability with a 450 ms debounce; HTTP 404 means available, a successful `getAgent` means taken, other errors become unknown.
- Submit trims text fields; empty optional text fields are sent as `null`.
- Create preflights for name conflict; backend 500 bodies containing `already` or `exists` are treated as name conflicts.
- Create request sends `bot_name`, display name, description, avatar URL, `is_public`, provider, model, API key, base URL, soul, and `settings.auto_join`.
- Edit request sends display name, description, avatar URL, `is_public`, provider, model, API key, base URL, soul, and `settings.auto_join`.
- Create attempts to create/open a DM with the created agent; best-effort `agentJoinRoom` is called after creating the DM. Android must provide the seam and test the state transition, while room navigation remains delegated to the app navigator.
- Successful create has a success phase with created agent summary and optional DM room ID. Successful edit emits an updated action and returns to detail.

## Android Existing State

Existing Android modules and patterns:

- `/Users/Ruihan/.config/superpowers/worktrees/unseal-android/chatbot-api-service/libraries/chatbot/api`
- `/Users/Ruihan/.config/superpowers/worktrees/unseal-android/chatbot-api-service/libraries/chatbot/impl`
- `/Users/Ruihan/.config/superpowers/worktrees/unseal-android/chatbot-api-service/libraries/chatbot/test`
- `/Users/Ruihan/.config/superpowers/worktrees/unseal-android/chatbot-api-service/libraries/architecture/src/main/kotlin/io/element/android/libraries/architecture/Presenter.kt`
- `/Users/Ruihan/.config/superpowers/worktrees/unseal-android/chatbot-api-service/features/preferences/impl/src/main/kotlin/io/element/android/features/preferences/impl/root/PreferencesRootNode.kt`
- `/Users/Ruihan/.config/superpowers/worktrees/unseal-android/chatbot-api-service/features/preferences/impl/src/main/kotlin/io/element/android/features/preferences/impl/root/PreferencesRootView.kt`
- `/Users/Ruihan/.config/superpowers/worktrees/unseal-android/chatbot-api-service/features/startchat/impl/src/main/kotlin/io/element/android/features/startchat/impl/root/StartChatNode.kt`
- `/Users/Ruihan/.config/superpowers/worktrees/unseal-android/chatbot-api-service/features/roomdetails/impl/src/main/kotlin/io/element/android/features/roomdetails/impl/RoomDetailsPresenter.kt`

Android implementation should follow existing feature structure:

- `features/agentmanagement/api`: entry point and public navigator types.
- `features/agentmanagement/impl`: Appyx nodes, presenters, state/events, Compose views, and DI.
- `features/agentmanagement/test`: fake entry point/presenter fixtures used by app integration tests.

The feature depends on `libraries/chatbot/api` for models and service calls. It must not depend on Matrix Rust SDK, voice player/recorder, vault components, or Unseal component libraries.

## Android Functional Requirements

### Entry Points And Navigation

- Add an Agent Management feature entry point with params that can open:
  - list root,
  - detail for a `botName`,
  - create form,
  - edit form for a `botName`.
- Provide navigator callbacks for:
  - close/back,
  - open detail,
  - create agent,
  - edit agent,
  - open skills management seam,
  - open existing room,
  - open or create direct room result.
- Settings and Start Chat integration can be added after the standalone feature compiles; the first spec must at least make the entry point callable by future integration specs.

### Agent List

- State:
  - `agents`
  - `isLoading`
  - `searchQuery`
  - `error`
  - derived `filteredAgents`
- Events:
  - `OnAppear`
  - `Refresh`
  - `SearchQueryChanged`
  - `CreateAgent`
  - `SelectAgent`
  - `OpenSkills`
- Presenter:
  - Load only once on first appear.
  - Refresh reloads.
  - Sort by `displayName ?: botName`.
  - On failure keep existing data and expose a user-visible error.

### Agent Detail

- State:
  - `botName`
  - `agent`
  - `rooms`
  - `isLoading`
  - `isSoulExpanded`
  - `error`
  - derived `navigationTitle`, `matrixId`, `providerModelText`, `agentMatrixUserId`
- Events:
  - `OnAppear`
  - `Refresh`
  - `Edit`
  - `CopyAgentId`
  - `ToggleSoulExpanded`
  - `ManageSkills`
  - `StartChat`
  - `OpenRoom`
  - `LeaveRoom`
- Presenter:
  - Load agent and rooms concurrently or sequentially with equivalent result semantics.
  - If initial partial agent exists and fresh load fails, preserve partial display data and show error only when no agent exists.
  - Copy ID behavior matches iOS fallback order.
  - Start-chat behavior uses a small Android seam around Matrix direct-room lookup/create so tests do not require real SDK state.
  - Leave-room calls `ChatbotApiService.agentLeaveRoom(botName, roomId)` then reloads rooms.

### Agent Create/Edit

- State:
  - mode `Create` or `Edit(botName)`
  - providers
  - name availability
  - phase `Editing`, `Submitting(step)`, `Success(summary)`
  - bindable form fields: bot name, display name, description, avatar URL, public flag, auto-join flag, provider ID, model, API key, base URL, soul
  - derived selected provider, available models, needs API key, supports base URL
- Events:
  - `OnAppear`
  - `RefreshProviders`
  - `BotNameChanged`
  - `ProviderChanged`
  - `Submit`
  - `GoToChat`
  - `CreateAnother`
- Presenter:
  - Create defaults: private agent and auto-join enabled.
  - Edit preloads agent and providers.
  - Provider ordering, default selection, and model auto-selection match iOS.
  - Name availability debounce is 450 ms.
  - Create validates non-empty `botName`, preflights name conflict, sends create request, optionally starts DM, then enters success phase.
  - Edit sends update request and emits updated action.
  - Do not implement voice, vault, sandbox setup, or skills attach in this spec.

## Android UI Requirements

The first Android UI should be native Compose and consistent with existing Element X screens.

- Use a settings/list screen layout instead of a marketing page.
- Agent list:
  - top create action,
  - search field,
  - loading skeleton rows,
  - empty state,
  - agent rows/cards showing generated avatar fallback, display name, Matrix ID when available, provider/model, description, public/private badge.
- Detail:
  - profile header,
  - Start chat and Edit actions,
  - description when non-empty,
  - soul block with expand/collapse when long,
  - skills section as a seam action only,
  - rooms section with empty state and room rows.
- Create/edit:
  - identity, visibility/access, AI provider/model, optional base URL, optional API key, soul, and submit sections.
  - no vault, no sandbox, no voice, no photo picker in this spec.

## Test Requirements

Unit tests must cover:

- list load once, refresh, sorting, search matching, and failure state;
- detail derived Matrix ID and provider/model text;
- detail load preserving partial agent when fresh load fails;
- detail copy fallback order;
- detail start-chat existing room and create-room paths;
- detail leave-room reloads rooms;
- create defaults;
- provider ordering and model auto-selection;
- name availability success/taken/unknown behavior;
- create submit trim/null mapping and conflict handling;
- edit preload and update request mapping.

Compile verification:

- `./gradlew --no-daemon --no-configuration-cache :features:agentmanagement:impl:testDebugUnitTest`
- `./gradlew --no-daemon --no-configuration-cache :features:agentmanagement:impl:compileDebugKotlin :features:agentmanagement:impl:compileDebugUnitTestKotlin`
- Existing `libraries:chatbot` tests should remain green.

Dependency verification:

- `features/agentmanagement` must not reference `rustsdk`, `org.matrix.rust`, `voiceplayer`, `voicerecorder`, `vault`, `UnsealUI`, `UnsealAgent`, or `UnsealMiniApp`.

## Acceptance Criteria

- A standalone Android Agent Management feature exists and compiles.
- It can list, search, refresh, inspect, create, and edit agents using `ChatbotApiService`.
- It preserves iOS behavior for sorting, filtering, provider/model defaults, name availability, submit payload trimming, start-chat seams, and copy ID fallback.
- The feature exposes navigation seams for skills management and room opening without implementing those later specs.
- It has focused unit tests and fake test fixtures.
- It introduces no Rust SDK, voice, vault, sandbox runtime, or Unseal component-library dependency.

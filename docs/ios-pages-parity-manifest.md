# iOS Pages Parity Manifest

This manifest tracks non-room Unseal product pages that must be migrated from iOS to Android. The rule for every item is:

1. Match the iOS data source and request route first.
2. Match the iOS screen state and render model second.
3. Match Android Compose UI and gestures last.

iOS source root:
`/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens`

Android source root:
`/Users/Ruihan/.config/superpowers/worktrees/unseal-android/chatbot-api-service/features`

## Priority Legend

- P0: Required for the core agent room/product loop.
- P1: Required for feature parity, can ship after P0 if isolated.
- P2: Polish or secondary settings parity.

## Page Task ID Overlay

These page IDs are the execution handles for `docs/superpowers/plans/2026-06-15-ios-parity-independent-tasks.md`.
Each page migration must pass the gates below before visual parity work is accepted.

| Page ID | Plan task | Existing manifest area | Scope | Status |
|---|---|---|---|---|
| PAGE-SETTINGS | Task 12A | Settings Product Pages, Global Shell / Navigation | Settings root/product hub, AI assistant section, navigation entry parity, screen render model. | P1 |
| PAGE-AGENTS | Task 12B | Agent Management | Agent list/detail/edit data routes, rooms, skills, vault/env/voice/provider/model fields, create/start-chat flows. | P0 |
| PAGE-SKILLS | Task 12C | Skills | Skills home, marketplace, detail, file viewer/editor, create/import ZIP, install ownership states. | P0 |
| PAGE-SCHEDULES | Task 12D | Room Adjacent Features, Feature List By Area | Schedule list/edit, cron picker, enable/disable/delete, room context badge refresh. | P0 |
| PAGE-WEBHOOKS | Task 12E | Feature List By Area | Room/global webhook list/edit, source/account/event pickers, room-agent filtering, create/update/delete refresh. | P0/P1 |
| PAGE-VAULT | Task 12F | Settings Product Pages | Personal vault list/edit/delete/search, AI-stream route parity, empty/error/success states. | P0 |
| PAGE-CREDITS-STRIPE | Task 12G | Credits | Credits dashboard, top-up, Stripe official SDK payment flow, success/cancel/failure refresh. | P0 |
| PAGE-ONBOARDING | Task 12H | Onboarding / Welcome | First-entry guide, post-login welcome, recovery/session verification prompts, iOS shell transition parity. | P0 |

## Global Migration Workflow

- Build an Android state/render model for each page before changing UI.
- Keep API route parity with iOS and `ChatbotApiServiceFactory`:
  - Homeserver chatbot API: agents, skills, room schedules, webhooks.
  - Unseal API: sandbox, vault clone, voice config.
  - AI-stream/homeserver route: stream and personal vault.
- Do not fetch business data from Composables.
- Use full screen loading/empty/error states when iOS does.
- Preserve success/error messages and optimistic updates only where iOS has them.
- Add presenter/unit tests for every migrated state transition.

## Required Migration Gate

Every page migration must pass this gate before visual work starts. The intent is to prevent the
Android page from becoming a similar-looking but semantically different implementation.

### Gate 1: iOS Data Source Inventory

For the target iOS page, record the actual source of every value shown on screen:

| Data kind | What to record | Examples |
| --- | --- | --- |
| Matrix room/session data | Which `UserSession`, `RoomProxy`, `JoinedRoomProxy`, member provider, or client proxy call produces it. | Room members, agent members, current user, device id, room id, room name. |
| Homeserver chatbot API | Exact ViewModel call and endpoint family. | Agents, skills, schedules, room working memory. |
| Unseal API | Exact resolver/client path and endpoint family. | Connectors, webhook catalog, credits, voice catalog, sandbox/env/vault clone. |
| AI-stream / personal vault | Whether iOS uses `VaultService`, stream route, or Matrix event content. | Personal vault list/edit/delete, stream content. |
| Local/session cache | Cache key, lifetime, invalidation trigger. | Agent chat mode memory cache, downloaded voice preview mp3, completed stream snapshot. |
| External intent | Browser, OAuth callback, document picker, file importer, share sheet, payment sheet. | Connector OAuth, skill zip import, voice share/import, Stripe PaymentSheet. |

Deliverable: add a row in this manifest with iOS source files and the Android client route.

### Gate 2: Android Client / Facade Contract

Do not let each Composable or presenter rediscover routes independently. Each migrated feature must
declare a small client/facade contract when the page combines more than one data source.

| Client route | Android factory/API | Use for | Do not use for |
| --- | --- | --- | --- |
| Homeserver chatbot API | `ChatbotApiServiceFactory.createForHomeserver(matrixClient)` | Agents, skills, room schedules, room working memory, direct room agent metadata. | Global connectors, credits, voice catalog. |
| Unseal API | `ChatbotApiServiceFactory.createForUnsealApi(matrixClient)` | Credits, connectors, webhook event catalog/triggers, voice library, sandbox/env, agent vault clone, agent voice config. | Agent CRUD and skills CRUD. |
| AI-stream route | `ChatbotApiServiceFactory.createForAiStream(matrixClient)` and `libraries/agentstream` | Stream snapshots and personal vault endpoints already verified against iOS semantics. | Room-agent list, schedules, webhook management. |
| Matrix client | `MatrixClient`, `Room`, `JoinedRoom`, to-device APIs | Members, room details, composer send, D2D terminal/device-agent chat. | Business APIs already represented by chatbot/unseal clients. |

Deliverable: presenter tests or client route tests proving the intended factory method is used.

### Gate 3: Domain State / Render Model

Before Compose changes, add or update a state/render model that mirrors iOS `ViewState` and
`ViewAction` semantics.

Required fields for each model:

- `screenStatus`: loading, loaded, empty, full-screen error.
- `partialResources`: per-resource success/error when iOS keeps partial data.
- `bindings/draft`: editable form values, selected picker items, pending confirmation dialogs.
- `busyId` or operation status for row-level mutations.
- `successMessage` / `errorMessage` if iOS displays transient feedback.
- `navigationActions`: explicit actions that match iOS coordinator outputs.
- `externalActions`: open URL, share, file picker, payment sheet, permission prompt.

Compose views should only read this model and emit events. They must not:

- choose API route,
- parse raw JSON payloads that should have been converted in an adapter,
- create business caches,
- start network requests from row Composables,
- infer completed/running states that belong to SDK/domain state.

### Gate 4: UI / Gesture / Interaction Parity

Only after Gates 1-3 are complete:

- Match iOS section ordering, grouping, empty/error copy, destructive confirmations, and disabled states.
- Match iOS gestures for the same page: swipe actions, pull-to-refresh, long press/context menu,
  sheet detents, picker selection, file import/share, payment dismissal, OAuth return refresh.
- Use Android-native Compose/Material components, but preserve the same data contract and state flow.

### Gate 5: Verification

Each migrated feature needs at least:

- route/client test for the API family,
- presenter/reducer tests for loading, loaded, empty, partial error, mutation success, mutation failure,
- screenshot/manual fixture for light/dark and long content,
- manual device check for external intents when applicable.

## Current Cross-Page Gap Summary

This section records the current non-room parity gaps found by comparing the iOS `ElementX/Sources/Screens`
tree with Android `features/*`. Treat it as the development queue after room/timeline parity.

## Other Pages Parity Audit - 2026-06-15

This audit is the current authoritative queue for pages outside the room timeline. It is intentionally
written from the iOS data source and coordinator first, then Android route/state, then UI. A page is not
considered migrated just because the Android menu item exists.

### Global Shell / Navigation

| Area | iOS source | Android source | Current difference | Priority | Next action |
| --- | --- | --- | --- | --- | --- |
| Logged-in root shell | `UserSessionFlowCoordinator.swift` creates `SidebarOverlayCoordinator` wrapping tab/split coordinators; settings and call are sheet/overlay coordinators. | `features/home`, `HomeFlowNode`, app navigation, `PreferencesFlowNode` for settings. | Android keeps an Element-style home shell with top app bar, bottom floating toolbar, and FAB. iOS uses toolbar avatar/start-chat/space-filter actions plus optional sidebar overlay and sheets. Android does not yet have a shell render model that captures iOS sidebar/settings/search/start-chat/space-filter presentation semantics. | P1 | Add `HomeShellRenderModel` before UI work: toolbar actions, selected space filter, current user/avatar badge, bottom navigation policy, sidebar availability, sheet slot. Then decide whether Android keeps bottom toolbar or migrates to iOS toolbar-only behavior. |
| Sidebar | `SidebarScreen/*`, `UserSessionFlowCoordinator.setupSidebar()`. | No dedicated `features/sidebar`; Android spaces are under `features/home/impl/spaces` and bottom navigation. | iOS sidebar filter/space entry and settings trigger are not represented as a parity model. Android currently exposes Spaces as a bottom-nav destination, which is a product-level divergence from iOS overlay/sidebar semantics. | P1 | Add `SidebarParityModel` audit: selected space, settings entry, create space availability, gestures, and interaction with Home selected space filter. |
| Calls overlay | `UserSessionFlowCoordinator.presentCallScreen`, PiP callbacks minimize/fullscreen overlay. | `features/call`, `features/roomcall`. | Android call modules exist but overlay/PiP relationship to room/settings shell has not been compared. | P2 | Compare call overlay lifecycle only after room/menu parity is stable. |
| Post-login welcome | `PostLoginWelcomeFlowCoordinator.swift`, `PostLoginWelcome/*`; `PostLoginWelcomeScreenViewModel` initializes `selectedTheme` from `appSettings.appAppearance`, writes selected theme immediately, and writes `onboardingSubscribeChangelog/onboardingSubscribeMarketing` on complete. | Android now has a dedicated `PostLoginWelcomeView` routed from `RootFlowNode` before the signed-out/login flow when there are no existing sessions. `PostLoginWelcomeCompletion` carries `selectedTheme/subscribeChangelog/subscribeMarketing` back to root, and `AppPreferencesStore` now persists the two onboarding subscription flags plus theme. | Android now mirrors the iOS three-step state shape: welcome feature cards, theme selection, updates subscriptions/follow card, animated step indicator, primary continue action, immediate theme persistence, and complete-time subscription persistence. Remaining gap is clean/no-session visual verification and copy/spacing polish against iOS screenshots; FTUE ordering is still aligned separately and does not replace this welcome flow. | P1 | Verify on a clean test profile or emulator without clearing the user's logged-in phone session; then polish final copy/spacing against iOS screenshots and decide whether the stored subscription flags need to feed a backend subscription API. |
| Onboarding chain | `OnboardingFlowCoordinator.swift` gates identity confirmation, app lock mandatory, analytics prompt, notification permissions. | `features/ftue`, `features/verifysession`, `features/lockscreen`, `features/securebackup`. | Android `DefaultFtueService` now follows the iOS order after verification: lockscreen setup -> analytics opt-in -> notifications opt-in. Copy, modal dismissal behavior, identity-confirmed interstitial and app settings flags still need mapping. | P2 | Add visual/copy comparison and decide how to model iOS identity-confirmed interstitial on Android. |

### Home / Chat List Shell

| Area | iOS source | Android source | Data route parity | Current difference | Priority | Next action |
| --- | --- | --- | --- | --- | --- | --- |
| Home screen state | `HomeScreenModels.swift`, `HomeScreenViewModel.swift` | `HomeState.kt`, `HomePresenter.kt`, `RoomListState.kt`, `HomePresentationModel.kt` | iOS uses `UserSession.clientProxy`, `sessionSecurityStatePublisher`, `spaceService.spaceFiltersPublisher`, room summary provider, app settings, and notification manager. Android uses Matrix room list datasource, recovery/security state, filters presenters, and home spaces presenters. | Android now has a first-pass `HomePresentationModel` reducer for toolbar actions, selected space title, banner slot, room-list visibility, empty-filter state, and bottom-nav policy; `HomeView` consumes it for filter visibility and bottom-nav hiding. Remaining iOS-equivalent shell fields not fully represented: `requiresExtraAccountSetup`, `presentedSheet`, `roomListActivityVisibility`, full filtered-space toolbar management, and agent welcome sheet. | P1 | Extend `HomePresentationModel` with sheet/space-toolbar/activity-visibility fields, then migrate `HomeTopBar` actions to consume the model instead of recomputing from raw state. |
| Toolbar / top chrome | `HomeScreen.swift` toolbar: avatar settings button, start chat button, optional space filter button, filtered-space menu. | `HomeTopBar.kt`, `HomeView.kt` | Same underlying current user, filters, and start chat actions exist. | Android topbar currently includes search/filter controls and bottom FAB/nav; iOS toolbar uses avatar/start-chat/filter in nav toolbar and filtered-space menu. Android has no explicit filtered-space toolbar render model for create room/add existing/remove/members/share/settings/leave. | P1 | Add toolbar action model with iOS filtered-space actions and permission flags, then map Android UI to that model. |
| Room list rows | `HomeScreenRoomList.swift`, `HomeScreenRoomCell.swift`, invite/knock cells. | `RoomListContentView.kt`, `RoomSummaryRow.kt`, `HomeRoomRowRenderModel.kt`, invite/decline menus. | Both use Matrix room summaries and security/invite settings. | Android now has `HomeRoomRowRenderModel` from `RoomListRoomSummary`, mirroring the core iOS `HomeScreenRoom` semantics: row type, display name, timestamp, preview state, dot/mention/mute/call badges, numeric unread policy, highlight state, pinned/favourite/archive flags, selection slot, invite-seen slot, text emphasis, and shared swipe/context action presentation. `RoomListContentState.Rooms` now carries `selectedRoomId` and `activityVisibility`, and `RoomSummaryRow` consumes the model for normal, invite, and knocked rows, including selected row background and header/preview emphasis. Verified with `HomeRoomRowRenderModelTest` and `:features:home:impl:compileDebugKotlin`. Remaining gaps: Android still needs a real iOS-equivalent source for `roomListActivityVisibility` from settings/shell state, selected row id is only a model slot until split/sidebar navigation supplies it, and row visual polish still differs from iOS. | P1 | Wire real `roomListActivityVisibility` and selected-room source from the Home shell/sidebar contract, then tune row background/footer badge ordering against iOS screenshots. |
| Swipe and context actions | `SwipeActionModifier.swift`, `HomeScreenRoomList.roomContextMenu`. | `RoomListContextMenu.kt`, `RoomListItemActionsPresentation.kt`, `RoomSummaryRow.kt`, long-press context menu. | Both can mark read/unread and favourite today. Android now has data fields and action model slots for mute, pin, archive, settings, report, leave, and clear-cache extension; actual pin/archive/mute event handlers are still incomplete. | `RoomListItemActionsPresentation` now matches iOS ordering: swipe pin/read/favourite and context read/pin/mute/favourite/archive/settings/report/leave. `RoomSummaryRow` exposes iOS-style left-swipe trailing actions for room rows, keeps one open row at a time, and drives actions from `HomeRoomRowRenderModel.actions`. `RoomListContextMenu` now consumes the same render model via `ContextMenu.Shown.toHomeRoomRowRenderModel(canReportRoom)`, so swipe and long-press menu share one action source. Device verification on PHK110 after install: `/tmp/unseal-home-before-swipe.png`, `/tmp/unseal-home-row-swipe-actions.png`; latest true-device mock found and fixed a partial-reveal regression, with verified screenshots `/tmp/unseal-home-swipe-draggable-open.png`, `/tmp/unseal-home-swipe-draggable-closed.png`, room entry `/tmp/unseal-room-after-home-open.png`, post-model install swipe `/tmp/unseal-after-home-row-model-swipe.png`, installed Home `/tmp/unseal-context-home-loaded.png`, and mock long-press context menu `/tmp/unseal-context-menu-model-open.png`. UIAutomator previously confirmed `置顶 / 设为未读 / 收藏`; latest long-press screenshot confirms `设为未读 / 置顶 / 静音 / 收藏 / Archive` ordering. Remaining gaps: pin/archive/mute stay disabled until Android has the matching event routes, and row action colors/motion still need final iOS visual polish. | P1 | Implement pin/archive/mute event paths or keep them explicitly unavailable, then tune swipe/context visuals and motion against iOS screenshots. |
| Banners | `HomeScreenRecoveryKeyConfirmationBanner.swift`, `HomeScreenNewSoundBanner.swift` | `SetUpRecoveryKeyBanner.kt`, `ConfirmRecoveryKeyBanner.kt`, `NewNotificationSoundBanner.kt` | Both use session security/recovery state. | iOS has non-dismissable cannot-recover variant and appSettings dismissal flags; Android has `SecurityBannerState.SetUpRecovery/RecoveryKeyConfirmation` but no explicit cannot-recover state in `RoomListContentState`. | P1 | Extend banner render model to include iOS states: none, dismissed, setup recovery, recovery out-of-sync, cannot recover, new sound. |
| Agent welcome sheet | `AgentWelcomeSheetView.swift`, `HomeScreenViewModel.agentWelcomeAppeared`. | No equivalent Android home sheet found. | iOS driven by `appSettings.hasSeenAgentWelcome`. | Android does not show the iOS agent welcome sheet on first home appearance. | P1 | Add `HomePresentedSheet.AgentWelcome` equivalent and app preference flag before UI polish. |
| Space filters | `ChatsSpaceFiltersScreen/*`, `HomeScreen.spaceFilters`, `SpaceFiltersButton`. | `SpaceFiltersPresenter/View`, `HomeSpacesView`, bottom nav spaces. | Both use Matrix space service filters. | Android has space filters but also bottom-nav Spaces; iOS has a sheet/zoom transition and filtered-space toolbar management. | P1 | Model iOS `selectedSpaceFilter` and filtered-space toolbar; then decide how bottom-nav Spaces coexists or is hidden for parity. |

### Settings Product Hub

| Area | iOS source | Android source | Data route parity | Current difference | Priority | Next action |
| --- | --- | --- | --- | --- | --- | --- |
| Settings root AI section | `SettingsScreen.swift`, `SettingsScreenViewModel.swift`, `SettingsFlowCoordinator.swift`. | `PreferencesRootPresenter.kt`, `PreferencesRootView.kt`, `PreferencesFlowNode.kt`, `SettingsAiAssistantRenderModel.kt`. | Credit balance uses Unseal API in Android, matching iOS credits client family. AI hub entries now come from `SettingsAiAssistantRenderModel.iOSOrderedEntries`: Agent, Voice, Skills, Vault, Connectors, Triggers. | Entry order/action dispatch is now model-owned and covered by presenter/view/model tests; `openCreditsTopUp()` routes to `Credits(openTopUpInitially=true)`. Remaining gap is final iOS visual polish/icons/light-dark screenshots. | P0 | Continue visual parity for Settings root and add flow-level screenshot checks for each AI row transition. |
| Credits | iOS `SettingsFlowCoordinator.presentCredits(initialTab:)` and `presentTopup(balance:)` resolve credits client once and refresh balance after topup completion. | `features/credits`, `CreditsFlowNode`, `TopupNode`; `PreferencesFlowNode.NavTarget.Credits`; `PreferencesRootEvent.RefreshCreditBalance`. | Android uses `createForUnsealApi` in presenter/client layer; `CreditsEntryPoint.Params` supports `openTopUpInitially`; topup completion now emits a root refresh request so `PreferencesRootPresenter` reloads the balance like iOS `loadCreditBalance()`. | Native Stripe PaymentSheet bridge is wired through the topup flow and presenter tests were refreshed on 2026-06-17. Remaining gap is final visual parity/screenshots for Credits/Topup. | P1 | Finish visual parity for Credits/Topup and capture light/dark/device screenshot coverage. |
| Agent management from settings | iOS pushes AgentList, then AgentDetail/Edit; create can open the DM room through SettingsFlow action `.openRoom`. | Android `AgentManagementEntryPoint.Callback.onOpenRoom/onOpenCreatedDirectRoom` now bubbles through `PreferencesEntryPoint.Callback` into `LoggedInFlowNode.attachRoom`. | Homeserver chatbot API for agents; Unseal API for sandbox/env/voice/vault clone inside edit. | Route semantics now match iOS at the flow layer; still needs device/screenshot verification for settings dismissal and room transition animation. | P0 | Verify create/existing-agent open-room on device and keep Agent detail/edit field parity work separate. |
| Skills from settings | iOS SkillsHome can open detail/create; SkillCreate returns `.viewDetail(id)`; Agent detail opens skills scoped to the current bot. | Android `SkillsFlowNode` handles Home/Create/Detail internally, and `PreferencesFlowNode.NavTarget.Skills` now preserves `SkillsEntryPoint.InitialTarget` including `AgentSkills(botName)` and `ManagementHub`. | Homeserver chatbot API. | Agent -> Skills context now matches iOS at the flow target layer. Remaining: refresh/delete behavior and visual parity for Skills home/detail/create/marketplace. | P0 | Verify Settings root Skills, Agent list Skills hub, and Agent detail scoped skills on device; then finish create/delete refresh and file viewer/editor parity. |
| Vault management | iOS creates `VaultService(vaultModel: homeserver:)` from user session homeserver. | Android `VaultManagementNode`, `VaultEditNode`, AI-stream/personal vault route. | Needs continued verification: Android route currently documented as AI-stream/personal vault. | Core CRUD exists. Android now emits a list refresh after create/edit completion, matching the iOS save-then-return expectation. UI copy/empty/error and route semantics still need final audit against iOS `VaultService`. | P0 | Add route test documenting AI-stream vs iOS `VaultService` equivalence or fix route if mismatch; finish visual/empty/error parity. |
| Connectors | iOS pushes ConnectorList; Manage connector is pushed and list refreshes on pop. | Android `ConnectorsEntryPoint`, `OpenUrlInTabView`. | Unseal API via well-known. | OAuth browser return refresh and manage-account refresh parity not proven. | P1 | Add connector refresh-on-return test/manual checklist; verify real icon/category fields. |
| Webhook triggers | iOS global settings entry uses `WebhookTriggerListScreenCoordinator(mode: .global)`; edit save reloads list. Room agent pickers are based on Matrix room members enriched by homeserver `getRoomAgents`, matching `RoomAgentMemberEnricher`. | Android `WebhookTriggersEntryPoint.InitialTarget.Global`; outer callback `onTriggersChanged()` is no-op in settings, but `WebhookTriggersFlowNode` owns `reloadRequests` and refreshes the visible list locally after mutations. Room mode now uses shared `loadWebhookRoomAgents()` to filter homeserver agents through Matrix joined members and copy Matrix display/avatar metadata. | Unseal API for triggers/catalog, homeserver for room/agent metadata where needed, Matrix joined-room members for room agent visibility. | Global entry, local list refresh, and room-agent filtering semantics now match iOS. Remaining gaps are source/account/event picker parity, outer settings badge/root refresh if needed, and room scoped menu entry verification. | P0/P1 | Audit source/account/event pickers, room scoped entry, and whether settings root needs any trigger-count refresh after returning. |
| Voice Library | iOS `VoiceLibraryScreenCoordinator(userSession,userIndicatorController)`. | Android `VoiceLibraryEntryPoint`, `VoiceLibraryPresenter/View`, native recorder bridge. | Unseal API. | Data features mostly present; post-record preview now stores decoded waveform samples from the local m4a recording instead of a fixed demo waveform. Final iOS visual parity remains. | P1 | Screenshot compare create/preview/share/import states and verify real-device recorder playback/waveform gestures. |

### Agent / Skill / Automation Features

| Area | iOS source | Android source | Current difference | Priority | Next action |
| --- | --- | --- | --- | --- | --- |
| Agent list | `Agents/AgentListScreen/*`. | `features/agentmanagement/impl/list`. | Android now exposes `AgentListRenderModel` / `AgentListItemRenderModel` so the grid consumes stable iOS card fields (`title/avatar/providerModel/description/visibility/relativeTime`) instead of formatting raw `ChatbotAgent` in Compose. Remaining gaps are iOS search-controller styling, card matched-transition/animation, alert copy, pull-to-refresh visuals, and light/dark screenshot parity. | P0 | Polish Agent grid UI against iOS screenshots and add screenshot coverage for loading/empty/loaded/error/search. |
| Agent detail | `Agents/AgentDetailScreen/*`. | `features/agentmanagement/impl/detail`. | Android now exposes `AgentDetailRenderModel` for header/profile URL, Matrix ID, provider/model, visibility, description, soul, action labels, skill chips and joined-room rows. Presenter now loads `listAgentSkills(botName)` with agent/rooms like iOS, and skills failure remains non-blocking. Remaining gaps are iOS hero transition, Safari overlay behavior, gradient/header polish, horizontal skill chip scrolling fidelity, room row visual styling, leave-room affordance and alert copy. | P0 | Polish detail UI/gestures against iOS screenshots; add screenshot coverage for loaded/no-skills/no-rooms/long soul/load error. |
| Agent edit/create | `Agents/AgentEditScreen/*`. | `features/agentmanagement/impl/edit`. | Reported as mostly migrated, but mutation ordering across homeserver/Unseal/AI-stream needs locked tests. | P0 | Add end-to-end presenter test for create -> optional DM room route, update -> pop. |
| Skills home/marketplace | `Skills/SkillsHomeScreen`, `SkillMarketplaceScreen`. | `features/skills/impl/home`, `marketplace`. | Search/paging/install ownership states need parity proof. | P0 | Add marketplace paging/search/install state matrix. |
| Skill detail file viewer/editor | `SkillDetailScreen`, `SkillFileViewerScreen` iOS code viewer/editor. | `features/skills/impl/detail`. | Android now has `SkillFileRenderModel` for iOS-style `presignedUrls/preuploadUrls` handling: query/path extraction, skill-directory scoping, common-prefix trimming, editable preupload alignment, and icon kind. It also has an internal `SkillFileViewerNode` route that opens files from detail, loads presigned content with `Cache-Control: no-cache`, enables editing only when `preuploadUrl` exists, saves with a filename-based content type, shows retry/save-error/saved states, then reloads content after save. | P0 | Polish visual/editor parity with iOS `RunestoneTextView`: syntax highlighting, richer code editor gestures, ISO-Latin1 fallback fidelity, and screenshot coverage. |
| Skill create/import ZIP | `SkillCreateScreen/*`. | `features/skills/impl/create`. | Core import/conflict work exists; document-provider errors and screenshot parity remain. | P0 | Add device import matrix: text, zip, conflict keep-both, overwrite, STS failure. |
| Room schedules | `RoomSchedules/*`; room topbar/settings route. | `features/roomschedules`. | List/edit exists. Android schedule list state exposes `ScheduleRenderModel` so rows consume stable display fields (`title/agentLabel/cronLabel/actionPreview/status/toggle/owner`) instead of parsing raw `ChatbotSchedule` in UI. Schedule create/edit now exposes `ScheduleEditRenderModel` for title, agent options, selected agent label, cron mode labels, cron summary, submit state, and out-of-room warning. Shared room context refresh and iOS visual/gesture parity still need final pass. | P0 | Move old presenter consumers onto shared room context or document remaining duplicate fetches; then polish cron picker and row/sheet UI against iOS screenshots. |
| Room/global webhooks | `WebhookTriggers/*`. | `features/webhooks`. | Room mode and global mode both exist; room agent filtering now follows iOS Matrix-member enrichment. Source/account/event pickers and final refresh behavior still need parity proof. | P0/P1 | Test room-scoped create/edit/delete with an agent room and global source/account/event picker. |

### Element-Native Pages Exposed By Room / Settings

These pages are lower priority because they are not Unseal-specific, but room menu parity can expose them.
They still need a pass once the P0 product loop is stable.

| Area | iOS source | Android source | Current difference | Priority |
| --- | --- | --- | --- | --- |
| Room details / members / permissions | `RoomDetailsScreen`, `RoomMemberListScreen`, `RoomRolesAndPermissionsScreen`, `RoomChangeRolesScreen`, `SecurityAndPrivacyScreen`. | `features/roomdetails`, `features/roommemberdetails`, `features/securityandprivacy`, related modules. | Android modules exist, but iOS room details can navigate to agent config and schedules; Android route parity must be checked from room menu. | P1 |
| Composer attachment features | `RoomScreen/ComposerToolbar`, `MediaPickerScreen`, `SketchScreen`, `LocationSharing`, game picker. | `features/messages`, `features/location`, media modules. | Attachment menu order, icons, permissions, sketch/location/game routes not fully compared. | P1/P2 |
| Message actions | iOS timeline action sheets, forwarding, report, reactions, read receipts. | `features/messages`, `features/forward`, `features/reportroom`, reactions modules. | Android action render model exists but still missing some iOS actions: select text, translate, save/unsave, share/save media where handlers exist. | P0/P1 |
| Polls/media/search/forward | `CreatePollScreen`, `RoomPollsHistoryScreen`, `MediaEventsTimelineScreen`, `GlobalSearchScreen`, `MessageForwardingScreen`. | Existing Element modules. | Likely functional but not visually/data audited against iOS. | P2 |
| App lock / backup / verification | `AppLock`, `SecureBackup`, `Onboarding/SessionVerification`. | `features/lockscreen`, `features/securebackup`, `features/verifysession`. | Existing Element flows; Unseal/iOS ordering and copy not audited. | P2 |

### Immediate Fixes Identified

- Android Settings Top Up row must not be a no-op. It now routes through
  `PreferencesFlowNode.NavTarget.Credits(openTopUpInitially = true)` and
  `CreditsEntryPoint.Params(openTopUpInitially = true)`.
- Android Settings Agent `openRoom` and `onOpenCreatedDirectRoom` now bubble to app-level room navigation; device verification remains.
- Android Settings Skills now preserves Home/ManagementHub/AgentSkills targets; create/delete refresh and detail/file-editor visual parity remain.
- Android Webhook settings callback ignores outer trigger changes, but the Webhook flow itself now has node-local reload after save/delete/list changes; remaining work is picker and any settings-root refresh parity.
- Android Vault edit/create completion now emits a list reload when returning to Vault management.
- Android Credits topup now has the native Stripe PaymentSheet bridge. Settings balance refresh propagation is wired through `PreferencesFlowNode` -> `PreferencesRootEvent.RefreshCreditBalance` -> `PreferencesRootPresenter.loadCreditBalance()`.

### P0 Workflow Before UI Work

| Step | Requirement | Current Android risk |
| --- | --- | --- |
| 1. API client route | Each feature must declare whether it uses homeserver chatbot API, Unseal agent-api, AI-stream/personal vault, or Matrix client data. | Most presenters call `ChatbotApiServiceFactory` directly. Routes are mostly correct, but there is no feature-level client/facade manifest like the new room context path. |
| 2. Domain state | Convert iOS `ViewState` / `Context` into Android state before Compose work. | Some pages expose raw API models directly to View; this makes iOS parity harder to test. |
| 3. Partial failure | iOS often keeps partial screen data when one sub-request fails. | Android pages often collapse into one `error` string or full-screen error. Need per-resource state where iOS does it. |
| 4. Navigation/action model | iOS coordinators expose explicit actions. | Android nodes exist, but page actions are not listed as a parity contract. Missing actions can hide silently. |
| 5. Screenshot/manual fixtures | Each product page needs dark/light, loading, empty, error, loaded, mutation success/failure. | Current tests are presenter-heavy; visual/manual checklist is incomplete. |

### Feature List By Area

| Area | iOS pages | Android pages | Priority | Main gaps |
| --- | --- | --- | --- | --- |
| Agent management | Agent list, detail, edit, skill picker, vault picker | `features/agentmanagement`, `features/skills/agentskills` | P0 | Data route mostly present. Agent list now has a render model for iOS card fields and presenter tests covering sorted render output. Agent detail now has a render model and loads agent skills alongside rooms. Need verify direct-room creation/invite after create, agent availability/name validation, provider/model fields, sandbox/env/vault/voice config mutation ordering, avatar upload limits, room list/status fields and detail UI gestures. |
| Skills | Home, marketplace, detail, file viewer/editor, create/import ZIP | `features/skills` | P0 | Android has core flows, import conflict work, detail file render models, and a first-pass file viewer/editor route with GET presigned + PUT preupload save flow. Need visual/code-editor parity with iOS `RunestoneTextView`, STS upload error fidelity, marketplace paging/search/install ownership states, and screenshots. |
| Room schedules | Schedule list/edit, cron picker | `features/roomschedules` | P0 | API route uses homeserver. Schedule list and create/edit now have first-pass render models for row fields/status/owner, agent picker options, cron labels/summary, and submit state. Need align row visuals, enable/disable/delete confirmation UI, cron picker UX, agent picker sheet styling, active badge refresh from shared room context. |
| Vault | Vault list/edit | `features/preferences/vault` | P0 | Android now has list CRUD/search and save-return reload. Delete endpoint is aligned with iOS key semantics (`DELETE /chatbot/v1/vault/{key}`) and covered by route test. Edit value loading and create/update validation are covered by presenter tests. Remaining: visual polish, full empty/error copy, resource strings. |
| Webhook triggers | Trigger list/edit, source/account/event pickers | `features/webhooks` | P1/P0 for room entry | API route uses Unseal API plus homeserver agent lookup. Room agent filter now combines homeserver `getRoomAgents` with Matrix joined members like iOS. Need source/account/event picker parity, draft generation, enable status, delete/update confirmations, room menu entry. |
| Connectors | Connector list/manage OAuth | `features/connectors` | P1 | Need confirm OAuth Safari/webview return refresh, categories/search pagination, connected account count, manage reconnect/disconnect confirmation, action placement. |
| Voice library | Provider catalog, voice profiles, share/import, preview, recording upload | `features/voicelibrary` | P1 | Android has Mine/Public tabs, provider catalog, save/delete/share/import, delete notice copy, client-side filtering, presenter-owned remote preview ids/target, downloaded `.mp3` preview file cache, upload-clone API, iOS-style recording domain state, native m4a recorder bridge, post-record local playback/progress/seek/scrub state, and decoded recorded-file waveform samples wired through a design-system waveform UI. Remaining high-gap area: final visual parity and real-device recorder gesture verification. |
| Credits/topup | Credits balance/ledger/usage/analytics, topup | `features/credits` | P1 | Route split exists (`createForUnsealApi` + homeserver). Credits data/state covers balance, ledger pagination, daily usage, analytics period values and partial failure. Topup now has Android state/presenter/child page for preset/custom amount, PaymentIntent creation, native Stripe PaymentSheet bridge, success/cancel/failure and status polling. Remaining: final visual parity and screenshot coverage. |
| Settings root | Settings entries to Unseal pages | `features/preferences/root` | P1 | Need iOS order/grouping/icons and route availability. Some Unseal pages exist but entry placement/labels may differ. |
| Post-login welcome | Logo/theme/updates steps | `appnav/root/PostLoginWelcomeView.kt`, `RootFlowNode.NavTarget.PostLoginWelcome`, `AppPreferencesStore.setOnboardingSubscriptions(...)` | P1 | Root routing, iOS-style feature/theme/updates state, theme persistence, and onboarding subscription preference persistence are implemented. Need clean-session visual/device verification and backend subscription decision. |
| Onboarding/security | Login/server selection, notifications, verification, app lock | Existing Element modules | P2 | Need scan for Unseal branding/copy/order differences. Lower priority unless user-facing during current demo. |
| Media/polls/location/forward/search | Existing Element room-adjacent screens | Existing Element modules | P2 | Not Unseal-specific, but room parity can expose layout/menu differences. Audit after P0 room menu/timeline stabilizes. |

### Migration Work Queue

This is the execution order for non-room pages. It intentionally starts with data/client parity, not
visual parity.

#### P0: Core agent loop

| Workstream | Required features | Data/client first task | State/render first task | UI/gesture task |
| --- | --- | --- | --- | --- |
| Settings AI entry hub | Credits balance card, Agent, Voice, Skills, Vault, Connectors, Triggers, billing/usage/topup links. | Verify `SettingsScreenViewModel` credit balance uses Unseal API and Android `PreferencesRootPresenter` refreshes the same value on return from Topup/Credits. | `SettingsAiAssistantRenderModel` now owns iOS entry order and preserves `creditBalanceLoadState`; model/presenter/view tests cover order and row callbacks. | Match iOS grouping/icons and finish light/dark visual verification; device screenshot captured at `/tmp/unseal-settings-ai-hub-render-model.png`. |
| Agent management | List, detail, edit/create, direct chat, room list, skill picker, vault picker, voice/sandbox/env. | Split route expectations: homeserver for agent CRUD/skills, Unseal API for sandbox/env/voice/vault clone, AI-stream for personal vault picker. | Agent list and detail render models now own card/detail fields; next state work is Agent edit render checklist and ensuring `AgentEditFormState` owns all draft/picker/confirmation state with no ad hoc View parsing. | Match iOS list/detail animations/search/profile styling, form sections and confirmations; verify avatar, provider/model, sandbox, secret vars, access. |
| Skills | Home, marketplace, detail, file tree/viewer/editor, create/import ZIP. | Homeserver client only; verify STS upload route and marketplace/install endpoints. | `SkillFileRenderModel` normalizes detail file inputs before UI, and `SkillFileViewerPresenter` owns content load/save/retry/saved/error state. | Match iOS code viewer/editor visual behavior, import conflict, file picker/share gestures, and final syntax-highlight editor polish. |
| Room schedules | List/edit from room menu/topbar and settings-like page. | Homeserver client plus shared room context refresh. | Schedule list and edit render models exist; remaining state work is shared room-context loading, richer confirmation/success/error contracts, and final picker sheet behavior. | Match iOS cron picker, enable/delete actions, active badge refresh. |
| Room webhooks | Room detail/menu entry, list/edit, source/account/event pickers. | Unseal API for event catalog/triggers; homeserver API plus Matrix joined members for agent lookup, matching iOS room member enrichment. | Add source/account/event picker render models and draft trigger state. | Match iOS picker sheets, enable/delete/update confirmations, OAuth/manage connector handoff. |

#### P1: Product pages and monetization

| Workstream | Required features | Data/client first task | State/render first task | UI/gesture task |
| --- | --- | --- | --- | --- |
| Connectors | Category/search list, OAuth connect, manage accounts, reconnect/disconnect. | Unseal API via well-known; verify OAuth return refresh and account count. | Separate catalog, connected accounts, selected source, operation busy state. | Match iOS manage sheet/list, confirmation, external browser return. |
| Voice Library | Provider voices, saved profiles, preview cache, share/import, recording upload. | Unseal API; Android already has preview download cache and upload-clone route. | Recorder domain state now mirrors iOS create-voice bindings, upload validation, recorded playback state, progress, seek, and scrubbing. Native recorder bridge emits m4a/base64 samples through `RecordingReady`, retains a local file path for post-record preview until upload/dismiss/retake cleanup, and decodes the m4a into normalized waveform samples for `WaveformPlaybackView`. | Match iOS record/post-record phases, preview button states, share/import gestures; verify the decoded waveform on device. |
| Credits / Topup | Balance, ledger, usage, analytics, topup, PaymentSheet. | Unseal API for credits/topup, homeserver where iOS uses chatbot client. | Topup state, PaymentSheet external action, result handling, status polling, and settings balance refresh are implemented. | Match iOS amount selector visuals and success/cancel screenshot flow. |
| Vault | Personal vault list/edit/search/delete. | AI-stream/personal vault route; route tests already started. | Keep list/edit state separated, preserve success/error. | Match iOS empty/error/search/card styling and destructive confirmation. |

#### P2: Product shell, onboarding, media utilities

| Workstream | iOS source | Android status | Required decision/work |
| --- | --- | --- | --- |
| Post-login welcome | `PostLoginWelcome/*`, `HomeScreen` agent welcome state. | Android now has a dedicated root pre-login Unseal welcome page with theme selection, updates options, and persisted subscription flags. | Verify with clean-session screenshot and complete exact visual/copy parity; decide whether persisted subscription choices should be synced to an API. |
| Sidebar / Home shell | `SidebarScreen/*`, `HomeScreen/*`. | Android likely inherits Element home/navigation. | Audit iOS sidebar menu, agent welcome card, filters, gestures. |
| Splash/Add to home | `Splash/*`, `AddToHomeScreen/*`. | Android likely uses existing splash/launcher. | Audit branding parity and first-run flow. |
| Composer attachments | `ComposerToolbarModels.swift`: camera, photo library, file, location, sketch, game picker. | Android has Element composer plus game picker work. | Verify attachment menu order, icons, permissions, sketch/location flow. |
| Media/file preview | `FilePreviewScreen/*`, `ImageAnnotationScreen/*`, `SketchScreen/*`. | Android has Element media/file flows. | Audit save/share/redact/detail gestures and annotation/sketch parity. |
| Search/forward/polls/location | `GlobalSearchScreen`, `MessageForwardingScreen`, `CreatePollScreen`, `RoomPollsHistoryScreen`, `LocationSharing`. | Existing Element modules. | Lower priority unless exposed by room menu; compare action availability and long-press routes. |

### API Route Contract

| Feature | iOS route/source | Android route to keep | Notes |
| --- | --- | --- | --- |
| Agents / skills / schedules / room agents / working memory | `ChatbotAPIClientFactory.makeClient(userSession:appSettings:)` | `ChatbotApiServiceFactory.createForHomeserver(matrixClient)` | Follows logged-in homeserver. Do not hardcode `api.unseal.network`. |
| Connectors / webhooks / credits / voice / sandbox / agent vault clone / agent voice config | iOS `IntegrationAPIResolver` or injected Unseal API client | `ChatbotApiServiceFactory.createForUnsealApi(matrixClient)` | Uses `.well-known` `org.unseal.api.base_url` fallback. |
| Personal vault | iOS `VaultService` / AI-stream-style route | `ChatbotApiServiceFactory.createForAiStream(matrixClient)` currently used by Android vault | Must verify delete/update endpoint semantics before UI polish. |
| Stream render | iOS stream renderer, Android Rust SDK | `libraries/agentstream` only | UI must not fetch or parse SSE directly. |
| D2D terminal | iOS `ClientProxy.sendToDeviceEvent(io.unseal.d2d)` | Matrix API now exposes `sendUnsealD2DMessage`; messages module has `MatrixDeviceAgentTerminalTransport` for `cmd.open/input/close`. | Must receive D2D actions and connect presenter lifecycle before terminal is considered functional. |

### Per-Feature Parity Checklist Template

For each page, add a concrete row before implementation:

- iOS source files: coordinator, models, view model, view.
- Android source files: presenter, state, view, node/entry point.
- API route: homeserver, Unseal API, AI-stream, Matrix.
- Input data and mutations.
- Loading / empty / partial error / full error / success states.
- Navigation actions and external intents.
- Visual states: light/dark, small phone, large/landscape, long content.
- Tests: presenter reducer tests plus manual screenshot fixture.

## Pages

### Room Adjacent Features

| Feature | Priority | iOS Source | Android Source | Current Gap |
| --- | --- | --- | --- | --- |
| Device Agent Terminal | P0 | `RoomScreen/View/UnsealTerminalPanelView.swift` | `features/messages/impl/.../MessagesPresenter.kt`, `MessagesView.kt`, `terminal/DeviceAgentTerminalModels.kt`, `terminal/DeviceAgentTerminalTransport.kt` | Android now opens a room-scoped terminal panel state from `OpenDeviceAgentTerminal` and no longer falls back to unsupported snackbar. Reducer covers iOS-style request/session matching, output accumulation, close/fail states and ANSI stripping. Matrix D2D send path and transport for `cmd.open/input/close` are implemented and tested. Remaining gap: observe incoming D2D actions (`heartbeat`, `terminalReady`, `terminalOutput`, `terminalClosed`) and connect open/input/close events to presenter. |
| Game Picker | P1 | `RoomScreen/GamePicker/*` | `features/messages/impl/.../messagecomposer/gamepicker` | Android has composer integration and presenter coverage for app/my-playing loading, create-room + invite send ordering, send failure handling, and existing-room navigation. Remaining: picker UI/data parity polish, pagination, room menu entry, and screenshot verification. |
| Room Schedules | P0 | `RoomSchedules/*` | `features/roomschedules/impl/*` | Data exists; verify working memory, edit permission, cron picker, enable/disable, delete confirmation, badge refresh. |
| Webhook Triggers | P1 | `WebhookTriggers/*` | `features/webhooks/impl/*` | Room agent filters now match iOS joined-member enrichment. Verify source/account/event-type pickers, enable switch, delete/update payload parity. |

### Agent Management

| Feature | Priority | iOS Source | Android Source | Current Gap |
| --- | --- | --- | --- | --- |
| Agent List | P0 | `Agents/AgentListScreen/*` | `features/agentmanagement/impl/list/*` | Android has search/grid. Verify iOS card fields, status, empty/error states, open skills action. |
| Agent Detail | P0 | `Agents/AgentDetailScreen/*` | `features/agentmanagement/impl/detail/*` | Verify rooms list, start chat, copy ID, leave/join actions, soul/personality expansion. |
| Agent Edit | P0 | `Agents/AgentEditScreen/*` | `features/agentmanagement/impl/edit/*` | Must keep iOS data semantics for providers, models, voice, sandbox, env vars, vault key picker, skill picker, access. |
| Agent Skills Management Hub | P1 | `AgentSkillsManagementScreen/*` | `features/skills/impl/managementhub/*` | Need navigation and visual parity with iOS hub. |

### Skills

| Feature | Priority | iOS Source | Android Source | Current Gap |
| --- | --- | --- | --- | --- |
| Skills Home | P0 | `Skills/SkillsHomeScreen/*` | `features/skills/impl/home/*` | Android has tabs/search/pagination; verify iOS card content, marketplace paging, refresh and errors. |
| Skill Detail | P0 | `Skills/SkillDetailScreen/*` | `features/skills/impl/detail/*` | Android now exposes iOS-style detail file render items, but still lacks the full file viewer/editor screen, content loading, preupload save, and rich code viewer behavior. |
| Skill Create | P0 | `Skills/SkillCreateScreen/*` | `features/skills/impl/create/*` | Android has ZIP/text import, editor, STS upload and create flow. File import conflict handling now mirrors iOS keep-both/overwrite semantics. Remaining: richer document-provider error handling and screenshot/UI parity. |
| Marketplace | P1 | `Skills/SkillMarketplaceScreen/*` | `features/skills/impl/marketplace/*` | Verify server search debounce, paging, ownership/install actions and empty/error states. |

### Settings Product Pages

| Feature | Priority | iOS Source | Android Source | Current Gap |
| --- | --- | --- | --- | --- |
| Vault Management | P0 | `Settings/VaultManagement/*` | `features/preferences/impl/vault/*` | Android now has list CRUD; parity work started for search/filter, screen error, success message, relative dates. Personal vault delete route now matches iOS key semantics. Edit page loads secret values and saves create/update paths with tests. Visual polish and string resources still pending. |
| Connectors List | P1 | `Settings/ConnectorListScreen/*` | `features/connectors/impl/list/*` | Android has search/category/connect; verify OAuth return refresh, categories, connected state and action placement. |
| Connector Manage | P1 | `Settings/ConnectorManageScreen/*` | `features/connectors/impl/manage/*` | Verify account list, disconnect confirmation, reconnect flow, error/success states. |
| Voice Library | P1 | `Settings/VoiceLibraryScreen/*` | `features/voicelibrary/impl/*` | Android supports provider voices, saved profiles, save/delete/share/import, delete notice and filtering. Remote preview now exposes iOS-style `previewTarget/loadingPreviewId/remotePreviewId` from presenter/state and downloads previews to a cached local `.mp3` before MediaPlayer playback. Recording upload now has `/api/voices/profiles/upload-clone` API, fake service, route test, direct `UploadRecording`, current-recording domain state/events, `RECORD_AUDIO` permission, native `VoiceLibraryRecordingController` m4a/base64 recording bridge, post-record local playback/progress/seek/scrub state, and recorded-file waveform extraction wired to the create-voice dialog. Remaining: final iOS visual parity and device screenshot coverage. |
| Settings Root Entries | P1 | `Settings/SettingsScreen/*` | `features/preferences/impl/root/*` | AI hub entry ordering and row callback routing are now model-owned via `SettingsAiAssistantRenderModel` and verified on PHK110 screenshot `/tmp/unseal-settings-ai-hub-render-model.png`. Remaining: exact iOS icons/grouping/light-dark visual polish and route transition screenshot set. |

### Credits

| Feature | Priority | iOS Source | Android Source | Current Gap |
| --- | --- | --- | --- | --- |
| Credits Screen | P1 | `Credits/CreditsScreen/*` | `features/credits/impl/*` | Balance, ledger pagination, daily usage chart, token analytics, low balance, tabs and partial error preservation are implemented in presenter/state tests. Analytics period values now mirror iOS `sevendays/thirtydays/all`. `CreditsPresenterTest` / `CreditsViewTest` / `CreditFormattersTest` refreshed on 2026-06-17. Remaining: visual polish, resource strings, screenshot coverage. |
| Topup | P1 | `Credits/TopupScreen/*` | `features/credits/impl/topup/*` | Android now mirrors iOS state semantics: preset/custom amount, `$1-$500` validation, `processing/awaitingPaymentSheet/confirming/success/failed`, PaymentIntent creation, official Stripe PaymentSheet bridge, client-secret ID parsing, ledger-settled polling, dismiss completed/cancel callbacks. `TopupPresenterTest` refreshed on 2026-06-17. Remaining: match iOS screen visuals and device screenshot flow. |

### Onboarding / Welcome

| Feature | Priority | iOS Source | Android Source | Current Gap |
| --- | --- | --- | --- | --- |
| Post Login Welcome | P1 | `PostLoginWelcome/*` | `appnav/root/PostLoginWelcomeView.kt`, `RootFlowNode.NavTarget.PostLoginWelcome`, `AppPreferencesStore` onboarding subscription keys | Android has a dedicated Unseal flow before signed-out login when no sessions exist. Data semantics now match iOS: step, selected theme, changelog opt-in, marketing opt-in, follow-X action hook, completion action. Remaining: clean-session screenshot, final copy/spacing polish, and backend subscription sync decision. |
| FTUE / Notifications / Verification | P2 | `Onboarding/*` | `features/ftue`, `features/verifysession` | Android now follows iOS post-verification ordering: Lockscreen -> Analytics -> Notifications. Unit coverage exists for full traversal, skipped verification and acknowledgement gating. Remaining: iOS identity-confirmed intermediate screen, copy/dismiss semantics and PostLoginWelcome feature decision. |

## Current Implementation Sample: Vault

iOS data/state:

- `VaultManagementViewState.ScreenState`: loading, empty, loaded, error.
- `filteredItems` derived by `searchText`.
- delete/save set `successMessage`; failures set `errorMessage`.
- list item date uses `RelativeDateTimeFormatter`.

Android target:

- `VaultManagementState` exposes `items`, `filteredItems`, `searchQuery`, `isFullScreenError`, `isSearchEmpty`.
- Presenter owns filtering and success/error state.
- View only renders state, with full-screen retry and relative date formatting.

Remaining Vault UI work:

- Replace hardcoded Chinese/English mixed strings with resources after parity stabilizes.
- Match iOS card shadow/background more closely.
- Add presenter tests for search/filter/delete success/error.

## Current Implementation Sample: Skills Create

iOS data/state:

- `ManualSkillFile` represents editable skill files.
- Importing a duplicate path raises a conflict alert with keep-both and overwrite choices.
- Keep-both appends a numeric suffix before the extension (`skill2.md`).
- Overwrite replaces the existing file content.

Android target:

- `SkillCreateState.pendingFileConflict` holds the pending imported file and target existing file.
- `KeepBothConflictingFile` and `OverwriteConflictingFile` apply the chosen resolution in the presenter.
- Compose only renders an `AlertDialog` from the state and emits events; it does not re-run import conflict logic.

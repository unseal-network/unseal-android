# Credits Dashboard Design

Date: 2026-06-09

## Feature Boundary

Feature name: `credits-dashboard`.

User-visible goal: Android users can open Credits & Billing from Settings, see their credit balance, recent transactions, daily usage, and token usage rankings, matching the iOS Credits screen without implementing Stripe top-up yet.

Dependency class: native Android implementable.

Blocked by SDK artifact work: no.

Blocked by component library migration: no.

Out of scope:

- Stripe PaymentSheet, top-up checkout, payment confirmation polling, and success/failure payment result UI. Those belong to a later `credits-topup` spec because Android needs a Stripe dependency and a payment-sheet host flow.
- Settings credit balance cache persistence. iOS caches the last balance in `AppSettings`; Android first version can load directly through `ChatbotApiService` and expose loading/unavailable states.
- Billing web portal or external payment management beyond the Credits dashboard entry point.
- Credits-related AI timeline rendering.
- Pull-to-refresh. The first Android dashboard reloads on first appear, range changes, analytics period changes, and explicit load-more.
- Voice, vault, sandbox, MiniApp, local Agent runtime, rich renderer, or Matrix Rust SDK changes.

## iOS Source References

Primary iOS files:

- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/FlowCoordinators/SettingsFlowCoordinator.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Settings/SettingsScreen/SettingsScreenModels.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Settings/SettingsScreen/SettingsScreenViewModel.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Settings/SettingsScreen/View/SettingsScreen.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Credits/CreditsScreen/CreditsScreenCoordinator.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Credits/CreditsScreen/CreditsScreenModels.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Credits/CreditsScreen/CreditsScreenViewModel.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Credits/CreditsScreen/View/CreditsScreen.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Credits/CreditsUIStyle.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Services/ChatbotAPI/ChatbotAPIClient.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Services/ChatbotAPI/ChatbotAPIModels.swift`

Deferred iOS top-up references:

- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Credits/TopupScreen/TopupScreenModels.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Credits/TopupScreen/TopupScreenViewModel.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Credits/TopupScreen/View/TopupScreen.swift`

Core Swift types:

- `CreditBalanceLoadState`
- `CreditsTab`
- `DailyUsageRange`
- `UsageRankingTab`
- `AnalyticsPeriod`
- `CreditsScreenViewState`
- `CreditsScreenViewAction`
- `CreditsScreenViewModel`
- `CreditsScreenCoordinator`
- `CreditBalance`
- `CreditLedgerItem`
- `CreditLedgerResponse`
- `CreditDailyBucket`
- `CreditDailyUsageResponse`
- `AnalyticsTokensResponse`

Important iOS behaviors to preserve:

- Settings has an AI assistant section with a credit balance card above AI management rows.
- Settings balance starts in a loading state, can show a cached balance, then refreshes from `getBalance()`.
- Settings balance unavailable renders `$0.00` in a secondary style.
- Settings card actions:
  - Recharge opens Topup.
  - Billing opens Credits with `initialTab = .balance`.
  - Usage opens Credits with `initialTab = .dailyUsage`.
- Credits screen has three tabs: balance, daily usage, and usage.
- On first appear, Credits starts four independent loads: balance, ledger, daily usage, and analytics.
- Balance tab shows current balance, user id, a top-up action, a small daily usage sparkline, and recent transactions.
- Recent transactions load with `getLedger(limit: 10, cursor: nil)`.
- Load more appends `getLedger(limit: 10, cursor: transactionsCursor)` and hides when `nextCursor == nil`.
- Daily usage tab supports seven-day and thirty-day ranges.
- Daily usage range uses local day boundaries: start is the beginning of today minus `days - 1`; end is the beginning of tomorrow.
- Daily usage calls `getDailyUsage(start, end)` and displays `totalUsageMicros`.
- Usage tab supports Agent and Model ranking, and analytics periods seven days, thirty days, and all.
- Analytics calls `getAnalyticsTokens(period: period.rawValue.lowercased())`.
- Loading failures are logged in iOS and do not clear already-loaded content.
- Balance is considered low when `balanceMicros` converted from micros to decimal is less than `1`.
- Money formatting:
  - `balanceUsd` displays with a leading `$` if missing.
  - micros are converted by dividing by `1_000_000`.
  - deltas use a sign and USD formatting.

## Android Existing State

Existing Android modules and files:

- `libraries/chatbot/api/src/main/kotlin/io/element/android/libraries/chatbot/api/ChatbotApiService.kt`
- `libraries/chatbot/api/src/main/kotlin/io/element/android/libraries/chatbot/api/model/credits/CreditModels.kt`
- `libraries/chatbot/api/src/main/kotlin/io/element/android/libraries/chatbot/api/model/analytics/AnalyticsModels.kt`
- `libraries/chatbot/impl/src/main/kotlin/io/element/android/libraries/chatbot/impl/DefaultChatbotApiService.kt`
- `libraries/chatbot/test/src/main/kotlin/io/element/android/libraries/chatbot/test/FakeChatbotApiService.kt`
- `libraries/chatbot/test/src/main/kotlin/io/element/android/libraries/chatbot/test/ChatbotFixtures.kt`
- `libraries/chatbot/impl/src/test/kotlin/io/element/android/libraries/chatbot/impl/DefaultChatbotApiServiceTest.kt`
- `features/preferences/impl/src/main/kotlin/io/element/android/features/preferences/impl/root/PreferencesRootView.kt`
- `features/preferences/impl/src/main/kotlin/io/element/android/features/preferences/impl/root/PreferencesRootNode.kt`
- `features/preferences/impl/src/main/kotlin/io/element/android/features/preferences/impl/PreferencesFlowNode.kt`

Existing aligned pieces:

- `ChatbotApiService` already exposes `getBalance`, `getLedger`, `getDailyUsage`, `createPaymentIntent`, `getPaymentIntentStatus`, and `getAnalyticsTokens`.
- `DefaultChatbotApiService` maps balance to `/api/credits/balance`, ledger to `/api/credits/ledger`, daily usage to `/api/credits/daily-usage`, top-up payment intent to `/api/credits/topup/payment-intent`, payment status to `/api/credits/topup/payment-intent/status`, and analytics tokens to `/chatbot/v1/analytics/tokens`.
- `FakeChatbotApiService` already exposes fake lambdas for credit operations.
- `ChatbotFixtures.aCreditBalance` exists.
- Preferences already has a navigation pattern for feature entry points, including recently added Webhook Triggers.

Android gaps:

- No `features/credits` module exists.
- Preferences has no credit balance state, no credit balance card, and no Credits navigation callbacks.
- No Android formatter exists for credit micros, balance strings, deltas, daily usage dates, or usage ranking percentages.
- No Android Credits presenter exists for concurrent balance, ledger, daily usage, and analytics loading.
- No Android Credits Compose screen exists.
- No top-up screen exists; this first spec should expose a top-up callback seam but can show the action as disabled or route it to a no-op until the later `credits-topup` spec.

## Target Android Behavior

### Entry Points And Navigation

Add `CreditsEntryPoint`.

Inputs:

- `initialTab: CreditsTab`

Callbacks:

- `onDone()`
- `onTopUpRequested(balance: CreditBalance?)`

Initial tabs:

- `Balance`
- `DailyUsage`
- `Usage`

Navigation behavior:

- Settings "Billing" opens Credits with `Balance`.
- Settings "Usage" opens Credits with `DailyUsage`.
- Settings "Recharge" calls the `onTopUpRequested` callback only after the later top-up spec wires it. In this first spec, the callback can be present and test-covered while the host may ignore it.
- Credits top-up button calls `onTopUpRequested(currentBalance)`.
- Back dismisses the Credits node.

### Settings Integration

Add an Android AI/Credits section to Preferences that mirrors iOS at a practical first-version level:

- A credit balance card appears near the top of Settings.
- It shows loading, loaded, and unavailable states.
- Loaded state displays `balanceUsd` with a leading `$` when needed.
- Unavailable state displays `$0.00`.
- Card actions are Recharge, Billing, and Usage.
- Billing opens the Credits screen on the Balance tab.
- Usage opens the Credits screen on the Daily Usage tab.
- Recharge is wired through a callback seam for the later top-up spec.
- The existing Webhook Triggers and Manage App rows remain unchanged.

### Credits Screen

The Credits screen should use Android Compose patterns:

- Top app bar title: `Credits & Billing`.
- Segmented tab control for Balance, Daily Usage, and Usage.
- Pull-to-refresh is not included in this first dashboard spec; explicit reload happens on first appear, range changes, analytics period changes, and load-more.
- Loading states should not erase existing successful content.
- Error states should be non-blocking text/snackbar state in the screen, because iOS logs and keeps the screen usable.

Balance tab:

- Load balance and ledger on first appear.
- Display available balance, user id, low balance styling, and top-up action.
- Display recent transactions.
- Show empty text when no transactions exist.
- Load more appends transactions while `nextCursor` is present.

Daily usage tab:

- Range control for seven days and thirty days.
- Load daily usage for local-day epoch-second range.
- Display rows or a simple chart-like list using existing Compose components; a custom chart library is not required.
- Display total spent using micros-to-USD formatting.

Usage tab:

- Ranking control for Agent and Model.
- Period control for seven days, thirty days, and all.
- Load analytics tokens with period strings matching iOS `period.rawValue.lowercased()`: `sevendays`, `thirtydays`, and `all`.
- Show the top five agents or models.
- Show token/call stats and percentage.
- Show empty text when no analytics data exists.

### Error Handling

- Each load has its own loading flag and error string.
- Balance, ledger, daily usage, and analytics failures should not clear previously loaded state.
- Load-more is ignored while already loading or when `hasMoreTransactions` is false.
- If `getBalance` fails in Preferences, the card becomes unavailable.
- If `getBalance` fails in Credits, the balance card shows `$0.00` and preserves any older balance in state if present.

## Data And API Mapping

iOS to Android model mapping:

- `CreditBalance` -> `CreditBalance`
- `CreditLedgerItem` -> `CreditLedgerItem`
- `CreditLedgerResponse.items` -> `CreditLedgerResponse.items`
- `CreditLedgerResponse.nextCursor` -> `CreditLedgerResponse.nextCursor`
- `CreditDailyBucket` -> `CreditDailyBucket`
- `CreditDailyUsageResponse` -> `CreditDailyUsageResponse`
- `AnalyticsTokensResponse` -> existing Android analytics model in `libraries/chatbot/api/model/analytics`

API mapping:

- `getBalance()` -> `ChatbotApiService.getBalance()`
- `getLedger(limit:cursor:)` -> `ChatbotApiService.getLedger(limit, cursor)`
- `getDailyUsage(start:end:)` -> `ChatbotApiService.getDailyUsage(start, end)`
- `getAnalyticsTokens(period:)` -> `ChatbotApiService.getAnalyticsTokens(period)`

Local persistence:

- No new persistence in this spec.
- Balance cache can be added in a later refinement if Android has an established session settings store for this feature.

Pagination:

- Ledger page size is `10`.
- Initial load uses `cursor = null`.
- Load more uses the last `nextCursor`.
- `hasMoreTransactions` is `nextCursor != null`.

## Dependency Boundaries

Allowed dependencies:

- Existing `ChatbotApiService` and `ChatbotApiServiceFactory`.
- Existing Matrix session/client abstractions for authentication through the API factory.
- Existing Compose, Appyx, Metro, design system, and UI string libraries.
- Existing Preferences feature for host entry.

Forbidden dependencies:

- `libraries/rustsdk`
- `org.matrix.rust`
- `voiceplayer`
- `voicerecorder`
- vault/sandbox/MiniApp/local Agent runtime modules
- `UnsealUI`
- `UnsealAgent`
- `UnsealMiniApp`
- Stripe SDK in this first dashboard spec

## Acceptance Criteria

- A `features/credits` feature family exists with API, implementation, and test modules.
- Credits entry point can open at Balance, Daily Usage, or Usage.
- Credits presenter loads balance, first ledger page, daily usage, and analytics on first appear.
- Changing daily usage range reloads daily usage.
- Changing analytics period reloads analytics.
- Load more appends ledger results and respects `hasMoreTransactions`.
- Credit formatters cover balance display, micros-to-USD, delta sign, and low-balance detection.
- Preferences shows a credit balance card with loading, loaded, and unavailable states.
- Preferences card Billing and Usage actions navigate to Credits with the correct initial tab.
- The top-up/recharge action is exposed through callbacks but does not implement Stripe checkout in this spec.
- Focused presenter and view tests pass.
- `:features:credits:impl:testDebugUnitTest` passes.
- `:features:credits:impl:compileDebugKotlin :features:preferences:impl:compileDebugKotlin` pass.
- `:app:assembleDebug` passes.
- Dependency scan over `features/credits` and the touched Preferences files has no forbidden dependencies.

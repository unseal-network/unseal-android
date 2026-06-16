# Tool Call Card Cross-Platform Parity

This document records the current iOS and Android Tool Call Card implementation comparison. It is intentionally separate from the Android room migration plan because card parity is now a two-way product requirement: Android may need to adopt iOS behavior, and iOS may need to adopt Android behavior when Android is more complete.

## Goal

Unify Tool Call Card data semantics and user-visible behavior across iOS and Android.

The target is not "Android copies iOS" by default. For every card, we compare both implementations, choose the more complete behavior, then migrate the missing pieces to the other client.

## Source Of Truth Candidates

### iOS

- Root dispatch: `/Users/Ruihan/go/src/unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/ToolCallRootCard.swift`
- Card transforms: `/Users/Ruihan/go/src/unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/CardTransforms.swift`
- Card implementations:
  - `ToolCardsIOS/Sources/ToolCardsIOS/GitHub/*.swift`
  - `ToolCardsIOS/Sources/ToolCardsIOS/ComposioSearch/*.swift`
  - `ToolCardsIOS/Sources/ToolCardsIOS/Linear/*.swift`
  - `ToolCardsIOS/Sources/ToolCardsIOS/Gmail/*.swift`
  - `ToolCardsIOS/Sources/ToolCardsIOS/GoogleDrive/*.swift`
  - `ToolCardsIOS/Sources/ToolCardsIOS/Twitter/*.swift`
  - `ToolCardsIOS/Sources/ToolCardsIOS/Schedule/*.swift`
  - `ToolCardsIOS/Sources/ToolCardsIOS/Moltbook/*.swift`

### Android

Current Android development is in the active worktree:

- `/Users/Ruihan/.config/superpowers/worktrees/unseal-android/chatbot-api-service`

Card implementation files:

- Root registry: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/ToolCardRegistry.kt`
- Root dispatch: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/ToolCardDispatcher.kt`
- Card transforms: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/toolcards/CardTransforms.kt`
- Card implementations:
  - `ComposioSearchCards.kt`
  - `GitHubCardsActivity.kt`
  - `GitHubCardsPrimary.kt`
  - `GmailDriveCards.kt`
  - `LinearTwitterCards.kt`
  - `ScheduleMoltbookCards.kt`

## Parity Method

For each card type:

1. Define canonical props as the union of iOS and Android consumed fields.
2. Ensure each client's transform layer can produce canonical props from raw tool output.
3. Ensure each UI consumes the same meaningful fields.
4. Allow native UI implementation differences, but align:
   - information hierarchy
   - semantic colors
   - loading/empty/error states
   - interactive affordances
   - scroll bounds
   - link behavior
   - image/gallery behavior
5. Verify with the same fixture rendered on both clients.

## Shared Card Intersection

| Group | cardType | More Complete Side Today | Parity Direction |
| --- | --- | --- | --- |
| GitHub | `checkRuns` | Similar | Add duration/timing on both sides if raw data exists. |
| GitHub | `commentThread` | Similar | Add PR context/link on both sides. |
| GitHub | `commitComparison` | iOS visual is closer; both are field-light | Add total/htmlUrl/date/file status/author details; Android needs stronger rendering first. |
| GitHub | `contributors` | Similar | Add type/htmlUrl display and verify avatar/link behavior. |
| GitHub | `deployments` | Similar but field-light | Add sha/task/description/time/creator avatar on both sides. |
| GitHub | `githubIssue` | Similar | Add repository context where available. |
| GitHub | `githubIssuesList` | Similar | Align label limit and relative time formatting. |
| GitHub | `notifications` | Both weak | Use subject URL when available; add reason/updatedAt. |
| GitHub | `orgsList` | Similar | Low priority; verify avatars and links. |
| GitHub | `release` | Both weak | Add assets list/download links, author, publishedAt, htmlUrl. |
| GitHub | `repoList` | Android transform preserves more fields; iOS/Android UI are lighter | Consume owner/forks/topics/visibility where useful. |
| GitHub | `secretAlerts` | Both weak | Add number/validity/resolution/createdAt. |
| GitHub | `workflows` | Similar | Add id/createdAt and fuller state display. |
| Search | `breakingNews` | Similar | Low priority; verify image and link. |
| Search | `headlineList` | iOS visual is better | Android should align density, spacing, image handling, and source styling. |
| Search | `imageGrid` | Similar | Verify grid ratio and image tap behavior. |
| Search | `productList` | iOS visual is better | Android should align product row layout, color, rating, and price emphasis. |
| Search | `urlContent` | Similar | Verify link handling and title fallback. |
| Travel | `flightAlert` | Similar | Add list-level trip metadata if present: departure/arrival/date/passengers. |
| Travel | `hotelBooking` | Android has richer image data; iOS interaction/visuals are stronger | Two-way: Android aligns visual hierarchy; iOS must keep image/gallery support working. |
| Market | `finance` | Similar | Align chart, change color, and secondary segmented tabs. |
| Local | `placeList` | Android has richer image/gallery data; iOS visual is stronger | Two-way: align image display, spacing, and place metadata. |
| Events | `eventList` | Similar | Verify with real fixture. |
| Linear | `linearIssue` | Similar | Low priority. |
| Linear | `linearIssuesList` | Similar | Low priority. |
| Mail | `composeEmail` | Android supports message list mode | Decide whether iOS should add list mode. |
| Drive | `fileAttachment` | iOS visual is better | Android should align row density, icon sizing, and metadata layout. |
| Social | `socialPostFeed` | Similar | Verify media display and engagement stats. |
| Schedule | `createSchedule` | Similar | Low priority. |
| Schedule | `updateSchedule` | Similar | Low priority. |
| Schedule | `updateScheduleStatus` | Similar | Low priority. |

## Single-Side Or Special Cases

| Card / Behavior | Current State | Action |
| --- | --- | --- |
| `weather` | Android has a dedicated `WeatherCard`; iOS root dispatch does not currently list `weather`. | Find the iOS weather render path. If absent, migrate weather to iOS from Android/canonical design. |
| `moltbookRegister` | iOS has an interactive Moltbook registration card; Android has a read-only suspended card render. | Android should adopt the interactive behavior if the feature is expected on Android. |
| Hotel/place gallery | Both sides reference image gallery behavior, but screenshots show inconsistent results. | Use the same fixture and verify image loading, tap, paging, and fallback states on both clients. |

## P0 Alignment Targets

1. `hotelBooking`
2. `placeList`
3. `productList`
4. `finance`
5. `headlineList`
6. `weather`
7. `moltbookRegister`
8. `fileAttachment`
9. `release`
10. `notifications`
11. `commitComparison`

## Acceptance Criteria

- The same fixture produces equivalent canonical props on iOS and Android.
- Neither client displays raw JSON to users for known card types.
- Cards with no meaningful content do not reserve large empty space.
- Card content has bounded height when embedded in timeline and supports internal scrolling when needed.
- Links open the intended target, not an unrelated parent URL.
- Image cards use stable dimensions and do not cause timeline reflow after load.
- Completed streams render cached card state without returning to loading/running UI.

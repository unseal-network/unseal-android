# iOS Room Data Client Workflow

This document is the implementation contract for migrating room features from iOS to Android. The order is intentional:

1. identify the iOS data source and route;
2. map it to an Android client/facade;
3. expose a stable domain/render model;
4. let Compose render from the model only.

Android UI must not call room API endpoints directly. Stream lifecycle still belongs to Stream SDK; Android room code consumes stream snapshots/render models.

## Route Ownership

| Area | iOS source of truth | Route semantics | Android owner | Consumer model | Notes |
|---|---|---|---|---|---|
| Matrix room members | `JoinedRoomProxy.updateMembers()`, `RoomScreenViewModel` member publisher | Matrix SDK room members, refreshed before agent enrichment when empty/stale | `RoomUnsealContextLoader` | `RoomUnsealContext.members` | Members are the base identity list for mentions, avatars, sender labels, direct-agent detection, and menu state. |
| Room agents | `ClientProxy.loadRoomAgents(roomId:)` | Homeserver chatbot API, room-scoped | `RoomUnsealDataClient.getRoomAgents` via `ChatbotApiServiceFactory.createForHomeserver(matrixClient)` | `RoomUnsealContext.roomAgents` | Never hardcode `api.unseal.network` for room agent state. |
| Account/global agents | `ChatbotAPIClient.listAgents()` | Homeserver chatbot API for logged-in account | `RoomUnsealDataClient.listAgents` | `RoomUnsealContext.allAgents` | Used to enrich room members and detect device agents when room membership alone is not enough. |
| Member-agent enrichment | `RoomAgentMemberEnricher.swift` | Matrix member + room/global agent descriptors | `RoomAgentMemberEnricher` | `RoomMemberRender` | Mentions, skill picker, topbar, and device-agent mode must share this enrichment. |
| Schedules | `RoomScreenViewModel.loadActiveScheduleCount()` | Homeserver chatbot API, filtered by room | `RoomUnsealDataClient.listSchedules` | `RoomUnsealContext.schedules`, `activeScheduleCount` | Topbar/menu reads the shared context; it must not repeat `listAgents/listSchedules`. |
| Room working memory | `ChatbotAPIClient.getRoomWorkingMemory(roomId:)` | Homeserver chatbot API, room-scoped | `RoomUnsealDataClient.getRoomWorkingMemory` | `RoomUnsealContext.workingMemory` | Menu/details surfaces should consume this field when enabled. |
| Webhook triggers | iOS room webhook trigger screens | Homeserver chatbot API, room-scoped triggers | `RoomUnsealDataClient.listWebhookTriggers` | `RoomUnsealContext.webhookTriggers`, `RoomWebhookSummary` | Webhook management pages can still have page clients, but room chrome reads shared summary. |
| Runtime skills | `ComposerToolbarViewModel.loadRoomAgentSkillCatalogs` | Prefer room-agent runtime skills, then legacy fallback | `ComposerAgentSkillCatalogLoader` | `ComposerAgentSkillState` | The skill picker reads reducer state; the picker UI cannot fetch skills directly. |
| Game packages | `GamePickerViewModel`, `GamePickerModels` | Homeserver `/app-mgr/package/json?method=pkg.app.list...` with `APP-U` header | `RoomGameApiServiceProvider` -> `GameApiService` | `GamePickerState` | Presenter receives a provider/handle instead of constructing URL/client itself. |
| AI stream | `unseal-agent-ios` stream/card views | stream id -> SSE patches -> parts -> render model | Stream SDK + `AiStreamHandleStore` | `AiStreamRenderModel`, `TimelineItemAiContent` | Android cannot parse/fetch stream outside SDK. |

## Android Client Layers

| Layer | Allowed responsibility | Not allowed |
|---|---|---|
| `RoomUnsealDataClient` | Load room-scoped chatbot data with partial failure resources. | Compose rendering, direct UI state, stream lifecycle. |
| `RoomUnsealContextStore` | Dedup/in-flight guard and expose room context as `StateFlow<AsyncData<RoomUnsealContext>>`. | Endpoint selection per UI component. |
| `RoomGameApiServiceProvider` | Resolve homeserver game API once per room scope and provide a `GameApiService` handle. | Presenter-local URL resolution or ad hoc OkHttp creation. |
| `ComposerAgentSkillCatalogLoader` | Load runtime/legacy skill candidates from room context targets. | Showing picker UI or mutating editor text directly. |
| Stream SDK / `AiStreamHandleStore` | Fetch/dedupe/cache stream snapshots and completed state. | Tool-card UI layout or Android-only stream parsing. |
| Reducers/adapters | Convert domain data into render models. | Network calls, raw JSON display fallback for normal users. |

## Feature Migration List

P0 room migration must complete these features in this order:

| Feature | Data dependency | Android render/interaction target | Done when |
|---|---|---|---|
| Room shared context | Members, agents, schedules, webhooks, working memory | `RoomUnsealContext` | One store feeds Messages, composer, topbar, menus. |
| Timeline presentation | Matrix event metadata + stream/content kind | `TimelinePresentationModel` | Bubble/standalone/avatar/timestamp/edited policies are model-driven. |
| Stream lifecycle/cache | Stream SDK snapshots | `AiStreamRenderModel` | Completed streams first-frame render from memory/store, no loading flash. |
| Stream parts/root card | SDK parts | `ToolCallRootRenderModel` | Root card inserted once at first tool position; tool parts are not duplicated. |
| Tool cards | Typed tool props from stream parts | Weather/finance/news/shopping/places/hotels/files/email/search/schedule cards | No raw JSON fallback, empty cards do not reserve large space. |
| Markdown | Stream text/normal body | `MarkdownRenderModel` | Links are clickable; completed markdown is cached. |
| Composer mentions/skills | Enriched members + skill catalogs | `ComposerSuggestionRenderModel`, `ComposerAgentSkillState` | Agent badge, direct agent slash flow, skill picker, selected skill send payload all match iOS semantics. |
| Room topbar/menu | Room context + call/schedule/device terminal capabilities | `RoomMenuRenderModel` | Floating iOS-style chrome and expanded tools do not shift timeline. |
| Message actions | Timeline target state | `MessageActionMenuRenderModel` | Select text, copy, translate, save, share, destructive actions match iOS availability. |
| Room key recovery | Matrix encryption state + recovery sources | `RoomKeyRecoveryTimelineRunner` models | Backup/sender/member restore flows are available and not rendered as normal bubbles. |
| Performance/cache | Stable item keys, render memoization, SDK handle reuse | Timeline/stream render cache | Scrolling over completed streams does not recreate handles or recompute heavy markdown/tool transforms. |

## Request Workflow Checklist

Every migrated feature should answer these before UI work starts:

- Which iOS file owns the source data?
- Is the route Matrix SDK, homeserver chatbot API, unseal API, app manager, Stripe, or Stream SDK?
- Which Android client/facade owns the request?
- Is the request room-scoped, account-scoped, or global?
- What is the domain model exposed to reducers?
- What is the render model consumed by Compose?
- How is refresh triggered: member change, schedule/webhook edit, composer mention trigger, app resume, or stream status change?
- How is partial failure represented without blocking the rest of the room UI?

## Verification

- `./gradlew :features:messages:impl:compileDebugKotlin --no-daemon -Pkotlin.incremental=false --console=plain`
- `./gradlew :features:messages:impl:testDebugUnitTest --tests 'io.element.android.features.messages.impl.roomdata.*' --no-daemon -Pkotlin.incremental=false --console=plain`
- For UI tasks, install to PHK110 and capture Android/iOS comparison screenshots before marking the task done.

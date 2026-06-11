# Android Agent Management & Menus Handoff

## Goal

Migrate the Unseal iOS agent-management surface (and its dependent menus) to Android,
keeping **layout / fields / logic / interactions identical to iOS** while rendering the
UI and animations in **native Material 3 / Material You**. iOS source of truth:
`/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Screens/Agents/...`.

## Build & Run

JDK 21 path changed — `/usr/libexec/java_home -v 21` is broken, use Homebrew:

```bash
export JAVA_HOME=/usr/local/opt/openjdk@21 \
       ANDROID_HOME=/usr/local/share/android-commandlinetools \
       PATH="/usr/local/opt/openjdk@21/bin:$PATH"
# one variant at a time (concurrent gradle corrupts the cache)
./gradlew -Pkotlin.incremental=false -Dorg.gradle.workers.max=1 :app:installGplayDebug
```

Debug app id: `network.unseal.android.debug`. Dev/test server: `https://un-server.dev-excel-alt.pagepeek.org`.

## API routing (matches iOS)

- **Homeserver** (`createForHomeserver`, `.well-known` `m.homeserver.base_url`, `/chatbot/v1/*`):
  agent CRUD, skills.
- **Agent-api** (`createForUnsealApi`, `.well-known` `org.unseal.api.base_url`, fallback
  `https://agent-api.unseal.network`, `/api/agent/*`): sandbox, vault clone, voice-config.
  This is iOS's `makeEnvironmentAPIClient()`.
- **AI-stream** (`createForAiStream(matrixClient)`): uses the logged-in Matrix user's
  homeserver server name and `.well-known` resolution, then opens `/chatbot/v1/agent/streams/{streamId}`.
  Do **not** hardcode `api.unseal.network` for stream downloads; the stream host must follow
  the same homeserver routing as the logged-in account.

## Completed

### Material 3 migration (compiles green, installed)
- **Agent management**: List (search + card grid + FAB + skeleton), Detail, Edit.
- **Skills**: Home / Marketplace / Detail / AgentSkills / ManagementHub.
- **Connectors**: List / Manage.
- **Webhooks**: List / Edit.
- **Voice Library**, **Credits** dashboard.

### Agent Edit — full iOS parity
- Avatar via system photo picker (launcher registered in `AgentEditNode`, not the View, to
  avoid Paparazzi crash; View takes `onSetAvatar: () -> Unit`).
- Basic info (name availability check, read-only identifier), Access control (public / auto-join),
  AI engine (provider/model `ExposedDropdownMenuBox` + conditional baseUrl/apiKey),
  Voice selection, Personality (default Soul prefilled on create).
- **Runtime environment (sandbox)**: per-user vs agent-dedicated; create-mode init radios
  (empty / clone-owner); edit-mode action buttons with **iOS-parity confirmation dialog**;
  busy spinner; success/error feedback inline.
- **Secret variables (vault)**: select from personal vault (clone) + manual entries.
- **Skills**: multi-select picker sheet.
- Create flow: createAgent → skills → voice → sandbox setup → vault clone → DM.
  Edit flow: updateAgent → skills → voice → vault clone.

### Bug fixes
- **Network-on-main-thread**: wrapped OkHttp blocking calls in `withContext(Dispatchers.IO)`.
- **404**: agent/skills endpoints were hitting agent-api; switched to `createForHomeserver`.
- **500 on submit**: `AgentSandboxMode` enum was serializing `none`; fixed to
  `own_sandbox` / `agent_sandbox`.
- **Sandbox mode not loading** (page always showed per-user): `loadAgentIfNeeded` now syncs
  `form.sandboxMode` from `getAgentSandbox().sandboxMode` (mirrors iOS ViewModel L214).
- **Error visibility**: `ChatbotApiError.HttpError` now includes the redacted server body;
  `ChatbotHttpClient` logs every non-2xx as `Timber.w("Chatbot HTTP <code> <method> <path> -> <body>")`;
  `credits_exhausted` mapped to a friendly message.

### AI SDK stream lifecycle SDK + Android timeline integration
- New shared stream data layer in `libraries/agentstream`:
  - Public entrypoint: `AgentStreamClient.getStream(StreamRequest)`.
  - Owns stream lifecycle: memory hot cache, storage lookup, in-flight de-dupe,
    async SSE consumption, Rust reducer session feeding, listener fan-out, terminal snapshot
    persistence, and refresh/cancel cleanup.
  - Public data contract is `StreamSnapshot(status, parts, rawEvents, error, updatedAtMs)`.
    UI must render from `snapshot.parts`; `rawEvents` is retained for debug/advanced fallback only.
  - `StreamPart` covers AI SDK-style text/reasoning/tool/data/source/file/step/error/custom
    parts. Tool/text states are normalized so UI can be `UI = f(parts)`.
  - Storage is injected through `StreamStorageProvider`; Android currently provides SQLite-backed
    persistence via `SQLiteStreamStorageProvider`. Completed snapshots are reused, so timeline
    re-entry should not re-download or show thinking again.
  - HTTP/thread resources are injected through `StreamHttpClient` and `StreamTaskRunner`.
    Android uses `ChatbotStreamHttpClient` plus `CoroutineStreamTaskRunner`; SDK logic stays
    independent from platform-specific pools and dispatchers.
- Android client adapters in `features/messages/impl/.../timeline/components/event/AndroidAgentStreamAdapters.kt`:
  - `AndroidAgentStreamClient` wires the SDK into Metro DI.
  - `ChatbotStreamHttpClient` calls `ChatbotApiServiceFactory.createForAiStream(matrixClient)`
    and streams chunks into the SDK without accumulating the full response.
  - Blank Matrix sender values are sanitized before stream download.
- Timeline rendering path:
  - `TimelineItemContentFactory` parses Matrix event `originalJson` first, including top-level
    `m.stream.start` / `m.stream.complete` events even when Rust Matrix SDK maps them as
    non-`MessageContent`.
  - `TimelineItemAiPresenter` now subscribes to `AgentStreamClient.getStream(...)` and maps
    SDK snapshots with `AiSdkStreamReducer`; it no longer opens SSE, owns a reducer session,
    or keeps its own in-flight stream cache.
  - `TimelineItemEventContentView` renders AI content from presenter state, not from the raw
    event body. Normal messages still fall through to the existing text/media renderers.
  - Presenter snapshot queue is conflated and terminal snapshots force a final UI update before
    collection exits, avoiding unbounded backpressure during fast timeline scrolling.
- Parser edge cases covered:
  - `m.stream.complete` defaults to non-streaming even without explicit `is_streaming: false`.
  - Terminal statuses `complete`, `completed`, `done`, `failed`, `error`, `cancelled`, `canceled`
    are non-streaming; `loading`, `streaming`, `active`, `pending` are streaming.
- Important tests:
  - `:libraries:agentstream:testDebugUnitTest`
  - `:features:messages:impl:testDebugUnitTest --tests '*AiSdkStreamReducerTest' --tests '*AndroidAgentStreamAdaptersTest' --tests '*TimelineItemAiPresenterTest' --tests '*AiMessageContentParserTest' --tests '*TimelineItemContentFactoryTest'`
  - `:libraries:chatbot:impl:testDebugUnitTest --tests '*ChatbotHttpClientTest'`
  - `:features:messages:impl:compileDebugKotlin :libraries:chatbot:impl:compileDebugKotlin`
  - `:app:installFdroidDebug` installed `app-fdroid-arm64-v8a-debug.apk` to USB device `PHK110 - 15`.

### Diagnosed, NOT a bug
- Clone-owner returns 200 ("Sandbox cloned successfully") and works.
- Create-empty 500 = server `credits_exhausted` (account out of credits), not an app issue.

## Pending / TODO

1. **Verify on a credited account**: create-empty success path can't be tested while the
   current account is `credits_exhausted`.
2. **Re-record Paparazzi snapshots** for all migrated M3 views and wire into CI
   (`recordPaparazzi*`). Snapshot PNGs currently committed are initial baselines.
3. **Confirmation-dialog parity audit**: iOS has distinct copy for create vs overwrite vs
   reclone; Android covers create/overwrite/reclone but strings are hardcoded (not Localazy).
4. **Localization**: new Android strings are inline English literals, not in `localazy.xml`.
5. **Dead code**: `shared/AgentModalScaffold.kt` and `shared/AgentFormComponents.kt` are
   unused (List/Detail/Edit are M3 and don't use them) — remove or repurpose.
6. **Remaining menu polish**: Voice Library preview/playback deferred (no events);
   Skills Detail "Files" section omitted (no `presignedUrls` field in the Android model).
7. **Tests**: presenter unit tests for the new sandbox/vault/skills/voice flows.

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

#### Current commits / repos
- Android branch: `feature/agent-management`.
  - Latest stream commits: `90daf2068a fix(stream): preserve final parts state`,
    `10b32d0780 refactor(stream): move snapshot update policy into sdk`.
- Rust stream SDK repo: `git@pagepeek:unseal-network/agent-stream-sdk.git`,
  branch `feature/stream-core-types`.
  - Latest stream commit: `d98e0de fix: coalesce stream part patches in sdk`.

#### Layer ownership
- **Rust stream SDK** (`agent-stream-sdk`, crate `unseal-agent-stream`) owns the shared data
  semantics:
  - SSE frame parsing.
  - AI SDK / Unseal stream event reduction.
  - Canonical `parts` state machine.
  - Raw/normalized event retention.
  - `StreamSnapshot`, `StreamUpdate`, `PersistedStream` schemas.
  - Runtime emit policy for part patches: terminal/error/state changes emit immediately;
    unchanged snapshots are skipped; same-state content patches coalesce by default for 500 ms.
- **Android SDK wrapper** (`unseal-android/libraries/agentstream`) owns Android-facing lifecycle:
  - Public entrypoint: `AgentStreamClient.getStream(StreamRequest)`.
  - Memory hot cache for completed snapshots.
  - Storage lookup before network.
  - In-flight stream de-dupe by `streamId`.
  - Async SSE consumption through injected `StreamHttpClient`.
  - Background execution through injected `StreamTaskRunner`.
  - JNI reducer session lifecycle.
  - Listener fan-out.
  - Final snapshot memory cache + storage persistence before final publish.
  - Android-side `StreamSnapshotUpdatePolicy` so UI presenters do not implement part update
    throttling/skip rules themselves.
- **Android timeline UI** owns rendering only:
  - Matrix event parsing finds `streamId` and optional inline `parts`.
  - `TimelineItemAiPresenter` subscribes to `AgentStreamClient`, applies
    `StreamSnapshotUpdatePolicy`, maps `StreamSnapshot.parts` to the existing Android
    `TimelineItemAiContent` model through `AiSdkStreamReducer`.
  - `TimelineItemAiView` renders `UI = f(parts)`.
  - UI should not download SSE, run a reducer, or maintain a separate stream cache.

#### Stream SDK public contract used by Android
- `StreamRequest`
  - `streamId`: required stream id from Matrix event content.
  - `sender`: Matrix sender/user id when available; blank values are sanitized before request.
  - `roomId`, `eventId`: reserved for callers and future storage/debug context.
  - `includeRawEvents`: Android timeline currently passes `false` for normal UI rendering.
- `StreamHandle`
  - `snapshot()`: current snapshot.
  - `subscribe(listener)`: listener immediately receives the current snapshot, then updates.
  - `refresh()`: force re-download.
  - `cancel()`: stop active transport and close native reducer session.
- `StreamSnapshot`
  - `status`: `Idle`, `Loading`, `Streaming`, `Completed`, `Failed`, `Cancelled`.
  - `parts`: canonical render data. Clients render from this list.
  - `rawEvents`: debug/advanced fallback only, not normal UI.
  - `error`, `updatedAtMs`, `completedAtMs`.
- `StreamPart`
  - Standard-ish AI SDK parts: `Text`, `Reasoning`, `Tool`, `Data`, `Source`, `File`,
    `Step`, `Error`, `Custom`.
  - Text/reasoning states: `streaming`, `complete`, `done`.
  - Tool states: `input-streaming`, `input-available`, `output-available`, `output-error`.
  - Unknown/custom server payloads remain accessible through `Custom` or raw JSON fields,
    but normal UI should prefer meaningful typed renderers over showing JSON.

#### Android implementation map
- SDK module:
  - `libraries/agentstream/src/main/kotlin/io/element/android/libraries/agentstream/api/StreamModels.kt`
    defines `StreamRequest`, `StreamSnapshot`, `StreamPart`, status/state enums.
  - `libraries/agentstream/src/main/kotlin/io/element/android/libraries/agentstream/api/DefaultAgentStreamClient.kt`
    implements lifecycle, memory cache, storage-first loading, de-dupe, SSE consumption,
    final persistence, cancellation, and listener fan-out.
  - `libraries/agentstream/src/main/kotlin/io/element/android/libraries/agentstream/api/StreamSnapshotUpdatePolicy.kt`
    implements UI update policy: skip unchanged, emit state/terminal immediately, coalesce
    same-state content patch updates for 500 ms.
  - `libraries/agentstream/src/main/kotlin/io/element/android/libraries/agentstream/jni/UnsealAgentStreamNative.kt`
    loads `libunseal_agent_stream.so` and calls the Rust reducer session.
  - Native libraries are checked in under
    `libraries/agentstream/src/main/jniLibs/{arm64-v8a,armeabi-v7a,x86_64}/libunseal_agent_stream.so`.
- Android dependency injection / platform adapters:
  - `features/messages/impl/.../timeline/components/event/AndroidAgentStreamAdapters.kt`
    binds `AgentStreamClient`, `StreamHttpClient`, `StreamTaskRunner`, and `StreamStorageProvider`.
  - `ChatbotStreamHttpClient` calls
    `ChatbotApiServiceFactory.createForAiStream(matrixClient).streamAgentMessage(...)`.
    This is important: stream downloads follow the logged-in user's homeserver `.well-known`
    routing and must not hardcode `api.unseal.network`.
  - `CoroutineStreamTaskRunner` runs SDK-submitted stream work on `dispatchers.io` inside
    `@RoomCoroutineScope`.
  - `SQLiteStreamStorageProvider` persists terminal snapshots in
    `agent_stream_snapshots.db`, table `agent_stream_snapshots`.
- Timeline path:
  - `features/messages/impl/.../factories/event/AiMessageContentParser.kt` parses stream
    pointers and terminal flags from Matrix event `originalJson`.
  - `TimelineItemContentFactory` chooses AI stream content before normal text fallback.
  - `TimelineItemAiPresenter` subscribes to `AgentStreamClient.getStream(...)`.
  - `AiSdkStreamReducer` maps SDK `StreamPart` values into Android timeline render models.
  - `TimelineItemAiView` renders text/reasoning/tool cards. Tool cards are extracted above
    the text flow; multiple tools render as tabs, single tool renders directly.

#### Rust SDK functionality completed
- Canonical stream schemas and serde coverage.
- SSE frame parser, including partial frame handling and final flush.
- Unseal content-envelope unwrapping.
- AI SDK stream protocol reducer into stable `parts`.
- Tool state transitions through `input-streaming` / `input-available` /
  `output-available` / `output-error`.
- Text/reasoning accumulation and terminal completion.
- Raw event retention options.
- Runtime lifecycle with injected transport, persistence, worker runner, resource hints,
  queued starts, and max active stream tasks.
- Emit/update policy in Rust runtime:
  - terminal/status/state changes emit immediately;
  - identical snapshots skip;
  - same-state content patches coalesce with defaults:
    `text_coalesce_ms = 500`, `patch_coalesce_ms = 500`,
    `text_coalesce_chars = 80`, `patch_coalesce_chars = 80`.
- Android JNI bridge:
  - `nativeCreateSession(streamId, includeRawEvents, includeNormalizedEvents)`.
  - `nativeApplySseChunk(session, chunk)` returns snapshot JSON.
  - `nativeFinish(session)` flushes parser and returns final snapshot JSON.
  - `nativeSnapshot(session)`.
  - `nativeDestroySession(session)`.
  - `nativeIsAvailable()`.

#### How to build Rust stream SDK for Android
Prereqs on this machine:

```bash
export ANDROID_HOME=/usr/local/share/android-commandlinetools
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/28.2.13676358"
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
cargo install cargo-ndk
```

Build all Android ABIs from the Rust SDK repo:

```bash
cd /Users/Ruihan/go/src/unseal-agent-stream-core/.worktrees/stream-core-types
ANDROID_NDK_HOME="$ANDROID_HOME/ndk/28.2.13676358" \
cargo ndk \
  --target aarch64-linux-android \
  --target armv7-linux-androideabi \
  --target x86_64-linux-android \
  --platform 26 \
  -- build --release -p unseal-agent-stream
```

Expected outputs:

```text
target/aarch64-linux-android/release/libunseal_agent_stream.so
target/armv7-linux-androideabi/release/libunseal_agent_stream.so
target/x86_64-linux-android/release/libunseal_agent_stream.so
```

Copy into Android:

```bash
ANDROID_REPO=/Users/Ruihan/.config/superpowers/worktrees/unseal-android/chatbot-api-service
SDK_REPO=/Users/Ruihan/go/src/unseal-agent-stream-core/.worktrees/stream-core-types

mkdir -p \
  "$ANDROID_REPO/libraries/agentstream/src/main/jniLibs/arm64-v8a" \
  "$ANDROID_REPO/libraries/agentstream/src/main/jniLibs/armeabi-v7a" \
  "$ANDROID_REPO/libraries/agentstream/src/main/jniLibs/x86_64"

cp "$SDK_REPO/target/aarch64-linux-android/release/libunseal_agent_stream.so" \
  "$ANDROID_REPO/libraries/agentstream/src/main/jniLibs/arm64-v8a/libunseal_agent_stream.so"
cp "$SDK_REPO/target/armv7-linux-androideabi/release/libunseal_agent_stream.so" \
  "$ANDROID_REPO/libraries/agentstream/src/main/jniLibs/armeabi-v7a/libunseal_agent_stream.so"
cp "$SDK_REPO/target/x86_64-linux-android/release/libunseal_agent_stream.so" \
  "$ANDROID_REPO/libraries/agentstream/src/main/jniLibs/x86_64/libunseal_agent_stream.so"
```

Then verify Android can load the JNI library:

```bash
cd "$ANDROID_REPO"
./gradlew :libraries:agentstream:testDebugUnitTest
./gradlew :app:installGplayDebug
```

Current installed test device: `PHK110 - 15`.

#### Important validation commands
- Rust SDK:
  - `cargo test`
- Android SDK wrapper + timeline:
  - `./gradlew :libraries:agentstream:testDebugUnitTest :features:messages:impl:testDebugUnitTest --tests '*DefaultAgentStreamClientTest*' --tests '*StreamSnapshotUpdatePolicyTest*' --tests '*TimelineItemAiPresenterTest*'`
- Broader Android stream/timeline checks:
  - `./gradlew :features:messages:impl:testDebugUnitTest --tests '*AiSdkStreamReducerTest' --tests '*AndroidAgentStreamAdaptersTest' --tests '*TimelineItemAiPresenterTest' --tests '*AiMessageContentParserTest' --tests '*TimelineItemContentFactoryTest'`
  - `./gradlew :libraries:chatbot:impl:testDebugUnitTest --tests '*ChatbotHttpClientTest'`
  - `./gradlew :features:messages:impl:compileDebugKotlin :libraries:chatbot:impl:compileDebugKotlin`

#### Known constraints / next work
- `StreamSnapshotUpdatePolicy` currently exists in Android wrapper as well as Rust runtime
  policy. Keep Android policy as the UI-facing guard until the Kotlin layer consumes runtime
  `StreamUpdate` directly; do not move this logic into timeline presenters.
- SQLite provider is intentionally simple and app-local. If replacing it with Room or encrypted
  storage, keep the `StreamStorageProvider` boundary and preserve completed snapshot reuse.
- Android renderer still needs iOS card parity work by `StreamPart`/tool type. Data should come
  from `parts`; avoid rendering raw JSON directly to users.
- Virtual scrolling and visibility policy remain client-owned. The stream SDK provides
  `getStream`, `subscribe`, `refresh`, and `cancel`; list screens decide when to subscribe
  or cancel recycled rows.

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

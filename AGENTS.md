# AGENTS.md — Unseal Android

> **Repo:** `unseal-android` — Unseal's Android Matrix client, based on Element X Android (Compose UI + `matrix-rust-sdk`).

---

## Strong Conventions

PRs must meet these rules.

### Code Style

- Style enforced by **Editor config** (`.editorconfig`).
- Set "Hard wrap at" to 160 chars in Android Studio.

### PII & Logging

- We use **Timber** for logging. Never use `android.util.Log`.
- **Never log secrets, passwords, keys, or user content** (e.g. message bodies).
- Matrix IDs (User IDs, Room IDs, Event IDs) are safe to log.

### Strings & Localisation

- Default localisation: `en` (en-GB strings), shared with Element X iOS via [Localazy](https://localazy.com/p/element).
- **Never edit `localazy.xml`** — it is auto-generated and overwritten.
- New English strings go in **`temporary.xml`**. The core team imports these to Localazy.
- **Key naming**:
  - Cross-screen verbs: `action_` (e.g., `action_copy`).
  - Common nouns/other: `common_` (e.g., `common_error`).
  - Accessibility: `a11y_`.
  - Screen-specific: `screen_<name>_<key>` (e.g., `screen_onboarding_welcome_title`).
  - Errors: `error_` prefix.
  - Platform-specific: `_ios` or `_android` suffix.
  - Placeholders: Use numbered form `%1$s`, `%2$d`.

### Previews

- Create previews for **all main states** of a Composable.
- Use `@PreviewsDayNight` for consistency.
- Use `PreviewParameterProvider` (e.g., `FooStateProvider`) to provide states.
- Wrap previews in `ElementPreview { ... }`.

---

## Pull Request Guidelines

- Sentence-style titles (no conventional commits).
- Exactly one `pr-` label (see `.github/release.yml`).
- Title = changelog entry — descriptive, no "Fixes #…".
- Leave description template for the developer. Redirect them to the [contributing etiquette](CONTRIBUTING.md#etiquette).
- Screenshots/videos for visual changes.
- 500 additions max — split large changes.
- Commits need a title and description; no tiny or massive commits.
- No history rewrites.

---

## Project Structure

### Build System

Common Gradle tasks:
- Build: `./gradlew assembleDebug`
- Unit Tests: `./gradlew test`
- Lint: `./gradlew lint`
- Format: `./gradlew ktlintFormat`
- Update Docs TOC: `./gradlew generateDocsToc`

### Android Debugging

Use JDK 17 or newer. JDK 21 is recommended for local Gradle builds.

Before building or installing from the command line, make sure the Android SDK tools are on `PATH`:

```bash
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
```

Install or verify the basic Android SDK packages:

```bash
sdkmanager "platform-tools" "emulator" "platforms;android-36" "build-tools;36.0.0"
yes | sdkmanager --licenses
```

Build a debug APK:

```bash
./gradlew --no-daemon --no-configuration-cache :app:assembleGplayDebug
```

If the GPlay flavor is not desired, build the F-Droid flavor instead:

```bash
./gradlew --no-daemon --no-configuration-cache :app:assembleFdroidDebug
```

Find the generated APK:

```bash
find app/build/outputs/apk -name "*.apk" -print
```

Start a visible emulator:

```bash
emulator -list-avds
emulator -avd <AVD_NAME> -no-snapshot -gpu swiftshader_indirect
```

Do not pass `-no-window` when a human needs to inspect the app. A process launched with `-no-window` or `qemu-system-*-headless` is usable by `adb` but will not show an emulator window.

Wait for the emulator or device:

```bash
adb wait-for-device
adb devices -l
```

Install the APK:

```bash
adb install -r <APK_PATH>
```

Launch the debug app:

```bash
adb shell monkey -p network.unseal -c android.intent.category.LAUNCHER 1
```

If launch fails, confirm the package and launchable activity from the APK:

```bash
aapt dump badging <APK_PATH> | grep -E "package:|launchable-activity"
```

For true device debugging, enable Developer Options and USB Debugging on the phone, connect it by USB, then use the same `adb devices`, `adb install -r <APK_PATH>`, and `adb shell monkey ...` commands. If the device ABI is unknown, use a universal APK; otherwise prefer the ABI-specific APK that matches the device.

### Gradle Modules

Features follow a 3-module structure:
- `features/foo/api`: Public interfaces and data classes.
- `features/foo/impl`: Internal implementation, Presenter, and View.
- `features/foo/test`: Test fakes and utilities.

---

## Architecture: Appyx + Molecule

We use [Appyx](https://bumble-tech.github.io/appyx/) for navigation and [Molecule](https://github.com/cashapp/molecule) for Presenters.

### Files Per Screen (`Foo`)

| File | Purpose |
| :--- | :--- |
| `FooNode.kt` | Appyx Node: Handles navigation and wires the Presenter to the View. |
| `FooPresenter.kt` | A `@Composable` function that produces `FooState` from `FooEvent`s. |
| `FooView.kt` | Stateless Composable rendering the UI from `FooState`. |
| `FooState.kt` | Data class representing the immutable UI state. |
| `FooEvent.kt` | Sealed interface for UI actions sent to the Presenter. |
| `FooStateProvider.kt` | Provides sample states for Previews and Screenshot tests. |
| `FooPresenterTest.kt` | Unit tests for the Presenter logic using Turbine. |

> **Gotcha:** `awaitStateWhere { predicate }` is a **private per-file helper**, not a shared util — copy its `private suspend fun TurbineTestContext<FooState>.awaitStateWhere(...)` definition into each new `FooPresenterTest.kt` (import `app.cash.turbine.TurbineTestContext`).

---

## Dependency Injection (Metro)

- We use [Metro](https://zacsweers.github.io/metro/) for DI.
- Inject via constructor parameters using `@Inject`.
- Use `@AssistedInject` and `@AssistedFactory` for components requiring runtime arguments (like Navigators or IDs).
- Use `@ContributesBinding(AppScope::class)` for singleton-like services.
- Use `@ContributesNode(RoomScope::class)` for Appyx Nodes.

---

## Compound Design System

Always prefer Compound components and tokens from `libraries/compound/` module.

- **Colours**: `ElementTheme.colors.textPrimary`, `ElementTheme.colors.bgCanvasDefault`.
- **Typography**: `ElementTheme.typography.fontBodyMdRegular`.
- **Icons**: Use `CompoundIcons.IconName()` (e.g., `CompoundIcons.UserProfileSolid()`).

---

## The Rust SDK Layer

We wrap the `matrix-rust-sdk` to isolate the UI from the underlying SDK.
- Naming: SDK `Room` → `JoinedRoom` or `RoomInfo`.
- Type Mapping: Map Rust SDK types to Kotlin data classes in the `api` module to avoid leaking `MatrixRustSDK` into the UI.
- Always follow Kotlin naming conventions (e.g., `userId` instead of `userID`).

## Agent Stream SDK Dependency

AI SDK stream parsing and `parts` state updates are shared through the Rust stream SDK:

- SDK repo: `git@pagepeek:unseal-network/agent-stream-sdk.git`
- Android dependency publishing repo: `git@pagepeek:unseal-network/agent-stream-components-kotlin.git`
- GitHub Packages Maven repo: `https://maven.pkg.github.com/unseal-network/agent-stream-components-kotlin`
- Current Android SDK release: `network.unseal:agent-stream-android:0.1.0-rc.18`
- Android wrapper module: `libraries/agentstream`
- Native library name loaded by Android: `libunseal_agent_stream.so`
- JNI Kotlin entrypoint: `libraries/agentstream/src/main/kotlin/io/element/android/libraries/agentstream/jni/UnsealAgentStreamNative.kt`
- Do not commit stream SDK `.so` files into `libraries/agentstream/src/main/jniLibs`. The final Android client must resolve `network.unseal:agent-stream-android` from GitHub Packages.

The ownership split is important:

- Rust stream SDK owns SSE frame parsing, AI SDK / Unseal stream event reduction, canonical `parts`, part states, raw events, and patch coalescing.
- Android `libraries/agentstream` owns platform lifecycle: `AgentStreamClient.getStream`, memory cache, SQLite storage provider, HTTP provider injection, task runner injection, JNI session lifecycle, listener fan-out, and final snapshot persistence.
- Timeline UI owns rendering only. UI should render `UI = f(snapshot.parts)` and should not open SSE, run a reducer, or keep a separate stream cache.

Build and publish the Rust stream SDK for Android from the dependency publishing repo:

```bash
cd /path/to/agent-stream-components-kotlin
export GITHUB_ACTOR=<github-user>
export GITHUB_TOKEN=<classic-token-with-write-packages>
export AGENT_STREAM_SDK_REPO=git@pagepeek:unseal-network/agent-stream-sdk.git
export AGENT_STREAM_SDK_REF=<agent-stream-sdk-ref>
./scripts/publish-release.sh <version>
```

Verify after updating the SDK binary:

```bash
./gradlew :libraries:agentstream:testDebugUnitTest
./gradlew :features:messages:impl:testDebugUnitTest --tests '*TimelineItemAiPresenterTest*'
./gradlew :app:installGplayDebug
```

---

## Unseal Android Project Notes

These are stable project rules. Do not add temporary transfer/status files for normal work; fold durable facts into this file or a focused document under `docs/`.

### App Identity

- Android application id is `network.unseal`.
- The Kotlin/Android namespace remains `network.unseal.android`; do not rename source packages just to change the installed package.
- Nightly builds may use `network.unseal.nightly`. Debug builds intentionally use the production package id unless a task explicitly says otherwise.
- The launcher command is:

```bash
adb shell monkey -p network.unseal -c android.intent.category.LAUNCHER 1
```

### Unseal Data Boundaries

- iOS is the product-parity reference, but do not implement from screenshots alone. Check the iOS data source first, then align Android client/domain models, reducer/render models, and finally Compose UI.
- Homeserver-backed Chatbot APIs (`/chatbot/v1/*`) resolve from `.well-known/matrix/client` `m.homeserver.base_url`.
- Unseal agent-api features such as credits, voice, environment, vault, connectors, and triggers resolve from `.well-known/matrix/client` `org.unseal.api.base_url`; when absent they fall back to the same homeserver host, not an unrelated production host.
- Matrix room data such as members, timeline events, read receipts, typing, and encryption state must continue to come from the Matrix SDK / room wrappers.
- UI code must not hardcode API hosts or bypass the resolver layer.

### Agent Stream Rendering

Stable stream-render assets remain in the repo:

- Fixture manifest: `docs/agent-stream-fixtures/manifest.json`
- Fixture replay tool: `tools/agent-stream-parity/README.md`
- Android stream wrapper: `libraries/agentstream`

Android AI stream rendering consumes `libraries/agentstream` through `AgentStreamClient`:

1. Matrix timeline event exposes `streamId`.
2. Room/timeline binding uses `AiStreamHandleStore`, which calls `AgentStreamClient.getStream(StreamRequest(...))`.
3. The binding subscribes to `StreamHandle` snapshots and normalizes terminal snapshots with `normalizedForTerminalState()`.
4. `AiSdkStreamReducer.mapSnapshot()` converts SDK `StreamSnapshot` to `TimelineItemAiContent`; `mapRenderModel()` owns the lower-level render model conversion.
5. Compose renders `TimelineItemAiContent` only.

Do not fetch SSE, parse full stream JSON, run reducers, create HTTP tasks, or write stream storage from Compose or messages UI code. UI should render `UI = f(renderModel)`.

Stream update policy:

- Terminal snapshots emit immediately.
- State changes emit immediately.
- Text-only streaming patches are coalesced in the timeline presenter with `STREAMING_TEXT_PATCH_COALESCE_MS = 120L`; the generic library default is 500 ms.
- Completed snapshots loaded from memory or storage should render as completed content immediately. If a completed stream still has non-terminal part states, fix SDK normalization or `StreamSnapshot.normalizedForTerminalState()`, not the UI layer.

### Tool Cards And Fixtures

Tool-card rendering is intentionally native on Android while matching the iOS field model:

- `AiToolCardLogic.kt` extracts AI SDK tool entries, including nested `COMPOSIO_MULTI_EXECUTE_TOOL` / `renderUI` payloads.
- `toolcards/CardTransforms.kt` mirrors iOS `ToolCardsIOS/CardTransforms` and normalizes payloads before rendering.
- `toolcards/ToolCardDispatcher.kt`, `ToolCardKit.kt`, and the `*Cards.kt` files own card selection, shells, tabs, status indicators, and card-specific layout.
- Supported fixture families include web search, images, shopping, events, places, hotels, weather, GitHub, Gmail/Drive, Linear, Twitter/X, schedules, files, generic data, and failure details.
- Do not show raw JSON to normal users. Raw payloads are allowed only as explicit failure/detail developer affordances.

When changing stream or tool-card behavior, prefer these checks:

```bash
./gradlew --no-daemon --no-configuration-cache :libraries:agentstream:testDebugUnitTest
./gradlew --no-daemon --no-configuration-cache :features:messages:impl:testDebugUnitTest --tests '*AgentStreamParityReplayTest*'
./gradlew --no-daemon --no-configuration-cache :features:messages:impl:testDebugUnitTest --tests '*ToolCardDispatcherTest*'
./gradlew --no-daemon --no-configuration-cache :features:messages:impl:testDebugUnitTest --tests '*TimelineItemAiPresenterTest*'
```

### Room And Timeline Rules

- Keep timeline rows on a single layout policy. Avatar, content, metadata, read receipts, warnings, and overlays should have fixed regions so one event type cannot push another into a different layout system.
- Bottom composer and top room bar are overlays. Timeline content should use measured padding/insets so first and last messages remain reachable without adding arbitrary whitespace.
- Read receipts and timestamps belong to metadata layout, not inside event content rows.
- Markdown and tool cards should be parsed/transformed outside hot Compose paths when possible; avoid creating regexes, date formatters, image decoders, or large JSON objects during recomposition.
- Use stable LazyColumn keys and keep image/markdown work cached to protect room-list, timeline, and stream performance.

### Project Skills And Device QA

- `.agents/skills/android-device-debugging/SKILL.md`: use for real-device connection, `scrcpy` mirroring, APK install, simulated UI operations, logs, frame stats, CPU, memory, and resource monitoring.
- For timeline or room-list performance work, verify with a real device or emulator screenshot/recording and, when relevant, `adb shell dumpsys gfxinfo network.unseal framestats`.

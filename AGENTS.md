# AGENTS.md — Element X Android

> **Repo:** `element-hq/element-x-android` — Android Matrix client (Compose UI + `matrix-rust-sdk`).

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
adb shell monkey -p io.element.android.x.debug -c android.intent.category.LAUNCHER 1
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
- Current Android SDK release: `network.unseal:agent-stream-android:0.1.0-rc.9`
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

More context and the current handoff are in `HANDOFF_AGENT_MANAGEMENT.md`.

---

## Unseal Android Handoff

Use `HANDOFF_AGENT_MANAGEMENT.md` as the single current handoff for Unseal-specific Android work. Older one-off specs and migration plans were intentionally removed because they had stale branch names, stale worktree paths, and contradictory guidance.

Project skill:

- `.agents/skills/android-device-debugging/SKILL.md`: use for real-device connection, `scrcpy` mirroring, APK install, simulated UI operations, logs, frame stats, CPU, memory, and resource monitoring.

Stable stream-render assets remain in the repo:

- Fixture manifest: `docs/agent-stream-fixtures/manifest.json`
- Fixture replay tool: `tools/agent-stream-parity/README.md`
- Android stream wrapper: `libraries/agentstream`

Android AI stream rendering must still consume `libraries/agentstream` through `AgentStreamClient`:

1. Matrix timeline event exposes `streamId`.
2. Room/timeline binding calls `AgentStreamClient.getStream(StreamRequest(...))`.
3. The binding subscribes to `StreamHandle` snapshots.
4. `AiSdkStreamReducer.mapSnapshot()` converts SDK `StreamSnapshot` to `TimelineItemAiContent`.
5. Compose renders `TimelineItemAiContent` only.

Do not fetch SSE, parse full stream JSON, or write stream store from Compose or messages UI code.

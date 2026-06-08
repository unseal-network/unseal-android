# Matrix Rust SDK Artifact Integration Design

Date: 2026-06-08

## Feature Boundary

Feature name: `matrix-rust-sdk-artifact-integration`

User-visible goal: Android builds can consume a Matrix Rust SDK Android AAR compiled from `/Users/Ruihan/go/src/matrix-rust-sdk`, so later Unseal security and verification features can depend on SDK changes before they are published to Maven.

Dependency class: Matrix Rust SDK dependent.

This feature is not a verification feature. It only defines and implements the artifact build and dependency-selection path for Android.

Out of scope:

- Implementing session verification behavior.
- Implementing secure backup behavior.
- Implementing room key recovery behavior.
- Publishing a new Maven version.
- Changing Matrix Rust SDK Rust code.
- Migrating any Unseal Agent, Skills, Vault, MiniApp, or Chatbot feature.

## Source References

### Matrix Rust SDK

- `/Users/Ruihan/go/src/matrix-rust-sdk/xtask/src/kotlin.rs`
  - `KotlinCommand::BuildAndroidLibrary`
  - `Package::FullSDK`
  - `build_android_library`
  - `build_for_android_target`
  - `generate_uniffi_bindings`
- `/Users/Ruihan/go/src/matrix-rust-sdk/.github/workflows/bindings_ci.yml`
  - `test-android`
  - `target/debug/xtask kotlin build-android-library --package full-sdk --only-target x86_64-linux-android --src-dir rust-components-kotlin/sdk/sdk-android/src/main`
  - `./gradlew :sdk:sdk-android:assembleDebug`

The Matrix Rust SDK build path uses `cargo ndk` to build `matrix-sdk-ffi` with the `rustls-tls,sentry` features for Android targets, then uses UniFFI to generate Kotlin bindings under a provided `sdk-android/src/main` directory.

### Unseal Android

- `/Users/Ruihan/go/src/unseal-android/libraries/rustsdk/build.gradle.kts`
  - exposes `libraries/rustsdk/matrix-rust-sdk.aar` as the default artifact.
- `/Users/Ruihan/go/src/unseal-android/libraries/rustsdk/.gitignore`
  - expected to keep generated AARs out of git.
- `/Users/Ruihan/go/src/unseal-android/libraries/matrix/impl/build.gradle.kts`
  - currently uses `libs.matrix.sdk` for release.
  - currently uses local `projects.libraries.rustsdk` for debug only when `libraries/rustsdk/matrix-rust-sdk.aar` exists.
- `/Users/Ruihan/go/src/unseal-android/gradle/libs.versions.toml`
  - current Maven SDK is `org.matrix.rustcomponents:sdk-android:26.06.3`.
- `/Users/Ruihan/go/src/unseal-android/settings.gradle.kts`
  - includes `libraries/rustsdk` through `includeProjects(File(rootDir, "libraries"), ":libraries")`.

## Android Existing State

Android already has a local AAR wrapper module at `:libraries:rustsdk`, and `:libraries:matrix:impl` can use that local AAR for debug builds when the file exists.

The current behavior is not enough for Unseal SDK-dependent work because:

- release builds always use the Maven SDK.
- local SDK selection is implicit for debug builds.
- there is no explicit Gradle switch to require the local SDK.
- a missing local AAR does not fail early when local SDK usage is intended.
- the build commands for producing and copying the AAR are not documented in the Android repo.

## Target Android Behavior

Android should support two dependency modes:

1. Default mode:
   - debug and release use `libs.matrix.sdk` unless the existing debug auto-detection path is intentionally preserved.
   - no developer is forced to build Matrix Rust SDK locally.

2. Explicit local SDK mode:
   - enabled with a Gradle property named `unseal.useLocalRustSdk=true`.
   - both debug and release use `projects.libraries.rustsdk`.
   - configuration fails early with a clear message if `libraries/rustsdk/matrix-rust-sdk.aar` is missing.

The explicit mode is the authoritative mode for validating Unseal Matrix Rust SDK changes. The default mode keeps regular Android builds working with Maven.

## Data And API Mapping

This feature has no app runtime data model.

The artifact mapping is:

- Matrix Rust SDK `full-sdk`
  - Rust package: `matrix-sdk-ffi`
  - Rust features: `rustls-tls,sentry`
  - Kotlin/AAR module: `matrix-rust-components-kotlin:sdk:sdk-android`
  - Android local artifact: `unseal-android/libraries/rustsdk/matrix-rust-sdk.aar`
  - Android wrapper project: `:libraries:rustsdk`
  - Android consumer: `:libraries:matrix:impl`

## Build Flow

The documented local build flow should be:

```bash
cd /Users/Ruihan/go/src/matrix-rust-sdk
rustup target add x86_64-linux-android aarch64-linux-android armv7-linux-androideabi i686-linux-android
cargo install cargo-ndk
cargo build -p xtask

cd /Users/Ruihan/go/src
git clone https://github.com/matrix-org/matrix-rust-components-kotlin

cd /Users/Ruihan/go/src/matrix-rust-sdk
target/debug/xtask kotlin build-android-library \
  --package full-sdk \
  --release \
  --src-dir /Users/Ruihan/go/src/matrix-rust-components-kotlin/sdk/sdk-android/src/main

cd /Users/Ruihan/go/src/matrix-rust-components-kotlin
./gradlew :sdk:sdk-android:assembleRelease

cp /Users/Ruihan/go/src/matrix-rust-components-kotlin/sdk/sdk-android/build/outputs/aar/sdk-android-release.aar \
  /Users/Ruihan/go/src/unseal-android/libraries/rustsdk/matrix-rust-sdk.aar
```

Validation with the local SDK should be:

```bash
cd /Users/Ruihan/go/src/unseal-android
./gradlew :libraries:matrix:impl:assembleDebug -Punseal.useLocalRustSdk=true
./gradlew :libraries:matrix:impl:assembleRelease -Punseal.useLocalRustSdk=true
```

Validation without the local SDK should be:

```bash
cd /Users/Ruihan/go/src/unseal-android
./gradlew :libraries:matrix:impl:assembleDebug
./gradlew :libraries:matrix:impl:assembleRelease
```

## Implementation Boundary For Subagent

Expected files to modify:

- `/Users/Ruihan/go/src/unseal-android/libraries/matrix/impl/build.gradle.kts`
- `/Users/Ruihan/go/src/unseal-android/docs/superpowers/specs/2026-06-08-matrix-rust-sdk-artifact-integration-design.md`

Expected files to create:

- `/Users/Ruihan/go/src/unseal-android/docs/matrix-rust-sdk-local-aar.md`

Files to avoid:

- Matrix Rust SDK Rust source files.
- Generated AAR binaries.
- Generated Kotlin bindings.
- Unrelated Android feature modules.
- `gradle/libs.versions.toml`, unless Maven SDK version publishing becomes the chosen strategy in a later spec.

Required public interface:

- Gradle property: `unseal.useLocalRustSdk`
- Local AAR path: `libraries/rustsdk/matrix-rust-sdk.aar`

Test target expectations:

- Gradle configuration must fail when `-Punseal.useLocalRustSdk=true` is passed and the local AAR is missing.
- Gradle configuration must continue to use Maven SDK when the property is absent.
- If the local AAR exists, debug and release variants of `:libraries:matrix:impl` must use `projects.libraries.rustsdk` when the property is true.

## Acceptance Checks

1. `docs/matrix-rust-sdk-local-aar.md` documents the build, copy, and validation commands.
2. `:libraries:matrix:impl` exposes a clear explicit local SDK mode through `-Punseal.useLocalRustSdk=true`.
3. Explicit local SDK mode fails early with a useful message when `libraries/rustsdk/matrix-rust-sdk.aar` is missing.
4. Default builds still use Maven SDK without requiring a local AAR.
5. No generated AAR or `.codegraph/` files are committed.
6. The implementation is small enough to review without touching runtime Matrix behavior.

## Follow-Up Specs

After this spec is implemented and verified, the next spec in order is `chatbot-api-service`.

Security specs that depend on Matrix Rust SDK can begin after this artifact integration path is available:

- `session-verification`
- `secure-backup-recovery`
- `encrypted-room-key-recovery`
- `agent-room-key-recovery`

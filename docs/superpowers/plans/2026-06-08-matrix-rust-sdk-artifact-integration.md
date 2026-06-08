# Matrix Rust SDK Artifact Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow `unseal-android` to explicitly consume a local Matrix Rust SDK AAR for both debug and release builds while preserving the default Maven SDK path.

**Architecture:** Keep the existing `:libraries:rustsdk` AAR wrapper module. Add a Gradle property gate in `:libraries:matrix:impl` so `-Punseal.useLocalRustSdk=true` selects the local project for all variants and fails early when the AAR is missing. Document the Matrix Rust SDK build, copy, and validation workflow in the Android repository.

**Tech Stack:** Gradle Kotlin DSL, Android Gradle Plugin, Kotlin, Matrix Rust SDK UniFFI Android AAR.

---

### Task 1: Add Explicit Local SDK Dependency Selection

**Files:**
- Modify: `libraries/matrix/impl/build.gradle.kts`

- [ ] **Step 1: Run the current explicit-local failure check**

Run:

```bash
cd /Users/Ruihan/go/src/unseal-android
./gradlew :libraries:matrix:impl:dependencies --configuration releaseRuntimeClasspath -Punseal.useLocalRustSdk=true
```

Expected before implementation: the command does not fail because `unseal.useLocalRustSdk` is currently ignored. This confirms the spec's missing explicit mode.

- [ ] **Step 2: Replace the Matrix SDK dependency selection block**

In `libraries/matrix/impl/build.gradle.kts`, replace this existing block:

```kotlin
    releaseImplementation(libs.matrix.sdk)
    if (file("${rootDir.path}/libraries/rustsdk/matrix-rust-sdk.aar").exists()) {
        println("\nNote: Using local binary of the Rust SDK.\n")
        debugImplementation(projects.libraries.rustsdk)
    } else {
        debugImplementation(libs.matrix.sdk)
    }
```

with this block:

```kotlin
    val localRustSdkAar = rootProject.layout.projectDirectory.file("libraries/rustsdk/matrix-rust-sdk.aar")
    val useLocalRustSdk = providers.gradleProperty("unseal.useLocalRustSdk")
        .map { rawValue ->
            rawValue.toBooleanStrictOrNull()
                ?: error("Gradle property unseal.useLocalRustSdk must be either 'true' or 'false'.")
        }
        .orElse(false)
        .get()

    if (useLocalRustSdk) {
        check(localRustSdkAar.asFile.exists()) {
            "Local Matrix Rust SDK AAR is required because -Punseal.useLocalRustSdk=true was set. " +
                "Build and copy it to ${localRustSdkAar.asFile.absolutePath}."
        }
        println("\nNote: Using explicit local binary of the Rust SDK for all variants.\n")
        implementation(projects.libraries.rustsdk)
    } else {
        releaseImplementation(libs.matrix.sdk)
        if (localRustSdkAar.asFile.exists()) {
            println("\nNote: Using local binary of the Rust SDK for debug builds.\n")
            debugImplementation(projects.libraries.rustsdk)
        } else {
            debugImplementation(libs.matrix.sdk)
        }
    }
```

- [ ] **Step 3: Run the explicit-local missing-AAR check**

Run:

```bash
cd /Users/Ruihan/go/src/unseal-android
./gradlew :libraries:matrix:impl:dependencies --configuration releaseRuntimeClasspath -Punseal.useLocalRustSdk=true
```

Expected after implementation when `libraries/rustsdk/matrix-rust-sdk.aar` is absent: FAIL during configuration with this message:

```text
Local Matrix Rust SDK AAR is required because -Punseal.useLocalRustSdk=true was set.
```

- [ ] **Step 4: Run the default release dependency check**

Run:

```bash
cd /Users/Ruihan/go/src/unseal-android
./gradlew :libraries:matrix:impl:dependencies --configuration releaseRuntimeClasspath
```

Expected: PASS and output includes:

```text
org.matrix.rustcomponents:sdk-android:26.06.3
```

- [ ] **Step 5: Commit the Gradle change**

Run:

```bash
cd /Users/Ruihan/go/src/unseal-android
git add libraries/matrix/impl/build.gradle.kts
git commit -m "build: add explicit local Matrix Rust SDK mode"
```

Expected: one commit containing only `libraries/matrix/impl/build.gradle.kts`.

### Task 2: Document The Local AAR Workflow

**Files:**
- Create: `docs/matrix-rust-sdk-local-aar.md`

- [ ] **Step 1: Create the local AAR documentation**

Create `docs/matrix-rust-sdk-local-aar.md` with this content:

````markdown
# Matrix Rust SDK Local AAR

This document explains how to build a Matrix Rust SDK Android AAR from `/Users/Ruihan/go/src/matrix-rust-sdk` and use it from `unseal-android`.

The default Android build uses the Maven artifact declared in `gradle/libs.versions.toml`:

```text
org.matrix.rustcomponents:sdk-android:26.06.3
```

Use the local AAR path when validating Matrix Rust SDK changes that have not been published to Maven yet.

## Build The SDK AAR

Install Android Rust targets and `cargo-ndk`:

```bash
cd /Users/Ruihan/go/src/matrix-rust-sdk
rustup target add x86_64-linux-android aarch64-linux-android armv7-linux-androideabi i686-linux-android
cargo install cargo-ndk
cargo build -p xtask
```

Prepare the Kotlin components checkout if it does not already exist:

```bash
cd /Users/Ruihan/go/src
git clone https://github.com/matrix-org/matrix-rust-components-kotlin
```

Generate the Android libraries and Kotlin bindings:

```bash
cd /Users/Ruihan/go/src/matrix-rust-sdk
target/debug/xtask kotlin build-android-library \
  --package full-sdk \
  --release \
  --src-dir /Users/Ruihan/go/src/matrix-rust-components-kotlin/sdk/sdk-android/src/main
```

Build the AAR:

```bash
cd /Users/Ruihan/go/src/matrix-rust-components-kotlin
./gradlew :sdk:sdk-android:assembleRelease
```

Copy the result into the Android wrapper module:

```bash
cp /Users/Ruihan/go/src/matrix-rust-components-kotlin/sdk/sdk-android/build/outputs/aar/sdk-android-release.aar \
  /Users/Ruihan/go/src/unseal-android/libraries/rustsdk/matrix-rust-sdk.aar
```

The copied AAR is ignored by git and must not be committed.

## Build Android With The Local AAR

Pass `-Punseal.useLocalRustSdk=true` to force all variants to use `:libraries:rustsdk`.

```bash
cd /Users/Ruihan/go/src/unseal-android
./gradlew :libraries:matrix:impl:assembleDebug -Punseal.useLocalRustSdk=true
./gradlew :libraries:matrix:impl:assembleRelease -Punseal.useLocalRustSdk=true
```

If `libraries/rustsdk/matrix-rust-sdk.aar` is missing, Gradle fails during configuration with a message that points to the expected file.

## Build Android With The Maven SDK

Omit `unseal.useLocalRustSdk` to use the Maven SDK.

```bash
cd /Users/Ruihan/go/src/unseal-android
./gradlew :libraries:matrix:impl:assembleDebug
./gradlew :libraries:matrix:impl:assembleRelease
```

## Dependency Selection

- `-Punseal.useLocalRustSdk=true`: debug and release use `:libraries:rustsdk`.
- property omitted or `-Punseal.useLocalRustSdk=false`: release uses the Maven SDK.
- if the local AAR exists and the property is omitted, debug preserves the existing local-AAR auto-detection behavior.

## Notes

- The Matrix Rust SDK `full-sdk` package builds Rust package `matrix-sdk-ffi`.
- The `full-sdk` build enables Rust features `rustls-tls,sentry`.
- The AAR wrapper module is `:libraries:rustsdk`.
- The Android consumer module is `:libraries:matrix:impl`.
````

- [ ] **Step 2: Check the documentation for placeholders**

Run:

```bash
cd /Users/Ruihan/go/src/unseal-android
rg -n "TBD|TODO|FIXME|placeholder|\\?\\?" docs/matrix-rust-sdk-local-aar.md
```

Expected: no matches and exit code `1`.

- [ ] **Step 3: Commit the documentation**

Run:

```bash
cd /Users/Ruihan/go/src/unseal-android
git add docs/matrix-rust-sdk-local-aar.md
git commit -m "docs: document local Matrix Rust SDK AAR"
```

Expected: one commit containing only `docs/matrix-rust-sdk-local-aar.md`.

### Task 3: Final Verification

**Files:**
- Verify: `libraries/matrix/impl/build.gradle.kts`
- Verify: `docs/matrix-rust-sdk-local-aar.md`
- Verify: `docs/superpowers/specs/2026-06-08-matrix-rust-sdk-artifact-integration-design.md`

- [ ] **Step 1: Verify explicit mode rejects a missing AAR**

Run:

```bash
cd /Users/Ruihan/go/src/unseal-android
./gradlew :libraries:matrix:impl:dependencies --configuration releaseRuntimeClasspath -Punseal.useLocalRustSdk=true
```

Expected when `libraries/rustsdk/matrix-rust-sdk.aar` is absent: FAIL with:

```text
Local Matrix Rust SDK AAR is required because -Punseal.useLocalRustSdk=true was set.
```

- [ ] **Step 2: Verify default release still uses Maven**

Run:

```bash
cd /Users/Ruihan/go/src/unseal-android
./gradlew :libraries:matrix:impl:dependencies --configuration releaseRuntimeClasspath
```

Expected: PASS and output includes:

```text
org.matrix.rustcomponents:sdk-android:26.06.3
```

- [ ] **Step 3: Verify default debug can configure**

Run:

```bash
cd /Users/Ruihan/go/src/unseal-android
./gradlew :libraries:matrix:impl:dependencies --configuration debugRuntimeClasspath
```

Expected: PASS. If `libraries/rustsdk/matrix-rust-sdk.aar` is absent, output includes:

```text
org.matrix.rustcomponents:sdk-android:26.06.3
```

- [ ] **Step 4: Verify the matrix implementation module builds**

Run:

```bash
cd /Users/Ruihan/go/src/unseal-android
./gradlew :libraries:matrix:impl:assembleDebug
```

Expected: PASS.

- [ ] **Step 5: Verify committed scope**

Run:

```bash
cd /Users/Ruihan/go/src/unseal-android
git status --short
```

Expected: no tracked-file changes. `.codegraph/` may remain untracked and must not be committed.

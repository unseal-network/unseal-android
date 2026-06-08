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

# Android Home/Onboarding Handoff

## Goal

Align the Android first screen and logged-out onboarding flow with Unseal iOS:

- first visible logged-out screen should look like iOS `AuthenticationStartScreen`
- use Unseal launch background in light and dark mode
- use circular Unseal app logo
- use iOS welcome copy
- preserve existing login actions: QR login, manual login, create account, version/report-problem flow
- keep auth/session behavior unchanged except fixing the splash-to-logged-out initial state

## Current Status

Implemented:

- Android onboarding screen was replaced with an Unseal-specific start screen.
- Light/dark launch background assets were copied from iOS.
- Circular Unseal logo was added.
- Welcome title now matches iOS: `Be in your Unseal`.
- Preview app name was changed from `Element` to `Unseal`.
- Root nav logged-in state flow now emits an initial logged-in/logged-out value so cold start does not remain stuck on splash.

Not finished:

- Full APK with these changes has not yet been successfully rebuilt and visually verified.
- Emulator screenshots for light/dark mode have not yet been captured after the root-nav fix.

## Files Changed For This Task

- `features/login/impl/src/main/kotlin/io/element/android/features/login/impl/screens/onboarding/OnBoardingView.kt`
  - Main implementation for the iOS-aligned Unseal onboarding screen.
  - Replaces generic Element onboarding page with full-screen launch background, centered circular logo, welcome copy, login/create-account buttons, and bottom version text.

- `features/login/impl/src/main/kotlin/io/element/android/features/login/impl/screens/onboarding/OnBoardingStateProvider.kt`
  - Preview default app name changed to `Unseal`.

- `features/login/impl/src/main/res/values/localazy.xml`
  - `screen_onboarding_welcome_title` changed to `Be in your Unseal`.

- `features/login/impl/src/main/res/drawable-nodpi/unseal_launch_background.png`
  - iOS light launch background.

- `features/login/impl/src/main/res/drawable-night-nodpi/unseal_launch_background.png`
  - iOS dark launch background.

- `features/login/impl/src/main/res/drawable-nodpi/unseal_app_logo.png`
  - Unseal app icon used by the onboarding logo.

- `appnav/src/main/kotlin/io/element/android/appnav/root/RootNavStateFlowFactory.kt`
  - Adds initial emission from `sessionStore.getLatestSession()` before collecting `loggedInStateFlow()`.
  - This was added because an installed APK stayed on a blank white splash screen with nav target still at `SplashScreen`.

## iOS References

Use these as the behavior/UI source of truth:

- `unseal-ios/ElementX/Sources/Screens/Authentication/StartScreen/View/AuthenticationStartScreen.swift`
- `unseal-ios/ElementX/Sources/Screens/Authentication/StartScreen/View/AuthenticationStartLogo.swift`
- `unseal-ios/ElementX/Sources/Screens/Authentication/StartScreen/View/AuthenticationStartScreenBackgroundImage.swift`
- iOS strings:
  - `screen_onboarding_welcome_title = "Be in your Unseal"`
  - `screen_onboarding_welcome_message = "Welcome to the fastest %1$@ ever. Supercharged for speed and simplicity."`

Splash animation was inspected but not ported yet:

- `unseal-ios/ElementX/Sources/Screens/Splash/UnsealSplashAnimationView.swift`
- `unseal-ios/ElementX/Sources/Screens/Splash/UnsealLogoPathProvider.swift`

## Verification Already Done

Passed:

```sh
ANDROID_HOME="$ANDROID_HOME" JAVA_HOME="$JAVA_HOME" \
./gradlew --no-daemon --no-configuration-cache :features:login:impl:compileDebugKotlin
```

Passed:

```sh
ANDROID_HOME="$ANDROID_HOME" JAVA_HOME="$JAVA_HOME" \
./gradlew --no-daemon --no-configuration-cache :appnav:compileDebugKotlin
```

Passed after cleaning stale module outputs:

```sh
ANDROID_HOME="$ANDROID_HOME" JAVA_HOME="$JAVA_HOME" \
./gradlew --no-daemon --no-configuration-cache \
  -Dkotlin.compiler.execution.strategy=in-process \
  -Dorg.gradle.workers.max=1 \
  :features:location:api:clean \
  :features:location:impl:clean \
  :features:location:impl:compileDebugKotlin
```

## Current Build Problem

Full `:app:assembleGplayDebug` failed after a long run with compile errors in:

- `:features:location:impl:compileDebugKotlin`
- `:features:linknewdevice:impl:compileDebugKotlin`

The `location` errors looked like missing classpath/unresolved references across many existing imports. After cleaning `features:location:api` and `features:location:impl`, `location` compiled successfully. That points to stale/corrupted Gradle/Kotlin build outputs, not the onboarding code.

There were multiple concurrent Gradle builds in the same worktree, including a separate Claude-triggered `:app:assembleDebug`. Those concurrent builds likely caused the Kotlin/KSP cache/output problems.

Before continuing, make sure only one Gradle build is active.

Check:

```sh
ps -axo pid,ppid,stat,pcpu,pmem,command | rg 'gradlew|GradleDaemon|KotlinCompileDaemon|:app:assemble'
```

If stale Gradle/Kotlin processes are still alive, stop them before rebuilding.

## Recommended Next Steps

1. Finish cleaning and compiling `linknewdevice` alone:

```sh
ANDROID_HOME="$ANDROID_HOME" JAVA_HOME="$JAVA_HOME" \
./gradlew --no-daemon --no-configuration-cache \
  -Dkotlin.compiler.execution.strategy=in-process \
  -Dorg.gradle.workers.max=1 \
  :features:linknewdevice:api:clean \
  :features:linknewdevice:impl:clean \
  :features:linknewdevice:impl:compileDebugKotlin
```

2. Rebuild the APK with a single Gradle process:

```sh
ANDROID_HOME="$ANDROID_HOME" JAVA_HOME="$JAVA_HOME" \
./gradlew --no-daemon --no-configuration-cache \
  -Dkotlin.compiler.execution.strategy=in-process \
  -Dorg.gradle.workers.max=1 \
  :app:assembleGplayDebug
```

3. Install and clear state:

```sh
adb install -r -d app/build/outputs/apk/gplay/debug/app-gplay-universal-debug.apk
adb shell pm clear network.unseal.android.debug
adb shell am start -n network.unseal.android.debug/io.element.android.x.MainActivity
```

4. Verify first screen:

- It should not stay on a blank white splash screen.
- It should show the Unseal launch background.
- It should show the circular Unseal logo.
- It should show `Be in your Unseal`.
- Buttons should still route to QR login, manual login, and create account.
- Version text should still be visible at the bottom.

5. Verify dark mode:

```sh
adb shell cmd uimode night yes
adb shell am force-stop network.unseal.android.debug
adb shell am start -n network.unseal.android.debug/io.element.android.x.MainActivity
```

After verification, restore light mode if desired:

```sh
adb shell cmd uimode night no
```

## Important Notes

- Use JDK 21. Do not downgrade to JDK 11.
- Do not run multiple Gradle builds in this same worktree at the same time.
- Do not revert unrelated files. This worktree already contains many unrelated/subagent changes, especially under `features/messages`, `features/preferences`, `features/connectors`, `features/voicelibrary`, and `libraries/chatbot`.
- The onboarding implementation should stay strictly aligned with the iOS files listed above. This is a migration, not a new design.

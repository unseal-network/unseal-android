# Credits Final Verification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Verify the migrated Android Credits dashboard and Preferences entry compile, test, assemble, and do not depend on excluded component libraries.

**Architecture:** This task does not add new product behavior. It proves the already-migrated Credits and Preferences features are buildable as a runnable app, then records completion in the parent migration plan.

**Tech Stack:** Gradle, Android debug unit tests, ripgrep, git.

---

### Task 1: Run Credits And Preferences Verification

**Files:**
- Inspect: `features/credits`
- Inspect: `features/preferences/impl/src/main/kotlin/io/element/android/features/preferences/impl`
- Modify: `docs/superpowers/plans/2026-06-09-credits-dashboard.md`

- [x] **Step 1: Run focused Credits tests**

Run:

```bash
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:credits:impl:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 2: Run compile checks**

Run:

```bash
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :features:credits:impl:compileDebugKotlin :features:preferences:impl:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 3: Run app assemble**

Run:

```bash
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools ./gradlew --no-daemon --no-configuration-cache :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 4: Run forbidden dependency scan**

Run:

```bash
rg -n "libraries\\.rustsdk|org\\.matrix\\.rust|voiceplayer|voicerecorder|UnsealUI|UnsealAgent|UnsealMiniApp|stripe|Stripe" features/credits features/preferences/impl/src/main/kotlin/io/element/android/features/preferences/impl
```

Expected: no output.

- [x] **Step 5: Update verification checkboxes**

In `docs/superpowers/plans/2026-06-09-credits-dashboard.md`, mark Task 6 Step 1 through Step 5 complete after the commands above have passed. In this plan file, mark this task's completed steps as `[x]`.

- [x] **Step 6: Check diff and commit docs if needed**

Run:

```bash
git diff --check
git status --short
```

Expected: no whitespace errors. If only plan files changed, commit:

```bash
git add docs/superpowers/plans/2026-06-09-credits-dashboard.md docs/superpowers/plans/2026-06-09-credits-final-verification.md
git commit -m "docs: verify credits dashboard migration"
```

If no files changed after verification, do not create an empty commit.

---

## Self-Review

- Spec coverage: Covers focused Credits tests, Credits/Preferences compile checks, full app assemble, forbidden dependency scan, and parent plan completion.
- Placeholder scan: No TBD/TODO/fill-in instructions remain.
- Type consistency: Commands match Task 6 in `2026-06-09-credits-dashboard.md`.

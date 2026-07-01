# Website APK Release Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a GitHub Actions release flow that publishes a signed universal APK to GitHub Releases for the official website to download.

**Architecture:** Create a dedicated manual workflow that builds only `assembleGplayRelease`, prepares the universal APK and checksums, then creates a GitHub Release. Keep this separate from the Google Play workflow so website APK publishing does not upload an AAB or touch Play Console.

**Tech Stack:** GitHub Actions, Gradle Android build, GitHub CLI, shell.

---

### Task 1: Add Website APK Release Workflow

**Files:**
- Create: `.github/workflows/website-apk-release.yml`
- Modify: `plugins/src/main/kotlin/Versions.kt`

- [x] **Step 1: Create a manual workflow**

Create `.github/workflows/website-apk-release.yml` with `workflow_dispatch` inputs for `tag_name`, `release_name`, and `prerelease`.

- [x] **Step 2: Build only signed APKs**

Use the same upload keystore secrets as the Play release workflow, but run only:

```bash
./gradlew assembleGplayRelease $CI_GRADLE_ARG_PROPERTIES
```

- [x] **Step 3: Prepare GitHub Release assets**

Copy the universal APK to `release/unseal-android-<version>-gplay-universal-release.apk`, generate `SHA256SUMS.txt`, and fail if the universal APK is missing.

- [x] **Step 4: Create the GitHub Release**

Use `gh release create` with the prepared APK and checksum files.

- [x] **Step 5: Set version to 26.06.27**

Update `versionReleaseNumber` to `27`, making `Versions.VERSION_NAME` equal `26.06.27`.

### Task 2: Document Website Consumption

**Files:**
- Create: `docs/website-apk-release-ci.md`

- [x] **Step 1: Document workflow inputs and required secrets**

List the existing signing secrets and the manual trigger inputs.

- [x] **Step 2: Document website download API**

Tell the website to read:

```text
https://api.github.com/repos/<owner>/<repo>/releases/latest
```

and select the asset ending in `gplay-universal-release.apk`.

### Task 3: Verify

**Files:**
- Check: `.github/workflows/website-apk-release.yml`
- Check: `docs/website-apk-release-ci.md`
- Check: `plugins/src/main/kotlin/Versions.kt`

- [x] **Step 1: Validate YAML parses**

Run a local YAML parser over the new workflow file.

- [x] **Step 2: Confirm version string**

Verify `versionReleaseNumber = 27` produces `26.06.27`.

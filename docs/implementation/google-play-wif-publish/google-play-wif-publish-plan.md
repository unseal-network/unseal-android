# Google Play Workload Identity Publish Plan

## Executive Summary

Build a manual GitHub Actions release pipeline that takes the signed GPlay AAB from an existing GitHub Release, authenticates to Google Cloud through Workload Identity Federation, and uploads that AAB to Google Play.

## Current State

- `.github/workflows/play-release.yml` builds signed GPlay AAB/APK artifacts and creates a GitHub Release.
- `docs/android-play-release-ci.md` describes a separate Google Play publish step, but the matching workflow is missing.
- App signing secrets are already consumed through GitHub Secrets and Gradle environment variables.
- The repo has Fastlane Play metadata, including version-code-specific changelog files.

## Future State

- A workflow dispatcher chooses the Play track and release status.
- The publish workflow downloads the signed `*play-store.aab` asset from the requested GitHub Release.
- GitHub OIDC authenticates to Google Cloud through Workload Identity Federation.
- The workflow mints a short-lived Android Publisher access token and never stores a Google JSON key.
- A small standard-library Python script calls the Android Publisher API to create an edit, upload the signed AAB, update the selected track, and commit safely.
- Setup and runbook details are documented for operators.

## Phases

### Phase 1 - Release Workflow Wiring

Observable outcome: `.github/workflows/publish-google-play.yml` has inputs for tag/track/status, requests `id-token: write`, validates WIF secrets, downloads the release AAB, authenticates with `google-github-actions/auth`, and calls the publisher script.

### Phase 2 - Publisher Script

Observable outcome: `tools/release/publish_google_play.py` can dry-run a track release body locally and, in CI, publish through Android Publisher API using `GOOGLE_PLAY_ACCESS_TOKEN`.

### Phase 3 - Operator Documentation

Observable outcome: repo docs list required secrets, Google Cloud/Play Console setup, workflow inputs, and review-safety behavior.

### Phase 4 - Validation

Observable outcome: workflow YAML parses, the publisher dry-run returns the expected release payload, and docs are accepted by the markdown checker.

## Risks

- Google Play publication cannot be tested end-to-end locally without real Play Console permissions.
- Service-account Play Console permissions are separate from Google Cloud IAM; both must be configured.
- Release notes are optional in the API call, but an over-500-character matching changelog file must fail before upload commit.
- If there are already Play changes in review, the workflow intentionally fails instead of canceling that review.

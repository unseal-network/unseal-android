# Google Play Workload Identity Publish Context

## Key Files

- `.github/workflows/play-release.yml`: manual release workflow that builds signed GPlay artifacts.
- `.github/workflows/publish-google-play.yml`: manual publish workflow that downloads a signed release AAB and uploads it to Google Play.
- `app/build.gradle.kts`: release signing reads `ANDROID_UPLOAD_KEYSTORE_PATH`, `ANDROID_UPLOAD_KEY_ALIAS`, `ANDROID_UPLOAD_KEYSTORE_PASSWORD`, and `ANDROID_UPLOAD_KEY_PASSWORD`.
- `plugins/src/main/kotlin/config/BuildTimeConfig.kt`: release package name is `network.unseal.android`.
- `fastlane/metadata/android/en-US/changelogs/`: Play release notes keyed by version code.
- `tools/release/publish_google_play.py`: Android Publisher API helper added for CI.
- `docs/android-play-release-ci.md`: operator setup and runbook.

## Decisions Made

- Keep `play-release.yml` focused on signed artifact/GitHub Release creation and add a second workflow for Play publication.
- Use Workload Identity Federation through `google-github-actions/auth` with `token_format: access_token`.
- Request only `https://www.googleapis.com/auth/androidpublisher` for the short-lived access token.
- Call the Android Publisher API directly from a small Python script to avoid adding a Gradle publishing plugin or third-party CLI.
- Use `changesInReviewBehavior=ERROR_IF_IN_REVIEW` by default so CI does not cancel existing Play reviews.
- Publish from the GitHub Release AAB so Play publication can be retried independently from the expensive signed Android build.

## Dependencies

- GitHub Actions OIDC permission: `id-token: write`.
- Google Cloud Workload Identity Pool and provider for the repository.
- Service account with `roles/iam.workloadIdentityUser` binding for the GitHub repository principal.
- Service account with `roles/iam.serviceAccountTokenCreator` on itself so the auth action can generate an access token.
- Google Play Console API access for the same service account on the `network.unseal.android` app.
- Existing Android upload signing secrets in GitHub.

## Surprises

- Refreshed docs already described a two-step process, so the missing piece was a dedicated publish workflow rather than changing the signed release workflow.
- Android Publisher commit supports `changesInReviewBehavior=ERROR_IF_IN_REVIEW`; using it avoids the default behavior of canceling an active review.

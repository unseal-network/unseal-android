# Android Play release CI

<!--- TOC -->

* [Required GitHub secrets](#required-github-secrets)
* [Tag release flow](#tag-release-flow)
* [Manual Google Play publish](#manual-google-play-publish)

<!--- END -->

Android tag releases are automated through `.github/workflows/play-release.yml`:

1. Build signed Android artifacts.
2. Create the GitHub Release.
3. Upload the signed AAB to Google Play `internal` as a completed release.

`.github/workflows/publish-google-play.yml` remains available for manually re-publishing an existing GitHub Release when needed.

## Required GitHub secrets

Configure these repository secrets before creating the GitHub release:

- `ANDROID_UPLOAD_KEYSTORE_BASE64`: base64 content of the Play upload keystore file.
- `ANDROID_UPLOAD_KEY_ALIAS`: upload key alias.
- `ANDROID_UPLOAD_KEYSTORE_PASSWORD`: keystore password.
- `ANDROID_UPLOAD_KEY_PASSWORD`: key password.

Configure these repository secrets before publishing to Google Play:

- `GOOGLE_WORKLOAD_IDENTITY_PROVIDER`: full Workload Identity Provider resource name, for example `projects/123456789/locations/global/workloadIdentityPools/github/providers/unseal-android`.
- `GOOGLE_PLAY_SERVICE_ACCOUNT_EMAIL`: Google Cloud service account email used for Google Play publishing.

The Google Cloud service account must have access to the `network.unseal` app in Play Console. The GitHub repository must be allowed to impersonate that service account through Workload Identity Federation.

The workflow also passes GitHub Actions' default `GITHUB_TOKEN` and `GITHUB_ACTOR` to Gradle so private GitHub Packages dependencies can be resolved. The repository still needs package read access to `unseal-network/agent-stream-components-kotlin`.

## Tag release flow

Push a release tag:

```bash
git tag v26.07.10
git push origin v26.07.10
```

The tag push runs `Build signed Play release`. The workflow builds:

- `bundleGplayRelease`: signed AAB for Google Play.
- `assembleGplayRelease`: signed APKs for direct install and smoke testing.

After a successful run, GitHub creates a Release for the tag and uploads the AAB, APKs, and `SHA256SUMS.txt`. The same workflow then publishes the signed AAB to Google Play for package `network.unseal`:

- track: `internal`
- status: `completed`
- changes in review behavior: `ERROR_IF_IN_REVIEW`

Android `versionName` and `versionCode` still come from `plugins/src/main/kotlin/Versions.kt`; update that file before pushing a tag when publishing a new Play upgrade version.

Manual workflow dispatch for `Build signed Play release` is still supported for creating GitHub Release artifacts without automatically publishing to Google Play:

- `tag_name`: release tag, for example `v26.06.26`.
- `release_name`: optional display name. If empty, the tag is used.
- `prerelease`: keep enabled for internal testing builds.

## Manual Google Play publish

Open GitHub Actions, choose `Publish Google Play release`, then run it manually only when re-publishing an existing GitHub Release or publishing to a different track/status:

- `tag_name`: the GitHub Release tag created in step 1.
- `track`: normally `internal` for internal testing.
- `status`: normally `draft` so the Play Console release can be reviewed before rollout.
- `user_fraction`: optional staged rollout fraction. Use only with `inProgress`.
- `changes_not_sent_for_review`: commit the Play edit without sending the changes for review.

The workflow downloads the `*play-store.aab` asset from the GitHub Release and uploads it to the selected Google Play track for package `network.unseal`.

The publish step uses `changesInReviewBehavior=ERROR_IF_IN_REVIEW`, so it fails instead of canceling an existing Play review.

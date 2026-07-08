# Android Play release CI

<!--- TOC -->

* [Required GitHub secrets](#required-github-secrets)
* [Step 1: Create the GitHub release](#step-1-create-the-github-release)
* [Step 2: Publish the Google Play release](#step-2-publish-the-google-play-release)

<!--- END -->

Android releases are split into two explicit CI steps:

1. `.github/workflows/play-release.yml` builds signed Android artifacts and creates the GitHub Release.
2. `.github/workflows/publish-google-play.yml` downloads the AAB from that GitHub Release and uploads it to Google Play.

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

## Step 1: Create the GitHub release

Open GitHub Actions, choose `Build signed Play release`, then run it manually with:

- `tag_name`: release tag, for example `v26.06.26`.
- `release_name`: optional display name. If empty, the tag is used.
- `prerelease`: keep enabled for internal testing builds.

The workflow builds:

- `bundleGplayRelease`: signed AAB for Google Play Console upload.
- `assembleGplayRelease`: signed APKs for direct install and smoke testing.

After a successful run, GitHub creates a Release for the tag and uploads the AAB, APKs, and `SHA256SUMS.txt`.

Android `versionName` and `versionCode` still come from `plugins/src/main/kotlin/Versions.kt`; update that file before running the workflow when publishing a new Play upgrade version.

## Step 2: Publish the Google Play release

Open GitHub Actions, choose `Publish Google Play release`, then run it manually with:

- `tag_name`: the GitHub Release tag created in step 1.
- `track`: normally `internal` for internal testing.
- `status`: normally `draft` so the Play Console release can be reviewed before rollout.
- `user_fraction`: optional staged rollout fraction. Use only with `inProgress`.
- `changes_not_sent_for_review`: commit the Play edit without sending the changes for review.

The workflow downloads the `*play-store.aab` asset from the GitHub Release and uploads it to the selected Google Play track for package `network.unseal`.

The publish step uses `changesInReviewBehavior=ERROR_IF_IN_REVIEW`, so it fails instead of canceling an existing Play review.

# Android Play release CI

<!--- TOC -->

* [Required GitHub secrets](#required-github-secrets)
* [Run](#run)

<!--- END -->

Use `.github/workflows/play-release.yml` to build a signed GPlay release in GitHub Actions and attach the output to a GitHub Release.

## Required GitHub secrets

Configure these repository secrets before running the workflow:

- `ANDROID_UPLOAD_KEYSTORE_BASE64`: base64 content of the Play upload keystore file.
- `ANDROID_UPLOAD_KEY_ALIAS`: upload key alias.
- `ANDROID_UPLOAD_KEYSTORE_PASSWORD`: keystore password.
- `ANDROID_UPLOAD_KEY_PASSWORD`: key password.

The workflow also passes GitHub Actions' default `GITHUB_TOKEN` and `GITHUB_ACTOR` to Gradle so private GitHub Packages dependencies can be resolved. The repository still needs package read access to `unseal-network/agent-stream-components-kotlin`.

## Run

Open GitHub Actions, choose `Build signed Play release`, then run it manually with:

- `tag_name`: release tag, for example `v26.06.1`.
- `release_name`: optional display name. If empty, the tag is used.
- `prerelease`: keep enabled for internal testing builds.

The workflow builds:

- `bundleGplayRelease`: signed AAB for Google Play Console upload.
- `assembleGplayRelease`: signed APKs for direct install and smoke testing.

After a successful run, GitHub creates a Release for the tag and uploads the AAB, APKs, and `SHA256SUMS.txt`.

Android `versionName` and `versionCode` still come from `plugins/src/main/kotlin/Versions.kt`; update that file before running the workflow when publishing a new Play upgrade version.

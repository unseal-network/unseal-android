# Google Play Workload Identity Publish Tasks

## Phase 1 - Release Workflow Wiring

- ✅ Add a dedicated publish workflow with inputs for release tag, Play track, release status, staged rollout fraction, and review submission behavior.
- ✅ Add `id-token: write` permission for GitHub OIDC.
- ✅ Validate WIF secrets, release tag, and staged rollout inputs before publishing.
- ✅ Authenticate with `google-github-actions/auth` and request an Android Publisher access token.
- ✅ Download the signed AAB from the GitHub Release and invoke the Play publisher.

## Phase 2 - Publisher Script

- ✅ Add an Android Publisher API helper under `tools/release`.
- ✅ Create an edit, upload the AAB, update the track, and commit the edit.
- ✅ Read optional Play release notes from Fastlane changelog metadata.
- ✅ Fail clearly for invalid staged rollout fractions or oversized Play release notes.
- ✅ Support a local dry-run mode for request body validation.

## Phase 3 - Operator Documentation

- ✅ Document required GitHub secrets.
- ✅ Document Google Cloud Workload Identity Federation and Play Console prerequisites.
- ✅ Document workflow dispatch inputs and default review-safety behavior.

## Phase 4 - Validation

- ✅ Run workflow YAML parse validation.
- ✅ Run publisher dry-run validation.
- ✅ Run markdown docs check.

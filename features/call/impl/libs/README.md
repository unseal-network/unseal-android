# Embedded Unseal Call

`unseal-call-embedded.aar` packages the Unseal Call web client into
`assets/element-call`, so Android meeting and audience routes do not depend on a
deployed frontend.

- Source repository: `unseal-call-audience-web`
- Source revision: `635841bb5fe63e7f1b85398ebd124bcdee3e4c1c+audience-lease-recovery-local` (reviewed local candidate)
- SHA-256: `59a6b75e4c1cbd867c7151c3416e2865659c5e8a112b86b2b79a7992e698398a`

Rebuild it from that source revision with:

```sh
UNSEAL_CALL_SHA=635841bb5fe63e7f1b85398ebd124bcdee3e4c1c+audience-lease-recovery-local corepack yarn build:embedded:production
mkdir -p embedded/android/lib/src/main/assets/element-call
rsync -a --delete dist/ embedded/android/lib/src/main/assets/element-call/
ANDROID_HOME=/path/to/android-sdk embedded/android/gradlew \
  -p embedded/android --no-daemon :lib:assembleRelease
```

Then replace this AAR with
`embedded/android/lib/build/outputs/aar/lib-release.aar` and update the source
commit and checksum above.

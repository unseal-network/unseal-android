# Embedded Unseal Call

`unseal-call-embedded.aar` packages the Unseal Call web client into
`assets/element-call`, so Android meeting and audience routes do not depend on a
deployed frontend.

- Source repository: `unseal-call-audience-web`
- Source commit: `635841bb5fe63e7f1b85398ebd124bcdee3e4c1c`
- SHA-256: `e361a89e944d2ea4d4457542d357a54ae5f77415803f2611f0e8fb9799508de7`

Rebuild it from that source revision with:

```sh
UNSEAL_CALL_SHA=635841bb5fe63e7f1b85398ebd124bcdee3e4c1c corepack yarn build:embedded:production
mkdir -p embedded/android/lib/src/main/assets/element-call
rsync -a --delete dist/ embedded/android/lib/src/main/assets/element-call/
ANDROID_HOME=/path/to/android-sdk embedded/android/gradlew \
  -p embedded/android --no-daemon :lib:assembleRelease
```

Then replace this AAR with
`embedded/android/lib/build/outputs/aar/lib-release.aar` and update the source
commit and checksum above.

# Embedded Unseal Call

`unseal-call-embedded.aar` packages the Unseal Call web client into
`assets/element-call`, so Android meeting and audience routes do not depend on a
deployed frontend.

- Source repository: `unseal-call-audience-web`
- Source revision: `30b97b9f11cae6636e02528ee80b8870088dc4ae`
- SHA-256: `face5f31d4de0e0e48f6b3cca6b03b4769dfb1e23949583379a44065b2b5d45d`

Rebuild it from that source revision with:

```sh
UNSEAL_CALL_SHA=30b97b9f11cae6636e02528ee80b8870088dc4ae corepack yarn build:embedded:production
mkdir -p embedded/android/lib/src/main/assets/element-call
rsync -a --delete dist/ embedded/android/lib/src/main/assets/element-call/
ANDROID_HOME=/path/to/android-sdk embedded/android/gradlew \
  -p embedded/android --no-daemon :lib:assembleRelease
```

Then replace this AAR with
`embedded/android/lib/build/outputs/aar/lib-release.aar` and update the source
commit and checksum above.

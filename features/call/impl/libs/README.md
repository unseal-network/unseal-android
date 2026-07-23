# Embedded Unseal Call

`unseal-call-embedded.aar` packages the Unseal Call web client into
`assets/element-call`, so Android meeting and audience routes do not depend on a
deployed frontend.

- Source repository: `unseal-call-audience-web`
- Source revision: `018b8222caf8425095f36971fd0cadd018cf5e89`
- SHA-256: `d06d369d110a733863c241f5cd512b376fa6a664fbd73ae41154dda0ce9a4ad2`

Rebuild it from that source revision with:

```sh
UNSEAL_CALL_SHA=018b8222caf8425095f36971fd0cadd018cf5e89 corepack yarn build:embedded:production
mkdir -p embedded/android/lib/src/main/assets/element-call
rsync -a --delete dist/ embedded/android/lib/src/main/assets/element-call/
ANDROID_HOME=/path/to/android-sdk embedded/android/gradlew \
  -p embedded/android --no-daemon :lib:assembleRelease
```

Then replace this AAR with
`embedded/android/lib/build/outputs/aar/lib-release.aar` and update the source
commit and checksum above.

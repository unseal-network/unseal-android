# Embedded Unseal Call

`unseal-call-embedded.aar` packages the Unseal Call web client into
`assets/element-call`, so Android meeting and audience routes do not depend on a
deployed frontend.

- Source repository: `unseal-call-audience-web`
- Source revision: `6a695476c76be31b1b49d3272726d477303fa967`
- SHA-256: `a28c026501602f281a321273746e601f48229f6c3697a991db7b50a710ecdc7b`

Rebuild it from that source revision with:

```sh
UNSEAL_CALL_SHA=6a695476c76be31b1b49d3272726d477303fa967 corepack yarn build:embedded:production
mkdir -p embedded/android/lib/src/main/assets/element-call
rsync -a --delete dist/ embedded/android/lib/src/main/assets/element-call/
ANDROID_HOME=/path/to/android-sdk embedded/android/gradlew \
  -p embedded/android --no-daemon :lib:assembleRelease
```

Then replace this AAR with
`embedded/android/lib/build/outputs/aar/lib-release.aar` and update the source
commit and checksum above.

# Embedded Unseal Call

`unseal-call-embedded.aar` packages the Unseal Call web client into
`assets/element-call`, so Android meeting and audience routes do not depend on a
deployed frontend.

- Source repository: `unseal-call-audience-web`
- Source release: `v0.17.17-2-gead6cd2c`
- Source revision: `ead6cd2c00457671130140ed5b1df7f6fcf6a21d`
- SHA-256: `36728e21496a8b40a39a0e4c4cb06135ee3f3ee9e292376ccbd598394a68abb9`

Rebuild it from that source revision with:

```sh
UNSEAL_CALL_SHA=ead6cd2c00457671130140ed5b1df7f6fcf6a21d corepack yarn build:embedded:production
mkdir -p embedded/android/lib/src/main/assets/element-call
rsync -a --delete dist/ embedded/android/lib/src/main/assets/element-call/
ANDROID_HOME=/path/to/android-sdk embedded/android/gradlew \
  -p embedded/android --no-daemon :lib:assembleRelease
```

Then replace this AAR with
`embedded/android/lib/build/outputs/aar/lib-release.aar` and update the source
commit and checksum above.

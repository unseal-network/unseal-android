# Embedded Unseal Call

`unseal-call-embedded.aar` packages the Unseal Call web client into
`assets/element-call`, so Android meeting and audience routes do not depend on a
deployed frontend.

- Source repository: `unseal-call-audience-web`
- Source release: `v0.17.17`
- Source revision: `f29e0a5fa202bfcaad61ad26c1bf5088abefacc8`
- SHA-256: `d560f8910d221a46b760d3d5e5ba941b23a6530aece09d14183a126ed4082c09`

Rebuild it from that source revision with:

```sh
UNSEAL_CALL_SHA=f29e0a5fa202bfcaad61ad26c1bf5088abefacc8 corepack yarn build:embedded:production
mkdir -p embedded/android/lib/src/main/assets/element-call
rsync -a --delete dist/ embedded/android/lib/src/main/assets/element-call/
ANDROID_HOME=/path/to/android-sdk embedded/android/gradlew \
  -p embedded/android --no-daemon :lib:assembleRelease
```

Then replace this AAR with
`embedded/android/lib/build/outputs/aar/lib-release.aar` and update the source
commit and checksum above.

# Embedded Unseal Call

`unseal-call-embedded.aar` packages the Unseal Call web client into
`assets/element-call`, so Android meeting and audience routes do not depend on a
deployed frontend.

- Source repository: `unseal-call` (`v0.17.26`)
- Source revision: `b077f9f31df197e55af59702c5a1b1480872415d`
- SHA-256: `e5f67a22f0d3a921027b52fbe6029d6704a220bfc9198dfc25e9207ebb836a90`

Rebuild it from that source revision with:

```sh
UNSEAL_CALL_SHA=b077f9f31df197e55af59702c5a1b1480872415d corepack yarn build:embedded:production
mkdir -p embedded/android/lib/src/main/assets/element-call
rsync -a --delete dist/ embedded/android/lib/src/main/assets/element-call/
ANDROID_HOME=/path/to/android-sdk embedded/android/gradlew \
  -p embedded/android --no-daemon :lib:assembleRelease
```

Then replace this AAR with
`embedded/android/lib/build/outputs/aar/lib-release.aar` and update the source
commit and checksum above.

# Embedded Unseal Call

`unseal-call-embedded.aar` packages the Unseal Call web client into
`assets/element-call`, so Android meeting and audience routes do not depend on a
deployed frontend.

- Source repository: `unseal-call-audience-web`
- Source revision: `c4cbcc478685b1425a3b481e3a70049cb2fb71ff`
- SHA-256: `b1730ad0f35320c1eeced68028f4e9af69a2e00a2c8f3628403eb673cd8f90d8`

Rebuild it from that source revision with:

```sh
UNSEAL_CALL_SHA=c4cbcc478685b1425a3b481e3a70049cb2fb71ff corepack yarn build:embedded:production
mkdir -p embedded/android/lib/src/main/assets/element-call
rsync -a --delete dist/ embedded/android/lib/src/main/assets/element-call/
ANDROID_HOME=/path/to/android-sdk embedded/android/gradlew \
  -p embedded/android --no-daemon :lib:assembleRelease
```

Then replace this AAR with
`embedded/android/lib/build/outputs/aar/lib-release.aar` and update the source
commit and checksum above.

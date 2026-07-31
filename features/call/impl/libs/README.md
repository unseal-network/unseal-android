# Embedded Unseal Call

`unseal-call-embedded.aar` packages the Unseal Call web client into
`assets/element-call`, so Android meeting and audience routes do not depend on a
deployed frontend.

- Source repository: `unseal-call` (`release/unseal-call-0.17.25`)
- Source revision: `38c95b3a56ef9d9f5e236b9914dff6d8c104e04a`
- SHA-256: `83d837a3933333bd22b9e19a1ecd2db369b1450f176bc298d51cc7eb6e18a87e`

Rebuild it from that source revision with:

```sh
UNSEAL_CALL_SHA=38c95b3a56ef9d9f5e236b9914dff6d8c104e04a corepack yarn build:embedded:production
mkdir -p embedded/android/lib/src/main/assets/element-call
rsync -a --delete dist/ embedded/android/lib/src/main/assets/element-call/
ANDROID_HOME=/path/to/android-sdk embedded/android/gradlew \
  -p embedded/android --no-daemon :lib:assembleRelease
```

Then replace this AAR with
`embedded/android/lib/build/outputs/aar/lib-release.aar` and update the source
commit and checksum above.

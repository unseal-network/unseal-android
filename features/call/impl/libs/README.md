# Embedded Unseal Call

`unseal-call-embedded.aar` packages the Unseal Call web client into
`assets/element-call`, so Android meeting and audience routes do not depend on a
deployed frontend.

- Source repository: `unseal-call` (`release/unseal-call-0.17.27`)
- Source revision: `18c6f2c72121682fd19f7639703a61389eea5df6`
- SHA-256: `ec2ad9d6257a9013d65a625168344e67908b93df505dba781410b9bae3560ddb`

Rebuild it from that source revision with:

```sh
UNSEAL_CALL_SHA=18c6f2c72121682fd19f7639703a61389eea5df6 corepack yarn build:embedded:production
mkdir -p embedded/android/lib/src/main/assets/element-call
rsync -a --delete dist/ embedded/android/lib/src/main/assets/element-call/
ANDROID_HOME=/path/to/android-sdk embedded/android/gradlew \
  -p embedded/android --no-daemon :lib:assembleRelease
```

Then replace this AAR with
`embedded/android/lib/build/outputs/aar/lib-release.aar` and update the source
commit and checksum above.

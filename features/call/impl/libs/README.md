# Embedded Unseal Call

`unseal-call-embedded.aar` packages the Unseal Call web client into
`assets/element-call`, so Android meeting and audience routes do not depend on a
deployed frontend.

- Source repository: `unseal-call` (tag `v0.17.29`)
- Source revision: `18e148f0bada1297b357224b8329f7286f19cf04`
- Web build: release asset `element-call-embedded-0.17.29.tar.gz` built by the
  `publish-embedded-packages` workflow (tarball SHA-256
  `87f6c58d9a3489848064254cb6eabd03a317fb1cb5005aa172554f9b7515a30c`)
- SHA-256: `63e1fbd6ff2e7195db9c58a4bcec2e4e8d1b2b54d214fcbbfe0b37c4bae20536`

The AAR has no code (`embedded/android/lib` applies no Kotlin plugin, so
`classes.jar` is empty) and `embedded/android` is unchanged between `v0.17.27`
and `v0.17.29`. It was therefore assembled from the release tarball with the
same non-asset entries (`AndroidManifest.xml`, `R.txt`, `classes.jar`,
`META-INF/com/android/build/gradle/aar-metadata.properties`) as the previous
AAR:

```sh
gh release download v0.17.29 -R unseal-network/unseal-call -p 'element-call-embedded-0.17.29.*'
tar xzf element-call-embedded-0.17.29.tar.gz
# start from the previous AAR's non-asset entries, then:
cp -R element-call-embedded-0.17.29 aar/assets/element-call
(cd aar && find . -exec touch -h -t 198002010000 {} + &&
  find R.txt AndroidManifest.xml classes.jar META-INF assets -print | LC_ALL=C sort |
  zip -X -q -D ../unseal-call-embedded.aar -@)
```

Equivalent build from source with Gradle:

```sh
UNSEAL_CALL_SHA=18e148f0bada1297b357224b8329f7286f19cf04 corepack yarn build:embedded:production
mkdir -p embedded/android/lib/src/main/assets/element-call
rsync -a --delete dist/ embedded/android/lib/src/main/assets/element-call/
ANDROID_HOME=/path/to/android-sdk embedded/android/gradlew \
  -p embedded/android --no-daemon :lib:assembleRelease
```

Then replace this AAR with
`embedded/android/lib/build/outputs/aar/lib-release.aar` and update the source
commit and checksum above and in `verifyEmbeddedUnsealCall`
(`features/call/impl/build.gradle.kts`).

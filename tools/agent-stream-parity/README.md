# Agent Stream Parity Tools

This folder contains local tooling for comparing iOS and Android render artifacts generated from shared AI SDK SSE fixtures.

Run from the Android repo:

```bash
node tools/agent-stream-parity/report.mjs --root docs/agent-stream-fixtures
```

The script reads:

- `docs/agent-stream-fixtures/manifest.json`
- `docs/agent-stream-fixtures/artifacts/<fixture>/ios-render-completed.json`
- `docs/agent-stream-fixtures/artifacts/<fixture>/android-render-completed.json`

It writes:

- `docs/agent-stream-fixtures/artifacts/<fixture>/parity-report.md`

Generated artifacts are ignored by git.

# Deliver the Android Broadcast Usage overview

Status: complete

## What to build

Deliver an end-to-end Android feature slice with an independent navigation entry, authenticated API client, exact byte model, state owner, and Compose overview for available traffic, unallocated usage, active sessions, and history.

## Acceptance criteria

- An authenticated user can reach “直播流量” through the established navigation pattern.
- The new feature is independent from audience playback, call control, Credits, and membership code paths.
- Requests use the resolved API base and current Matrix Bearer Token.
- Available traffic, unallocated usage, active count, active sessions, and history render from the published contract.
- All five lifecycle states use the approved Chinese labels.
- Decimal byte strings are parsed and formatted with arbitrary precision.
- Opaque-cursor pagination avoids duplicate sessions.
- Loading, empty, offline, 401, and server-error states are represented and recoverable where possible.
- Tests exercise shared fixtures and Compose navigation.

## Blocked by

- Verify and freeze the Web Broadcast Usage reference behavior in `unseal-webapp`.

## Parent

[Android Broadcast Usage](../PRD.md)

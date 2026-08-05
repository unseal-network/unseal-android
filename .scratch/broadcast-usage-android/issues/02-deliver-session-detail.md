# Deliver the Android Broadcast Usage session detail

Status: complete

## What to build

Add native overview-to-detail navigation and a session detail that explains one broadcast's identity, lifecycle, timing, exact confirmed usage, synchronization progress, and stop reason.

## Acceptance criteria

- Active and historical rows navigate to a detail through a serializable stable identifier.
- Back navigation returns to the prior overview state without duplicating data.
- Local room name is displayed when available; the room identifier is the fallback.
- State, start and end times, duration, confirmed bytes, synchronized-through time, and stop reason render when supplied.
- Live and syncing sessions communicate the expected two-to-three-minute provider delay.
- Reload or restored navigation can reconstruct the detail from its identifier.
- Loading, offline, 401, 404, and server-error states are distinct.
- Tests cover all lifecycle states, large bytes, and missing local-room metadata.

## Blocked by

- Deliver the Android Broadcast Usage overview.

## Parent

[Android Broadcast Usage](../PRD.md)

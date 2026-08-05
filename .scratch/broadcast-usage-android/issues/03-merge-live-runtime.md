# Merge live Runtime Status into the Android session detail

Status: complete

## What to build

Display live audience and presentation health for active sessions while managing Runtime Status and confirmed-usage refresh independently through Android lifecycle changes.

## Acceptance criteria

- Active details show listener count, participant count, presentation total and healthy count, and playable or recovering state.
- Runtime failures do not remove or alter confirmed traffic data.
- Runtime polling follows the server-provided interval only while the session is active and the screen is started.
- Runtime polling stops at `closed_syncing`.
- Usage refresh continues every 60 seconds until `finalized` or `failed`.
- Moving the app or screen to background cancels scheduled work; returning triggers one immediate refresh before normal cadence resumes.
- Leaving the detail cancels its active work and avoids parallel polling loops on return.
- Controlled-clock tests verify lifecycle, cancellation, transitions, and non-overlap.

## Blocked by

- Deliver the Android Broadcast Usage session detail.

## Parent

[Android Broadcast Usage](../PRD.md)

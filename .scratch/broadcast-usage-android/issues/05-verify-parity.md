# Verify Android Broadcast Usage parity

Status: complete

## What to build

Run the complete Android fixture, UI, lifecycle, accessibility, and development-service acceptance suite, then record semantic parity with the frozen Web reference.

## Acceptance criteria

- Every frozen Web reference fixture passes through the Android user interface.
- All lifecycle labels, totals, activity reasons, grant semantics, and provider-delay messaging match the Web reference in meaning.
- Large byte values and signed deltas remain exact.
- Runtime and usage polling boundaries pass controlled lifecycle tests.
- Offline, 401, 404, server failure, missing local room, empty, and pagination states are verified.
- Compose semantics, focus or traversal, touch targets, and representative device sizes are checked.
- One authenticated device or emulator reads overview, detail, Runtime Status, activity, and grants from the development service.
- Results identify the tested application and server revisions without including secrets.
- No membership, pricing, purchase, payment, or subscription action appears.

## Blocked by

- Deliver the Android Broadcast Usage session detail.
- Merge live Runtime Status into the Android session detail.
- Deliver Android Traffic Activity and Grant visibility.

## Parent

[Android Broadcast Usage](../PRD.md)

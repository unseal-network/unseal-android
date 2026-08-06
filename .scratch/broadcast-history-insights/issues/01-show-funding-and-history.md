Status: resolved

# Show funding and completed Broadcast history

## What to build

Deliver an overview that shows Traffic Grant capacity, current Balance, compact active Broadcasts, and cursor-paginated completed Broadcast cards populated from the composite history contract.

## Blocked by

None — can start immediately.

## Acceptance criteria

- [x] Dashboard funding and history responses preserve all integer precision and nullable values.
- [x] Active Broadcasts show only room identity and start time.
- [x] Completed Broadcast cards show confirmed Traffic and conditionally show viewing sessions and cost.
- [x] Additional history pages load near the end of scrolling without duplicate rows.
- [x] Internal synchronization, audience, and billing statuses are not displayed.

## Comments

- 2026-08-06: Implemented dashboard funding, compact active Broadcasts, cursor-paginated history, conditional metrics, and precision-safe parsing.

Status: resolved

# Open completed Broadcast insight details

## What to build

Let a broadcaster open a closed Broadcast and see the exact Traffic funding split, available audience totals, timing, and actual Balance charge returned by the server.

## Blocked by

Show funding and completed Broadcast history.

## Acceptance criteria

- [x] Selecting a history card loads the owner-scoped history detail by Broadcast ID.
- [x] Detail shows confirmed, Grant-covered, and Balance-covered Traffic.
- [x] Audience rows appear only for counters returned by the server.
- [x] Billing appears only when `costMicros` exists and includes charge time when available.
- [x] Room name resolution and start-time fallback avoid raw room IDs as the primary title.

## Comments

- 2026-08-06: Added history detail loading and adaptive Traffic, audience, billing, room-name, and time presentation.

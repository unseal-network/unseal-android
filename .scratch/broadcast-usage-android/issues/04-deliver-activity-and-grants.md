# Deliver Android Traffic Activity and Grant visibility

Status: complete

## What to build

Add platform-native read-only traffic activity and Traffic Grant views inside the Broadcast Usage feature. Explain signed changes by reason and expose currently usable balances with validity periods.

## Acceptance criteria

- Activity shows signed exact amounts, reason, time, and related broadcast context when available.
- Refresh replaces a mutable live activity item with the same stable identity instead of duplicating it.
- Pagination preserves order and passes opaque cursors unchanged.
- Grants show original amount, remaining amount, usable start, expiry, and current usability.
- Displayed usable grants can reconcile to available traffic under the contract rules.
- Empty grants can coexist with unallocated usage without membership messaging.
- Positive and negative values beyond primitive integer ranges retain exact precision.
- No editing, purchase, membership, pricing, or payment action is exposed.
- Repository and Compose tests cover pagination, mutable activity, expired grants, future grants, and empty states.

## Blocked by

- Deliver the Android Broadcast Usage overview.

## Parent

[Android Broadcast Usage](../PRD.md)

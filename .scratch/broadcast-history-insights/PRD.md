Status: ready-for-agent

# Android Broadcast history insights

## Problem Statement

The Android client currently presents live broadcasting primarily as raw Traffic state. It does not consume the server's closed-Broadcast history projection, so a broadcaster cannot understand the Traffic consumed, Balance cost, or audience reach of an individual completed Broadcast. The current top-level tabs and internal synchronization labels also expose implementation details instead of answering the broadcaster's main questions.

## Solution

Replace the Traffic-centric dashboard with a history-first Broadcast data experience backed only by the latest server contracts. The overview shows current Traffic Grant capacity and Balance, a deliberately minimal list of active Broadcasts, and cursor-paginated completed Broadcasts. Each history row shows only values that exist: confirmed Traffic, viewing sessions, and actual Balance cost. A separate detail view exposes the server-provided Traffic funding split, audience totals, timestamps, and charged cost. Traffic Activity and Traffic Grants remain available as secondary destinations.

## User Stories

1. As a broadcaster, I want to see my remaining Traffic Grant capacity, so that I know how much granted Traffic remains.
2. As a broadcaster, I want to see my current Balance, so that I understand the account value available for usage charges.
3. As a broadcaster, I want active Broadcasts to remain visually compact, so that incomplete statistics are not mistaken for final values.
4. As a broadcaster, I want active Broadcasts identified by room name and start time, so that I can recognize them without reading a Matrix room ID.
5. As a broadcaster, I want completed Broadcasts ordered from newest to oldest, so that the latest result is easiest to find.
6. As a broadcaster, I want history to load additional pages while I scroll, so that long histories remain easy to browse.
7. As a broadcaster, I want to refresh the overview, so that newly finalized server data appears without restarting the app.
8. As a broadcaster, I want each history row to show confirmed Traffic, so that I can compare distribution usage across Broadcasts.
9. As a broadcaster, I want each history row to show viewing sessions when available, so that I can understand total viewing occasions.
10. As a broadcaster, I want each history row to show actual Balance cost when available, so that I can understand what the Broadcast charged.
11. As a broadcaster, I want absent audience or billing values omitted, so that null does not look like zero.
12. As a broadcaster, I want to open a completed Broadcast, so that I can inspect its server-provided details.
13. As a broadcaster, I want detail to distinguish Traffic covered by Grants from Traffic covered by Balance, so that I understand how consumption was funded.
14. As a broadcaster, I want detail to show viewing sessions, unique viewers, and peak concurrent viewers when available, so that I can understand reach without a full analytics product.
15. As a broadcaster, I want detail to show start and end times, so that I can identify the completed Broadcast precisely.
16. As a broadcaster, I want detail to show charged cost and charge time only when a charge exists, so that Grant-covered usage is not presented as a fabricated zero-dollar bill.
17. As a broadcaster, I want Traffic Activity to remain accessible, so that I can inspect Traffic increases, decreases, and reasons.
18. As a broadcaster, I want Traffic Grant records to remain accessible, so that I can inspect remaining amounts and validity periods.
19. As a Chinese or English user, I want the same concepts and labels on Android and iOS, so that platform choice does not change product meaning.
20. As a user with a transient network failure, I want a retryable error state, so that I can recover without leaving the screen.

## Implementation Decisions

- The latest local server `develop` contract is the source of truth.
- The overview reads the Broadcast Usage Dashboard for funding and active Broadcast facts, and Broadcast History for closed Broadcast rows.
- Broadcast History contains closed Broadcasts only, ordered by close time with an opaque cursor and a maximum page size of 100; the client requests 20 at a time.
- History list and detail share the server's composite Traffic, audience, and billing projection rather than joining multiple endpoints on the client.
- The client preserves decimal integer strings as arbitrary-precision values and never converts byte or micro-dollar fields through floating point.
- The top summary displays Traffic Grant bytes and current Balance. Effective Balance, pending usage, equivalent Traffic, and price metadata are not primary overview metrics.
- The server currently reports `balanceBackedTrafficEnabled` as false even though receiver admission falls back to Balance. Android ignores this inconsistent flag and does not use it to hide Balance or deny capability.
- Balance amounts and Broadcast costs are displayed as USD because the server does not expose a currency code.
- Active Broadcasts display room identity and start time only. The client does not show live Traffic, live cost, or live audience estimates.
- History cards display confirmed Traffic unconditionally and display viewing sessions and cost only when their nullable values exist.
- Server lifecycle status values remain decodeable implementation data but are not rendered as product status labels.
- A separate history detail view shows the Traffic split, available audience counters, timestamps, and actual charge facts.
- Room display names are resolved from the local Matrix SDK; start time is the fallback title. Raw room IDs are reserved for detail or diagnostics.
- Traffic Activity and Traffic Grants are secondary destinations rather than top-level tabs.
- Existing API base URL resolution and Matrix bearer-token authentication remain unchanged.
- WebApp and server schema changes are outside this Android specification.

## Testing Decisions

- Prefer the existing HTTP service seam with MockWebServer to verify URLs, bearer authentication, cursor encoding, nullable values, and arbitrary-precision integer parsing.
- Prefer the existing Presenter seam to verify overview loading, history pagination, detail selection, secondary destinations, independent failures, and formatting behavior.
- Compose UI is verified through module compilation and existing previews; tests assert user-observable state rather than private coroutine or parser implementation details.
- The main Broadcast Usage module must compile and its focused unit tests must pass before delivery.
- A simulator or physical-device smoke test should verify navigation, localization, scrolling pagination, and adaptive history rows when suitable API data is available.

## Out of Scope

- WebApp changes.
- Server schema, billing, Cloudflare synchronization, or deployment changes.
- Membership purchase, subscription pricing, invoicing, or payment flows.
- Live cost estimates, live Traffic estimates, and live audience analytics.
- Watch duration, retention, geography, device, referrer, viewer identity, or revenue analytics.
- Inventing values for absent server fields or exposing internal processing, syncing, settlement, or availability statuses.
- Treating Balance as a finite Traffic allowance; the server permits negative Balance after finalized usage.

## Further Notes

Traffic is decimal bytes and pricing is decimal-GB based. `viewerSessionCount` represents successful logical viewing sessions, `uniqueViewerCount` represents unique Matrix viewers through a server-side privacy-preserving key, and `peakConcurrentViewers` is the maximum simultaneous successful audience count. These counters can finalize independently from Traffic and billing.

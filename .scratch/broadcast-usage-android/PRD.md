# Android Broadcast Usage

## Problem Statement

Android 用户无法查看自己的直播流量概览、按场次汇总的已确认用量、实时运行状态、流量增减原因和可用流量记录。现有广播网络能力主要服务于通话或观众运行流程，不适合承载账户级历史与流量信息；把新能力塞入现有通话客户端会混淆生命周期和职责。

Android 实现需要匹配已经冻结的 Web 参考行为，同时遵循原生导航、生命周期和精度要求。服务端字节值可能超过基础整数范围，前后台切换也会直接影响轮询正确性和资源消耗。

## Solution

建立独立的 Broadcast Usage 功能边界，包含认证 API、领域模型、状态管理、Compose 界面和 Settings 导航接入。概览展示可用流量、未分配用量、活跃场次和历史；详情展示按场次确认的 Cloudflare 用量，并在直播期间合并独立 Runtime Status。

增加流量明细和只读 Traffic Grant 视图，保持与会员、Credits 和现有通话广播实现解耦。网络层复用现有服务器发现和 Matrix access token，字节值使用任意精度整数，轮询绑定页面与应用生命周期。

## User Stories

1. As an Android broadcaster, I want a dedicated Broadcast Usage entry, so that I can find live traffic without entering membership or call controls.
2. As an Android broadcaster, I want to see available traffic, so that I understand my currently usable balance.
3. As an Android broadcaster, I want unallocated usage shown separately, so that a missing grant is visible.
4. As an Android broadcaster, I want active and historical sessions, so that usage is organized by broadcast.
5. As an Android broadcaster, I want clear lifecycle labels, so that I understand live, syncing, completed, and failed sessions.
6. As an Android broadcaster, I want to open a session detail, so that I can inspect confirmed traffic and timing.
7. As an Android broadcaster, I want a room name or identifier fallback, so that every session remains recognizable.
8. As an Android broadcaster, I want exact large byte totals, so that the app never rounds my traffic.
9. As an Android broadcaster, I want synchronization time and delay guidance, so that normal provider lag is understandable.
10. As an Android broadcaster, I want live listener and participant counts, so that I understand current reach.
11. As an Android broadcaster, I want presentation health and playability, so that I can detect an operational issue.
12. As an Android broadcaster, I want background polling to stop, so that the feature does not waste network or battery.
13. As an Android broadcaster, I want an immediate refresh when I return, so that the screen catches up promptly.
14. As an Android broadcaster, I want signed traffic activity with reasons, so that changes are explainable.
15. As an Android broadcaster, I want updated live activity to replace its previous row, so that the list remains accurate.
16. As an Android broadcaster, I want to browse older data, so that a long broadcast history remains usable.
17. As an Android broadcaster, I want to see usable traffic records and expiry dates, so that I know which balance can still be consumed.
18. As an Android user, I want clear empty, offline, authorization, not-found, and server-error states, so that failures are actionable.
19. As an accessibility user, I want readable semantics and touch targets, so that the feature works with assistive technology.
20. As a product owner, I want semantic parity with Web, so that users see the same facts on both platforms.
21. As a product owner, I want no membership or purchase actions, so that broadcast and membership remain separate.

## Implementation Decisions

- Broadcast Usage is a new modular feature with its own API and implementation boundaries. It does not extend the audience broadcast HTTP client or call control state.
- The feature is reachable from the authenticated settings or account navigation as an independent destination.
- Networking reuses the existing Chatbot API base resolver and current Matrix access token.
- Contract byte strings and signed deltas are represented with arbitrary-precision integers throughout data and presentation layers.
- Cursors remain opaque strings.
- The screen flow follows the project's node, presenter, state, Compose, and navigation-target conventions.
- Overview, detail, activity, and grant states expose explicit loading, content, empty, partial-failure, and recoverable-error behavior.
- Local room data resolves the session display name, with the room identifier as fallback.
- Confirmed usage and Runtime Status remain separate state branches. Runtime failures cannot corrupt or hide confirmed traffic.
- Overview usage refreshes every 60 seconds only while the relevant screen is active and the application is foregrounded.
- Runtime polling follows the returned interval for active broadcasts and stops at `closed_syncing`; usage refresh continues until a terminal state.
- Returning to foreground triggers one immediate refresh before normal cadence resumes.
- Activity is reconciled by stable identity so live amount updates replace earlier values.
- Web's frozen copy and information meaning are the semantic reference; Android uses platform-native layout and interactions.
- Grants are read-only. The feature contains no membership, pricing, purchase, or payment surface.

## Testing Decisions

- Drive repository and presentation tests with the frozen Web reference fixtures and behavior notes.
- Test decimal strings larger than primitive integer ranges and large signed deltas.
- Test overview, detail, activity, and grant loading, empty, content, pagination, and error states.
- Test every lifecycle label and transition, including live to syncing to finalized and failed.
- Test room-name resolution and identifier fallback.
- Test Runtime Status healthy, recovering, not playable, partial presentation health, and unavailable cases.
- Use controlled dispatchers and clocks to verify polling cadence, cancellation, foreground refresh, and terminal-state behavior.
- Test that stable identities replace mutable live activity entries and prevent duplicates.
- Test process recreation or restored navigation with stable identifiers rather than retaining non-serializable state.
- Run Compose UI tests for navigation, semantics, touch targets, scrolling, and representative compact and large layouts.
- Complete one authenticated development-environment acceptance pass on a device or emulator.

## Out of Scope

- Broadcast creation, start, stop, moderation, or audience playback.
- Changes to existing call and audience broadcast behavior.
- Membership, subscription, pricing, payment, purchase, renewal, top-up, or invoice UI.
- Editing, allocating, or purchasing Traffic Grants.
- Client-side traffic metering.
- Server schema, Cloudflare synchronization, or accounting changes.
- iOS implementation.

## Further Notes

- Android begins after the Web reference behavior is verified and frozen.
- Available traffic is the sum of currently usable grant balances, not a membership entitlement.
- Confirmed traffic can lag live activity by approximately two to three minutes.
- Native layout may differ from Web, but labels, data meanings, polling boundaries, precision, and failure semantics must match.

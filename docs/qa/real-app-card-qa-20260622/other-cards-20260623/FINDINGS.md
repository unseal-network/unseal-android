# Other Tool Cards — Real-Device QA (2026-06-23)

Device: physical `5fd76ce3` (OnePlus PHK110). Chat: `geminirayson`.
Build: includes flight logo/aircraft + softer-shadow changes (now merged to develop).

## Verified rendering (all correct)

- **Finance** (`finance`) ✅ — Apple Inc / AAPL·NASDAQ / $297.01 / ▲0.00%, price chart,
  News·Financials·Stats sub-tabs, news headline. `finance-card-verified.png`
- **Shopping** (`productList`) ✅ — products with name, price (green), retailer
  (Home Depot/Best Buy/Target), star rating + review count, product thumbnail.
  `shopping-card-products-verified.png`
- **News / headlines** (`headlineList`) ✅ — title, snippet, source (Vatican News/Frontiers),
  relative time, domain, open chevron. `news-headlines-verified.png`
- **GitHub** (`githubIssuesList`) ✅ connected — returned real element-x-android issues
  (#7011 Target SDK 37, #6533 rooms crash, #6462, #6235, #3184). Data confirmed in the
  room/markdown; GitHub-tab card capture interrupted by a USB disconnect.

## Cross-platform UX observation (NOT an Android-only bug)

When the agent calls multiple tools, the root card opens on the **last** tool's tab.
The agent often appends a `socialPostFeed` ("Posts") tool that returns nothing, so the
card opens on an empty "Posts" tab ("No posts returned") and the header reads "Posts",
hiding the populated tab. Reproduced on Events, Shopping, News, GitHub.

Root cause is shared design, not an Android regression:
- Android `ToolCallRootCardAdapter.rootModel()` → `val selectedOriginal = entries.last()`
- iOS `ToolCallRootCard` → `selectedId = entries.last?.id` (lines 168/273/341)

Because iOS behaves identically, this was left unchanged to preserve parity. Options for
the team: (a) default the active tab to the first **populated** entry; (b) stop the agent
from appending empty `socialPostFeed` calls. Either is a product decision spanning both
clients + agent backend.

## Integration-card rendering verified via the debug preview harness

Connector-gated cards can't be triggered live in `geminirayson` (Gmail shows a "完成 Gmail 授权"
OAuth link; GitHub IS connected and returned real issues). To verify their *rendering* without
live OAuth, launched the debug harness:

```
adb shell am start -n network.unseal.android.debug/io.element.android.features.messages.impl.timeline.components.event.AgentStreamToolCardPreviewActivity
```

It renders the parity fixture set as stable multi-tab cards (no chat navigation). Confirmed
rendering for:

- **flightAlert** — Air China row shows the airline-logo slot (airplane-icon fallback) + Economy,
  i.e. the flight logo/aircraft fix is present in fixtures too. `harness-flightAlert-composeEmail.png`
- **composeEmail (Gmail)** — subject, sender, date, snippet, labels, star + attachment icons;
  Draft / Files (Drive) tabs present.
- **githubIssue** (#128 Open, android/ui labels), **githubIssuesList**, Repositories,
  **orgsList** (unseal org), **contributors** (rayson · 84), Checks.
  `harness-githubIssue-orgsList-linearIssue.png`, `harness-contributors-schedule-failureRaw.png`
- **linearIssue** (MOB-42 In Progress) + Posts (socialPostFeed/Twitter) tab.
- **Schedule** (Create/Update Schedule, Schedule Status tabs).
- **moltbookRegister** — interactive form (name, verification code, claim URL, Continue).
  `harness-moltbook-jsonSpec-errorCard.png`
- **jsonSpec** ("Generated UI spec"), **generic** fallback list (`harness-generic-results-jsonSpec.png`).
- **Failure rendering** — error tab shows "Failed to get results" + raw payload, and a
  `data-error-card` ("Tool execution failed / permission error").

All render cleanly with the softened shadow. Together with the live captures, this covers the
full card set across families.

## Still pending (true live integration, optional)

- Live GitHub card *tab* capture in chat (data confirmed live; harness covers rendering).
- Live Gmail/Drive/Linear/Twitter/Schedule require connecting those Composio integrations in the
  account; only rendering (not live data) was verified for them.

## Device note

The phone repeatedly dropped off USB during this session, and KEYCODE_BACK intermittently
switched the foreground app to WeChat. Re-plug / a different cable recommended before
continuing the remaining integration-card captures.

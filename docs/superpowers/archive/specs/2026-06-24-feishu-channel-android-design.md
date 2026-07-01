# Android Feishu Channel — Design

**Date:** 2026-06-24
**Status:** Approved (brainstorming)
**Repo:** `unseal-android` (Jetpack Compose, Element X Android fork). Backend (`unseal-agents`) Feishu channel is **already live — no backend changes**.
**Port of:** the shipped & device-verified iOS Feishu channel (`unseal-ios`, spec `2026-06-24-feishu-channel-ios-design.md`). This brings Android to parity. Same backend contract, same UX, Android-native mechanics.

## Goal

Add **Feishu** as a third channel platform (alongside Telegram & WeCom) on Android. Feishu connects via an OAuth 2.0 device-authorization (QR) flow: the backend returns a `verification_uri_complete` link; the user taps **Open in Feishu** (hands off to the Feishu app via an applink) or scans a QR (cross-device); the client polls the channel until `status == "active"`.

## Decisions (settled — port of the validated iOS design)

1. **Extend the existing `channels` sub-feature** (`features/agentmanagement/impl/.../channels/`), which already does Telegram/WeCom. Add a Feishu platform + a Feishu auth-panel state inside the existing `AddChannelSheet`. No new screen/node.
2. **Hybrid connect UI:** primary action opens the verification URL **wrapped in a Feishu applink** (`https://applink.feishu.cn/client/web_url/open?url=…`) so Android hands off to the Feishu app (where the user is logged in) and loads the page in-app. Secondary "Scan with another device" reveals a locally-rendered QR of the **raw** verification URL.
   - **This applink wrapping is the device-verified fix from iOS** — opening the raw `accounts.feishu.cn` URL just lands in the browser (no shared Feishu session). The applink hands off to the app. International tenants use `applink.larksuite.com`.
3. **QR via the existing `libraries/qrcode` `QrCodeImage(data=…)` composable** (ZXing). No new dependency code — just a Gradle module dependency.
4. **Success detected only by polling** `getAgentChannel` until `status == "active"` — the applink/browser handoff gives no callback. Poll loop uses the established `repeat(N) { delay }` pattern; cancellation is automatic via a `LaunchedEffect` keyed on the Feishu installation id (closing the sheet cancels it).
5. **Strings hardcoded in Kotlin** — matches the `agentmanagement` module's existing convention (no `res/values`). "飞书" inline (proper noun).

## Why the applink (mechanism, carried from iOS)

Backend uses the official Lark SDK `registerApp` = OAuth 2.0 Device Authorization Grant (RFC 8628). `qrUrl` = `verification_uri_complete`, a web consent page on **`accounts.feishu.cn`**. It is QR-renderable OR same-device-openable, but it is NOT app-deep-linkable and a mobile browser doesn't share the Feishu app session. Wrapping it in the Feishu **applink** (`applink.feishu.cn`, which IS associated with the app) opens the app and loads the page in its in-app browser with the logged-in session → one-tap approve. Authorization is bound to the backend `device_code` (not the device), so app-side approval unblocks the same registration session. Registration is fire-and-forget, so the connect response may lack `qrUrl` — poll until it appears.

## Backend contract (already live — for reference)

- `POST /api/agents/{agentId}/channels` body `{ credentials: { platform: "feishu" } }` → `{ installationId, platform, status?, qrUrl? }`. No secrets; `ownerUserId` is server-authoritative.
- `GET /api/agents/{agentId}/channels/{installationId}` → `ChatbotChannelSummary` (used for polling).
- `GET/DELETE /api/agents/{agentId}/channels[...]` — list/disconnect (already consumed).

`ChatbotChannelSummary` JSON is camelCase (kotlinx.serialization default), gains `qrUrl?`. `status` is `active|pending|error`. Active Feishu `label` carries the tenant brand (shown as the row subtitle).

## Existing Android facts (current code — verified)

- **Models:** `libraries/chatbot/api/.../model/channels/ChannelModels.kt` — `ChatbotChannelPlatform { Telegram, WeCom }` (`@SerialName`), `ChatbotChannelSummary`, `ChatbotConnectChannelResponse`, `ChatbotChannelCredentials`, `sealed interface ChatbotChannelConnectBody { Telegram, WeCom }`.
- **API:** `ChatbotApiService` (interface) + `DefaultChatbotApiService` (OkHttp, `httpClient.requestJson(path, method, body)`, `path(x)` percent-encodes). `connectAgentChannel` builds the credentials `JsonObject` per platform. **No single-channel `getAgentChannel` yet.** `ChatbotApiServiceFactory.createForUnsealApi(matrixClient)` resolves the agent-api base URL + Bearer matrix token.
- **Fake (tests):** `libraries/chatbot/test/.../FakeChatbotApiService.kt` — per-method `var xxxResult: (args) -> Result<T>` lambdas + `override … = simulateLongTask { xxxResult(...) }`.
- **Channels feature:** `features/agentmanagement/impl/.../channels/` — `AgentChannelsState` (+ `ChannelSheetState`), `AgentChannelsEvents` (sealed), `AgentChannelsPresenter` (Molecule `@Composable present()`, `rememberCoroutineScope`, `connect()`/`loadChannels()`/etc.), `AgentChannelsView` (Compose: list + `ModalBottomSheet` add/edit + delete dialog; `PlatformBadge`/`PlatformCard`/`brandColor()`/`displayName()` are file-private in the View). `AgentChannelsPresenterTest` uses Turbine (`presenter.test { awaitStateWhere {…} }`) + `FakeChatbotApiService`.
- **Open URL:** `LocalUriHandler.current.openUri(url)` → external app intent (handles applink handoff). Available in any composable.
- **QR:** `QrCodeImage(data: String, modifier, forceMaxBrightness)` in `libraries/qrcode` (depend via `implementation(projects.libraries.qrcode)`).
- **Polling pattern:** `repeat(MAX) { attempt -> … delay(MS) }` (see `credits/.../TopupPresenter`). Under `runTest`, `delay` is virtual → tests are fast (no injected interval needed, unlike iOS).
- **DI:** Metro `@AssistedInject` presenter; the `@ContributesNode` `AgentChannelsNode` wires it. No DI change needed (no new constructor deps).
- **Empty-state copy:** `AgentChannelsView` EmptyState + `AgentDetailView` ChannelsSection both say "Connect Telegram or WeCom" — genericize (carried iOS Minor).
- **Build/test:** `./gradlew :features:agentmanagement:impl:testDebugUnitTest` (module tests); `./gradlew :libraries:chatbot:impl:compileDebugKotlin` or `:app:assembleGplayDebug` (build); `./gradlew ktlintFormat` (format).

---

## Part A — Data models (`libraries/chatbot/api`)

`ChannelModels.kt`:
- `ChatbotChannelPlatform`: add `@SerialName("feishu") Feishu`.
- `ChatbotChannelSummary`: add `val qrUrl: String? = null`.
- `ChatbotConnectChannelResponse`: add `val status: String? = null`, `val qrUrl: String? = null`.
- `ChatbotChannelConnectBody`: add `data object Feishu : ChatbotChannelConnectBody` (no fields).

`DefaultChatbotApiService.connectAgentChannel`: add a `ChatbotChannelConnectBody.Feishu ->` branch building `JsonObject(mapOf("platform" to JsonPrimitive("feishu")))` (no secrets). The `when (body)` becomes exhaustive over three subtypes.

## Part B — `getAgentChannel` API method

- `ChatbotApiService`: add `suspend fun getAgentChannel(agentId: String, installationId: String): Result<ChatbotChannelSummary>`.
- `DefaultChatbotApiService`: implement → `httpClient.requestJson("/api/agents/${path(agentId)}/channels/${path(installationId)}", ChatbotHttpMethod.GET)` (mirror `getAgentChannelCredentials` minus `/credentials`).
- `FakeChatbotApiService`: add `var getAgentChannelResult: (String, String) -> Result<ChatbotChannelSummary>` (default returns a pending summary) + `override … = simulateLongTask { getAgentChannelResult(agentId, installationId) }`.

## Part C — Presenter + state (connect + polling)

`ChannelSheetState`: add `feishuInstallationId: String? = null`, `feishuQrUrl: String? = null`, `feishuExpired: Boolean = false`; computed `isFeishuPanel get() = feishuInstallationId != null`; `canConnect` returns `true` for Feishu.

`AgentChannelsPresenter`:
- `connect()`: add a Feishu branch — POST `ChatbotChannelConnectBody.Feishu`; on success set `feishuInstallationId` + `feishuQrUrl` on the sheet (do NOT close); on failure set error.
- Polling: a `LaunchedEffect(sheet?.feishuInstallationId)` in `present()` — when non-null, `repeat(FEISHU_MAX_POLL_ATTEMPTS) { delay(FEISHU_POLL_DELAY_MILLIS); getAgentChannel }`: fill `feishuQrUrl` if it arrives late; `active` → close sheet + reload; `error` → set sheet error; loop exhaustion → `feishuExpired = true`. Closing the sheet nulls `feishuInstallationId` → the effect cancels automatically (no manual job).
- Constants: `FEISHU_POLL_DELAY_MILLIS = 2000L`, `FEISHU_MAX_POLL_ATTEMPTS = 150`.

## Part D — Brand identity + empty-state copy (`AgentChannelsView`)

- `brandColor()`: add `Feishu -> Color(0xFF3370FF)`.
- `displayName()`: add `Feishu -> "飞书"`.
- `PlatformBadge`: pick the Feishu icon (a confirmed-existing `CompoundIcons`; brand color carries the distinction).
- Genericize the EmptyState hint to "Connect a channel so this agent can reply there." Also genericize the same hint in `AgentDetailView` ChannelsSection if present.

## Part E — View (picker card, panel, QR, applink)

- `AddForm`: add a third `PlatformCard(Feishu)`. The platform `Row` holds three weighted cards.
- Credential fields shown only for non-Feishu (`when (platform)`); Feishu shows no fields.
- `AddChannelSheet` body routing: when `sheet.isFeishuPanel`, render `FeishuPanel`; the Connect button row is hidden in the panel.
- `FeishuPanel`: badge + title + subtitle; if `feishuExpired` → expired text; else if `feishuQrUrl != null` → **Open in Feishu** button (`LocalUriHandler.current.openUri(feishuAppLink(qrUrl))`) + "Scan with another device" toggle revealing `QrCodeImage(data = qrUrl)` + a "waiting…" status row; else → generating spinner.
- `feishuAppLink(qrUrl)` helper: `https://${host}/client/web_url/open?url=${URLEncoder.encode(qrUrl, "UTF-8")}`, host = `applink.larksuite.com` if the url contains "larksuite" else `applink.feishu.cn`.
- Gradle: add `implementation(projects.libraries.qrcode)` to `features/agentmanagement/impl/build.gradle.kts`.

## Testing (`AgentChannelsPresenterTest`)

Add Feishu tests (Turbine + `FakeChatbotApiService`, `runTest` makes `delay` virtual):
- **Feishu connect enters panel:** `connectAgentChannelResult` returns feishu `{installationId, status:"pending", qrUrl}`; `getAgentChannelResult` keeps returning pending. Assert sheet becomes `isFeishuPanel` with `feishuQrUrl` set and not closed. Assert the connect body was `ChatbotChannelConnectBody.Feishu`.
- **Poll qrUrl-late → active:** connect returns `qrUrl = null`; a call-counting `getAgentChannelResult` returns `qrUrl` on call 1, then `status="active"` → assert sheet closes (becomes null).
- **Poll error:** `getAgentChannelResult` returns `status="error"` → assert sheet error set, sheet not closed.

## Out of scope (YAGNI)

- Backend changes (contract is live).
- Editing a Feishu installation (no editable secrets — connect + disconnect only).
- A websocket/push "connected" signal — polling matches iOS/web.
- Localization resources — hardcoded Kotlin strings per module convention.
- iOS parity beyond this flow.

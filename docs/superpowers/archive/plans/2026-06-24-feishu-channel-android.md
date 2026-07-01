# Android Feishu Channel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Feishu as a third channel platform on Android (parity with the device-verified iOS feature), connected via an OAuth device-authorization flow — primary path opens the verification URL wrapped in a Feishu applink (hands off to the Feishu app), with a QR fallback, and polling to detect activation.

**Architecture:** Extend the existing `features/agentmanagement/impl/.../channels` sub-feature (Telegram/WeCom) — add a Feishu platform + a Feishu auth-panel state in the existing `AddChannelSheet`. Backend (`unseal-agents`) is unchanged. Success is detected by polling `getAgentChannel` until `status == "active"`; the poll is a `LaunchedEffect` keyed on the Feishu installation id (closing the sheet auto-cancels it). QR uses the existing `libraries/qrcode` `QrCodeImage`.

**Tech Stack:** Kotlin, Jetpack Compose, Molecule presenter, Metro DI, Appyx nav, kotlinx.serialization, OkHttp client, ZXing (`libraries/qrcode`), Turbine + JUnit tests.

## Global Constraints

- **No backend changes** — consume existing oRPC routes only.
- **Channel JSON is camelCase** — kotlinx.serialization defaults; new fields are nullable with `= null` defaults so deserialization and existing call sites stay valid.
- **Feishu connect sends no secrets** — POST body is `{ "credentials": { "platform": "feishu" } }`.
- **Applink wrapping (device-verified on iOS):** open `https://applink.feishu.cn/client/web_url/open?url=<URL-encoded verification URL>`; use `applink.larksuite.com` when the verification URL contains `larksuite`. The QR encodes the RAW verification URL (not the applink).
- **Strings hardcoded in Kotlin** (the `agentmanagement` module has no `res/values`); "飞书" inline (proper noun).
- **Poll constants:** `FEISHU_POLL_DELAY_MILLIS = 2000L`, `FEISHU_MAX_POLL_ATTEMPTS = 150`.
- **Generated/Fake:** `FakeChatbotApiService` is hand-written test code (NOT codegen) — edit it directly.
- **Build/test commands:**
  - Module tests: `./gradlew --no-daemon :features:agentmanagement:impl:testDebugUnitTest`
  - Compile a library module: `./gradlew --no-daemon :libraries:chatbot:impl:compileDebugKotlin`
  - Compile the feature: `./gradlew --no-daemon :features:agentmanagement:impl:compileDebugKotlin`
  - Format: `./gradlew --no-daemon ktlintFormat`
  - Run gradle from the repo root `/Users/jelf/Projects/work/unseal-android`. Redirect to a log file and grep for `BUILD SUCCESSFUL|BUILD FAILED|error:`/`FAILED` rather than piping to tail/head.

---

### Task 1: Data models + Feishu connect serialization

**Files:**
- Modify: `libraries/chatbot/api/src/main/kotlin/io/element/android/libraries/chatbot/api/model/channels/ChannelModels.kt`
- Modify: `libraries/chatbot/impl/src/main/kotlin/io/element/android/libraries/chatbot/impl/DefaultChatbotApiService.kt` (the `connectAgentChannel` `when (body)` near lines 393–411)

**Interfaces:**
- Consumes: nothing (foundation).
- Produces: `ChatbotChannelPlatform.Feishu`; `ChatbotChannelSummary.qrUrl: String?`; `ChatbotConnectChannelResponse.status: String?` + `.qrUrl: String?`; `ChatbotChannelConnectBody.Feishu` (data object); connect serializes Feishu as `{platform:"feishu"}`.

- [ ] **Step 1: Edit `ChannelModels.kt`**

Add the enum case:
```kotlin
@Serializable
enum class ChatbotChannelPlatform {
    @SerialName("telegram")
    Telegram,

    @SerialName("wecom")
    WeCom,

    @SerialName("feishu")
    Feishu,
}
```

Add `qrUrl` to the summary:
```kotlin
@Serializable
data class ChatbotChannelSummary(
    val installationId: String,
    val platform: ChatbotChannelPlatform,
    val status: String,
    val label: String,
    val callbackUrl: String? = null,
    val qrUrl: String? = null,
)
```

Add `status` + `qrUrl` to the connect response:
```kotlin
@Serializable
data class ChatbotConnectChannelResponse(
    val installationId: String,
    val platform: ChatbotChannelPlatform,
    val botUsername: String? = null,
    val callbackUrl: String? = null,
    val status: String? = null,
    val qrUrl: String? = null,
)
```

Add the Feishu connect body (no fields — no secrets):
```kotlin
sealed interface ChatbotChannelConnectBody {
    data class Telegram(val botToken: String) : ChatbotChannelConnectBody
    data class WeCom(val token: String, val encodingAESKey: String) : ChatbotChannelConnectBody
    data object Feishu : ChatbotChannelConnectBody
}
```

- [ ] **Step 2: Edit `connectAgentChannel` in `DefaultChatbotApiService.kt`**

Add a `Feishu` branch to the `when (body)` (it becomes exhaustive over three subtypes):
```kotlin
val credentials = when (body) {
    is ChatbotChannelConnectBody.Telegram -> JsonObject(
        mapOf(
            "platform" to JsonPrimitive("telegram"),
            "botToken" to JsonPrimitive(body.botToken),
        )
    )
    is ChatbotChannelConnectBody.WeCom -> JsonObject(
        mapOf(
            "platform" to JsonPrimitive("wecom"),
            "token" to JsonPrimitive(body.token),
            "encodingAESKey" to JsonPrimitive(body.encodingAESKey),
        )
    )
    ChatbotChannelConnectBody.Feishu -> JsonObject(
        mapOf(
            "platform" to JsonPrimitive("feishu"),
        )
    )
}
```

- [ ] **Step 3: Compile both modules to verify**

```
cd /Users/jelf/Projects/work/unseal-android
./gradlew --no-daemon :libraries:chatbot:impl:compileDebugKotlin > /tmp/t1.log 2>&1
grep -E 'BUILD SUCCESSFUL|BUILD FAILED|error:' /tmp/t1.log | tail -5
```
Expected: `BUILD SUCCESSFUL`, no `error:` lines (the `when` is exhaustive over the three subtypes).

- [ ] **Step 4: Commit**

```bash
git add libraries/chatbot/api/src/main/kotlin/io/element/android/libraries/chatbot/api/model/channels/ChannelModels.kt libraries/chatbot/impl/src/main/kotlin/io/element/android/libraries/chatbot/impl/DefaultChatbotApiService.kt
git commit -m "feat(channels): feishu platform + qrUrl/status models + connect body

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01Dyw2vNsZuZvvzYRQGeCGQw"
```

---

### Task 2: `getAgentChannel` API method + fake

**Files:**
- Modify: `libraries/chatbot/api/src/main/kotlin/io/element/android/libraries/chatbot/api/ChatbotApiService.kt` (channel section ~lines 146–151)
- Modify: `libraries/chatbot/impl/src/main/kotlin/io/element/android/libraries/chatbot/impl/DefaultChatbotApiService.kt` (next to `getAgentChannelCredentials`)
- Modify: `libraries/chatbot/test/src/main/kotlin/io/element/android/libraries/chatbot/test/FakeChatbotApiService.kt`

**Interfaces:**
- Consumes: `ChatbotChannelSummary` (Task 1).
- Produces: `suspend fun getAgentChannel(agentId, installationId): Result<ChatbotChannelSummary>` on the service + fake field `getAgentChannelResult: (String, String) -> Result<ChatbotChannelSummary>` (used by Task 3 tests).

- [ ] **Step 1: Add to the `ChatbotApiService` interface**

In the "Agent channels" section (after `getAgentChannelCredentials`):
```kotlin
suspend fun getAgentChannel(agentId: String, installationId: String): Result<ChatbotChannelSummary>
```
(`ChatbotChannelSummary` is already imported in this file.)

- [ ] **Step 2: Implement in `DefaultChatbotApiService`**

Next to `getAgentChannelCredentials`:
```kotlin
override suspend fun getAgentChannel(agentId: String, installationId: String): Result<ChatbotChannelSummary> =
    httpClient.requestJson("/api/agents/${path(agentId)}/channels/${path(installationId)}", ChatbotHttpMethod.GET)
```

- [ ] **Step 3: Add the fake field + override in `FakeChatbotApiService`**

Add the field next to the other channel result lambdas (after `getAgentChannelCredentialsResult`):
```kotlin
var getAgentChannelResult: (String, String) -> Result<ChatbotChannelSummary> = { _, installationId ->
    Result.success(ChatbotChannelSummary(installationId = installationId, platform = ChatbotChannelPlatform.Feishu, status = "pending", label = ""))
}
```
Add the override next to the other channel overrides:
```kotlin
override suspend fun getAgentChannel(agentId: String, installationId: String) = simulateLongTask { getAgentChannelResult(agentId, installationId) }
```

- [ ] **Step 4: Compile to verify**

```
cd /Users/jelf/Projects/work/unseal-android
./gradlew --no-daemon :libraries:chatbot:impl:compileDebugKotlin :libraries:chatbot:test:compileDebugKotlin > /tmp/t2.log 2>&1
grep -E 'BUILD SUCCESSFUL|BUILD FAILED|error:' /tmp/t2.log | tail -5
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add libraries/chatbot/api/src/main/kotlin/io/element/android/libraries/chatbot/api/ChatbotApiService.kt libraries/chatbot/impl/src/main/kotlin/io/element/android/libraries/chatbot/impl/DefaultChatbotApiService.kt libraries/chatbot/test/src/main/kotlin/io/element/android/libraries/chatbot/test/FakeChatbotApiService.kt
git commit -m "feat(channels): getAgentChannel single-channel read + fake

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01Dyw2vNsZuZvvzYRQGeCGQw"
```

---

### Task 3: Presenter + state — Feishu connect & polling

**Files:**
- Modify: `features/agentmanagement/impl/src/main/kotlin/io/element/android/features/agentmanagement/impl/channels/AgentChannelsState.kt`
- Modify: `features/agentmanagement/impl/src/main/kotlin/io/element/android/features/agentmanagement/impl/channels/AgentChannelsPresenter.kt`
- Test: `features/agentmanagement/impl/src/test/kotlin/io/element/android/features/agentmanagement/impl/channels/AgentChannelsPresenterTest.kt`

**Interfaces:**
- Consumes: `ChatbotChannelConnectBody.Feishu`, `ChatbotConnectChannelResponse.{status,qrUrl}` (Task 1); `getAgentChannel` + `FakeChatbotApiService.getAgentChannelResult` (Task 2).
- Produces (on `ChannelSheetState`): `feishuInstallationId: String?`, `feishuQrUrl: String?`, `feishuExpired: Boolean`, `isFeishuPanel: Boolean`; `canConnect` true for Feishu. Presenter gains a Feishu connect branch + a polling `LaunchedEffect`.

- [ ] **Step 1: Write the failing tests**

Add to `AgentChannelsPresenterTest.kt` (inside the class). `runTest` makes the poll `delay` virtual:
```kotlin
@Test
fun `present - feishu connect enters panel without closing`() = runTest {
    val service = FakeChatbotApiService().apply {
        connectAgentChannelResult = { _, body ->
            assertThat(body).isEqualTo(ChatbotChannelConnectBody.Feishu)
            Result.success(ChatbotConnectChannelResponse(installationId = "fs1", platform = ChatbotChannelPlatform.Feishu, status = "pending", qrUrl = "https://accounts.feishu.cn/x?user_code=A"))
        }
        getAgentChannelResult = { _, installationId ->
            Result.success(ChatbotChannelSummary(installationId = installationId, platform = ChatbotChannelPlatform.Feishu, status = "pending", label = "", qrUrl = "https://accounts.feishu.cn/x?user_code=A"))
        }
    }
    val presenter = createPresenter(service)
    presenter.test {
        awaitItem().eventSink(AgentChannelsEvents.OpenAdd)
        awaitStateWhere { it.sheet != null }.eventSink(AgentChannelsEvents.SetPlatform(ChatbotChannelPlatform.Feishu))
        awaitStateWhere { it.sheet?.platform == ChatbotChannelPlatform.Feishu }.eventSink(AgentChannelsEvents.Connect)
        val panel = awaitStateWhere { it.sheet?.isFeishuPanel == true }
        assertThat(panel.sheet?.feishuQrUrl).isEqualTo("https://accounts.feishu.cn/x?user_code=A")
        cancelAndIgnoreRemainingEvents()
    }
}

@Test
fun `present - feishu poll fills late qrUrl then closes on active`() = runTest {
    var tick = 0
    val service = FakeChatbotApiService().apply {
        connectAgentChannelResult = { _, _ ->
            Result.success(ChatbotConnectChannelResponse(installationId = "fs2", platform = ChatbotChannelPlatform.Feishu, status = "pending", qrUrl = null))
        }
        getAgentChannelResult = { _, installationId ->
            tick += 1
            if (tick == 1) {
                Result.success(ChatbotChannelSummary(installationId = installationId, platform = ChatbotChannelPlatform.Feishu, status = "pending", label = "", qrUrl = "https://accounts.feishu.cn/x?user_code=B"))
            } else {
                Result.success(ChatbotChannelSummary(installationId = installationId, platform = ChatbotChannelPlatform.Feishu, status = "active", label = "Acme"))
            }
        }
    }
    val presenter = createPresenter(service)
    presenter.test {
        awaitItem().eventSink(AgentChannelsEvents.OpenAdd)
        awaitStateWhere { it.sheet != null }.eventSink(AgentChannelsEvents.SetPlatform(ChatbotChannelPlatform.Feishu))
        awaitStateWhere { it.sheet?.platform == ChatbotChannelPlatform.Feishu }.eventSink(AgentChannelsEvents.Connect)
        awaitStateWhere { it.sheet?.feishuQrUrl == "https://accounts.feishu.cn/x?user_code=B" }
        awaitStateWhere { it.sheet == null }
        cancelAndIgnoreRemainingEvents()
    }
}

@Test
fun `present - feishu poll error sets sheet error`() = runTest {
    val service = FakeChatbotApiService().apply {
        connectAgentChannelResult = { _, _ ->
            Result.success(ChatbotConnectChannelResponse(installationId = "fs3", platform = ChatbotChannelPlatform.Feishu, status = "pending", qrUrl = "https://accounts.feishu.cn/x?user_code=C"))
        }
        getAgentChannelResult = { _, installationId ->
            Result.success(ChatbotChannelSummary(installationId = installationId, platform = ChatbotChannelPlatform.Feishu, status = "error", label = ""))
        }
    }
    val presenter = createPresenter(service)
    presenter.test {
        awaitItem().eventSink(AgentChannelsEvents.OpenAdd)
        awaitStateWhere { it.sheet != null }.eventSink(AgentChannelsEvents.SetPlatform(ChatbotChannelPlatform.Feishu))
        awaitStateWhere { it.sheet?.platform == ChatbotChannelPlatform.Feishu }.eventSink(AgentChannelsEvents.Connect)
        val errored = awaitStateWhere { it.sheet?.error != null }
        assertThat(errored.sheet?.isFeishuPanel).isTrue()
        cancelAndIgnoreRemainingEvents()
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```
cd /Users/jelf/Projects/work/unseal-android
./gradlew --no-daemon :features:agentmanagement:impl:testDebugUnitTest --tests "*AgentChannelsPresenterTest*" > /tmp/t3.log 2>&1
grep -E 'BUILD SUCCESSFUL|BUILD FAILED|FAILED|error:' /tmp/t3.log | tail -15
```
Expected: FAIL — compile errors (`ChannelSheetState` has no `isFeishuPanel`/`feishuQrUrl`; `ChatbotChannelPlatform.Feishu` is referenced but the presenter has no Feishu connect handling). That compile failure is the RED.

- [ ] **Step 3: Add the Feishu state fields to `AgentChannelsState.kt`**

Extend `ChannelSheetState` (add the three fields with defaults + `isFeishuPanel`; extend `canConnect`):
```kotlin
data class ChannelSheetState(
    val editInstallationId: String?,
    val platform: ChatbotChannelPlatform,
    val botToken: String,
    val wecomToken: String,
    val wecomAesKey: String,
    val busy: Boolean,
    val error: String?,
    /** Non-null ⇒ show the WeCom callback panel (created/loaded). */
    val callbackUrl: String?,
    val installationId: String?,
    val connectedToken: String,
    val connectedAesKey: String,
    val feishuInstallationId: String? = null,
    val feishuQrUrl: String? = null,
    val feishuExpired: Boolean = false,
) {
    val isEditMode: Boolean get() = editInstallationId != null
    val isFeishuPanel: Boolean get() = feishuInstallationId != null
    val credsChanged: Boolean
        get() = callbackUrl != null && (wecomToken != connectedToken || wecomAesKey != connectedAesKey)
    val canConnect: Boolean
        get() = when (platform) {
            ChatbotChannelPlatform.Feishu -> true
            ChatbotChannelPlatform.Telegram -> botToken.isNotBlank()
            ChatbotChannelPlatform.WeCom -> wecomToken.isNotBlank() && wecomAesKey.length == WECOM_AES_KEY_LENGTH
        }
}
```

- [ ] **Step 4: Add the Feishu connect branch + polling to `AgentChannelsPresenter.kt`**

Add imports at the top (with the other imports):
```kotlin
import androidx.compose.runtime.LaunchedEffect
import io.element.android.libraries.chatbot.api.model.channels.ChatbotConnectChannelResponse
import kotlinx.coroutines.delay
```
(Note: `ChatbotConnectChannelResponse` is already imported; do not duplicate. `LaunchedEffect` and `delay` are the new ones.)

Add the poll constants at file scope (next to `RANDOM_CHARS`):
```kotlin
private const val FEISHU_POLL_DELAY_MILLIS = 2_000L
private const val FEISHU_MAX_POLL_ATTEMPTS = 150
```

In `connect()`, add a Feishu branch as the FIRST thing (before the Telegram/WeCom `body` building):
```kotlin
fun connect() {
    val current = sheet ?: return
    if (current.platform == ChatbotChannelPlatform.Feishu) {
        sheet = current.copy(busy = true, error = null)
        coroutineScope.launch {
            api().connectAgentChannel(agentId, ChatbotChannelConnectBody.Feishu)
                .onSuccess { res ->
                    sheet = sheet?.copy(busy = false, feishuInstallationId = res.installationId, feishuQrUrl = res.qrUrl)
                }
                .onFailure { sheet = sheet?.copy(busy = false, error = it.message ?: it::class.simpleName ?: "Failed to connect Feishu.") }
        }
        return
    }
    val body: ChatbotChannelConnectBody = if (current.platform == ChatbotChannelPlatform.Telegram) {
        // …unchanged…
```
(Leave the rest of `connect()` exactly as-is below the inserted branch.)

Add the polling effect inside `present()`, after the `pendingDelete`/`hasLoadedOnce` state declarations and before `return AgentChannelsState(...)` (it must be at the composable top level of `present()`, not inside `handleEvent`):
```kotlin
LaunchedEffect(sheet?.feishuInstallationId) {
    val installationId = sheet?.feishuInstallationId ?: return@LaunchedEffect
    repeat(FEISHU_MAX_POLL_ATTEMPTS) {
        delay(FEISHU_POLL_DELAY_MILLIS)
        val summary = api().getAgentChannel(agentId, installationId).getOrNull()
        if (summary != null) {
            if (sheet?.feishuQrUrl == null && summary.qrUrl != null) {
                sheet = sheet?.copy(feishuQrUrl = summary.qrUrl)
            }
            when (summary.status) {
                "active" -> {
                    sheet = null
                    loadChannels(isInitial = false)
                    return@LaunchedEffect
                }
                "error" -> {
                    sheet = sheet?.copy(error = "Couldn't connect Feishu. Please try again.")
                    return@LaunchedEffect
                }
            }
        }
    }
    sheet = sheet?.copy(feishuExpired = true)
}
```
(Closing the sheet sets `sheet = null` → the `LaunchedEffect` key `sheet?.feishuInstallationId` becomes null → the effect's coroutine is cancelled automatically. No manual job tracking.)

- [ ] **Step 5: Run tests to verify they pass**

```
cd /Users/jelf/Projects/work/unseal-android
./gradlew --no-daemon :features:agentmanagement:impl:testDebugUnitTest --tests "*AgentChannelsPresenterTest*" > /tmp/t3.log 2>&1
grep -E 'BUILD SUCCESSFUL|BUILD FAILED|FAILED|Tests:|completed' /tmp/t3.log | tail -15
```
Expected: `BUILD SUCCESSFUL` — all tests (4 existing + 3 new feishu) pass.

- [ ] **Step 6: Commit**

```bash
git add features/agentmanagement/impl/src/main/kotlin/io/element/android/features/agentmanagement/impl/channels/AgentChannelsState.kt features/agentmanagement/impl/src/main/kotlin/io/element/android/features/agentmanagement/impl/channels/AgentChannelsPresenter.kt features/agentmanagement/impl/src/test/kotlin/io/element/android/features/agentmanagement/impl/channels/AgentChannelsPresenterTest.kt
git commit -m "feat(channels): feishu connect + device-auth polling in presenter

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01Dyw2vNsZuZvvzYRQGeCGQw"
```

---

### Task 4: Brand identity + empty-state copy

**Files:**
- Modify: `features/agentmanagement/impl/src/main/kotlin/io/element/android/features/agentmanagement/impl/channels/AgentChannelsView.kt`
- Modify: `features/agentmanagement/impl/src/main/kotlin/io/element/android/features/agentmanagement/impl/detail/AgentDetailView.kt` (only the empty-state hint copy, if it mentions Telegram/WeCom)

**Interfaces:**
- Consumes: `ChatbotChannelPlatform.Feishu` (Task 1).
- Produces: `brandColor()`/`displayName()`/`PlatformBadge` cover Feishu; empty-state hint genericized.

- [ ] **Step 1: Add the Feishu brand to `AgentChannelsView.kt`**

Add the brand constant next to the others:
```kotlin
private val TelegramBrand = Color(0xFF229ED9)
private val WeComBrand = Color(0xFF07C160)
private val FeishuBrand = Color(0xFF3370FF)
```

Extend `brandColor()` and `displayName()`:
```kotlin
private fun ChatbotChannelPlatform.brandColor(): Color = when (this) {
    ChatbotChannelPlatform.Telegram -> TelegramBrand
    ChatbotChannelPlatform.WeCom -> WeComBrand
    ChatbotChannelPlatform.Feishu -> FeishuBrand
}

private fun ChatbotChannelPlatform.displayName(): String = when (this) {
    ChatbotChannelPlatform.Telegram -> "Telegram"
    ChatbotChannelPlatform.WeCom -> "WeCom"
    ChatbotChannelPlatform.Feishu -> "飞书"
}
```

Update `PlatformBadge`'s icon selection to cover Feishu (brand color carries the distinction; `CompoundIcons.Chat()` is confirmed-existing):
```kotlin
val icon = when (platform) {
    ChatbotChannelPlatform.Telegram -> CompoundIcons.Send()
    else -> CompoundIcons.Chat()
}
```

- [ ] **Step 2: Genericize the empty-state hint in `AgentChannelsView.kt`**

In `EmptyState()`, change the hint line:
```kotlin
Text(
    "Connect a channel so this agent can reply there.",
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Center,
)
```

- [ ] **Step 3: Genericize the same hint in `AgentDetailView.kt` if present**

Search `AgentDetailView.kt` for a "Connect Telegram or WeCom"-style empty hint in the ChannelsSection. If found, change it to "Connect a channel so this agent can reply there." If no such string exists, skip this step (note it in the report).

- [ ] **Step 4: Compile to verify exhaustiveness + format**

```
cd /Users/jelf/Projects/work/unseal-android
./gradlew --no-daemon :features:agentmanagement:impl:compileDebugKotlin > /tmp/t4.log 2>&1
grep -E 'BUILD SUCCESSFUL|BUILD FAILED|error:' /tmp/t4.log | tail -5
```
Expected: `BUILD SUCCESSFUL` (the `when` statements are now exhaustive over three platforms).

- [ ] **Step 5: Commit**

```bash
git add features/agentmanagement/impl/src/main/kotlin/io/element/android/features/agentmanagement/impl/channels/AgentChannelsView.kt features/agentmanagement/impl/src/main/kotlin/io/element/android/features/agentmanagement/impl/detail/AgentDetailView.kt
git commit -m "feat(channels): feishu brand identity + genericize empty-state hint

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01Dyw2vNsZuZvvzYRQGeCGQw"
```

---

### Task 5: View — Feishu picker card, auth panel, QR, applink

**Files:**
- Modify: `features/agentmanagement/impl/src/main/kotlin/io/element/android/features/agentmanagement/impl/channels/AgentChannelsView.kt`
- Modify: `features/agentmanagement/impl/build.gradle.kts` (add the qrcode module dependency)

**Interfaces:**
- Consumes: `sheet.isFeishuPanel`/`feishuQrUrl`/`feishuExpired` (Task 3); `ChatbotChannelPlatform.Feishu` + brand (Tasks 1, 4); `QrCodeImage` (`libraries/qrcode`); `LocalUriHandler`.
- Produces: the full Feishu connect UI. Terminal task.

- [ ] **Step 1: Add the qrcode Gradle dependency**

In `features/agentmanagement/impl/build.gradle.kts`, add to the `dependencies { }` block next to the other `implementation(projects.libraries.*)` lines:
```kotlin
implementation(projects.libraries.qrcode)
```

- [ ] **Step 2: Add imports + the applink helper to `AgentChannelsView.kt`**

Add imports (with the existing ones):
```kotlin
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalUriHandler
import io.element.android.libraries.qrcode.QrCodeImage
import java.net.URLEncoder
```
(Some may already be imported — do not duplicate. `LaunchedEffect` is already imported in this file.)

Add the applink helper at file scope (next to `brandColor()`):
```kotlin
private fun feishuAppLink(verificationUrl: String): String {
    val host = if (verificationUrl.contains("larksuite")) "applink.larksuite.com" else "applink.feishu.cn"
    val encoded = URLEncoder.encode(verificationUrl, "UTF-8")
    return "https://$host/client/web_url/open?url=$encoded"
}
```

- [ ] **Step 3: Add the third PlatformCard + hide credentials for Feishu (in `AddForm`)**

Add the Feishu card to the platform `Row`:
```kotlin
SectionLabel("Platform")
Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    PlatformCard(ChatbotChannelPlatform.Telegram, sheet.platform == ChatbotChannelPlatform.Telegram) {
        eventSink(AgentChannelsEvents.SetPlatform(ChatbotChannelPlatform.Telegram))
    }
    PlatformCard(ChatbotChannelPlatform.WeCom, sheet.platform == ChatbotChannelPlatform.WeCom) {
        eventSink(AgentChannelsEvents.SetPlatform(ChatbotChannelPlatform.WeCom))
    }
    PlatformCard(ChatbotChannelPlatform.Feishu, sheet.platform == ChatbotChannelPlatform.Feishu) {
        eventSink(AgentChannelsEvents.SetPlatform(ChatbotChannelPlatform.Feishu))
    }
}
```

Wrap the existing credential fields + Connect row so they only render for non-Feishu. Replace the body of `AddForm` after the platform `Row` with:
```kotlin
when (sheet.platform) {
    ChatbotChannelPlatform.Telegram -> {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = sheet.botToken,
            onValueChange = { eventSink(AgentChannelsEvents.SetBotToken(it)) },
            label = { Text("Bot Token") },
            placeholder = { Text("123456:ABC-DEF…") },
            singleLine = true,
        )
        Text("Get this from @BotFather in Telegram.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    ChatbotChannelPlatform.WeCom -> {
        GeneratableField("Token", sheet.wecomToken, { eventSink(AgentChannelsEvents.SetWecomToken(it)) }) {
            eventSink(AgentChannelsEvents.GenerateToken)
        }
        GeneratableField("EncodingAESKey", sheet.wecomAesKey, { eventSink(AgentChannelsEvents.SetWecomAesKey(it)) }) {
            eventSink(AgentChannelsEvents.GenerateAesKey)
        }
    }
    ChatbotChannelPlatform.Feishu -> {
        Text(
            "Feishu needs no keys — tap Connect, then approve in the Feishu app.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

sheet.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    OutlinedButton(modifier = Modifier.weight(1f), enabled = !sheet.busy, onClick = { eventSink(AgentChannelsEvents.CloseSheet) }) {
        Text("Cancel")
    }
    Button(modifier = Modifier.weight(1f), enabled = !sheet.busy && sheet.canConnect, onClick = { eventSink(AgentChannelsEvents.Connect) }) {
        Text(if (sheet.busy) "Connecting…" else "Connect")
    }
}
```

- [ ] **Step 4: Route the sheet body to the Feishu panel**

In `AddChannelSheet`, update the title `when` and the body `when` to handle the Feishu panel first:
```kotlin
val title = when {
    sheet.isFeishuPanel -> "Authorize in Feishu"
    sheet.isEditMode -> "Edit WeCom channel"
    sheet.callbackUrl != null -> "Finish WeCom setup"
    else -> "Add a channel"
}
Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)

when {
    sheet.isFeishuPanel -> FeishuPanel(sheet)
    sheet.isEditMode && sheet.callbackUrl == null -> {
        Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
            if (sheet.error != null) {
                Text(sheet.error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            } else {
                CircularProgressIndicator()
            }
        }
    }
    sheet.callbackUrl != null -> CallbackPanel(sheet, eventSink)
    else -> AddForm(sheet, eventSink)
}
```

- [ ] **Step 5: Add the `FeishuPanel` composable**

Add next to `CallbackPanel`:
```kotlin
@Composable
private fun FeishuPanel(sheet: ChannelSheetState) {
    val uriHandler = LocalUriHandler.current
    var showQr by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PlatformBadge(ChatbotChannelPlatform.Feishu, 60.dp)
        Text("Authorize in Feishu", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(
            "Open Feishu as a workspace admin and approve — it connects automatically in a few seconds.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        when {
            sheet.feishuExpired -> {
                Text(
                    "This link has expired. Close and try again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
            sheet.feishuQrUrl != null -> {
                val qrUrl = sheet.feishuQrUrl
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { uriHandler.openUri(feishuAppLink(qrUrl)) },
                ) {
                    Text("Open in Feishu to authorize")
                }
                TextButton(onClick = { showQr = !showQr }) { Text("Scan with another device") }
                if (showQr) {
                    Box(
                        modifier = Modifier
                            .background(Color.White, RoundedCornerShape(18.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        QrCodeImage(data = qrUrl, modifier = Modifier.size(180.dp))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Text("Waiting for you to approve…", style = MaterialTheme.typography.bodySmall, color = FeishuBrand)
                }
            }
            else -> {
                CircularProgressIndicator()
                Text("Generating a secure link…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
```
(Verify the `QrCodeImage` parameter names against `libraries/qrcode/.../QrCodeImage.kt` — it is `data: String` + `modifier`. If `forceMaxBrightness` is required (no default), pass `forceMaxBrightness = false`.)

- [ ] **Step 6: Compile + run module tests (regression)**

```
cd /Users/jelf/Projects/work/unseal-android
./gradlew --no-daemon ktlintFormat > /tmp/t5fmt.log 2>&1
./gradlew --no-daemon :features:agentmanagement:impl:testDebugUnitTest > /tmp/t5.log 2>&1
grep -E 'BUILD SUCCESSFUL|BUILD FAILED|FAILED|error:' /tmp/t5.log | tail -10
```
Expected: `BUILD SUCCESSFUL`; presenter tests still pass (7 total). Manual device check (a real Feishu admin account is needed for full auth) verifies the panel's three states (generating / open+QR / expired) and the applink app-handoff — same as the iOS device verification.

- [ ] **Step 7: Commit**

```bash
git add features/agentmanagement/impl/src/main/kotlin/io/element/android/features/agentmanagement/impl/channels/AgentChannelsView.kt features/agentmanagement/impl/build.gradle.kts
git commit -m "feat(channels): feishu connect panel UI (applink open + QR fallback)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01Dyw2vNsZuZvvzYRQGeCGQw"
```

---

## Self-Review

**Spec coverage:**
- Part A (models: Feishu enum, qrUrl/status, Feishu body, connect serialization) → Task 1. ✓
- Part B (`getAgentChannel` + fake) → Task 2. ✓
- Part C (state fields + connect branch + polling LaunchedEffect) → Task 3. ✓
- Part D (brand color/name/icon + empty-state copy) → Task 4. ✓
- Part E (picker card, hide creds, panel applink + QrCodeImage + states, gradle dep) → Task 5. ✓
- Testing (connect-enters-panel, qrUrl-late→active, error) → Task 3. ✓
- YAGNI (no edit-feishu, no push signal, hardcoded strings) → respected. ✓

**Placeholder scan:** No "TBD/handle errors" — every step has full code. The two "verify against the file" notes (the Feishu `CompoundIcons` glyph in Task 4; `QrCodeImage` parameter names in Task 5) name a concrete default (`CompoundIcons.Chat()`, `data`/`modifier`/`forceMaxBrightness=false`) and a one-line check — not open-ended work.

**Type consistency:**
- `ChatbotChannelConnectBody.Feishu` (data object, Task 1) — used in the presenter connect branch (Task 3) and asserted with `isEqualTo(ChatbotChannelConnectBody.Feishu)` in the test (a data object has structural equality). ✓
- `getAgentChannel(agentId, installationId)` (Task 2) → `FakeChatbotApiService.getAgentChannelResult` used by Task 3 tests. ✓
- `ChannelSheetState.{feishuInstallationId, feishuQrUrl, feishuExpired, isFeishuPanel}` (Task 3) → consumed by the View (Tasks 4/5). ✓
- `FeishuBrand` defined in Task 4, used by `FeishuPanel` (Task 5) — both in `AgentChannelsView.kt`. ✓
- `feishuAppLink()` defined Task 5, used in `FeishuPanel` (Task 5). ✓
- `connect()` keeps its signature; the Feishu branch returns before the Telegram/WeCom path (Task 3). ✓

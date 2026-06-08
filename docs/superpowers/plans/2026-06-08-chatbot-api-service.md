# Chatbot API Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate the iOS `ChatbotAPI` service foundation into Android as reusable `libraries:chatbot` modules for later Agent, Skills, Schedules, Webhooks, Credits, and Connectors features.

**Architecture:** Add API, implementation, and test-helper modules under `libraries/chatbot`. The API module owns typed models and the `ChatbotApiService` facade; the impl module owns token lookup, base URL resolution, HTTP request execution, flexible JSON decoding, and Metro bindings; the test module owns fakes and fixtures for later presenter specs. Behavior must follow the iOS files in `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Services/ChatbotAPI`.

**Tech Stack:** Kotlin, kotlinx.serialization, OkHttp, Retrofit-compatible project networking, Metro DI, MockWebServer, Truth, coroutine tests.

---

## File Structure

Create:

- `libraries/chatbot/api/build.gradle.kts`
  - Android library module with Kotlin serialization.
- `libraries/chatbot/api/src/main/kotlin/io/element/android/libraries/chatbot/api/ChatbotApiService.kt`
  - Public facade mirroring iOS `ChatbotAPIClientProtocol` for in-scope endpoints.
- `libraries/chatbot/api/src/main/kotlin/io/element/android/libraries/chatbot/api/ChatbotApiServiceFactory.kt`
  - Public factory boundary for AI stream, Unseal API, and explicit base URL clients.
- `libraries/chatbot/api/src/main/kotlin/io/element/android/libraries/chatbot/api/ChatbotApiError.kt`
  - Error model matching iOS `ChatbotAPIError`.
- `libraries/chatbot/api/src/main/kotlin/io/element/android/libraries/chatbot/api/ChatbotBaseUrlResolver.kt`
  - Resolves `org.unseal.api.base_url` from Matrix well-known with fallback.
- `libraries/chatbot/api/src/main/kotlin/io/element/android/libraries/chatbot/api/ChatbotConfig.kt`
  - Constants for `https://api.unseal.network` and `https://agent-api.unseal.network`.
- `libraries/chatbot/api/src/main/kotlin/io/element/android/libraries/chatbot/api/model/agent/AgentModels.kt`
  - Agent, provider, room, sandbox/vault wrapper models needed by agent requests.
- `libraries/chatbot/api/src/main/kotlin/io/element/android/libraries/chatbot/api/model/skills/SkillModels.kt`
  - User skill, public skill, room agent skill, workspace skill models.
- `libraries/chatbot/api/src/main/kotlin/io/element/android/libraries/chatbot/api/model/schedules/ScheduleModels.kt`
  - Schedule request/response models.
- `libraries/chatbot/api/src/main/kotlin/io/element/android/libraries/chatbot/api/model/rooms/RoomModels.kt`
  - Room working memory and room agent models.
- `libraries/chatbot/api/src/main/kotlin/io/element/android/libraries/chatbot/api/model/connectors/ConnectorModels.kt`
  - Composio connector/toolkit/account models.
- `libraries/chatbot/api/src/main/kotlin/io/element/android/libraries/chatbot/api/model/webhooks/WebhookModels.kt`
  - Webhook trigger and event catalog models.
- `libraries/chatbot/api/src/main/kotlin/io/element/android/libraries/chatbot/api/model/credits/CreditModels.kt`
  - Credit balance, ledger, daily usage, payment intent models.
- `libraries/chatbot/api/src/main/kotlin/io/element/android/libraries/chatbot/api/model/analytics/AnalyticsModels.kt`
  - Analytics token usage models.
- `libraries/chatbot/api/src/main/kotlin/io/element/android/libraries/chatbot/api/model/json/ChatbotJsonValue.kt`
  - Flexible JSON value and object aliases for iOS `[String: Any]` request bodies.

Create:

- `libraries/chatbot/impl/build.gradle.kts`
  - Implementation module with Metro, OkHttp, Retrofit, session storage, Matrix API, serialization.
- `libraries/chatbot/impl/src/main/kotlin/io/element/android/libraries/chatbot/impl/DefaultChatbotApiServiceFactory.kt`
  - Creates service instances with resolved base URLs and Matrix token provider.
- `libraries/chatbot/impl/src/main/kotlin/io/element/android/libraries/chatbot/impl/DefaultChatbotBaseUrlResolver.kt`
  - Fetches and caches `org.unseal.api.base_url`.
- `libraries/chatbot/impl/src/main/kotlin/io/element/android/libraries/chatbot/impl/DefaultChatbotAccessTokenProvider.kt`
  - Reads the current access token from `SessionStore`.
- `libraries/chatbot/impl/src/main/kotlin/io/element/android/libraries/chatbot/impl/ChatbotAccessTokenProvider.kt`
  - Internal token provider boundary.
- `libraries/chatbot/impl/src/main/kotlin/io/element/android/libraries/chatbot/impl/DefaultChatbotApiService.kt`
  - Endpoint facade implementation.
- `libraries/chatbot/impl/src/main/kotlin/io/element/android/libraries/chatbot/impl/ChatbotHttpClient.kt`
  - Raw authenticated request executor with iOS-equivalent error mapping.
- `libraries/chatbot/impl/src/main/kotlin/io/element/android/libraries/chatbot/impl/ChatbotHttpMethod.kt`
  - HTTP method enum.
- `libraries/chatbot/impl/src/main/kotlin/io/element/android/libraries/chatbot/impl/ChatbotJson.kt`
  - Shared JSON encode/decode helpers.
- `libraries/chatbot/impl/src/main/kotlin/io/element/android/libraries/chatbot/impl/ChatbotRedactor.kt`
  - Secret redaction helper.
- `libraries/chatbot/impl/src/main/kotlin/io/element/android/libraries/chatbot/impl/ChatbotUrlBuilder.kt`
  - Path/query construction helpers.
- `libraries/chatbot/impl/src/main/kotlin/io/element/android/libraries/chatbot/impl/model/InternalUnsealWellKnown.kt`
  - Internal `.well-known/matrix/client` model with `org.unseal.api`.
- `libraries/chatbot/impl/src/main/kotlin/io/element/android/libraries/chatbot/impl/di/ChatbotModule.kt`
  - Metro bindings for the factory, resolver, and token provider.

Create tests:

- `libraries/chatbot/impl/src/test/kotlin/io/element/android/libraries/chatbot/impl/ChatbotRedactorTest.kt`
- `libraries/chatbot/impl/src/test/kotlin/io/element/android/libraries/chatbot/impl/ChatbotUrlBuilderTest.kt`
- `libraries/chatbot/impl/src/test/kotlin/io/element/android/libraries/chatbot/impl/DefaultChatbotAccessTokenProviderTest.kt`
- `libraries/chatbot/impl/src/test/kotlin/io/element/android/libraries/chatbot/impl/DefaultChatbotBaseUrlResolverTest.kt`
- `libraries/chatbot/impl/src/test/kotlin/io/element/android/libraries/chatbot/impl/ChatbotHttpClientTest.kt`
- `libraries/chatbot/impl/src/test/kotlin/io/element/android/libraries/chatbot/impl/DefaultChatbotApiServiceTest.kt`

Create:

- `libraries/chatbot/test/build.gradle.kts`
- `libraries/chatbot/test/src/main/kotlin/io/element/android/libraries/chatbot/test/FakeChatbotApiService.kt`
- `libraries/chatbot/test/src/main/kotlin/io/element/android/libraries/chatbot/test/FakeChatbotApiServiceFactory.kt`
- `libraries/chatbot/test/src/main/kotlin/io/element/android/libraries/chatbot/test/FakeChatbotBaseUrlResolver.kt`
- `libraries/chatbot/test/src/main/kotlin/io/element/android/libraries/chatbot/test/ChatbotFixtures.kt`

Documentation:

- Existing spec: `docs/superpowers/specs/2026-06-08-chatbot-api-service-design.md`
- This plan: `docs/superpowers/plans/2026-06-08-chatbot-api-service.md`

## Task 1: Add Chatbot Modules

**Files:**
- Create: `libraries/chatbot/api/build.gradle.kts`
- Create: `libraries/chatbot/impl/build.gradle.kts`
- Create: `libraries/chatbot/test/build.gradle.kts`

- [ ] **Step 1: Create the API module build file**

Create `libraries/chatbot/api/build.gradle.kts`:

```kotlin
/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

plugins {
    id("io.element.android-library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.element.android.libraries.chatbot.api"
}

dependencies {
    implementation(libs.serialization.json)
    implementation(projects.libraries.matrix.api)
}
```

- [ ] **Step 2: Create the impl module build file**

Create `libraries/chatbot/impl/build.gradle.kts`:

```kotlin
import extension.setupDependencyInjection
import extension.testCommonDependencies

/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

plugins {
    id("io.element.android-library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.element.android.libraries.chatbot.impl"
}

setupDependencyInjection()

dependencies {
    api(projects.libraries.chatbot.api)
    implementation(libs.coroutines.core)
    implementation(libs.serialization.json)
    implementation(platform(libs.network.okhttp.bom))
    implementation(libs.network.okhttp)
    implementation(projects.libraries.androidutils)
    implementation(projects.libraries.core)
    implementation(projects.libraries.di)
    implementation(projects.libraries.matrix.api)
    implementation(projects.libraries.network)
    implementation(projects.libraries.sessionStorage.api)

    testCommonDependencies(libs)
    testImplementation(libs.network.mockwebserver)
    testImplementation(projects.libraries.matrix.test)
    testImplementation(projects.libraries.sessionStorage.test)
}
```

- [ ] **Step 3: Create the test helper module build file**

Create `libraries/chatbot/test/build.gradle.kts`:

```kotlin
/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

plugins {
    id("io.element.android-library")
}

android {
    namespace = "io.element.android.libraries.chatbot.test"
}

dependencies {
    implementation(libs.coroutines.core)
    implementation(projects.libraries.chatbot.api)
    implementation(projects.libraries.matrix.api)
    implementation(projects.tests.testutils)
}
```

- [ ] **Step 4: Verify Gradle sees the modules**

Run:

```bash
env -u http_proxy -u https_proxy -u all_proxy -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u NO_PROXY -u no_proxy \
  JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools \
  ./gradlew --no-daemon --no-configuration-cache \
  :libraries:chatbot:api:tasks \
  :libraries:chatbot:impl:tasks \
  :libraries:chatbot:test:tasks
```

Expected: PASS and Gradle lists tasks for all three modules.

## Task 2: Add Public API Contracts And Models

**Files:**
- Create: `libraries/chatbot/api/src/main/kotlin/io/element/android/libraries/chatbot/api/ChatbotApiError.kt`
- Create: `libraries/chatbot/api/src/main/kotlin/io/element/android/libraries/chatbot/api/ChatbotConfig.kt`
- Create: `libraries/chatbot/api/src/main/kotlin/io/element/android/libraries/chatbot/api/ChatbotBaseUrlResolver.kt`
- Create: `libraries/chatbot/api/src/main/kotlin/io/element/android/libraries/chatbot/api/ChatbotApiServiceFactory.kt`
- Create: `libraries/chatbot/api/src/main/kotlin/io/element/android/libraries/chatbot/api/ChatbotApiService.kt`
- Create model files under `libraries/chatbot/api/src/main/kotlin/io/element/android/libraries/chatbot/api/model/...`

- [ ] **Step 1: Add the error and config contracts**

Create `ChatbotApiError.kt`:

```kotlin
package io.element.android.libraries.chatbot.api

sealed class ChatbotApiError(message: String? = null, cause: Throwable? = null) : Exception(message, cause) {
    data object InvalidBaseUrl : ChatbotApiError("Invalid API base URL.")
    data object MissingAccessToken : ChatbotApiError("Missing access token.")
    data object InvalidResponse : ChatbotApiError("Invalid server response.")
    data class HttpError(val statusCode: Int, val body: String?) : ChatbotApiError("Server error ($statusCode).")
    data class DecodingError(val bodySnippet: String?) : ChatbotApiError("Failed to decode server response.")
    data object EncodingError : ChatbotApiError("Failed to encode request.")
    data class NetworkError(val description: String, val original: Throwable? = null) : ChatbotApiError("Network error: $description", original)
}
```

Create `ChatbotConfig.kt`:

```kotlin
package io.element.android.libraries.chatbot.api

object ChatbotConfig {
    const val AI_STREAM_BASE_URL = "https://api.unseal.network"
    const val UNSEAL_API_FALLBACK_BASE_URL = "https://agent-api.unseal.network"
}
```

- [ ] **Step 2: Add resolver and factory contracts**

Create `ChatbotBaseUrlResolver.kt`:

```kotlin
package io.element.android.libraries.chatbot.api

interface ChatbotBaseUrlResolver {
    suspend fun resolveUnsealApiBaseUrl(serverName: String?): String
}
```

Create `ChatbotApiServiceFactory.kt`:

```kotlin
package io.element.android.libraries.chatbot.api

import io.element.android.libraries.matrix.api.MatrixClient

interface ChatbotApiServiceFactory {
    fun createForAiStream(matrixClient: MatrixClient): ChatbotApiService
    suspend fun createForUnsealApi(matrixClient: MatrixClient): ChatbotApiService
    fun createForBaseUrl(baseUrl: String, matrixClient: MatrixClient): ChatbotApiService
}
```

- [ ] **Step 3: Add the service facade**

Create `ChatbotApiService.kt` with the method signatures from `docs/superpowers/specs/2026-06-08-chatbot-api-service-design.md` under `### API Facade`. Return `Result<T>` for every method and use `ChatbotJsonObject` for flexible request bodies. The implementation in Task 6 must fail to compile if any method is missing, because `DefaultChatbotApiService` and `FakeChatbotApiService` both implement this interface.

- [ ] **Step 4: Add model files**

Create the model files listed in the File Structure section. Use `@Serializable` and explicit `@SerialName` for every JSON name that differs from Kotlin property casing. Preserve iOS names and fields from:

```bash
/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Services/ChatbotAPI/ChatbotAPIModels.swift
/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Services/ChatbotAPI/ChatbotAgentEnvironmentModels.swift
/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Services/ChatbotAPI/ChatbotIntegrationModels.swift
/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Services/ChatbotAPI/ChatbotWebhookTriggerModels.swift
```

Required JSON helper definitions in `ChatbotJsonValue.kt`:

```kotlin
package io.element.android.libraries.chatbot.api.model.json

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

typealias ChatbotJsonObject = JsonObject

@Serializable
sealed interface ChatbotJsonValue
```

If sealed arbitrary JSON values are needed during implementation, represent request bodies as `JsonObject` and model flexible response metadata as `Map<String, kotlinx.serialization.json.JsonElement>`.

- [ ] **Step 5: Verify API module compiles**

Run:

```bash
env -u http_proxy -u https_proxy -u all_proxy -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u NO_PROXY -u no_proxy \
  JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools \
  ./gradlew --no-daemon --no-configuration-cache :libraries:chatbot:api:compileDebugKotlin
```

Expected: PASS.

## Task 3: Add Token Provider, URL Helpers, And Redaction

**Files:**
- Create: `libraries/chatbot/impl/src/main/kotlin/io/element/android/libraries/chatbot/impl/ChatbotAccessTokenProvider.kt`
- Create: `libraries/chatbot/impl/src/main/kotlin/io/element/android/libraries/chatbot/impl/DefaultChatbotAccessTokenProvider.kt`
- Create: `libraries/chatbot/impl/src/main/kotlin/io/element/android/libraries/chatbot/impl/ChatbotRedactor.kt`
- Create: `libraries/chatbot/impl/src/main/kotlin/io/element/android/libraries/chatbot/impl/ChatbotUrlBuilder.kt`
- Test: `libraries/chatbot/impl/src/test/kotlin/io/element/android/libraries/chatbot/impl/DefaultChatbotAccessTokenProviderTest.kt`
- Test: `libraries/chatbot/impl/src/test/kotlin/io/element/android/libraries/chatbot/impl/ChatbotRedactorTest.kt`
- Test: `libraries/chatbot/impl/src/test/kotlin/io/element/android/libraries/chatbot/impl/ChatbotUrlBuilderTest.kt`

- [ ] **Step 1: Write token provider tests**

Create tests asserting:

```kotlin
@Test
fun `accessToken returns stored non blank token`() = runTest { /* InMemorySessionStore contains matching session id */ }

@Test
fun `accessToken returns null when session is missing`() = runTest { /* store empty */ }

@Test
fun `accessToken returns null when token is blank`() = runTest { /* matching session has blank token */ }
```

- [ ] **Step 2: Implement token provider**

Implement `ChatbotAccessTokenProvider` and `DefaultChatbotAccessTokenProvider` using `SessionStore.getSession(matrixClient.sessionId.value)`.

- [ ] **Step 3: Write redaction tests**

Create tests asserting that `ChatbotRedactor.redact(...)` redacts:

```json
{"api_key":"secret","apiKey":"secret","access_token":"secret","accessToken":"secret","secret_access_key":"secret","session_token":"secret","Authorization":"Bearer secret"}
```

Expected output contains `[REDACTED]` and does not contain `Bearer secret`.

- [ ] **Step 4: Implement redaction**

Implement regex-based redaction for the keys in the spec. Keep it package-internal.

- [ ] **Step 5: Write URL helper tests**

Create tests for:

```kotlin
assertThat(ChatbotUrlBuilder.path("/chatbot/v1/rooms/{roomId}/agents", mapOf("roomId" to "!room:server")))
    .isEqualTo("/chatbot/v1/rooms/%21room%3Aserver/agents")

assertThat(ChatbotUrlBuilder.query(mapOf("cursor" to "a/b+c", "empty" to "")))
    .isEqualTo("?cursor=a%2Fb%2Bc&empty=")
```

- [ ] **Step 6: Implement URL helpers**

Use OkHttp `HttpUrl.Builder` or `String.canonicalize` behavior through `toHttpUrl` builders. Do not hand-concatenate unencoded user input.

- [ ] **Step 7: Verify helper tests**

Run:

```bash
env -u http_proxy -u https_proxy -u all_proxy -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u NO_PROXY -u no_proxy \
  JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools \
  ./gradlew --no-daemon --no-configuration-cache \
  :libraries:chatbot:impl:testDebugUnitTest \
  --tests 'io.element.android.libraries.chatbot.impl.*Token*' \
  --tests 'io.element.android.libraries.chatbot.impl.*Redactor*' \
  --tests 'io.element.android.libraries.chatbot.impl.*UrlBuilder*'
```

Expected: PASS.

## Task 4: Add Base URL Resolver

**Files:**
- Create: `libraries/chatbot/impl/src/main/kotlin/io/element/android/libraries/chatbot/impl/model/InternalUnsealWellKnown.kt`
- Create: `libraries/chatbot/impl/src/main/kotlin/io/element/android/libraries/chatbot/impl/DefaultChatbotBaseUrlResolver.kt`
- Test: `libraries/chatbot/impl/src/test/kotlin/io/element/android/libraries/chatbot/impl/DefaultChatbotBaseUrlResolverTest.kt`

- [ ] **Step 1: Write resolver tests**

Use a fake fetch lambda or `FakeMatrixClient.getUrl` to assert:

- `resolveUnsealApiBaseUrl(null)` returns `https://agent-api.unseal.network`.
- Missing `org.unseal.api` returns fallback.
- Invalid JSON returns fallback.
- `{"org.unseal.api":{"base_url":"https://agent.example"}}` returns `https://agent.example`.
- Two calls for the same server hit fetch once and return the cached value.

- [ ] **Step 2: Implement resolver**

Implementation requirements:

- `@ContributesBinding(AppScope::class)` for `ChatbotBaseUrlResolver`.
- Internal cache is `MutableMap<String, String>`.
- Fetch URL is `https://<serverName>/.well-known/matrix/client`.
- Parse `org.unseal.api.base_url`.
- Return fallback on every failure.

- [ ] **Step 3: Verify resolver tests**

Run:

```bash
env -u http_proxy -u https_proxy -u all_proxy -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u NO_PROXY -u no_proxy \
  JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools \
  ./gradlew --no-daemon --no-configuration-cache \
  :libraries:chatbot:impl:testDebugUnitTest \
  --tests 'io.element.android.libraries.chatbot.impl.DefaultChatbotBaseUrlResolverTest'
```

Expected: PASS.

## Task 5: Add Authenticated HTTP Client

**Files:**
- Create: `libraries/chatbot/impl/src/main/kotlin/io/element/android/libraries/chatbot/impl/ChatbotHttpMethod.kt`
- Create: `libraries/chatbot/impl/src/main/kotlin/io/element/android/libraries/chatbot/impl/ChatbotJson.kt`
- Create: `libraries/chatbot/impl/src/main/kotlin/io/element/android/libraries/chatbot/impl/ChatbotHttpClient.kt`
- Test: `libraries/chatbot/impl/src/test/kotlin/io/element/android/libraries/chatbot/impl/ChatbotHttpClientTest.kt`

- [ ] **Step 1: Write HTTP client tests**

Using `MockWebServer`, create one test function for each of these assertions:

- Missing token returns `ChatbotApiError.MissingAccessToken` and server receives no request.
- A GET request sets `Accept: application/json` and `Authorization: Bearer token`.
- A POST request with a body sets `Content-Type: application/json`.
- Non-2xx response returns `ChatbotApiError.HttpError` with redacted body.
- Invalid JSON when decoding returns `ChatbotApiError.DecodingError` with redacted body snippet.
- Network failure maps to `ChatbotApiError.NetworkError`.

- [ ] **Step 2: Implement JSON helpers**

`ChatbotJson` should expose:

```kotlin
internal val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    isLenient = true
}
```

Add inline helpers:

```kotlin
internal inline fun <reified T> decode(data: String): T = json.decodeFromString(data)
internal inline fun <reified T> encode(value: T): String = json.encodeToString(value)
```

- [ ] **Step 3: Implement HTTP client**

`ChatbotHttpClient` constructor dependencies:

- `baseUrl: String`
- `matrixClient: MatrixClient`
- `okHttpClient: OkHttpClient`
- `tokenProvider: ChatbotAccessTokenProvider`

Public/internal methods:

```kotlin
suspend fun requestRaw(pathWithQuery: String, method: ChatbotHttpMethod, body: String? = null): Result<String>
inline suspend fun <reified T> requestJson(pathWithQuery: String, method: ChatbotHttpMethod, body: String? = null): Result<T>
```

Behavior:

- Validate `baseUrl.toHttpUrlOrNull()`, otherwise return `InvalidBaseUrl`.
- Read token before each request.
- Build URL with `baseUrl` plus relative path.
- Set headers like iOS.
- Run OkHttp calls through the normal synchronous `Call.execute()` path and preserve `CancellationException` when mapping failures.
- Map failures to `ChatbotApiError`.

- [ ] **Step 4: Verify HTTP tests**

Run:

```bash
env -u http_proxy -u https_proxy -u all_proxy -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u NO_PROXY -u no_proxy \
  JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools \
  ./gradlew --no-daemon --no-configuration-cache \
  :libraries:chatbot:impl:testDebugUnitTest \
  --tests 'io.element.android.libraries.chatbot.impl.ChatbotHttpClientTest'
```

Expected: PASS.

## Task 6: Add API Service Endpoint Facade

**Files:**
- Create: `libraries/chatbot/impl/src/main/kotlin/io/element/android/libraries/chatbot/impl/DefaultChatbotApiService.kt`
- Create: `libraries/chatbot/impl/src/main/kotlin/io/element/android/libraries/chatbot/impl/DefaultChatbotApiServiceFactory.kt`
- Create: `libraries/chatbot/impl/src/main/kotlin/io/element/android/libraries/chatbot/impl/di/ChatbotModule.kt`
- Test: `libraries/chatbot/impl/src/test/kotlin/io/element/android/libraries/chatbot/impl/DefaultChatbotApiServiceTest.kt`

- [ ] **Step 1: Write endpoint behavior tests**

Use `MockWebServer` and `DefaultChatbotApiService` to create one test function for each of these assertions:

- `listAgents()` decodes a direct array.
- `listAgents()` decodes wrapped `{ "agents": [...] }`.
- `listAgentSkills()` decodes wrapped skills.
- `listAgentSkills()` decodes direct skill array.
- `createSchedule()` fails with `HttpError(200, "schedule not created on server")` when `eb_schedule_id` is blank or absent.
- `createSchedule()` returns the response when `eb_schedule_id` is present.
- A path with `roomId = "!room:server"` reaches `/chatbot/v1/rooms/%21room%3Aserver/working-memory`.
- `updateWebhookTriggerStatus(triggerId, true)` calls `/api/webhook-triggers/{id}/enable`.
- `updateWebhookTriggerStatus(triggerId, false)` calls `/api/webhook-triggers/{id}/disable`.

- [ ] **Step 2: Implement factory**

Factory behavior:

- `createForAiStream(matrixClient)` returns service with `ChatbotConfig.AI_STREAM_BASE_URL`.
- `createForUnsealApi(matrixClient)` resolves with `baseUrlResolver.resolveUnsealApiBaseUrl(matrixClient.userIdServerName())`.
- `createForBaseUrl(baseUrl, matrixClient)` returns service for the explicit URL.

Use injected `OkHttpClient`, `ChatbotAccessTokenProvider`, and `ChatbotBaseUrlResolver`.

- [ ] **Step 3: Implement facade methods**

Implement every method in `ChatbotApiService` with iOS-equivalent paths. For request bodies:

- Typed requests use `ChatbotJson.encode`.
- `ChatbotJsonObject` bodies use `body.toString()`.
- `Unit` endpoints call `requestRaw` and map success to `Unit`.
- Query endpoints use `ChatbotUrlBuilder.query`.
- Flexible decode endpoints use raw response and decode fallback shapes in the same order as iOS.

- [ ] **Step 4: Add Metro bindings**

Add `ChatbotModule.kt` with these bindings:

- `DefaultChatbotApiServiceFactory` to `ChatbotApiServiceFactory`.
- `DefaultChatbotBaseUrlResolver` to `ChatbotBaseUrlResolver`.

- [ ] **Step 5: Verify endpoint tests**

Run:

```bash
env -u http_proxy -u https_proxy -u all_proxy -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u NO_PROXY -u no_proxy \
  JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools \
  ./gradlew --no-daemon --no-configuration-cache \
  :libraries:chatbot:impl:testDebugUnitTest \
  --tests 'io.element.android.libraries.chatbot.impl.DefaultChatbotApiServiceTest'
```

Expected: PASS.

## Task 7: Add Test Fakes And Fixtures

**Files:**
- Create: `libraries/chatbot/test/src/main/kotlin/io/element/android/libraries/chatbot/test/FakeChatbotApiService.kt`
- Create: `libraries/chatbot/test/src/main/kotlin/io/element/android/libraries/chatbot/test/FakeChatbotApiServiceFactory.kt`
- Create: `libraries/chatbot/test/src/main/kotlin/io/element/android/libraries/chatbot/test/FakeChatbotBaseUrlResolver.kt`
- Create: `libraries/chatbot/test/src/main/kotlin/io/element/android/libraries/chatbot/test/ChatbotFixtures.kt`

- [ ] **Step 1: Implement fake service**

`FakeChatbotApiService` must implement every `ChatbotApiService` method and expose mutable result lambdas, for example:

```kotlin
class FakeChatbotApiService : ChatbotApiService {
    var listAgentsResult: () -> Result<List<ChatbotAgent>> = { Result.success(emptyList()) }
    override suspend fun listAgents(): Result<List<ChatbotAgent>> = simulateLongTask { listAgentsResult() }
}
```

Implement one mutable result lambda for every method in `ChatbotApiService`, with default success values for list/unit methods and minimal fixture values for required object methods, so later feature tests can configure success/failure behavior independently.

- [ ] **Step 2: Implement fake factory and resolver**

`FakeChatbotApiServiceFactory` should return a supplied fake service for all factory methods and record explicit base URLs.

`FakeChatbotBaseUrlResolver` should expose:

```kotlin
var resolveResult: (String?) -> String = { ChatbotConfig.UNSEAL_API_FALLBACK_BASE_URL }
val seenServerNames = mutableListOf<String?>()
```

- [ ] **Step 3: Add fixtures**

Add small fixtures:

- `aChatbotAgent(...)`
- `aChatbotUserSkill(...)`
- `aChatbotSchedule(...)`
- `aChatbotWebhookTrigger(...)`
- `aCreditBalance(...)`
- `aChatbotToolkit(...)`

Each fixture should fill only required fields and accept optional overrides for commonly asserted fields.

- [ ] **Step 4: Verify test module compiles**

Run:

```bash
env -u http_proxy -u https_proxy -u all_proxy -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u NO_PROXY -u no_proxy \
  JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools \
  ./gradlew --no-daemon --no-configuration-cache :libraries:chatbot:test:compileDebugKotlin
```

Expected: PASS.

## Task 8: Final Verification And Review

**Files:**
- All files from previous tasks.

- [ ] **Step 1: Run the feature verification command**

Run:

```bash
env -u http_proxy -u https_proxy -u all_proxy -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u NO_PROXY -u no_proxy \
  JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools \
  ./gradlew --no-daemon --no-configuration-cache \
  :libraries:chatbot:api:testDebugUnitTest \
  :libraries:chatbot:impl:testDebugUnitTest \
  :libraries:chatbot:test:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 2: Run compile against likely consumers**

Run:

```bash
env -u http_proxy -u https_proxy -u all_proxy -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u NO_PROXY -u no_proxy \
  JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools \
  ./gradlew --no-daemon --no-configuration-cache \
  :libraries:chatbot:impl:compileDebugKotlin \
  :libraries:matrix:api:compileDebugKotlin
```

Expected: PASS.

- [ ] **Step 3: Self-review against the spec**

Review `docs/superpowers/specs/2026-06-08-chatbot-api-service-design.md` and verify:

- No Agent/Skills/Schedules/Webhooks/Credits/Connectors UI was added.
- Voice endpoints/models were not added.
- No Matrix Rust SDK rebuild was introduced.
- No Unseal component library dependency was introduced.
- Every in-scope service method exists on `ChatbotApiService`.
- Base URL resolution, token lookup, redaction, flexible decode behavior, and fakes are covered by tests.

- [ ] **Step 4: Check git status**

Run:

```bash
git status --short
```

Expected: only chatbot module files, this plan, and the chatbot spec are changed or untracked.

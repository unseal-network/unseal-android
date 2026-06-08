# Chatbot API Service Design

Date: 2026-06-08

## Feature Boundary

Feature name: `chatbot-api-service`.

User-visible goal: Android should have the same authenticated Unseal Chatbot API foundation that iOS uses, so later Agent, Skills, Schedules, Webhooks, Credits, and Connectors feature specs can call typed services instead of each rebuilding networking, auth, URL resolution, and JSON models.

Dependency class: native Android implementable.

Blocked by Matrix Rust SDK artifact work: no.

Blocked by component library migration: no.

This spec migrates the iOS service layer contract and support behavior only. It does not implement feature screens.

In scope:

- Add Android modules for a reusable Unseal Chatbot API service.
- Mirror iOS `ChatbotAPIClientProtocol` as a Kotlin API facade with typed suspend functions.
- Mirror iOS base URL resolution, including user/session token injection and `.well-known/matrix/client` lookup for `org.unseal.api`.
- Mirror iOS request behavior: Bearer auth, JSON accept/content type, 2xx success handling, typed decode failures, HTTP body preservation with secret redaction, and missing-token handling.
- Mirror iOS path/query encoding rules for endpoint paths that contain Matrix room IDs, user IDs, agent IDs, trigger IDs, and cursors.
- Define Android data models for the API groups used by the native P1 feature queue: Agents, Skills, Room Agent Skills, Schedules, Working Memory, Room Agents, Webhooks, Credits, Connectors/Composio, and Analytics Tokens.
- Keep Agent Sandbox/Vault models present because iOS `ChatbotCreateAgentRequest` and `ChatbotUpdateAgentRequest` embed sandbox/vault wrappers, but do not implement Vault UI flows in this feature.
- Add fakes/test helpers so later feature specs can test presenters without real network calls.

Out of scope:

- Agent list/detail/create/edit UI.
- Skills marketplace UI.
- Agent Skills management UI.
- Room schedules UI.
- Webhook trigger UI.
- Credits UI and Stripe/payment screen behavior.
- Connector UI.
- Voice Library UI.
- Voice API endpoints and models. Voice is still pending classification in the migration index and should receive its own spec.
- MiniApp runtime.
- Local agent runtime, sandbox file operations, or Vault management screens.
- AWS S3 upload implementation beyond preserving the iOS-compatible API boundary and model contracts. If a later feature needs direct S3 upload, it should get its own small spec.

## iOS Source References

Primary iOS files:

- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Services/ChatbotAPI/ChatbotAPIClient.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Services/ChatbotAPI/ChatbotAPIClientFactory.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Services/ChatbotAPI/ChatbotAPIError.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Services/ChatbotAPI/ChatbotAPIModels.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Services/ChatbotAPI/ChatbotAgentEnvironmentModels.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Services/ChatbotAPI/ChatbotIntegrationModels.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Services/ChatbotAPI/ChatbotWebhookTriggerModels.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Services/Authentication/AuthenticationService.swift`
- `/Users/Ruihan/go/src/unseal-ios/ElementX/Sources/Application/Settings/AppSettings.swift`

Important iOS behaviors to preserve:

- `ChatbotAPIClientFactory.makeClient(...)` uses `appSettings.aiStreamBaseURL`, whose default is `https://api.unseal.network`.
- `ChatbotAPIClientFactory.makeUnsealAPIClient(...)` resolves `org.unseal.api.base_url` from `https://<serverName>/.well-known/matrix/client`, caches the result per server name, and falls back to `https://agent-api.unseal.network`.
- Every normal API request requires a non-empty Matrix access token. Missing token maps to `ChatbotAPIError.missingAccessToken`.
- Requests set `Accept: application/json` and `Authorization: Bearer <token>`. Requests with bodies set `Content-Type: application/json`.
- Non-2xx HTTP responses map to `httpError(statusCode, body)` with a redacted body.
- Decode failures include a redacted body snippet and log the decode failure details.
- Redaction covers `api_key` and `apiKey`.
- JSON encoder/decoder use default key strategies. iOS models define explicit snake_case `CodingKeys` rather than automatic conversion.
- `listAgents()` accepts both a raw array and wrapped `{ "agents": [...] }`.
- `listAgentSkills()` accepts wrapped `{ "skills": [...] }` and direct arrays, mapping `ChatbotAgentSkillItem` to `ChatbotUserSkill`.
- `presignedUploadUrls(...)` accepts several response shapes: `uploads`, `presigned_urls`, `urls`, `data`, direct arrays, or URL strings.
- `createSchedule(...)` treats a response without a non-empty `eb_schedule_id` as a failure, even when HTTP status is 200.
- Path parameters are percent encoded with URL path rules. Query parameters are percent encoded with URL query rules.

Important iOS endpoint groups:

- Agents: `/chatbot/v1/agents`, providers, rooms, join/leave, agent skills.
- Room agent skills: `/api/rooms/{roomId}/agents/{agentId}/skills`.
- Skills: `/chatbot/v1/skills`, `/chatbot/v1/skills/public`.
- Storage tokens and presigned uploads: `/chatbot/v1/storage/*`.
- Schedules: `/chatbot/v1/schedules`.
- Working memory: `/chatbot/v1/rooms/{roomId}/working-memory`.
- Room agents: `/chatbot/v1/rooms/{roomId}/agents`.
- Connectors/Composio: `/api/integrations/composio/*`.
- Webhooks: `/api/webhook-event-types`, `/api/webhook-triggers`, `/api/system-agent/draft/webhook-trigger`.
- Agent sandbox/vault: `/api/agent/{agentId}/sandbox`, `/api/agent/{agentId}/vault`.
- Credits: `/api/credits/*`.
- Analytics tokens: `/chatbot/v1/analytics/tokens`.

## Android Existing State

Relevant Android patterns:

- `libraries/network`
  - Provides shared `OkHttpClient`, `RetrofitFactory`, JSON converter, user agent, and debug logging.
- `libraries/session-storage/api`
  - Stores `SessionData.accessToken`, `homeserverUrl`, and session identity.
- `libraries/matrix/api`
  - `MatrixClient.sessionId` identifies the current user.
  - `MatrixClient.userIdServerName()` exposes the current server name.
  - `MatrixClient.getUrl(url)` can execute generic GET requests through the SDK HTTP client when needed.
- `features/networkmonitor/api`
  - Existing network status abstraction for UI features; the service layer should surface errors rather than own UI network state.
- Metro DI is the established dependency injection mechanism through `setupDependencyInjection()`, `@Inject`, `@BindingContainer`, `@ContributesTo`, and test fakes.
- Existing API/impl/test library split examples: `libraries/wellknown`, `libraries/session-storage`, `libraries/matrix`.

Android gaps:

- No `libraries/chatbot` module exists.
- No shared Unseal API base URL config exists in Android appconfig.
- No Android equivalent of iOS `IntegrationAPIResolver` exists.
- `MatrixClient` does not expose an access token. The implementation must use `SessionStore.getSession(matrixClient.sessionId.value)` or a small injected token provider backed by session storage.
- No typed Chatbot/Agent/Skills/Credits/Webhook/Connector models exist.

## Target Android Behavior

### Module Shape

Create a library family:

- `libraries/chatbot/api`
  - Public service interface, error types, base URL resolver interface, model types, and request/response contracts.
- `libraries/chatbot/impl`
  - Retrofit API definitions, default service implementation, `.well-known` resolver, token provider, JSON helpers, redaction helpers, and Metro bindings.
- `libraries/chatbot/test`
  - Fake service, fake resolver, fake token provider, and fixtures for later feature tests.

This module is a native Android implementation. It must not depend on Matrix Rust SDK generated symbols beyond existing stable `MatrixClient`/session storage abstractions, and it must not depend on Unseal UI/component libraries.

### API Facade

Define a Kotlin equivalent of iOS `ChatbotAPIClientProtocol`:

- `ChatbotApiService`
  - `suspend fun listAgents(): Result<List<ChatbotAgent>>`
  - `suspend fun getAgent(botName: String): Result<ChatbotAgent>`
  - `suspend fun createAgent(request: ChatbotCreateAgentRequest): Result<ChatbotAgent>`
  - `suspend fun updateAgent(botName: String, request: ChatbotUpdateAgentRequest): Result<ChatbotAgent>`
  - `suspend fun getProviders(): Result<List<ChatbotAgentProvider>>`
  - `suspend fun listAgentRooms(botName: String): Result<List<ChatbotAgentRoom>>`
  - `suspend fun agentJoinRoom(botName: String, roomName: String): Result<Unit>`
  - `suspend fun agentLeaveRoom(botName: String, roomId: String): Result<Unit>`
  - `suspend fun listAgentSkills(botName: String): Result<List<ChatbotUserSkill>>`
  - `suspend fun addAgentSkill(botName: String, skillId: String, name: String?): Result<Unit>`
  - `suspend fun listRoomAgentSkills(roomId: String, agentId: String, runtimeOwnerUserId: String?): Result<ChatbotListRoomAgentSkillsResponse>`
  - `suspend fun listUserSkills(visibility: ChatbotSkillVisibility?): Result<List<ChatbotUserSkill>>`
  - `suspend fun listPublicSkills(page: Int, pageSize: Int, search: String?): Result<ChatbotListPublicSkillsResponse>`
  - `suspend fun getUserSkill(id: String): Result<ChatbotGetUserSkillResponse>`
  - `suspend fun createUserSkill(body: ChatbotJsonObject): Result<ChatbotCreateUserSkillResponse>`
  - `suspend fun updateUserSkill(id: String, body: ChatbotJsonObject): Result<ChatbotUpdateUserSkillResponse>`
  - `suspend fun deleteUserSkill(id: String): Result<ChatbotDeleteUserSkillResponse>`
  - `suspend fun listSchedules(roomId: String): Result<List<ChatbotSchedule>>`
  - `suspend fun createSchedule(request: ChatbotCreateScheduleRequest): Result<ChatbotCreateScheduleResponse>`
  - `suspend fun updateSchedule(scheduleId: String, request: ChatbotUpdateScheduleRequest): Result<ChatbotCreateScheduleResponse>`
  - `suspend fun updateScheduleStatus(scheduleId: String, status: String): Result<Unit>`
  - `suspend fun deleteSchedule(scheduleId: String): Result<Unit>`
  - `suspend fun getRoomWorkingMemory(roomId: String): Result<String>`
  - `suspend fun updateRoomWorkingMemory(roomId: String, content: String): Result<Unit>`
  - `suspend fun getRoomAgents(roomId: String): Result<ChatbotGetRoomAgentsResponse>`
  - `suspend fun listToolkitCategories(cursor: String?, limit: Int?): Result<ChatbotListToolkitCategoriesResponse>`
  - `suspend fun listToolkits(search: String?, category: String?, cursor: String?, limit: Int?): Result<ChatbotListToolkitsResponse>`
  - `suspend fun initiateConnection(toolkit: String, redirectUrl: String): Result<ChatbotInitiateConnectionResponse>`
  - `suspend fun listConnectedAccounts(toolkit: String?, cursor: String?, limit: Int?): Result<ChatbotListConnectedAccountsResponse>`
  - `suspend fun disconnectAccount(accountId: String): Result<ChatbotDisconnectAccountResponse>`
  - `suspend fun listWebhookEventTypes(): Result<ChatbotWebhookEventCatalogResponse>`
  - `suspend fun listWebhookTriggers(agentId: String?, source: String?, roomId: String?, status: String?): Result<List<ChatbotWebhookTrigger>>`
  - `suspend fun createWebhookTrigger(request: ChatbotCreateWebhookTriggerRequest): Result<ChatbotWebhookTrigger>`
  - `suspend fun updateWebhookTrigger(triggerId: String, request: ChatbotUpdateWebhookTriggerRequest): Result<ChatbotWebhookTrigger>`
  - `suspend fun updateWebhookTriggerStatus(triggerId: String, enabled: Boolean): Result<ChatbotWebhookTriggerStatusResponse>`
  - `suspend fun deleteWebhookTrigger(triggerId: String): Result<ChatbotWebhookTriggerDeleteResponse>`
  - `suspend fun draftWebhookTrigger(prompt: String): Result<ChatbotWebhookTriggerDraftResponse>`
  - `suspend fun getBalance(): Result<CreditBalance>`
  - `suspend fun getLedger(limit: Int, cursor: String?): Result<CreditLedgerResponse>`
  - `suspend fun getDailyUsage(start: Int, end: Int): Result<CreditDailyUsageResponse>`
  - `suspend fun createPaymentIntent(amountCents: Int): Result<CreditPaymentIntentResponse>`
  - `suspend fun getPaymentIntentStatus(paymentIntentId: String): Result<CreditPaymentIntentStatusResponse>`
  - `suspend fun getAnalyticsTokens(period: String): Result<AnalyticsTokensResponse>`
  - Sandbox/vault wrapper models must be included only where Agent create/update request models require them. Sandbox/vault service functions remain out of scope.

### Factory And Base URLs

Add:

- `ChatbotApiServiceFactory`
  - `createForAiStream(matrixClient: MatrixClient): ChatbotApiService`
  - `createForUnsealApi(matrixClient: MatrixClient): ChatbotApiService`
  - `createForBaseUrl(baseUrl: String, matrixClient: MatrixClient): ChatbotApiService`

Base URL rules:

- `createForAiStream` uses Android app config default `https://api.unseal.network` unless a future settings feature exposes a user override.
- `createForUnsealApi` mirrors iOS `makeUnsealAPIClient`: resolve `org.unseal.api.base_url` from server `.well-known`, cache per server name, fallback to `https://agent-api.unseal.network`.
- The resolver must not crash on invalid server name, invalid JSON, network failure, or missing well-known key; it returns the fallback.

### Authentication

Add a small API boundary:

- `ChatbotAccessTokenProvider`
  - `suspend fun accessToken(matrixClient: MatrixClient): String?`

Default implementation:

- Uses `SessionStore.getSession(matrixClient.sessionId.value)` and returns a non-empty `SessionData.accessToken`.
- Returns `null` when the session is missing or token is blank.

Request behavior:

- Missing/blank token returns `Result.failure(ChatbotApiError.MissingAccessToken)`.
- Token must be read at request time, not cached forever, matching iOS's async `accessTokenProvider`.

### Error Handling

Add sealed `ChatbotApiError`:

- `InvalidBaseUrl`
- `MissingAccessToken`
- `InvalidResponse`
- `HttpError(statusCode: Int, body: String?)`
- `DecodingError(bodySnippet: String?)`
- `EncodingError`
- `NetworkError(description: String)`

Error mapping:

- Invalid configured or resolved base URL -> `InvalidBaseUrl`.
- Missing token -> `MissingAccessToken`.
- Non-HTTP or empty response where a response is required -> `InvalidResponse`.
- Non-2xx HTTP -> `HttpError`, with redacted body.
- Serialization/decode failure -> `DecodingError`, with redacted body snippet.
- Request construction/body encoding failure -> `EncodingError`.
- IO/cancellation-adjacent network failures -> `NetworkError`, while preserving coroutine cancellation.

Redaction:

- Must redact at least `api_key` and `apiKey`.
- Should also redact `access_token`, `accessToken`, `Authorization`, `secret_access_key`, and `session_token` because Android logs may include more network details than iOS.

### Model Mapping

Use Kotlin data classes and `@Serializable` with explicit `@SerialName` fields. Do not rely on global snake_case conversion.

Preserve iOS model names where practical:

- `ChatbotAgent`, `ChatbotAgentConfig`, `ChatbotAgentSettings`
- `ChatbotCreateAgentRequest`, `ChatbotUpdateAgentRequest`
- `ChatbotAgentProvider`, `ChatbotProviderModel`, `ChatbotAgentRoom`
- `ChatbotUserSkill`, `ChatbotSkillVisibility`, `ChatbotListPublicSkillsResponse`
- `ChatbotRoomAgentSkill`, `ChatbotRoomAgentSkillSource`, `ChatbotRoomAgentSkillRelationKind`
- `ChatbotSchedule`, `ChatbotCreateScheduleRequest`, `ChatbotUpdateScheduleRequest`
- `ChatbotRoomAgent`, `ChatbotGetRoomAgentsResponse`
- `ChatbotToolkit`, `ChatbotToolkitCategory`, `ChatbotConnectedAccount`
- `ChatbotWebhookTrigger`, `ChatbotWebhookTriggerStatus`, webhook request/response models
- `CreditBalance`, `CreditLedgerItem`, `CreditLedgerResponse`, `CreditDailyUsageResponse`
- `AnalyticsTokensResponse`
- `AgentSandboxMode`, `AgentSandboxStatus`, `AgentVaultEntry`, wrappers used by agent create/update
- `ChatbotJSONValue` as a Kotlin sealed serializable value type for flexible metadata/body fields.

Model grouping:

- Keep API-facing models in `libraries/chatbot/api`.
- Split large model files by domain so this does not become a single 2,000-line Kotlin file:
  - `agent/`
  - `skills/`
  - `schedules/`
  - `webhooks/`
  - `credits/`
  - `connectors/`
  - `json/`

### Network Implementation

Implementation may use Retrofit for typed endpoints, but must support iOS's flexible decode behavior where Retrofit alone is too strict:

- For `listAgents`, first decode raw body as `List<ChatbotAgent>`, then as wrapped `ChatbotListAgentsResponse`.
- For `listAgentSkills`, decode wrapped response first, then direct list.
- For `presignedUploadUrls`, parse raw `JsonElement` and accept all iOS-supported shapes.
- For generic body functions that iOS represents as `[String: Any]`, use `ChatbotJsonObject`/`JsonObject` instead of string-building.

Path/query construction:

- Use OkHttp `HttpUrl.Builder` or Retrofit `@Path(encoded = false)`/`@Query` consistently.
- Matrix room IDs such as `!room:server` and user IDs such as `@user:server` must survive path encoding exactly as iOS `urlPathEncoded` intends.
- Query cursors and search strings must be encoded as query values, never concatenated raw except in tested helper functions.

### Testing Requirements

Unit tests must cover:

- Factory uses `https://api.unseal.network` for AI stream client.
- Unseal API resolver reads `org.unseal.api.base_url` from well-known and caches by server name.
- Resolver falls back to `https://agent-api.unseal.network` on missing server name, network error, invalid JSON, or missing `org.unseal.api`.
- Missing token returns `MissingAccessToken` and does not send HTTP.
- Bearer token, accept header, and JSON content type are set.
- Non-2xx HTTP maps to `HttpError` with redacted body.
- Decode failure maps to `DecodingError` with redacted body snippet.
- `api_key`, `apiKey`, authorization values, and STS secrets are redacted.
- `listAgents` decodes both direct array and wrapped response.
- `listAgentSkills` decodes wrapped response and direct array.
- `createSchedule` fails when `eb_schedule_id` is absent or blank, matching iOS.
- Path encoding handles room IDs, agent IDs, schedule IDs, trigger IDs, and cursors.
- Fake `ChatbotApiService` can be configured with success/failure responses for later presenter tests.

Verification command for this feature:

```bash
env -u http_proxy -u https_proxy -u all_proxy -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u NO_PROXY -u no_proxy \
  JAVA_HOME=/usr/local/opt/openjdk@21 ANDROID_HOME=/usr/local/share/android-commandlinetools \
  ./gradlew --no-daemon --no-configuration-cache \
  :libraries:chatbot:api:testDebugUnitTest \
  :libraries:chatbot:impl:testDebugUnitTest \
  :libraries:chatbot:test:testDebugUnitTest
```

## Acceptance Criteria

- Android has one standalone `chatbot-api-service` spec that can be used to write a matching implementation plan.
- The implementation introduces reusable `libraries:chatbot:api`, `libraries:chatbot:impl`, and `libraries:chatbot:test` modules.
- The API service mirrors iOS `ChatbotAPIClientProtocol` for foundation endpoints needed by P1 native features.
- Base URL resolution matches iOS behavior, including well-known lookup and fallback.
- Requests are authenticated with the current Matrix access token from session storage.
- Error behavior and redaction match iOS or are stricter where Android logging needs it.
- Flexible response decoding matches iOS compatibility behavior.
- Later feature specs can depend on `ChatbotApiService` without adding their own HTTP clients.
- No Matrix Rust SDK rebuild or Unseal component library migration is required.

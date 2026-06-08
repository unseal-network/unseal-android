# Unseal Android Feature Migration Index Design

Date: 2026-06-08

## Purpose

This spec defines the feature migration index for bringing Unseal iOS functionality into `unseal-android`.

The migration must be split into one feature spec per feature. Each feature spec must be small enough to hand to a subagent for independent implementation and validation. The Android implementation must strictly reference the iOS implementation for behavior, data flow, state handling, and edge cases before choosing Android-specific architecture.

This index is not an implementation plan. It is the source of truth for which feature specs should exist, how they are classified by dependency, and what each per-feature spec must contain.

## Migration Principles

1. iOS is the behavioral source of truth.
2. Each feature gets one standalone spec before implementation starts.
3. Each feature spec must be subagent-ready.
4. Features that depend on missing component libraries must be separated from features that Android can implement natively.
5. Matrix Rust SDK dependent work must be called out explicitly, because it may require a locally compiled Android artifact or a version bump before feature work can land.
6. Shared UI, navigation, state, and error handling should be unified through existing Android patterns instead of copied as one-off screens.

## Dependency Classes

### Matrix Rust SDK Dependent

These features depend on Matrix Rust SDK APIs, generated Kotlin bindings, or local SDK artifacts.

- `matrix-rust-sdk-artifact-integration`
- `session-verification`
- `secure-backup-recovery`
- `encrypted-room-key-recovery`
- `agent-room-key-recovery`

### Native Android Implementable

These features should be implementable with existing Android architecture, Compose UI, project libraries, HTTP/API clients, and the current Matrix client abstraction. They do not require Unseal-specific component libraries before a first complete version.

- `chatbot-api-service`
- `agent-management`
- `skills-marketplace`
- `agent-skills-management`
- `room-schedules`
- `webhook-triggers`
- `credits`
- `connectors`
- `game-picker`

### Component Library Dependent

These features depend on iOS-side component libraries or runtime systems that do not currently have clear Android equivalents. They should not block native feature migration, but each must receive its own spec before implementation.

- `ai-message-rich-renderer`
- `vault-management`
- `local-agent-runtime`
- `miniapp-runtime`
- `voice-library`

## Feature Spec Queue

### P0: SDK And Security Foundation

#### 1. matrix-rust-sdk-artifact-integration

Dependency class: Matrix Rust SDK dependent.

Purpose: define how Android consumes a Matrix Rust SDK artifact built from `/Users/Ruihan/go/src/matrix-rust-sdk`.

The spec must cover local AAR usage, release/debug dependency selection, version naming, artifact replacement, and validation builds.

#### 2. session-verification

Dependency class: Matrix Rust SDK dependent.

Purpose: align Android session verification with iOS verification behavior.

The spec must reference iOS session security state aggregation, cross-signing behavior, verification request handling, and Android's existing `features/verifysession`, `features/ftue`, and `libraries/matrix` implementations.

#### 3. secure-backup-recovery

Dependency class: Matrix Rust SDK dependent.

Purpose: align Android secure backup and recovery behavior with iOS recovery state handling.

The spec must cover backup state, recovery state, recovery key/passphrase flows, trust state, user-visible prompts, and retry behavior.

#### 4. encrypted-room-key-recovery

Dependency class: Matrix Rust SDK dependent.

Purpose: migrate iOS encrypted room key recovery behavior to Android.

The spec must reference iOS `RoomKeyRecovery` stages, unable-to-decrypt timeline behavior, recovery card interactions, and Android timeline/decryption services.

#### 5. agent-room-key-recovery

Dependency class: Matrix Rust SDK dependent plus Unseal custom policy.

Purpose: migrate iOS agent/bot device room key recovery behavior.

The spec must reference iOS `AgentRoomKeyRecovery`, agent device identification rules, room key request construction, retry behavior, and encrypted timeline UI states.

### P1: Native Android Implementable Features

#### 6. chatbot-api-service

Dependency class: native Android implementable.

Purpose: introduce the Android API/service foundation used by Unseal Agent, Skills, Schedules, Webhooks, Credits, Connectors, and related screens.

The spec must reference iOS `Services/ChatbotAPI` models and request flows, then map them to Android repositories, network models, error handling, authentication, pagination, and test seams.

#### 7. agent-management

Dependency class: native Android implementable.

Purpose: migrate Agent list, detail, create, and edit flows.

The spec must reference iOS Agent screens, models, validation, loading/error/empty states, navigation entry points, and room association behavior.

#### 8. skills-marketplace

Dependency class: native Android implementable.

Purpose: migrate Skills marketplace/list/detail behavior.

The spec must reference iOS Skills screens, skill metadata, enable/disable behavior, state handling, and navigation.

#### 9. agent-skills-management

Dependency class: native Android implementable.

Purpose: migrate the flow for managing skills attached to a specific Agent.

The spec must reference iOS `AgentSkillsManagementScreen`, selection behavior, persistence, conflict/error handling, and expected post-save state.

#### 10. room-schedules

Dependency class: native Android implementable.

Purpose: migrate room schedule list, create, edit, and delete flows.

The spec must reference iOS `RoomSchedules`, schedule data models, time/rule editing, validation, delete confirmation, and room/agent relationships.

#### 11. webhook-triggers

Dependency class: native Android implementable.

Purpose: migrate webhook trigger list, detail, create, edit, and delete flows.

The spec must reference iOS `WebhookTriggers`, trigger status, secret handling, payload configuration, enable/disable behavior, and destructive action confirmation.

#### 12. credits

Dependency class: native Android implementable.

Purpose: migrate credits balance, usage, and ledger display.

The spec must reference iOS `Credits` screens, balance formatting, transaction list behavior, loading/error/empty states, and any billing entry points that should exist on Android.

#### 13. connectors

Dependency class: native Android implementable.

Purpose: migrate connector list, detail, connect, disconnect, and management flows.

The spec must reference iOS connector settings screens, connector status, auth/config flows, error handling, and settings navigation placement.

#### 14. game-picker

Dependency class: native Android implementable for the picker only.

Purpose: migrate the room game picker entry point and selection flow without implementing MiniApp runtime.

The spec must reference iOS `RoomScreen/GamePicker`, list behavior, room event construction, send/start interactions, and explicitly exclude embedded MiniApp execution.

### P2: Component Library Dependent Features

#### 15. ai-message-rich-renderer

Dependency class: component library dependent.

Purpose: migrate rich AI timeline rendering after deciding whether Android needs an equivalent to iOS `UnsealUI`.

The spec must reference iOS AI timeline item views, thinking/tool/source blocks, streaming states, quick actions, fallback rendering, and a possible degraded Android first version.

#### 16. vault-management

Dependency class: component library dependent.

Purpose: migrate Vault management only after clarifying `UnsealUI` and `UnsealAgent` equivalents.

The spec must reference iOS Vault screens, agent vault picker behavior, secret/file/sandbox data handling, permissions, and security-sensitive UI states.

#### 17. local-agent-runtime

Dependency class: component library dependent.

Purpose: migrate local agent runtime and persistence behavior only after clarifying the Android equivalent of iOS `UnsealAgent`.

The spec must reference iOS local agent persistence, keychain bridge behavior, local state sync, stream persistence, and failure recovery.

#### 18. miniapp-runtime

Dependency class: component library dependent.

Purpose: migrate MiniApp runtime only after clarifying the Android equivalent of iOS `UnsealMiniApp`.

The spec must reference iOS MiniApp room placeholder, WebView bridge, media permissions, room integration, and lifecycle behavior.

#### 19. voice-library

Dependency class: pending classification.

Purpose: determine whether Voice Library is native Android implementable or component library dependent.

The spec must reference iOS Voice Library screens and dependencies first. If it is only list/detail/manage, it may move to P1. If it depends on specialized generation, recording, playback, or shared Unseal components, it remains P2.

## Required Per-Feature Spec Template

Each per-feature spec must include the following sections.

### Feature Boundary

- Feature name.
- User-visible goal.
- Explicit out-of-scope list.
- Dependency class.
- Whether the feature is blocked by SDK artifact work or component library migration.

### iOS Source References

- Exact iOS files and directories.
- Core Swift types and view models.
- Service/API models.
- Important state machines or enums.
- User interactions and edge cases that Android must preserve.

### Android Existing State

- Existing Android modules and files related to the feature.
- Whether similar Element X Android functionality already exists.
- Reusable architecture patterns.
- Missing modules, services, or navigation entries.

### Target Android Behavior

- User flows.
- Screen states: loading, empty, error, success, offline, permission denied, and retry.
- Navigation entry points.
- Timeline/composer/settings integration points where relevant.
- Destructive action handling.

### Data And API Mapping

- iOS model to Android model mapping.
- Network endpoints and request/response models.
- Local persistence requirements.
- Error model.
- Pagination or streaming behavior.

### Implementation Boundary For Subagent

- Files or modules the subagent is expected to touch.
- Files or modules the subagent should avoid.
- Required public interfaces.
- Test target expectations.
- Validation commands.
- Handoff notes for follow-up features.

### Acceptance Checks

- Behavior checks.
- Unit tests or integration tests.
- UI state checks.
- Build commands.
- Known limitations that are accepted for the first Android version.

## Subagent Readiness Rules

A feature spec is subagent-ready only when:

1. It names the exact iOS source files to inspect.
2. It names the Android modules likely to change.
3. It has a narrow feature boundary with explicit out-of-scope items.
4. It defines expected public interfaces or integration points.
5. It includes validation commands that can run locally.
6. It has enough acceptance checks for another agent or reviewer to judge completion without rereading this index.
7. It does not depend on hidden decisions from another spec unless that dependency is listed.

## First Specs To Write

The first three per-feature specs should be:

1. `matrix-rust-sdk-artifact-integration`
2. `chatbot-api-service`
3. `agent-management`

This order establishes the SDK baseline, then creates the native API foundation, then migrates the first user-visible native feature. Security features can proceed after the SDK artifact spec is approved. Other native features can proceed after the API service spec is approved.

## Approval

This index is approved when the team agrees that:

- The feature list is complete enough for the first migration wave.
- Each feature will receive its own spec before implementation.
- iOS source references are mandatory for every feature spec.
- Subagent readiness is mandatory for every feature spec.
- Component-library dependent features will not block native Android implementable features.

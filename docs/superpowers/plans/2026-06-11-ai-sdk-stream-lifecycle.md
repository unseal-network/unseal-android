# AI SDK Stream Lifecycle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the shared AI stream lifecycle SDK so Android timeline rendering is driven by SDK-produced `StreamSnapshot.parts`, with SDK-owned in-flight dedupe and storage-provider-backed final snapshot persistence.

**Architecture:** `libraries/agentstream` becomes the stream data layer: it owns `getStream`, memory cache, storage lookup, SSE consumption, Rust reducer sessions, listener fan-out, and final snapshot persistence. Android messages code injects HTTP, task runner, and storage adapters, then maps SDK snapshots to existing `TimelineItemAiContent` for Compose rendering. Card UI parity with iOS is documented in the spec but implemented in a later plan after lifecycle stability.

**Tech Stack:** Kotlin, Android library modules, kotlinx.serialization JSON, coroutines, Metro DI, existing Rust JNI `AgentStreamSession`, existing `ChatbotApiServiceFactory`, existing Compose timeline presenters.

---

## Scope Check

This plan implements Phase 1 and Phase 2 from [2026-06-11-ai-sdk-stream-lifecycle-design.md](/Users/Ruihan/.config/superpowers/worktrees/unseal-android/chatbot-api-service/docs/superpowers/specs/2026-06-11-ai-sdk-stream-lifecycle-design.md):

- Phase 1: Stream Lifecycle SDK.
- Phase 2: Android SDK-driven UI.

Phase 3, iOS card parity, is intentionally kept out of this plan. The spec already lists the iOS card source files and behavior. A later plan should migrate Android card UI one card family at a time after this lifecycle work is stable.

## File Structure

### `libraries/agentstream`

- Modify: `libraries/agentstream/build.gradle.kts`
  - Add serialization and coroutine test dependencies needed by SDK parser and tests.
- Keep: `libraries/agentstream/src/main/kotlin/io/element/android/libraries/agentstream/api/AgentStreamSession.kt`
  - Continue to be the thin JNI wrapper over Rust reducer sessions.
- Create: `libraries/agentstream/src/main/kotlin/io/element/android/libraries/agentstream/api/StreamModels.kt`
  - Defines `StreamRequest`, `StreamSnapshot`, statuses, parts, errors, raw events.
- Create: `libraries/agentstream/src/main/kotlin/io/element/android/libraries/agentstream/api/AgentStreamClient.kt`
  - Defines public lifecycle interfaces: `AgentStreamClient`, `StreamHandle`, `StreamListener`, `StreamSubscription`, `StreamStorageProvider`, `StreamHttpClient`, `StreamTaskRunner`, `StreamTask`, `StreamReducerSessionFactory`, `StreamReducerSession`.
- Create: `libraries/agentstream/src/main/kotlin/io/element/android/libraries/agentstream/api/DefaultAgentStreamClient.kt`
  - Implements memory cache, storage lookup, in-flight dedupe, native session consumption, listener fan-out, snapshot publish, final save.
- Create: `libraries/agentstream/src/main/kotlin/io/element/android/libraries/agentstream/api/StreamSnapshotParser.kt`
  - Parses Rust snapshot JSON into typed SDK models.
- Create: `libraries/agentstream/src/main/kotlin/io/element/android/libraries/agentstream/api/StreamSnapshotJsonCodec.kt`
  - Encodes typed snapshots to JSON and decodes them back for client storage providers.
- Create: `libraries/agentstream/src/test/kotlin/io/element/android/libraries/agentstream/api/StreamSnapshotParserTest.kt`
  - Verifies text/tool/data/source/error/custom parsing.
- Create: `libraries/agentstream/src/test/kotlin/io/element/android/libraries/agentstream/api/StreamSnapshotJsonCodecTest.kt`
  - Verifies persistent snapshot JSON round-trips.
- Create: `libraries/agentstream/src/test/kotlin/io/element/android/libraries/agentstream/api/DefaultAgentStreamClientTest.kt`
  - Verifies storage hit, in-flight dedupe, final save, failure snapshot, listener fan-out.

### `features/messages/impl`

- Modify: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/factories/event/AiSdkStreamReducer.kt`
  - Remove direct native session ownership; make this a pure SDK snapshot-to-`TimelineItemAiContent` adapter.
- Modify: `features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/timeline/factories/event/AiSdkStreamReducerTest.kt`
  - Update tests to feed typed `StreamSnapshot` instead of raw Rust JSON.
- Modify: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/TimelineItemAiPresenter.kt`
  - Replace direct `ChatbotApiServiceFactory`, `AgentStreamSession`, `TimelineItemAiStreamGate`, and memory-only cache usage with `AgentStreamClient.getStream`.
- Create: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/AndroidAgentStreamAdapters.kt`
  - Provides Android resource adapters: `ChatbotStreamHttpClient`, `CoroutineStreamTaskRunner`, and SQLite-backed `StreamStorageProvider`.
- Create: `features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/timeline/components/event/TimelineItemAiPresenterTest.kt`
  - Verifies presenter subscribes to SDK snapshots, maps parts, and does not call SSE APIs directly.

## Task 1: Add SDK Public Models And Interfaces

**Files:**
- Modify: `libraries/agentstream/build.gradle.kts`
- Create: `libraries/agentstream/src/main/kotlin/io/element/android/libraries/agentstream/api/StreamModels.kt`
- Create: `libraries/agentstream/src/main/kotlin/io/element/android/libraries/agentstream/api/AgentStreamClient.kt`
- Test: `libraries/agentstream/src/test/kotlin/io/element/android/libraries/agentstream/api/StreamModelsCompileTest.kt`

- [ ] **Step 1: Add the model compile test**

Create `libraries/agentstream/src/test/kotlin/io/element/android/libraries/agentstream/api/StreamModelsCompileTest.kt`:

```kotlin
/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.agentstream.api

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

class StreamModelsCompileTest {
    @Test
    fun `stream models expose stable parts contract`() {
        val snapshot = StreamSnapshot(
            schemaVersion = 1,
            streamId = "stream-1",
            status = StreamStatus.Streaming,
            parts = listOf(
                StreamPart.Text(
                    id = "text-1",
                    textState = TextPartState.Streaming,
                    text = "hello",
                ),
                StreamPart.Tool(
                    id = "tool-1",
                    toolName = "weather",
                    toolState = ToolPartState.InputAvailable,
                    input = JsonPrimitive("Shanghai"),
                    output = null,
                    error = null,
                    title = "Weather",
                ),
            ),
            rawEvents = emptyList(),
            updatedAtMs = 1000L,
            completedAtMs = null,
            error = null,
        )

        assertThat(snapshot.parts).hasSize(2)
        assertThat((snapshot.parts[0] as StreamPart.Text).text).isEqualTo("hello")
        assertThat((snapshot.parts[1] as StreamPart.Tool).state).isEqualTo(ToolPartState.InputAvailable)
    }
}
```

- [ ] **Step 2: Run the failing test**

Run:

```bash
./gradlew :libraries:agentstream:testDebugUnitTest --tests '*StreamModelsCompileTest' --no-daemon
```

Expected: FAIL because `StreamSnapshot`, `StreamPart`, and related types do not exist.

- [ ] **Step 3: Add serialization dependency**

Modify `libraries/agentstream/build.gradle.kts` to:

```kotlin
/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

import extension.testCommonDependencies

plugins {
    id("io.element.android-library")
}

android {
    namespace = "io.element.android.libraries.agentstream"
}

dependencies {
    implementation(libs.serialization.json)

    testCommonDependencies(libs, true)
}
```

- [ ] **Step 4: Add `StreamModels.kt`**

Create `libraries/agentstream/src/main/kotlin/io/element/android/libraries/agentstream/api/StreamModels.kt`:

```kotlin
/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.agentstream.api

import kotlinx.serialization.json.JsonElement

const val AGENT_STREAM_SCHEMA_VERSION = 1

data class StreamRequest(
    val streamId: String,
    val sender: String?,
    val roomId: String?,
    val eventId: String?,
    val includeRawEvents: Boolean = true,
)

data class StreamSnapshot(
    val schemaVersion: Int,
    val streamId: String,
    val status: StreamStatus,
    val parts: List<StreamPart>,
    val rawEvents: List<RawStreamEvent>,
    val updatedAtMs: Long,
    val completedAtMs: Long?,
    val error: StreamError?,
) {
    val isTerminal: Boolean
        get() = status == StreamStatus.Completed || status == StreamStatus.Failed || status == StreamStatus.Cancelled
}

enum class StreamStatus {
    Idle,
    Loading,
    Streaming,
    Completed,
    Failed,
    Cancelled,
}

data class StreamError(
    val message: String,
    val code: String? = null,
    val raw: JsonElement? = null,
)

data class RawStreamEvent(
    val sequence: Long,
    val eventType: String,
    val partId: String?,
    val payload: JsonElement,
    val receivedAtMs: Long,
)

sealed interface StreamPart {
    val id: String
    val state: String

    data class Text(
        override val id: String,
        val textState: TextPartState,
        val text: String,
    ) : StreamPart {
        override val state: String = textState.wireValue
    }

    data class Reasoning(
        override val id: String,
        val reasoningState: TextPartState,
        val text: String,
    ) : StreamPart {
        override val state: String = reasoningState.wireValue
    }

    data class Tool(
        override val id: String,
        val toolName: String,
        val toolState: ToolPartState,
        val input: JsonElement?,
        val output: JsonElement?,
        val error: StreamError?,
        val title: String?,
    ) : StreamPart {
        override val state: String = toolState.wireValue
    }

    data class Data(
        override val id: String,
        override val state: String,
        val type: String,
        val payload: JsonElement?,
    ) : StreamPart

    data class Source(
        override val id: String,
        override val state: String,
        val sourceType: String,
        val title: String,
        val url: String?,
        val filename: String?,
        val mediaType: String?,
    ) : StreamPart

    data class File(
        override val id: String,
        override val state: String,
        val mediaType: String?,
        val filename: String?,
        val url: String?,
    ) : StreamPart

    data class Step(
        override val id: String,
        override val state: String,
    ) : StreamPart

    data class Error(
        override val id: String,
        override val state: String,
        val error: StreamError,
    ) : StreamPart

    data class Custom(
        override val id: String,
        override val state: String,
        val type: String,
        val payload: JsonElement?,
    ) : StreamPart
}

enum class TextPartState(val wireValue: String) {
    Streaming("streaming"),
    Complete("complete"),
    Done("done");

    companion object {
        fun fromWire(value: String?): TextPartState {
            return when (value) {
                "complete" -> Complete
                "done" -> Done
                else -> Streaming
            }
        }
    }
}

enum class ToolPartState(val wireValue: String) {
    InputStreaming("input-streaming"),
    InputAvailable("input-available"),
    OutputAvailable("output-available"),
    OutputError("output-error");

    companion object {
        fun fromWire(value: String?): ToolPartState {
            return when (value) {
                "input-available" -> InputAvailable
                "output-available" -> OutputAvailable
                "output-error" -> OutputError
                else -> InputStreaming
            }
        }
    }
}
```

- [ ] **Step 5: Add lifecycle interfaces**

Create `libraries/agentstream/src/main/kotlin/io/element/android/libraries/agentstream/api/AgentStreamClient.kt`:

```kotlin
/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.agentstream.api

import java.io.Closeable

interface AgentStreamClient {
    fun getStream(request: StreamRequest): StreamHandle
}

interface StreamHandle {
    fun snapshot(): StreamSnapshot
    fun subscribe(listener: StreamListener): StreamSubscription
    fun refresh()
    fun cancel()
}

fun interface StreamListener {
    fun onSnapshot(snapshot: StreamSnapshot)
}

interface StreamSubscription {
    fun cancel()
}

interface StreamStorageProvider {
    suspend fun load(streamId: String): StreamSnapshot?
    suspend fun save(streamId: String, snapshot: StreamSnapshot)
    suspend fun delete(streamId: String)
}

interface StreamHttpClient {
    suspend fun openStream(
        request: StreamRequest,
        onChunk: suspend (String) -> Unit,
    )
}

interface StreamTaskRunner {
    fun run(key: String, block: suspend () -> Unit): StreamTask
}

interface StreamTask {
    fun cancel()
}

interface StreamReducerSession : Closeable {
    fun applySseChunk(chunk: String): String
    fun finish(): String
    fun snapshot(): String
}

interface StreamReducerSessionFactory {
    fun create(
        streamId: String,
        includeRawEvents: Boolean,
    ): StreamReducerSession
}

class NativeStreamReducerSessionFactory : StreamReducerSessionFactory {
    override fun create(
        streamId: String,
        includeRawEvents: Boolean,
    ): StreamReducerSession {
        val session = AgentStreamSession(
            streamId = streamId,
            includeRawEvents = includeRawEvents,
            includeNormalizedEvents = true,
        )
        return object : StreamReducerSession {
            override fun applySseChunk(chunk: String): String = session.applySseChunk(chunk)
            override fun finish(): String = session.finish()
            override fun snapshot(): String = session.snapshot()
            override fun close() = session.close()
        }
    }
}
```

- [ ] **Step 6: Run the test**

Run:

```bash
./gradlew :libraries:agentstream:testDebugUnitTest --tests '*StreamModelsCompileTest' --no-daemon
```

Expected: PASS.

- [ ] **Step 7: Commit**

Run:

```bash
git add libraries/agentstream/build.gradle.kts \
  libraries/agentstream/src/main/kotlin/io/element/android/libraries/agentstream/api/StreamModels.kt \
  libraries/agentstream/src/main/kotlin/io/element/android/libraries/agentstream/api/AgentStreamClient.kt \
  libraries/agentstream/src/test/kotlin/io/element/android/libraries/agentstream/api/StreamModelsCompileTest.kt
git commit -m "feat: add agent stream lifecycle contracts"
```

## Task 2: Parse Rust Snapshot JSON Into SDK Models

**Files:**
- Create: `libraries/agentstream/src/main/kotlin/io/element/android/libraries/agentstream/api/StreamSnapshotParser.kt`
- Create: `libraries/agentstream/src/main/kotlin/io/element/android/libraries/agentstream/api/StreamSnapshotJsonCodec.kt`
- Test: `libraries/agentstream/src/test/kotlin/io/element/android/libraries/agentstream/api/StreamSnapshotParserTest.kt`
- Test: `libraries/agentstream/src/test/kotlin/io/element/android/libraries/agentstream/api/StreamSnapshotJsonCodecTest.kt`

- [ ] **Step 1: Add parser tests**

Create `libraries/agentstream/src/test/kotlin/io/element/android/libraries/agentstream/api/StreamSnapshotParserTest.kt`:

```kotlin
/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.agentstream.api

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

class StreamSnapshotParserTest {
    private val parser = StreamSnapshotParser(clock = { 1234L })

    @Test
    fun `parses standard parts from rust snapshot json`() {
        val snapshot = parser.parse(
            """
            {
              "streamId": "stream-1",
              "status": "streaming",
              "parts": [
                { "type": "text", "id": "text-1", "state": "streaming", "text": "Hello" },
                { "type": "reasoning", "id": "reason-1", "state": "done", "text": "Think" },
                { "type": "tool", "id": "tool-1", "state": "input-available", "toolName": "weather", "title": "Weather", "input": { "city": "Shanghai" } },
                { "type": "tool-weather", "id": "tool-2", "state": "output-available", "toolCallId": "call-2", "output": { "temp": "21C" } },
                { "type": "source-url", "id": "source-1", "state": "done", "title": "Docs", "url": "https://example.com" },
                { "type": "data-ui-spec", "id": "data-1", "state": "done", "data": { "kind": "card" } },
                { "type": "file", "id": "file-1", "state": "done", "mediaType": "application/pdf", "filename": "a.pdf", "url": "mxc://file" },
                { "type": "step-start", "id": "step-1", "state": "done" },
                { "type": "error", "id": "error-1", "message": "bad" },
                { "type": "custom-x", "id": "custom-1", "state": "done", "value": 1 }
              ],
              "rawEvents": [
                { "sequence": 1, "eventType": "text-delta", "partId": "text-1", "payload": { "delta": "Hello" }, "receivedAtMs": 1000 }
              ]
            }
            """.trimIndent()
        )

        assertThat(snapshot.schemaVersion).isEqualTo(AGENT_STREAM_SCHEMA_VERSION)
        assertThat(snapshot.streamId).isEqualTo("stream-1")
        assertThat(snapshot.status).isEqualTo(StreamStatus.Streaming)
        assertThat(snapshot.updatedAtMs).isEqualTo(1234L)
        assertThat(snapshot.parts).hasSize(10)
        assertThat((snapshot.parts[0] as StreamPart.Text).text).isEqualTo("Hello")
        assertThat((snapshot.parts[1] as StreamPart.Reasoning).reasoningState).isEqualTo(TextPartState.Done)
        assertThat((snapshot.parts[2] as StreamPart.Tool).toolState).isEqualTo(ToolPartState.InputAvailable)
        assertThat((snapshot.parts[3] as StreamPart.Tool).toolName).isEqualTo("weather")
        assertThat((snapshot.parts[4] as StreamPart.Source).url).isEqualTo("https://example.com")
        assertThat((snapshot.parts[5] as StreamPart.Data).type).isEqualTo("data-ui-spec")
        assertThat((snapshot.parts[6] as StreamPart.File).filename).isEqualTo("a.pdf")
        assertThat((snapshot.parts[7] as StreamPart.Step).id).isEqualTo("step-1")
        assertThat((snapshot.parts[8] as StreamPart.Error).error.message).isEqualTo("bad")
        assertThat((snapshot.parts[9] as StreamPart.Custom).type).isEqualTo("custom-x")
        assertThat(snapshot.rawEvents.single().eventType).isEqualTo("text-delta")
    }

    @Test
    fun `maps terminal statuses`() {
        assertThat(parser.parse("""{"streamId":"s","status":"done","parts":[]}""").status).isEqualTo(StreamStatus.Completed)
        assertThat(parser.parse("""{"streamId":"s","status":"completed","parts":[]}""").status).isEqualTo(StreamStatus.Completed)
        assertThat(parser.parse("""{"streamId":"s","status":"failed","parts":[]}""").status).isEqualTo(StreamStatus.Failed)
        assertThat(parser.parse("""{"streamId":"s","status":"cancelled","parts":[]}""").status).isEqualTo(StreamStatus.Cancelled)
    }
}
```

- [ ] **Step 2: Run parser tests to verify failure**

Run:

```bash
./gradlew :libraries:agentstream:testDebugUnitTest --tests '*StreamSnapshotParserTest' --no-daemon
```

Expected: FAIL because `StreamSnapshotParser` does not exist.

- [ ] **Step 3: Implement parser**

Create `libraries/agentstream/src/main/kotlin/io/element/android/libraries/agentstream/api/StreamSnapshotParser.kt`:

```kotlin
/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.agentstream.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

class StreamSnapshotParser(
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(snapshotJson: String): StreamSnapshot {
        val root = json.parseToJsonElement(snapshotJson).jsonObject
        val streamId = root.string("streamId") ?: root.string("stream_id") ?: ""
        val status = root.string("status").toStreamStatus()
        val now = clock()
        return StreamSnapshot(
            schemaVersion = AGENT_STREAM_SCHEMA_VERSION,
            streamId = streamId,
            status = status,
            parts = root.objectArray("parts").mapNotNull { it.toPart() },
            rawEvents = root.objectArray("rawEvents").mapIndexed { index, event ->
                event.toRawEvent(defaultSequence = index.toLong(), now = now)
            },
            updatedAtMs = root.long("updatedAtMs") ?: now,
            completedAtMs = root.long("completedAtMs") ?: if (status == StreamStatus.Completed) now else null,
            error = root["error"]?.toStreamError(),
        )
    }

    private fun JsonObject.toPart(): StreamPart? {
        val type = string("type") ?: return null
        val id = string("id") ?: string("toolCallId") ?: type
        val state = string("state")
        return when {
            type == "text" -> StreamPart.Text(
                id = id,
                textState = TextPartState.fromWire(state),
                text = string("text").orEmpty(),
            )
            type == "reasoning" -> StreamPart.Reasoning(
                id = id,
                reasoningState = TextPartState.fromWire(state),
                text = string("text").orEmpty(),
            )
            type == "tool" || type == "dynamic-tool" || type.startsWith("tool-") -> StreamPart.Tool(
                id = id,
                toolName = string("toolName") ?: string("name") ?: type.removePrefix("tool-"),
                toolState = ToolPartState.fromWire(state),
                input = this["input"] ?: this["rawInput"],
                output = this["output"],
                error = this["error"]?.toStreamError() ?: string("errorText")?.let { StreamError(it) },
                title = string("title") ?: string("displayName") ?: string("display_name"),
            )
            type == "source" || type == "source-url" || type == "source-document" -> StreamPart.Source(
                id = id,
                state = state ?: "done",
                sourceType = string("sourceType") ?: if (type == "source-document") "document" else "url",
                title = string("title") ?: string("filename") ?: string("url") ?: "Source",
                url = string("url"),
                filename = string("filename"),
                mediaType = string("mediaType") ?: string("media_type"),
            )
            type == "file" -> StreamPart.File(
                id = id,
                state = state ?: "done",
                mediaType = string("mediaType") ?: string("media_type"),
                filename = string("filename"),
                url = string("url"),
            )
            type == "step-start" || type == "step" -> StreamPart.Step(
                id = id,
                state = state ?: "done",
            )
            type == "error" || type == "data-error" -> StreamPart.Error(
                id = id,
                state = state ?: "error",
                error = this["error"]?.toStreamError()
                    ?: StreamError(string("errorText") ?: string("message") ?: "Stream error", raw = this),
            )
            type.startsWith("data-") || type == "data" -> StreamPart.Data(
                id = id,
                state = state ?: "done",
                type = type,
                payload = this["data"] ?: JsonObject(this.filterKeys { it != "type" && it != "id" && it != "state" }),
            )
            else -> StreamPart.Custom(
                id = id,
                state = state ?: "unknown",
                type = type,
                payload = this,
            )
        }
    }

    private fun JsonObject.toRawEvent(defaultSequence: Long, now: Long): RawStreamEvent {
        return RawStreamEvent(
            sequence = long("sequence") ?: defaultSequence,
            eventType = string("eventType") ?: string("type") ?: "unknown",
            partId = string("partId") ?: string("id"),
            payload = this["payload"] ?: this,
            receivedAtMs = long("receivedAtMs") ?: now,
        )
    }

    private fun JsonElement.toStreamError(): StreamError {
        val obj = this as? JsonObject
        return StreamError(
            message = obj?.string("message")
                ?: obj?.string("errorText")
                ?: (this as? JsonPrimitive)?.contentOrNull
                ?: "Stream error",
            code = obj?.string("code"),
            raw = this,
        )
    }

    private fun String?.toStreamStatus(): StreamStatus {
        return when (this) {
            "loading" -> StreamStatus.Loading
            "streaming" -> StreamStatus.Streaming
            "done", "completed", "complete" -> StreamStatus.Completed
            "failed", "error" -> StreamStatus.Failed
            "cancelled", "canceled" -> StreamStatus.Cancelled
            else -> StreamStatus.Idle
        }
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.long(key: String): Long? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()

    private fun JsonObject.objectArray(key: String): List<JsonObject> =
        ((this[key] as? JsonArray) ?: JsonArray(emptyList())).mapNotNull { it as? JsonObject }
}
```

- [ ] **Step 4: Run parser tests**

Run:

```bash
./gradlew :libraries:agentstream:testDebugUnitTest --tests '*StreamSnapshotParserTest' --no-daemon
```

Expected: PASS.

- [ ] **Step 5: Add snapshot JSON codec test**

Create `libraries/agentstream/src/test/kotlin/io/element/android/libraries/agentstream/api/StreamSnapshotJsonCodecTest.kt`:

```kotlin
/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.agentstream.api

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

class StreamSnapshotJsonCodecTest {
    private val codec = StreamSnapshotJsonCodec()

    @Test
    fun `encodes and decodes completed snapshot`() {
        val snapshot = StreamSnapshot(
            schemaVersion = AGENT_STREAM_SCHEMA_VERSION,
            streamId = "stream-1",
            status = StreamStatus.Completed,
            parts = listOf(
                StreamPart.Text("text-1", TextPartState.Complete, "hello"),
                StreamPart.Tool(
                    id = "tool-1",
                    toolName = "weather",
                    toolState = ToolPartState.OutputAvailable,
                    input = JsonObject(mapOf("city" to JsonPrimitive("Shanghai"))),
                    output = JsonObject(mapOf("temp" to JsonPrimitive("21C"))),
                    error = null,
                    title = "Weather",
                ),
            ),
            rawEvents = emptyList(),
            updatedAtMs = 1000L,
            completedAtMs = 1000L,
            error = null,
        )

        val decoded = codec.decode(codec.encode(snapshot))

        assertThat(decoded.streamId).isEqualTo("stream-1")
        assertThat(decoded.status).isEqualTo(StreamStatus.Completed)
        assertThat(decoded.completedAtMs).isEqualTo(1000L)
        assertThat((decoded.parts[0] as StreamPart.Text).text).isEqualTo("hello")
        assertThat((decoded.parts[1] as StreamPart.Tool).toolName).isEqualTo("weather")
    }
}
```

- [ ] **Step 6: Run codec test to verify failure**

Run:

```bash
./gradlew :libraries:agentstream:testDebugUnitTest --tests '*StreamSnapshotJsonCodecTest' --no-daemon
```

Expected: FAIL because `StreamSnapshotJsonCodec` does not exist.

- [ ] **Step 7: Implement snapshot JSON codec**

Create `libraries/agentstream/src/main/kotlin/io/element/android/libraries/agentstream/api/StreamSnapshotJsonCodec.kt`:

```kotlin
/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.agentstream.api

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class StreamSnapshotJsonCodec(
    private val parser: StreamSnapshotParser = StreamSnapshotParser(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(snapshot: StreamSnapshot): String {
        val root = buildJsonObject {
            put("schemaVersion", snapshot.schemaVersion)
            put("streamId", snapshot.streamId)
            put("status", snapshot.status.wireValue())
            put("updatedAtMs", snapshot.updatedAtMs)
            snapshot.completedAtMs?.let { put("completedAtMs", it) }
            snapshot.error?.let { put("error", it.toJson()) }
            put("parts", JsonArray(snapshot.parts.map { it.toJson() }))
            put("rawEvents", JsonArray(snapshot.rawEvents.map { it.toJson() }))
        }
        return json.encodeToString<JsonElement>(root)
    }

    fun decode(value: String): StreamSnapshot = parser.parse(value)

    private fun StreamPart.toJson(): JsonObject {
        return when (this) {
            is StreamPart.Text -> buildJsonObject {
                put("type", "text")
                put("id", id)
                put("state", state)
                put("text", text)
            }
            is StreamPart.Reasoning -> buildJsonObject {
                put("type", "reasoning")
                put("id", id)
                put("state", state)
                put("text", text)
            }
            is StreamPart.Tool -> buildJsonObject {
                put("type", "tool")
                put("id", id)
                put("state", state)
                put("toolName", toolName)
                title?.let { put("title", it) }
                input?.let { put("input", it) }
                output?.let { put("output", it) }
                error?.let { put("error", it.toJson()) }
            }
            is StreamPart.Data -> buildJsonObject {
                put("type", type)
                put("id", id)
                put("state", state)
                payload?.let { put("data", it) }
            }
            is StreamPart.Source -> buildJsonObject {
                put("type", if (sourceType == "document") "source-document" else "source-url")
                put("id", id)
                put("state", state)
                put("sourceType", sourceType)
                put("title", title)
                url?.let { put("url", it) }
                filename?.let { put("filename", it) }
                mediaType?.let { put("mediaType", it) }
            }
            is StreamPart.File -> buildJsonObject {
                put("type", "file")
                put("id", id)
                put("state", state)
                mediaType?.let { put("mediaType", it) }
                filename?.let { put("filename", it) }
                url?.let { put("url", it) }
            }
            is StreamPart.Step -> buildJsonObject {
                put("type", "step-start")
                put("id", id)
                put("state", state)
            }
            is StreamPart.Error -> buildJsonObject {
                put("type", "error")
                put("id", id)
                put("state", state)
                put("error", error.toJson())
            }
            is StreamPart.Custom -> buildJsonObject {
                put("type", type)
                put("id", id)
                put("state", state)
                payload?.let { put("data", it) }
            }
        }
    }

    private fun StreamError.toJson(): JsonObject {
        return buildJsonObject {
            put("message", message)
            code?.let { put("code", it) }
            raw?.let { put("raw", it) }
        }
    }

    private fun RawStreamEvent.toJson(): JsonObject {
        return buildJsonObject {
            put("sequence", sequence)
            put("eventType", eventType)
            partId?.let { put("partId", it) }
            put("payload", payload)
            put("receivedAtMs", receivedAtMs)
        }
    }

    private fun StreamStatus.wireValue(): String {
        return when (this) {
            StreamStatus.Idle -> "idle"
            StreamStatus.Loading -> "loading"
            StreamStatus.Streaming -> "streaming"
            StreamStatus.Completed -> "completed"
            StreamStatus.Failed -> "failed"
            StreamStatus.Cancelled -> "cancelled"
        }
    }
}
```

- [ ] **Step 8: Run codec and parser tests**

Run:

```bash
./gradlew :libraries:agentstream:testDebugUnitTest --tests '*StreamSnapshotParserTest' --tests '*StreamSnapshotJsonCodecTest' --no-daemon
```

Expected: PASS.

- [ ] **Step 9: Commit**

Run:

```bash
git add libraries/agentstream/src/main/kotlin/io/element/android/libraries/agentstream/api/StreamSnapshotParser.kt \
  libraries/agentstream/src/main/kotlin/io/element/android/libraries/agentstream/api/StreamSnapshotJsonCodec.kt \
  libraries/agentstream/src/test/kotlin/io/element/android/libraries/agentstream/api/StreamSnapshotParserTest.kt \
  libraries/agentstream/src/test/kotlin/io/element/android/libraries/agentstream/api/StreamSnapshotJsonCodecTest.kt
git commit -m "feat: parse agent stream snapshots"
```

## Task 3: Implement SDK Lifecycle, Storage Lookup, And In-flight Dedupe

**Files:**
- Create: `libraries/agentstream/src/main/kotlin/io/element/android/libraries/agentstream/api/DefaultAgentStreamClient.kt`
- Test: `libraries/agentstream/src/test/kotlin/io/element/android/libraries/agentstream/api/DefaultAgentStreamClientTest.kt`

- [ ] **Step 1: Add lifecycle tests**

Create `libraries/agentstream/src/test/kotlin/io/element/android/libraries/agentstream/api/DefaultAgentStreamClientTest.kt`:

```kotlin
/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.agentstream.api

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultAgentStreamClientTest {
    private val parser = StreamSnapshotParser(clock = { 1000L })

    @Test
    fun `storage completed hit publishes cached snapshot without network`() = runTest {
        val cached = completedSnapshot("stream-1", "cached")
        val storage = FakeStreamStorageProvider(loadResult = cached)
        val http = FakeStreamHttpClient()
        val runner = TestStreamTaskRunner(this)
        val client = DefaultAgentStreamClient(
            storageProvider = storage,
            httpClient = http,
            taskRunner = runner,
            snapshotParser = parser,
            reducerSessionFactory = FakeStreamReducerSessionFactory(),
        )
        val snapshots = mutableListOf<StreamSnapshot>()

        client.getStream(request("stream-1")).subscribe { snapshots += it }
        advanceUntilIdle()

        assertThat(snapshots.last()).isEqualTo(cached)
        assertThat(http.openCount).isEqualTo(0)
    }

    @Test
    fun `same stream id dedupes in flight network request`() = runTest {
        val storage = FakeStreamStorageProvider()
        val http = FakeStreamHttpClient(
            chunks = listOf(
                """{"streamId":"stream-1","status":"streaming","parts":[{"type":"text","id":"t","state":"streaming","text":"hi"}]}""",
            )
        )
        val runner = TestStreamTaskRunner(this)
        val client = DefaultAgentStreamClient(storage, http, runner, parser, FakeStreamReducerSessionFactory())
        val first = mutableListOf<StreamSnapshot>()
        val second = mutableListOf<StreamSnapshot>()

        client.getStream(request("stream-1")).subscribe { first += it }
        client.getStream(request("stream-1")).subscribe { second += it }
        advanceUntilIdle()

        assertThat(http.openCount).isEqualTo(1)
        assertThat(first.any { it.status == StreamStatus.Streaming }).isTrue()
        assertThat(second.any { it.status == StreamStatus.Streaming }).isTrue()
    }

    @Test
    fun `completed stream saves final snapshot`() = runTest {
        val storage = FakeStreamStorageProvider()
        val http = FakeStreamHttpClient(
            chunks = listOf(
                """{"streamId":"stream-1","status":"streaming","parts":[{"type":"text","id":"t","state":"streaming","text":"hi"}]}""",
            )
        )
        val runner = TestStreamTaskRunner(this)
        val client = DefaultAgentStreamClient(storage, http, runner, parser, FakeStreamReducerSessionFactory())

        client.getStream(request("stream-1")).subscribe { }
        advanceUntilIdle()

        assertThat(storage.savedSnapshots).isNotEmpty()
        assertThat(storage.savedSnapshots.last().status).isEqualTo(StreamStatus.Completed)
        assertThat((storage.savedSnapshots.last().parts.single() as StreamPart.Text).text).isEqualTo("hi")
    }

    @Test
    fun `network failure publishes failed snapshot and saves error without replacing completed cache`() = runTest {
        val completed = completedSnapshot("stream-1", "old")
        val storage = FakeStreamStorageProvider(loadResult = null).apply {
            savedSnapshots += completed
        }
        val http = FakeStreamHttpClient(error = IllegalStateException("boom"))
        val runner = TestStreamTaskRunner(this)
        val client = DefaultAgentStreamClient(storage, http, runner, parser, FakeStreamReducerSessionFactory())
        val snapshots = mutableListOf<StreamSnapshot>()

        client.getStream(request("stream-1")).subscribe { snapshots += it }
        advanceUntilIdle()

        assertThat(snapshots.last().status).isEqualTo(StreamStatus.Failed)
        assertThat(snapshots.last().error?.message).contains("boom")
        assertThat(storage.savedSnapshots.last()).isEqualTo(completed)
    }

    private fun request(streamId: String) = StreamRequest(
        streamId = streamId,
        sender = "@bot:keepsecret.io",
        roomId = "!room:keepsecret.io",
        eventId = "\$event",
    )

    private fun completedSnapshot(streamId: String, text: String) = StreamSnapshot(
        schemaVersion = AGENT_STREAM_SCHEMA_VERSION,
        streamId = streamId,
        status = StreamStatus.Completed,
        parts = listOf(StreamPart.Text("text", TextPartState.Complete, text)),
        rawEvents = emptyList(),
        updatedAtMs = 1L,
        completedAtMs = 1L,
        error = null,
    )

    private class FakeStreamStorageProvider(
        private val loadResult: StreamSnapshot? = null,
    ) : StreamStorageProvider {
        val savedSnapshots = mutableListOf<StreamSnapshot>()

        override suspend fun load(streamId: String): StreamSnapshot? = loadResult

        override suspend fun save(streamId: String, snapshot: StreamSnapshot) {
            if (savedSnapshots.any { it.streamId == streamId && it.status == StreamStatus.Completed } && snapshot.status == StreamStatus.Failed) {
                return
            }
            savedSnapshots += snapshot
        }

        override suspend fun delete(streamId: String) = Unit
    }

    private class FakeStreamHttpClient(
        private val chunks: List<String> = emptyList(),
        private val error: Throwable? = null,
    ) : StreamHttpClient {
        var openCount = 0

        override suspend fun openStream(request: StreamRequest, onChunk: suspend (String) -> Unit) {
            openCount++
            error?.let { throw it }
            chunks.forEach { onChunk(it) }
        }
    }

    private class TestStreamTaskRunner(
        private val scope: TestScope,
    ) : StreamTaskRunner {
        override fun run(key: String, block: suspend () -> Unit): StreamTask {
            val job = scope.launch { block() }
            return object : StreamTask {
                override fun cancel() {
                    job.cancel()
                }
            }
        }
    }

    private class FakeStreamReducerSessionFactory : StreamReducerSessionFactory {
        override fun create(streamId: String, includeRawEvents: Boolean): StreamReducerSession {
            return object : StreamReducerSession {
                private var latest = """{"streamId":"$streamId","status":"streaming","parts":[]}"""
                override fun applySseChunk(chunk: String): String {
                    latest = chunk
                    return chunk
                }
                override fun finish(): String = latest
                override fun snapshot(): String = latest
                override fun close() = Unit
            }
        }
    }
}
```

- [ ] **Step 2: Run lifecycle tests to verify failure**

Run:

```bash
./gradlew :libraries:agentstream:testDebugUnitTest --tests '*DefaultAgentStreamClientTest' --no-daemon
```

Expected: FAIL because `DefaultAgentStreamClient` does not exist.

- [ ] **Step 3: Implement lifecycle client**

Create `libraries/agentstream/src/main/kotlin/io/element/android/libraries/agentstream/api/DefaultAgentStreamClient.kt`:

```kotlin
/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.agentstream.api

import java.util.LinkedHashMap
import java.util.UUID

class DefaultAgentStreamClient(
    private val storageProvider: StreamStorageProvider,
    private val httpClient: StreamHttpClient,
    private val taskRunner: StreamTaskRunner,
    private val snapshotParser: StreamSnapshotParser = StreamSnapshotParser(),
    private val reducerSessionFactory: StreamReducerSessionFactory = NativeStreamReducerSessionFactory(),
    private val clock: () -> Long = { System.currentTimeMillis() },
) : AgentStreamClient {
    private val lock = Any()
    private val handles = mutableMapOf<String, DefaultStreamHandle>()
    private val memoryCache = object : LinkedHashMap<String, StreamSnapshot>(MAX_MEMORY_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, StreamSnapshot>?): Boolean {
            return size > MAX_MEMORY_ENTRIES
        }
    }

    override fun getStream(request: StreamRequest): StreamHandle {
        synchronized(lock) {
            memoryCache[request.streamId]?.let { cached ->
                return CompletedStreamHandle(cached)
            }
            return handles.getOrPut(request.streamId) {
                DefaultStreamHandle(request).also { it.start() }
            }
        }
    }

    private inner class DefaultStreamHandle(
        private val request: StreamRequest,
    ) : StreamHandle {
        private val listeners = mutableMapOf<String, StreamListener>()
        private var task: StreamTask? = null
        private var currentSnapshot = loadingSnapshot(request.streamId)
        private var lastParsedSnapshot: StreamSnapshot? = null

        override fun snapshot(): StreamSnapshot = synchronized(lock) { currentSnapshot }

        override fun subscribe(listener: StreamListener): StreamSubscription {
            val id = UUID.randomUUID().toString()
            val snapshotToSend = synchronized(lock) {
                listeners[id] = listener
                currentSnapshot
            }
            listener.onSnapshot(snapshotToSend)
            return object : StreamSubscription {
                override fun cancel() {
                    synchronized(lock) {
                        listeners.remove(id)
                    }
                }
            }
        }

        override fun refresh() {
            synchronized(lock) {
                memoryCache.remove(request.streamId)
                task?.cancel()
                task = null
                currentSnapshot = loadingSnapshot(request.streamId)
            }
            start()
        }

        override fun cancel() {
            synchronized(lock) {
                task?.cancel()
                task = null
                publishLocked(cancelledSnapshot(request.streamId))
                handles.remove(request.streamId)
            }
        }

        fun start() {
            synchronized(lock) {
                if (task != null) return
                task = taskRunner.run(request.streamId) { runStream() }
            }
        }

        private suspend fun runStream() {
            val cached = storageProvider.load(request.streamId)
            if (cached != null && cached.parts.isNotEmpty() && cached.status == StreamStatus.Completed) {
                completeFromStorage(cached)
                return
            }
            if (cached != null && cached.parts.isNotEmpty()) {
                publish(cached)
            }

            try {
                reducerSessionFactory.create(
                    streamId = request.streamId,
                    includeRawEvents = request.includeRawEvents,
                ).use { session ->
                    httpClient.openStream(request) { chunk ->
                        val parsed = snapshotParser.parse(session.applySseChunk(chunk))
                        lastParsedSnapshot = parsed
                        publish(parsed)
                    }
                    val finalSnapshot = snapshotParser.parse(session.finish())
                        .asCompleted(request.streamId, clock())
                    lastParsedSnapshot = finalSnapshot
                    publish(finalSnapshot)
                    storageProvider.save(request.streamId, finalSnapshot)
                    synchronized(lock) {
                        memoryCache[request.streamId] = finalSnapshot
                        handles.remove(request.streamId)
                    }
                }
            } catch (throwable: Throwable) {
                val failed = failedSnapshot(request.streamId, throwable)
                publish(failed)
                val existingCompleted = synchronized(lock) { memoryCache[request.streamId]?.status == StreamStatus.Completed }
                if (!existingCompleted) {
                    storageProvider.save(request.streamId, failed)
                }
                synchronized(lock) {
                    handles.remove(request.streamId)
                }
            }
        }

        private fun completeFromStorage(snapshot: StreamSnapshot) {
            synchronized(lock) {
                memoryCache[request.streamId] = snapshot
                publishLocked(snapshot)
                handles.remove(request.streamId)
            }
        }

        private fun publish(snapshot: StreamSnapshot) {
            synchronized(lock) {
                publishLocked(snapshot)
            }
        }

        private fun publishLocked(snapshot: StreamSnapshot) {
            currentSnapshot = snapshot
            listeners.values.toList().forEach { listener ->
                listener.onSnapshot(snapshot)
            }
        }

        private fun failedSnapshot(streamId: String, throwable: Throwable): StreamSnapshot {
            val error = StreamError(throwable.message ?: "Failed to load stream")
            return StreamSnapshot(
                schemaVersion = AGENT_STREAM_SCHEMA_VERSION,
                streamId = streamId,
                status = StreamStatus.Failed,
                parts = listOf(StreamPart.Error("$streamId-error", "error", error)),
                rawEvents = lastParsedSnapshot?.rawEvents.orEmpty(),
                updatedAtMs = clock(),
                completedAtMs = null,
                error = error,
            )
        }
    }

    private class CompletedStreamHandle(
        private val completed: StreamSnapshot,
    ) : StreamHandle {
        override fun snapshot(): StreamSnapshot = completed
        override fun subscribe(listener: StreamListener): StreamSubscription {
            listener.onSnapshot(completed)
            return object : StreamSubscription {
                override fun cancel() = Unit
            }
        }
        override fun refresh() = Unit
        override fun cancel() = Unit
    }

    private fun loadingSnapshot(streamId: String): StreamSnapshot {
        return StreamSnapshot(
            schemaVersion = AGENT_STREAM_SCHEMA_VERSION,
            streamId = streamId,
            status = StreamStatus.Loading,
            parts = emptyList(),
            rawEvents = emptyList(),
            updatedAtMs = clock(),
            completedAtMs = null,
            error = null,
        )
    }

    private fun cancelledSnapshot(streamId: String): StreamSnapshot {
        return StreamSnapshot(
            schemaVersion = AGENT_STREAM_SCHEMA_VERSION,
            streamId = streamId,
            status = StreamStatus.Cancelled,
            parts = emptyList(),
            rawEvents = emptyList(),
            updatedAtMs = clock(),
            completedAtMs = null,
            error = null,
        )
    }

    private fun StreamSnapshot.asCompleted(streamId: String, now: Long): StreamSnapshot {
        val completedParts = parts.map { part ->
            when (part) {
                is StreamPart.Text -> part.copy(textState = TextPartState.Complete)
                is StreamPart.Reasoning -> part.copy(reasoningState = TextPartState.Complete)
                else -> part
            }
        }
        return copy(
            streamId = if (this.streamId.isBlank()) streamId else this.streamId,
            status = StreamStatus.Completed,
            parts = completedParts,
            updatedAtMs = now,
            completedAtMs = now,
            error = null,
        )
    }

    companion object {
        private const val MAX_MEMORY_ENTRIES = 128
    }
}
```

- [ ] **Step 4: Run lifecycle tests**

Run:

```bash
./gradlew :libraries:agentstream:testDebugUnitTest --tests '*DefaultAgentStreamClientTest' --no-daemon
```

Expected: PASS.

- [ ] **Step 5: Run all agentstream tests**

Run:

```bash
./gradlew :libraries:agentstream:testDebugUnitTest --no-daemon
```

Expected: PASS.

- [ ] **Step 6: Commit**

Run:

```bash
git add libraries/agentstream/src/main/kotlin/io/element/android/libraries/agentstream/api/DefaultAgentStreamClient.kt \
  libraries/agentstream/src/test/kotlin/io/element/android/libraries/agentstream/api/DefaultAgentStreamClientTest.kt
git commit -m "feat: add agent stream lifecycle client"
```

## Task 4: Convert Android Reducer Adapter To Consume SDK Snapshots

**Files:**
- Modify: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/factories/event/AiSdkStreamReducer.kt`
- Modify: `features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/timeline/factories/event/AiSdkStreamReducerTest.kt`

- [ ] **Step 1: Update adapter tests to use typed SDK snapshots**

Replace `features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/timeline/factories/event/AiSdkStreamReducerTest.kt` with:

```kotlin
/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.factories.event

import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.timeline.model.event.AiReasoningStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiSourceStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiTextStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiToolStreamPart
import io.element.android.libraries.agentstream.api.AGENT_STREAM_SCHEMA_VERSION
import io.element.android.libraries.agentstream.api.StreamPart
import io.element.android.libraries.agentstream.api.StreamSnapshot
import io.element.android.libraries.agentstream.api.StreamStatus
import io.element.android.libraries.agentstream.api.TextPartState
import io.element.android.libraries.agentstream.api.ToolPartState
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

class AiSdkStreamReducerTest {
    private val reducer = AiSdkStreamReducer()

    @Test
    fun `maps sdk stream snapshot into renderable timeline content`() {
        val snapshot = StreamSnapshot(
            schemaVersion = AGENT_STREAM_SCHEMA_VERSION,
            streamId = "stream-1",
            status = StreamStatus.Streaming,
            parts = listOf(
                StreamPart.Text("text-1", TextPartState.Complete, "Weather result"),
                StreamPart.Tool(
                    id = "tool-1",
                    toolName = "weather",
                    toolState = ToolPartState.InputAvailable,
                    input = JsonObject(mapOf("city" to JsonPrimitive("Shanghai"))),
                    output = null,
                    error = null,
                    title = "Weather",
                ),
                StreamPart.Tool(
                    id = "tool-2",
                    toolName = "lookup",
                    toolState = ToolPartState.OutputAvailable,
                    input = null,
                    output = JsonPrimitive("Sunny"),
                    error = null,
                    title = null,
                ),
                StreamPart.Reasoning("reason-1", TextPartState.Complete, "Check the city forecast."),
                StreamPart.Source("source-1", "done", "url", "Forecast API", "https://example.com/weather", null, null),
            ),
            rawEvents = emptyList(),
            updatedAtMs = 1L,
            completedAtMs = null,
            error = null,
        )

        val result = reducer.mapSnapshot(snapshot, isEdited = false, sender = "@bot:keepsecret.io")

        assertThat(result.isStreaming).isTrue()
        assertThat(result.body).isEqualTo("Weather result")
        assertThat(result.streamId).isEqualTo("stream-1")
        assertThat(result.sender).isEqualTo("@bot:keepsecret.io")
        assertThat(result.parts).hasSize(5)
        assertThat((result.parts[0] as AiTextStreamPart).text).isEqualTo("Weather result")
        assertThat((result.parts[1] as AiToolStreamPart).state).isEqualTo("input-available")
        assertThat((result.parts[2] as AiToolStreamPart).output).isEqualTo("\"Sunny\"")
        assertThat((result.parts[3] as AiReasoningStreamPart).text).isEqualTo("Check the city forecast.")
        assertThat((result.parts[4] as AiSourceStreamPart).url).isEqualTo("https://example.com/weather")
    }
}
```

- [ ] **Step 2: Run adapter tests to verify failure**

Run:

```bash
./gradlew :features:messages:impl:testDebugUnitTest --tests '*AiSdkStreamReducerTest' --no-daemon
```

Expected: FAIL because `mapSnapshot` does not exist and old JSON parsing API is still present.

- [ ] **Step 3: Replace `AiSdkStreamReducer` with a snapshot adapter**

Modify `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/factories/event/AiSdkStreamReducer.kt` so the public API is:

```kotlin
@Inject
class AiSdkStreamReducer {
    fun mapSnapshot(
        snapshot: StreamSnapshot,
        isEdited: Boolean,
        sender: String?,
    ): TimelineItemAiContent {
        val streamParts = snapshot.parts.map { it.toTimelinePart() }
        val textParts = streamParts.filterIsInstance<AiTextStreamPart>()
        val reasoningParts = streamParts.filterIsInstance<AiReasoningStreamPart>()
        val toolParts = streamParts.filterIsInstance<AiToolStreamPart>()
        val sourceParts = streamParts.filterIsInstance<AiSourceStreamPart>()

        return TimelineItemAiContent(
            body = textParts.joinToString(separator = "\n\n") { it.text },
            isEdited = isEdited,
            isStreaming = snapshot.status == StreamStatus.Loading || snapshot.status == StreamStatus.Streaming,
            streamId = snapshot.streamId,
            sender = sender,
            thinkingSteps = reasoningParts.mapIndexed { index, part ->
                AiThinkingStep(
                    title = "Thinking ${index + 1}",
                    description = part.text,
                    status = part.state,
                )
            }.toImmutableList(),
            toolCalls = toolParts.map { part ->
                val name = part.toolName.ifBlank { part.id }
                AiToolCall(
                    name = name,
                    displayName = part.title ?: name,
                    state = part.state,
                    output = part.output,
                    error = part.errorText,
                )
            }.toImmutableList(),
            sources = sourceParts.map { part ->
                AiSource(
                    title = part.title,
                    url = part.url,
                    snippet = null,
                )
            }.toImmutableList(),
            quickActions = emptyList<AiQuickAction>().toImmutableList(),
            parts = streamParts.toImmutableList(),
        )
    }

    private fun StreamPart.toTimelinePart(): AiStreamPart {
        return when (this) {
            is StreamPart.Text -> AiTextStreamPart(id, state, text)
            is StreamPart.Reasoning -> AiReasoningStreamPart(id, state, text)
            is StreamPart.Tool -> AiToolStreamPart(
                id = id,
                state = state,
                toolName = toolName,
                title = title,
                input = input?.toString(),
                output = output?.toString(),
                errorText = error?.message,
            )
            is StreamPart.Source -> AiSourceStreamPart(id, state, sourceType, title, url, filename, mediaType)
            is StreamPart.File -> AiFileStreamPart(id, state, mediaType, filename, url)
            is StreamPart.Data -> AiDataStreamPart(id, state, type, payload?.toString().orEmpty())
            is StreamPart.Error -> AiErrorStreamPart(id, state, error.message)
            is StreamPart.Step -> AiCustomStreamPart(id, state, "step-start", "")
            is StreamPart.Custom -> AiCustomStreamPart(id, state, type, payload?.toString().orEmpty())
        }
    }
}
```

Keep the package declaration, copyright header, imports for timeline model classes, `StreamPart`, `StreamSnapshot`, `StreamStatus`, `dev.zacsweers.metro.Inject`, and `kotlinx.collections.immutable.toImmutableList`. Remove imports for `AgentStreamSession`, `Json`, `JsonObject`, `JsonArray`, `JsonPrimitive`, and parser helper functions.

- [ ] **Step 4: Run adapter tests**

Run:

```bash
./gradlew :features:messages:impl:testDebugUnitTest --tests '*AiSdkStreamReducerTest' --no-daemon
```

Expected: PASS.

- [ ] **Step 5: Commit**

Run:

```bash
git add features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/factories/event/AiSdkStreamReducer.kt \
  features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/timeline/factories/event/AiSdkStreamReducerTest.kt
git commit -m "refactor: map ai timeline content from stream snapshots"
```

## Task 5: Add Android Resource Adapters And SQLite Stream Store

**Files:**
- Create: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/AndroidAgentStreamAdapters.kt`
- Test: `features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/timeline/components/event/AndroidAgentStreamAdaptersTest.kt`

- [ ] **Step 1: Add adapter tests**

Create `features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/timeline/components/event/AndroidAgentStreamAdaptersTest.kt`:

```kotlin
/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import com.google.common.truth.Truth.assertThat
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.element.android.libraries.agentstream.api.AGENT_STREAM_SCHEMA_VERSION
import io.element.android.libraries.agentstream.api.StreamPart
import io.element.android.libraries.agentstream.api.StreamSnapshot
import io.element.android.libraries.agentstream.api.StreamStatus
import io.element.android.libraries.agentstream.api.TextPartState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidAgentStreamAdaptersTest {
    @Test
    fun `sqlite storage ignores empty completed snapshots`() = runTest {
        val storage = newStorage("empty", reset = true)
        val emptyCompleted = StreamSnapshot(
            schemaVersion = AGENT_STREAM_SCHEMA_VERSION,
            streamId = "stream-1",
            status = StreamStatus.Completed,
            parts = emptyList(),
            rawEvents = emptyList(),
            updatedAtMs = 1L,
            completedAtMs = 1L,
            error = null,
        )

        storage.save("stream-1", emptyCompleted)

        assertThat(storage.load("stream-1")).isNull()
    }

    @Test
    fun `sqlite storage returns non empty completed snapshots across instances`() = runTest {
        val name = "persist"
        val first = newStorage(name)
        val completed = emptyCompletedSnapshot("stream-1").copy(
            parts = listOf(StreamPart.Text("text", TextPartState.Complete, "cached"))
        )

        first.save("stream-1", completed)
        first.close()
        val second = newStorage(name, reset = false)

        assertThat((second.load("stream-1")?.parts?.single() as StreamPart.Text).text).isEqualTo("cached")
        second.close()
    }

    private fun newStorage(name: String, reset: Boolean): SQLiteStreamStorageProvider {
        val context = ApplicationProvider.getApplicationContext<Context>()
        if (reset) {
            context.deleteDatabase("agent_stream_test_$name.db")
        }
        return SQLiteStreamStorageProvider(context, "agent_stream_test_$name.db")
    }

    private fun emptyCompletedSnapshot(streamId: String) = StreamSnapshot(
        schemaVersion = AGENT_STREAM_SCHEMA_VERSION,
        streamId = streamId,
        status = StreamStatus.Completed,
        parts = emptyList(),
        rawEvents = emptyList(),
        updatedAtMs = 1L,
        completedAtMs = 1L,
        error = null,
    )
}
```

- [ ] **Step 2: Run adapter tests to verify failure**

Run:

```bash
./gradlew :features:messages:impl:testDebugUnitTest --tests '*AndroidAgentStreamAdaptersTest' --no-daemon
```

Expected: FAIL because `SQLiteStreamStorageProvider` does not exist.

- [ ] **Step 3: Add Android adapters**

Create `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/AndroidAgentStreamAdapters.kt`:

```kotlin
/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.agentstream.api.AgentStreamClient
import io.element.android.libraries.agentstream.api.DefaultAgentStreamClient
import io.element.android.libraries.agentstream.api.StreamHttpClient
import io.element.android.libraries.agentstream.api.StreamRequest
import io.element.android.libraries.agentstream.api.StreamSnapshotJsonCodec
import io.element.android.libraries.agentstream.api.StreamSnapshot
import io.element.android.libraries.agentstream.api.StreamStatus
import io.element.android.libraries.agentstream.api.StreamStorageProvider
import io.element.android.libraries.agentstream.api.StreamTask
import io.element.android.libraries.agentstream.api.StreamTaskRunner
import io.element.android.libraries.chatbot.api.ChatbotApiServiceFactory
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.di.RoomScope
import io.element.android.libraries.matrix.api.MatrixClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@SingleIn(RoomScope::class)
@ContributesBinding(RoomScope::class, binding = AgentStreamClient::class)
class AndroidAgentStreamClient @Inject constructor(
    storageProvider: StreamStorageProvider,
    httpClient: StreamHttpClient,
    taskRunner: StreamTaskRunner,
) : AgentStreamClient by DefaultAgentStreamClient(
    storageProvider = storageProvider,
    httpClient = httpClient,
    taskRunner = taskRunner,
)

@SingleIn(RoomScope::class)
@ContributesBinding(RoomScope::class, binding = StreamHttpClient::class)
class ChatbotStreamHttpClient @Inject constructor(
    private val matrixClient: MatrixClient,
    private val chatbotApiServiceFactory: ChatbotApiServiceFactory,
) : StreamHttpClient {
    override suspend fun openStream(request: StreamRequest, onChunk: suspend (String) -> Unit) {
        chatbotApiServiceFactory
            .createForAiStream(matrixClient)
            .streamAgentMessage(request.streamId, request.sender, onChunk)
            .getOrThrow()
    }
}

@SingleIn(RoomScope::class)
@ContributesBinding(RoomScope::class, binding = StreamTaskRunner::class)
class CoroutineStreamTaskRunner @Inject constructor(
    private val dispatchers: CoroutineDispatchers,
) : StreamTaskRunner {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)

    override fun run(key: String, block: suspend () -> Unit): StreamTask {
        val job = scope.launch { block() }
        return object : StreamTask {
            override fun cancel() {
                job.cancel()
            }
        }
    }

    fun close() {
        scope.cancel()
    }
}

@SingleIn(RoomScope::class)
@ContributesBinding(RoomScope::class, binding = StreamStorageProvider::class)
class SQLiteStreamStorageProvider : SQLiteOpenHelper, StreamStorageProvider {
    private val codec = StreamSnapshotJsonCodec()

    @Inject constructor(
        @ApplicationContext context: Context,
    ) : this(context, DATABASE_NAME)

    internal constructor(
        context: Context,
        databaseName: String,
    ) : super(context, databaseName, null, DATABASE_VERSION)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS agent_stream_snapshots (
              stream_id TEXT PRIMARY KEY NOT NULL,
              schema_version INTEGER NOT NULL,
              status TEXT NOT NULL,
              snapshot_json TEXT NOT NULL,
              updated_at_ms INTEGER NOT NULL,
              completed_at_ms INTEGER
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS agent_stream_snapshots")
        onCreate(db)
    }

    override suspend fun load(streamId: String): StreamSnapshot? {
        val cursor = readableDatabase.query(
            "agent_stream_snapshots",
            arrayOf("snapshot_json"),
            "stream_id = ?",
            arrayOf(streamId),
            null,
            null,
            null,
            "1",
        )
        cursor.use {
            if (!it.moveToFirst()) return null
            val snapshot = codec.decode(it.getString(0))
            return snapshot.takeUnless { value -> value.status == StreamStatus.Completed && value.parts.isEmpty() }
        }
    }

    override suspend fun save(streamId: String, snapshot: StreamSnapshot) {
        if (snapshot.status == StreamStatus.Completed && snapshot.parts.isEmpty()) return
        val existing = load(streamId)
        if (existing?.status == StreamStatus.Completed && snapshot.status == StreamStatus.Failed) return
        val values = ContentValues().apply {
            put("stream_id", streamId)
            put("schema_version", snapshot.schemaVersion)
            put("status", snapshot.status.name)
            put("snapshot_json", codec.encode(snapshot))
            put("updated_at_ms", snapshot.updatedAtMs)
            put("completed_at_ms", snapshot.completedAtMs)
        }
        writableDatabase.insertWithOnConflict(
            "agent_stream_snapshots",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    override suspend fun delete(streamId: String) {
        writableDatabase.delete("agent_stream_snapshots", "stream_id = ?", arrayOf(streamId))
    }

    companion object {
        private const val DATABASE_NAME = "agent_stream_snapshots.db"
        private const val DATABASE_VERSION = 1
    }
}
```

- [ ] **Step 4: Run adapter tests**

Run:

```bash
./gradlew :features:messages:impl:testDebugUnitTest --tests '*AndroidAgentStreamAdaptersTest' --no-daemon
```

Expected: PASS.

- [ ] **Step 5: Commit**

Run:

```bash
git add features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/AndroidAgentStreamAdapters.kt \
  features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/timeline/components/event/AndroidAgentStreamAdaptersTest.kt
git commit -m "feat: add android agent stream sdk adapters"
```

## Task 6: Replace Timeline Presenter Stream Logic With SDK Subscription

**Files:**
- Modify: `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/TimelineItemAiPresenter.kt`
- Test: `features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/timeline/components/event/TimelineItemAiPresenterTest.kt`

- [ ] **Step 1: Add presenter test**

Create `features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/timeline/components/event/TimelineItemAiPresenterTest.kt`:

```kotlin
/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.timeline.factories.event.AiSdkStreamReducer
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemAiContent
import io.element.android.libraries.agentstream.api.AGENT_STREAM_SCHEMA_VERSION
import io.element.android.libraries.agentstream.api.AgentStreamClient
import io.element.android.libraries.agentstream.api.StreamHandle
import io.element.android.libraries.agentstream.api.StreamListener
import io.element.android.libraries.agentstream.api.StreamPart
import io.element.android.libraries.agentstream.api.StreamRequest
import io.element.android.libraries.agentstream.api.StreamSnapshot
import io.element.android.libraries.agentstream.api.StreamStatus
import io.element.android.libraries.agentstream.api.StreamSubscription
import io.element.android.libraries.agentstream.api.TextPartState
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test

class TimelineItemAiPresenterTest {
    @Test
    fun `presenter uses sdk stream id and maps latest snapshot`() {
        val client = FakeAgentStreamClient()
        val reducer = AiSdkStreamReducer()
        val content = TimelineItemAiContent(
            body = "",
            isEdited = false,
            isStreaming = true,
            streamId = "stream-1",
            sender = "@bot:keepsecret.io",
            thinkingSteps = persistentListOf(),
            toolCalls = persistentListOf(),
            sources = persistentListOf(),
            quickActions = persistentListOf(),
        )

        val handle = client.getStream(StreamRequest("stream-1", "@bot:keepsecret.io", null, null))
        val mapped = reducer.mapSnapshot(handle.snapshot(), false, "@bot:keepsecret.io")

        assertThat(client.requests.single().streamId).isEqualTo("stream-1")
        assertThat(mapped.body).isEqualTo("cached")
    }

    private class FakeAgentStreamClient : AgentStreamClient {
        val requests = mutableListOf<StreamRequest>()

        override fun getStream(request: StreamRequest): StreamHandle {
            requests += request
            return object : StreamHandle {
                private val snapshot = StreamSnapshot(
                    schemaVersion = AGENT_STREAM_SCHEMA_VERSION,
                    streamId = request.streamId,
                    status = StreamStatus.Completed,
                    parts = listOf(StreamPart.Text("text", TextPartState.Complete, "cached")),
                    rawEvents = emptyList(),
                    updatedAtMs = 1L,
                    completedAtMs = 1L,
                    error = null,
                )

                override fun snapshot(): StreamSnapshot = snapshot
                override fun subscribe(listener: StreamListener): StreamSubscription {
                    listener.onSnapshot(snapshot)
                    return object : StreamSubscription {
                        override fun cancel() = Unit
                    }
                }
                override fun refresh() = Unit
                override fun cancel() = Unit
            }
        }
    }
}
```

This test is intentionally small because Compose presenter tests in this module require more harness setup. It locks the dependency direction: timeline code consumes `AgentStreamClient` snapshots and maps through `AiSdkStreamReducer`.

- [ ] **Step 2: Run presenter test**

Run:

```bash
./gradlew :features:messages:impl:testDebugUnitTest --tests '*TimelineItemAiPresenterTest' --no-daemon
```

Expected: PASS after Task 4. This test does not instantiate the Compose presenter; it verifies the dependency direction and snapshot-to-content mapping used by the presenter.

- [ ] **Step 3: Replace presenter constructor dependencies**

Modify `TimelineItemAiPresenter` constructor in `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/TimelineItemAiPresenter.kt` to remove:

```kotlin
private val matrixClient: MatrixClient,
private val chatbotApiServiceFactory: ChatbotApiServiceFactory,
private val dispatchers: CoroutineDispatchers,
private val streamCache: TimelineItemAiStreamCache,
private val streamGate: TimelineItemAiStreamGate,
```

Add:

```kotlin
private val agentStreamClient: AgentStreamClient,
```

Keep:

```kotlin
@Assisted private val content: TimelineItemAiContent,
private val aiSdkStreamReducer: AiSdkStreamReducer,
```

- [ ] **Step 4: Replace `present()` body**

Replace the `present()` implementation with:

```kotlin
@Composable
override fun present(): TimelineItemAiState {
    val initialContent = content
    val streamId = initialContent.streamId
    var currentContent by remember(streamId, initialContent.parts) {
        mutableStateOf(initialContent)
    }

    LaunchedEffect(streamId, initialContent.parts) {
        if (streamId == null) {
            currentContent = initialContent
            return@LaunchedEffect
        }

        if (initialContent.parts.isNotEmpty()) {
            currentContent = initialContent
            return@LaunchedEffect
        }

        val handle = agentStreamClient.getStream(
            StreamRequest(
                streamId = streamId,
                sender = initialContent.sender,
                roomId = null,
                eventId = null,
                includeRawEvents = true,
            )
        )
        currentContent = aiSdkStreamReducer.mapSnapshot(
            snapshot = handle.snapshot(),
            isEdited = initialContent.isEdited,
            sender = initialContent.sender,
        )
        val subscription = handle.subscribe { snapshot ->
            currentContent = aiSdkStreamReducer.mapSnapshot(
                snapshot = snapshot,
                isEdited = initialContent.isEdited,
                sender = initialContent.sender,
            )
        }
        try {
            kotlinx.coroutines.awaitCancellation()
        } finally {
            subscription.cancel()
        }
    }

    return TimelineItemAiState(currentContent)
}
```

- [ ] **Step 5: Delete old local lifecycle classes from presenter file**

Remove these classes and related imports from `TimelineItemAiPresenter.kt`:

```kotlin
TimelineItemAiStreamGate
TimelineItemAiStreamCache
```

Also remove direct imports for:

```kotlin
io.element.android.libraries.agentstream.api.AgentStreamSession
io.element.android.libraries.chatbot.api.ChatbotApiServiceFactory
io.element.android.libraries.core.coroutine.CoroutineDispatchers
io.element.android.libraries.matrix.api.MatrixClient
kotlinx.coroutines.CompletableDeferred
kotlinx.coroutines.CancellationException
kotlinx.coroutines.currentCoroutineContext
kotlinx.coroutines.delay
kotlinx.coroutines.ensureActive
kotlinx.coroutines.sync.Mutex
kotlinx.coroutines.sync.withLock
kotlinx.coroutines.withContext
timber.log.Timber
java.util.LinkedHashMap
java.util.PriorityQueue
```

Add imports:

```kotlin
import io.element.android.libraries.agentstream.api.AgentStreamClient
import io.element.android.libraries.agentstream.api.StreamRequest
import kotlinx.coroutines.awaitCancellation
```

- [ ] **Step 6: Compile messages module**

Run:

```bash
./gradlew :features:messages:impl:compileDebugKotlin --no-daemon
```

Expected: PASS. Also run this search:

```bash
rg -n "AgentStreamSession|streamAgentMessage|TimelineItemAiStreamCache|TimelineItemAiStreamGate" features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/TimelineItemAiPresenter.kt
```

Expected: no matches.

- [ ] **Step 7: Run targeted tests**

Run:

```bash
./gradlew :features:messages:impl:testDebugUnitTest --tests '*AiSdkStreamReducerTest' --tests '*AndroidAgentStreamAdaptersTest' --tests '*TimelineItemAiPresenterTest' --no-daemon
```

Expected: PASS.

- [ ] **Step 8: Commit**

Run:

```bash
git add features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/TimelineItemAiPresenter.kt \
  features/messages/impl/src/test/kotlin/io/element/android/features/messages/impl/timeline/components/event/TimelineItemAiPresenterTest.kt
git commit -m "refactor: drive ai timeline stream from sdk snapshots"
```

## Task 7: Verify SDK-owned Lifecycle Against Spec Acceptance

**Files:**
- Modify: `docs/superpowers/plans/2026-06-11-ai-sdk-stream-lifecycle.md` only if a command or module path differs during execution.

- [ ] **Step 1: Run SDK tests**

Run:

```bash
./gradlew :libraries:agentstream:testDebugUnitTest --no-daemon
```

Expected: PASS.

- [ ] **Step 2: Run Android AI stream tests**

Run:

```bash
./gradlew :features:messages:impl:testDebugUnitTest --tests '*AiSdkStreamReducerTest' --tests '*AndroidAgentStreamAdaptersTest' --tests '*TimelineItemAiPresenterTest' --no-daemon
```

Expected: PASS.

- [ ] **Step 3: Compile Android messages implementation**

Run:

```bash
./gradlew :features:messages:impl:compileDebugKotlin --no-daemon
```

Expected: PASS.

- [ ] **Step 4: Verify Android timeline no longer owns stream lifecycle**

Run:

```bash
rg -n "AgentStreamSession|streamAgentMessage|TimelineItemAiStreamCache|TimelineItemAiStreamGate|tryStartLoading|finishLoading" features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline
```

Expected: no matches in `features/messages/impl/src/main/kotlin/io/element/android/features/messages/impl/timeline/components/event/TimelineItemAiPresenter.kt`. Matches in tests or comments should be removed if they imply timeline owns lifecycle.

- [ ] **Step 5: Verify SDK owns lifecycle concepts**

Run:

```bash
rg -n "StreamStorageProvider|StreamHttpClient|StreamTaskRunner|DefaultAgentStreamClient|AgentStreamSession" libraries/agentstream/src/main/kotlin
```

Expected: matches in `libraries/agentstream/src/main/kotlin/io/element/android/libraries/agentstream/api`.

- [ ] **Step 6: Build debug APK**

Run:

```bash
./gradlew :app:assembleFdroidDebug --no-daemon
```

Expected: PASS and APK created at `app/build/outputs/apk/fdroid/debug/app-fdroid-arm64-v8a-debug.apk`.

- [ ] **Step 7: Commit verification notes if plan was updated**

If this plan file was updated during execution, run:

```bash
git add docs/superpowers/plans/2026-06-11-ai-sdk-stream-lifecycle.md
git commit -m "docs: update ai stream lifecycle execution plan"
```

If the plan file was not updated, do not create a commit for this task.

## Task 8: Record Follow-up Plan Boundary For iOS Card Parity

**Files:**
- Create: `docs/superpowers/plans/2026-06-11-ai-sdk-ios-card-parity-followup.md`

- [ ] **Step 1: Create the follow-up stub**

Create `docs/superpowers/plans/2026-06-11-ai-sdk-ios-card-parity-followup.md`:

```markdown
# AI SDK iOS Card Parity Follow-up

This follow-up is intentionally separate from `2026-06-11-ai-sdk-stream-lifecycle.md`.

Start only after the Stream Lifecycle SDK plan is complete and Android timeline rendering is driven by `StreamSnapshot.parts`.

Source spec:

- `docs/superpowers/specs/2026-06-11-ai-sdk-stream-lifecycle-design.md`

Primary iOS implementation sources:

- `/Users/Ruihan/go/src/unseal-agent-ios/UnsealUI/Views/BubbleMessageView.swift`
- `/Users/Ruihan/go/src/unseal-agent-ios/UnsealUI/Views/UIParts/Utils/ToolGroupUtils.swift`
- `/Users/Ruihan/go/src/unseal-agent-ios/UnsealUI/Views/UIParts/ToolParts/ToolCallRootCardAdapter.swift`
- `/Users/Ruihan/go/src/unseal-agent-ios/UnsealUI/Views/UIParts/ToolParts/ToolPartView.swift`
- `/Users/Ruihan/go/src/unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/ToolCallRootCard.swift`
- `/Users/Ruihan/go/src/unseal-agent-ios/ToolCardsIOS/Sources/ToolCardsIOS/CardTransforms.swift`

Migration order:

1. Port grouping and ToolCallRootCard interaction parity.
2. Port fallback behavior that never exposes raw JSON to normal users.
3. Port Composio Search card family.
4. Port GitHub card family.
5. Port Gmail, Google Drive, Linear, Twitter, and Schedule cards.
6. Validate with real stream fixtures for single tool, multi tool, `COMPOSIO_MULTI_EXECUTE_TOOL`, `agent-*`, `data-ui-spec`, and `data-json-render`.
```

- [ ] **Step 2: Commit follow-up boundary**

Run:

```bash
git add docs/superpowers/plans/2026-06-11-ai-sdk-ios-card-parity-followup.md
git commit -m "docs: add ai stream card parity follow-up"
```

## Self-Review Checklist

- Spec coverage:
  - `getStream` unique entry: Task 1 and Task 3.
  - storage provider and final save: Task 1, Task 3, Task 5.
  - HTTP/task runner injection: Task 1, Task 5.
  - Rust reducer session stays in SDK lifecycle: Task 3.
  - Android timeline no longer consumes SSE directly: Task 6 and Task 7.
  - `UI = f(parts)` through SDK snapshot: Task 4 and Task 6.
  - iOS card parity deferred with source index preserved: Task 8.
- Placeholder scan:
  - No placeholder markers.
  - No unfinished implementation notes.
  - No “similar to Task N”.
  - Every code-changing step includes concrete code or exact replacement instructions.
- Type consistency:
  - `StreamRequest`, `StreamSnapshot`, `StreamPart`, `AgentStreamClient`, `StreamHandle`, `StreamStorageProvider`, `StreamHttpClient`, `StreamTaskRunner`, and `StreamReducerSessionFactory` are defined before later tasks use them.
  - Android adapter uses SDK interfaces defined in Task 1.
  - Presenter consumes `AgentStreamClient` and `AiSdkStreamReducer.mapSnapshot`.

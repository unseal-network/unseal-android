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
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.agentstream.api.AGENT_STREAM_SCHEMA_VERSION
import io.element.android.libraries.agentstream.api.StreamError
import io.element.android.libraries.agentstream.api.StreamPart
import io.element.android.libraries.agentstream.api.StreamRequest
import io.element.android.libraries.agentstream.api.StreamSnapshot
import io.element.android.libraries.agentstream.api.StreamSnapshotJsonCodec
import io.element.android.libraries.agentstream.api.StreamStatus
import io.element.android.libraries.agentstream.api.TextPartState
import io.element.android.libraries.chatbot.test.FakeChatbotApiService
import io.element.android.libraries.chatbot.test.FakeChatbotApiServiceFactory
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.matrix.test.FakeMatrixClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidAgentStreamAdaptersTest {
    private lateinit var context: Context

    private val dispatchers = CoroutineDispatchers(
        io = UnconfinedTestDispatcher(),
        computation = UnconfinedTestDispatcher(),
        main = UnconfinedTestDispatcher(),
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DATABASE_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun `empty completed snapshot is not saved`() = runTest {
        val provider = createProvider()

        provider.save(snapshot(status = StreamStatus.Completed, parts = emptyList()))

        assertThat(provider.load("stream-1")).isNull()
    }

    @Test
    fun `empty completed snapshot loaded from database is ignored and deleted`() = runTest {
        val provider = createProvider()
        assertThat(provider.load("stream-1")).isNull()
        insertSnapshotRow(snapshot(status = StreamStatus.Completed, parts = emptyList()))

        assertThat(provider.load("stream-1")).isNull()
        assertThat(rawRowCount()).isEqualTo(0)
    }

    @Test
    fun `blank stream id snapshot is not saved`() = runTest {
        val provider = createProvider()
        assertThat(provider.load("stream-1")).isNull()

        provider.save(
            snapshot(
                streamId = "",
                status = StreamStatus.Completed,
                parts = listOf(StreamPart.Text("part-1", "Hello", TextPartState.Done)),
            )
        )

        assertThat(rawRowCount()).isEqualTo(0)
    }

    @Test
    fun `completed snapshot is loaded across provider instances`() = runTest {
        createProvider().save(
            snapshot(
                status = StreamStatus.Completed,
                parts = listOf(StreamPart.Text("part-1", "Hello", TextPartState.Done)),
            )
        )

        val loaded = createProvider().load("stream-1")

        assertThat(loaded?.status).isEqualTo(StreamStatus.Completed)
        assertThat(loaded?.parts).containsExactly(StreamPart.Text("part-1", "Hello", TextPartState.Done))
    }

    @Test
    fun `delete removes saved snapshot`() = runTest {
        val provider = createProvider()
        provider.save(
            snapshot(
                status = StreamStatus.Completed,
                parts = listOf(StreamPart.Text("part-1", "Hello", TextPartState.Done)),
            )
        )

        provider.delete("stream-1")

        assertThat(provider.load("stream-1")).isNull()
    }

    @Test
    fun `failed snapshot does not replace existing completed snapshot`() = runTest {
        val provider = createProvider()
        val completed = snapshot(
            status = StreamStatus.Completed,
            parts = listOf(StreamPart.Text("part-1", "Hello", TextPartState.Done)),
        )
        provider.save(completed)

        provider.save(
            snapshot(
                status = StreamStatus.Failed,
                parts = listOf(StreamPart.Error("error-stream-1", StreamError("Network failed"))),
                error = StreamError("Network failed"),
            )
        )

        assertThat(provider.load("stream-1")).isEqualTo(completed)
    }

    @Test
    fun `completed snapshot replaces failed snapshot and rejects later failed snapshot`() = runTest {
        val provider = createProvider()
        val failed = snapshot(
            status = StreamStatus.Failed,
            parts = listOf(StreamPart.Error("error-stream-1", StreamError("Network failed"))),
            error = StreamError("Network failed"),
        )
        val completed = snapshot(
            status = StreamStatus.Completed,
            parts = listOf(StreamPart.Text("part-1", "Hello", TextPartState.Done)),
        )
        provider.save(failed)

        provider.save(completed)
        provider.save(
            snapshot(
                status = StreamStatus.Failed,
                parts = listOf(StreamPart.Error("error-stream-2", StreamError("Late failure"))),
                error = StreamError("Late failure"),
            )
        )

        assertThat(provider.load("stream-1")).isEqualTo(completed)
    }

    @Test
    fun `corrupted row returns null and is deleted`() = runTest {
        val provider = createProvider()
        assertThat(provider.load("stream-1")).isNull()
        insertCorruptedRow()

        assertThat(provider.load("stream-1")).isNull()
        assertThat(rawRowCount()).isEqualTo(0)
    }

    @Test
    fun `oversized row returns null and is deleted`() = runTest {
        val provider = createProvider()
        assertThat(provider.load("stream-1")).isNull()
        insertOversizedRow()

        assertThat(provider.load("stream-1")).isNull()
        assertThat(rawRowCount()).isEqualTo(0)
    }

    @Test
    fun `failed snapshot save deletes oversized completed snapshot instead of crashing`() = runTest {
        val provider = createProvider()
        assertThat(provider.load("stream-1")).isNull()
        insertOversizedRow()

        provider.save(
            snapshot(
                status = StreamStatus.Failed,
                parts = listOf(StreamPart.Error("error-stream-1", StreamError("Network failed"))),
                error = StreamError("Network failed"),
            )
        )

        assertThat(provider.load("stream-1")?.status).isEqualTo(StreamStatus.Failed)
    }

    // region unwrapPagepeekSseChunk

    @Test
    fun `unwrap extracts content from pagepeek wrapper`() {
        val inner = """{"type":"text-delta","id":"txt-0","delta":"hello"}"""
        val chunk = """data: {"subtype":"response.output_chunk.delta","item_id":"chunk_1","index":0,"type":"streaming","content":"${inner.replace("\"", "\\\"")}"}""" + "\n"

        val result = unwrapPagepeekSseChunk(chunk)

        assertThat(result).isEqualTo("data: $inner\n")
    }

    @Test
    fun `unwrap drops outer pagepeek start control frame`() {
        val chunk = """data: {"type":"start"}""" + "\n"

        val result = unwrapPagepeekSseChunk(chunk)

        assertThat(result).isNull()
    }

    @Test
    fun `unwrap drops outer pagepeek end control frame`() {
        val chunk = """data: {"type":"end"}""" + "\n"

        val result = unwrapPagepeekSseChunk(chunk)

        assertThat(result).isNull()
    }

    @Test
    fun `unwrap passes through blank line as SSE event terminator`() {
        assertThat(unwrapPagepeekSseChunk("\n")).isEqualTo("\n")
        assertThat(unwrapPagepeekSseChunk("")).isEqualTo("")
    }

    @Test
    fun `unwrap returns null for named event line`() {
        assertThat(unwrapPagepeekSseChunk("event: toolCallOutput\n")).isNull()
    }

    @Test
    fun `unwrap returns null when content field is missing`() {
        val chunk = """data: {"subtype":"response.output_chunk.delta","type":"streaming"}""" + "\n"

        val result = unwrapPagepeekSseChunk(chunk)

        assertThat(result).isNull()
    }

    @Test
    fun `unwrap drops line with invalid json`() {
        val chunk = "data: not-json\n"

        val result = unwrapPagepeekSseChunk(chunk)

        assertThat(result).isNull()
    }

    // endregion

    // region PagepeekSseProcessor

    @Test
    fun `processor assembles json block from content_block events`() {
        val processor = PagepeekSseProcessor()
        // Simulate streaming of {"content_type":"ppt_planning","topic":"Gold Report"} in two delta chunks.
        assertThat(processor.process(wrapInner("""{"type":"content_block_start","index":0,"content_type":"json"}"""))).isNull()
        assertThat(processor.process(wrapInner("""{"type":"content_block_delta","index":0,"delta":{"type":"json","json_chunk":"{\"content_type\":\"ppt_planning\","}}"""))).isNull()
        assertThat(processor.process(wrapInner("""{"type":"content_block_delta","index":0,"delta":{"type":"json","json_chunk":"\"topic\":\"Gold Report\"}"}}"""))).isNull()

        val result = processor.process(wrapInner("""{"type":"content_block_stop","index":0}"""))
        assertThat(result).isNotNull()
        assertThat(result!!).startsWith("data: {")
        assertThat(result).contains("\"type\":\"data-json-block\"")
        assertThat(result).contains("\"id\":\"json-block-0\"")
        assertThat(result).contains("content_type")
        assertThat(result).contains("ppt_planning")
        assertThat(result).endsWith("\n")
    }

    @Test
    fun `processor passes through non-block inner events`() {
        val processor = PagepeekSseProcessor()
        val inner = """{"type":"text-delta","id":"txt-0","delta":"hello"}"""

        val result = processor.process(wrapInner(inner))

        assertThat(result).isEqualTo("data: $inner\n")
    }

    @Test
    fun `processor increments block index across multiple blocks`() {
        val processor = PagepeekSseProcessor()

        fun emitBlock(): String? {
            processor.process(wrapInner("""{"type":"content_block_start","index":0,"content_type":"json"}"""))
            processor.process(wrapInner("""{"type":"content_block_delta","index":0,"delta":{"type":"json","json_chunk":"{}"}}"""))
            return processor.process(wrapInner("""{"type":"content_block_stop","index":0}"""))
        }

        val block0 = emitBlock()
        val block1 = emitBlock()

        assertThat(block0).contains(""""id":"json-block-0"""")
        assertThat(block1).contains(""""id":"json-block-1"""")
    }

    @Test
    fun `processor stop with no active block returns null`() {
        val processor = PagepeekSseProcessor()

        val result = processor.process(wrapInner("""{"type":"content_block_stop","index":0}"""))

        assertThat(result).isNull()
    }

    @Test
    fun `processor content_block_start with non-json content_type passes through`() {
        val processor = PagepeekSseProcessor()
        val inner = """{"type":"content_block_start","index":0,"content_type":"text"}"""

        val result = processor.process(wrapInner(inner))

        assertThat(result).isEqualTo("data: $inner\n")
    }

    // endregion

    /**
     * Wraps an inner AI SDK event JSON string in the pagepeek outer envelope.
     * Uses [buildJsonObject] so the content field is always properly JSON-encoded.
     */
    private fun wrapInner(innerJson: String): String {
        val outer = buildJsonObject {
            put("subtype", "response.output_chunk.delta")
            put("type", "streaming")
            put("content", innerJson)
        }
        return "data: $outer\n"
    }

    @Test
    fun `stream http client sends null sender when request sender is blank`() = runTest {
        var capturedSender: String? = "unset"
        val service = FakeChatbotApiService().apply {
            streamAgentMessageResult = { _, sender, _ ->
                capturedSender = sender
                Result.success(Unit)
            }
        }
        val httpClient = ChatbotStreamHttpClient(
            matrixClient = FakeMatrixClient(),
            chatbotApiServiceFactory = FakeChatbotApiServiceFactory(service),
        )

        httpClient.openStream(
            request = StreamRequest(
                streamId = "stream-1",
                sender = " ",
                roomId = "",
                eventId = "",
                includeRawEvents = false,
            ),
            onChunk = {},
        )

        assertThat(capturedSender).isNull()
    }

    private fun createProvider(): SQLiteStreamStorageProvider {
        return SQLiteStreamStorageProvider(context, dispatchers)
    }

    private fun snapshot(
        status: StreamStatus,
        parts: List<StreamPart>,
        streamId: String = "stream-1",
        error: StreamError? = null,
    ): StreamSnapshot {
        return StreamSnapshot(
            schemaVersion = AGENT_STREAM_SCHEMA_VERSION,
            streamId = streamId,
            status = status,
            parts = parts,
            rawEvents = emptyList(),
            updatedAtMs = 10L,
            completedAtMs = if (status == StreamStatus.Completed) 20L else null,
            error = error,
        )
    }

    private fun insertCorruptedRow() {
        openDatabase().use { database ->
            database.insertOrThrow(
                "agent_stream_snapshots",
                null,
                ContentValues().apply {
                    put("stream_id", "stream-1")
                    put("schema_version", AGENT_STREAM_SCHEMA_VERSION)
                    put("status", "completed")
                    put("snapshot_json", "{not-json")
                    put("updated_at_ms", 10L)
                    put("completed_at_ms", 20L)
                }
            )
        }
    }

    private fun insertOversizedRow() {
        openDatabase().use { database ->
            database.insertOrThrow(
                "agent_stream_snapshots",
                null,
                ContentValues().apply {
                    put("stream_id", "stream-1")
                    put("schema_version", AGENT_STREAM_SCHEMA_VERSION)
                    put("status", "completed")
                    put("snapshot_json", "x".repeat(3 * 1024 * 1024))
                    put("updated_at_ms", 10L)
                    put("completed_at_ms", 20L)
                }
            )
        }
    }

    private fun insertSnapshotRow(snapshot: StreamSnapshot) {
        openDatabase().use { database ->
            database.insertOrThrow(
                "agent_stream_snapshots",
                null,
                ContentValues().apply {
                    put("stream_id", snapshot.streamId)
                    put("schema_version", snapshot.schemaVersion)
                    put("status", "completed")
                    put("snapshot_json", StreamSnapshotJsonCodec().encode(snapshot))
                    put("updated_at_ms", snapshot.updatedAtMs)
                    put("completed_at_ms", snapshot.completedAtMs)
                }
            )
        }
    }

    private fun rawRowCount(): Int {
        return openDatabase().use { database ->
            database.rawQuery("SELECT COUNT(*) FROM agent_stream_snapshots", emptyArray()).use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }
        }
    }

    private fun openDatabase(): SQLiteDatabase {
        return SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(DATABASE_NAME), null)
    }

    private companion object {
        const val DATABASE_NAME = "agent_stream_snapshots.db"
    }
}

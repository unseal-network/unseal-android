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
import io.element.android.libraries.agentstream.api.StreamSnapshot
import io.element.android.libraries.agentstream.api.StreamSnapshotJsonCodec
import io.element.android.libraries.agentstream.api.StreamStatus
import io.element.android.libraries.agentstream.api.TextPartState
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
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
                parts = listOf(StreamPart.Text("part-1", "Hello", TextPartState.Complete)),
            )
        )

        assertThat(rawRowCount()).isEqualTo(0)
    }

    @Test
    fun `completed snapshot is loaded across provider instances`() = runTest {
        createProvider().save(
            snapshot(
                status = StreamStatus.Completed,
                parts = listOf(StreamPart.Text("part-1", "Hello", TextPartState.Complete)),
            )
        )

        val loaded = createProvider().load("stream-1")

        assertThat(loaded?.status).isEqualTo(StreamStatus.Completed)
        assertThat(loaded?.parts).containsExactly(StreamPart.Text("part-1", "Hello", TextPartState.Complete))
    }

    @Test
    fun `delete removes saved snapshot`() = runTest {
        val provider = createProvider()
        provider.save(
            snapshot(
                status = StreamStatus.Completed,
                parts = listOf(StreamPart.Text("part-1", "Hello", TextPartState.Complete)),
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
            parts = listOf(StreamPart.Text("part-1", "Hello", TextPartState.Complete)),
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
    fun `corrupted row returns null and is deleted`() = runTest {
        val provider = createProvider()
        assertThat(provider.load("stream-1")).isNull()
        insertCorruptedRow()

        assertThat(provider.load("stream-1")).isNull()
        assertThat(rawRowCount()).isEqualTo(0)
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

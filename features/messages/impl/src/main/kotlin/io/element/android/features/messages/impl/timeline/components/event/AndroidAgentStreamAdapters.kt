/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteBlobTooBigException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.agentstream.api.AgentStreamClient
import io.element.android.libraries.agentstream.api.DefaultAgentStreamClient
import io.element.android.libraries.agentstream.api.StreamHttpClient
import io.element.android.libraries.agentstream.api.StreamRequest
import io.element.android.libraries.agentstream.api.StreamTransportException
import timber.log.Timber
import io.element.android.libraries.agentstream.api.StreamSnapshot
import io.element.android.libraries.agentstream.api.StreamSnapshotJsonCodec
import io.element.android.libraries.agentstream.api.StreamStatus
import io.element.android.libraries.agentstream.api.StreamStorageProvider
import io.element.android.libraries.agentstream.api.StreamTask
import io.element.android.libraries.agentstream.api.StreamTaskRunner
import io.element.android.libraries.chatbot.api.ChatbotApiError
import io.element.android.libraries.chatbot.api.ChatbotApiServiceFactory
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.di.RoomScope
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.di.annotations.RoomCoroutineScope
import io.element.android.libraries.matrix.api.MatrixClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SingleIn(RoomScope::class)
@ContributesBinding(RoomScope::class)
@Inject
class AndroidAgentStreamClient(
    storageProvider: StreamStorageProvider,
    httpClient: StreamHttpClient,
    taskRunner: StreamTaskRunner,
) : AgentStreamClient {
    private val delegate = DefaultAgentStreamClient(
        storageProvider = storageProvider,
        httpClient = httpClient,
        taskRunner = taskRunner,
    )

    override fun getStream(request: StreamRequest) = delegate.getStream(request)
}

@SingleIn(RoomScope::class)
@ContributesBinding(RoomScope::class)
@Inject
class ChatbotStreamHttpClient(
    private val matrixClient: MatrixClient,
    private val chatbotApiServiceFactory: ChatbotApiServiceFactory,
) : StreamHttpClient {
    override suspend fun openStream(
        request: StreamRequest,
        onChunk: suspend (String) -> Unit,
    ) {
        Timber.tag("AiStreamDbg").d("HTTP openStream START stream=%s sender=%s", request.streamId, request.sender)
        var chunks = 0
        var bytes = 0
        chatbotApiServiceFactory
            .createForAiStream(matrixClient)
            .streamAgentMessage(request.streamId, request.sender.takeIf { it.isNotBlank() }) { chunk ->
                chunks++
                bytes += chunk.length
                onChunk(chunk)
            }
            .onSuccess { Timber.tag("AiStreamDbg").d("HTTP openStream DONE stream=%s chunks=%d bytes=%d (connection closed)", request.streamId, chunks, bytes) }
            .onFailure { Timber.tag("AiStreamDbg").w(it, "HTTP openStream FAILED stream=%s chunks=%d bytes=%d", request.streamId, chunks, bytes) }
            .getOrElse { throwable -> throw throwable.toStreamTransportException() }
    }
}

/**
 * Classifies a stream-fetch failure as retryable (transport-level) vs durable (stream-level).
 * Network blips, timeouts, 5xx and rate limiting are retryable; a definitive 4xx is not.
 */
private fun Throwable.toStreamTransportException(): StreamTransportException {
    val retryable = when (this) {
        is ChatbotApiError.NetworkError -> true
        is ChatbotApiError.HttpError -> statusCode >= 500 || statusCode == 408 || statusCode == 429
        is ChatbotApiError.MissingAccessToken,
        is ChatbotApiError.InvalidBaseUrl -> true
        else -> this is java.io.IOException
    }
    return StreamTransportException(
        message = message.orEmpty().ifBlank { "Failed to open stream." },
        retryable = retryable,
        cause = this,
    )
}

@SingleIn(RoomScope::class)
@ContributesBinding(RoomScope::class)
@Inject
class CoroutineStreamTaskRunner(
    @RoomCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatchers: CoroutineDispatchers,
) : StreamTaskRunner {
    override fun run(
        key: String,
        block: suspend () -> Unit,
    ): StreamTask {
        val job = coroutineScope.launch(dispatchers.io) {
            block()
        }
        return JobStreamTask(job)
    }

    private class JobStreamTask(
        private val job: Job,
    ) : StreamTask {
        override fun cancel() {
            job.cancel()
        }
    }
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class SQLiteStreamStorageProvider(
    @ApplicationContext context: Context,
    private val dispatchers: CoroutineDispatchers,
) : StreamStorageProvider {
    private val helper = AgentStreamSQLiteOpenHelper(context)
    private val codec = StreamSnapshotJsonCodec()

    override suspend fun load(streamId: String): StreamSnapshot? = withContext(dispatchers.io) {
        if (streamId.isBlank()) return@withContext null
        try {
            helper.readableDatabase.query(
                TABLE_NAME,
                arrayOf(COLUMN_SNAPSHOT_JSON),
                "$COLUMN_STREAM_ID = ?",
                arrayOf(streamId),
                null,
                null,
                null,
            ).use { cursor ->
                if (!cursor.moveToFirst()) {
                    return@withContext null
                }
                val json = cursor.getString(0)
                val snapshot = runCatching { codec.decode(json) }
                    .onFailure {
                        Timber.tag("AiStreamDbg").w(it, "sqlite cache CORRUPT stream=%s", streamId)
                        deleteSync(streamId)
                    }
                    .getOrNull()
                if (snapshot?.status == StreamStatus.Completed && snapshot.parts.isEmpty()) {
                    deleteSync(streamId)
                    null
                } else {
                    snapshot
                }
            }
        } catch (exception: SQLiteBlobTooBigException) {
            Timber.tag("AiStreamDbg").w(exception, "sqlite cache TOO_LARGE stream=%s", streamId)
            deleteSync(streamId)
            null
        }
    }

    override suspend fun save(snapshot: StreamSnapshot) = withContext(dispatchers.io) {
        if (!shouldSave(snapshot)) {
            return@withContext
        }
        val json = codec.encode(snapshot)
        val values = ContentValues().apply {
            put(COLUMN_STREAM_ID, snapshot.streamId)
            put(COLUMN_SCHEMA_VERSION, snapshot.schemaVersion)
            put(COLUMN_STATUS, snapshot.status.wireValue)
            put(COLUMN_SNAPSHOT_JSON, json)
            put(COLUMN_UPDATED_AT_MS, snapshot.updatedAtMs)
            put(COLUMN_COMPLETED_AT_MS, snapshot.completedAtMs)
        }
        val database = helper.writableDatabase
        database.beginTransaction()
        try {
            if (snapshot.status == StreamStatus.Failed && hasCompletedSnapshotWithParts(database, snapshot.streamId)) {
                return@withContext
            }
            database.insertWithOnConflict(
                TABLE_NAME,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE,
            )
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    override suspend fun delete(streamId: String) = withContext(dispatchers.io) {
        deleteSync(streamId)
    }

    private fun shouldSave(snapshot: StreamSnapshot): Boolean {
        if (snapshot.streamId.isBlank()) {
            return false
        }
        if (snapshot.status == StreamStatus.Completed && snapshot.parts.isEmpty()) {
            return false
        }
        return snapshot.status == StreamStatus.Completed || snapshot.status == StreamStatus.Failed
    }

    private fun hasCompletedSnapshotWithParts(database: SQLiteDatabase, streamId: String): Boolean {
        return try {
            database.query(
                TABLE_NAME,
                arrayOf(COLUMN_STATUS, COLUMN_SNAPSHOT_JSON),
                "$COLUMN_STREAM_ID = ?",
                arrayOf(streamId),
                null,
                null,
                null,
            ).use { cursor ->
                if (!cursor.moveToFirst()) {
                    false
                } else {
                    val status = cursor.getString(0)
                    val json = cursor.getString(1)
                    if (status != StreamStatus.Completed.wireValue) {
                        false
                    } else {
                        runCatching { codec.decode(json).parts.isNotEmpty() }
                            .onFailure { deleteSync(database, streamId) }
                            .getOrDefault(false)
                    }
                }
            }
        } catch (exception: SQLiteBlobTooBigException) {
            Timber.tag("AiStreamDbg").w(exception, "sqlite cache completed snapshot TOO_LARGE stream=%s", streamId)
            deleteSync(database, streamId)
            false
        }
    }

    private fun deleteSync(streamId: String) {
        deleteSync(helper.writableDatabase, streamId)
    }

    private fun deleteSync(database: SQLiteDatabase, streamId: String) {
        database.delete(
            TABLE_NAME,
            "$COLUMN_STREAM_ID = ?",
            arrayOf(streamId),
        )
    }

    private class AgentStreamSQLiteOpenHelper(
        context: Context,
    ) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
        override fun onCreate(database: SQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_NAME (
                    $COLUMN_STREAM_ID TEXT NOT NULL PRIMARY KEY,
                    $COLUMN_SCHEMA_VERSION INTEGER NOT NULL,
                    $COLUMN_STATUS TEXT NOT NULL,
                    $COLUMN_SNAPSHOT_JSON TEXT NOT NULL,
                    $COLUMN_UPDATED_AT_MS INTEGER NOT NULL,
                    $COLUMN_COMPLETED_AT_MS INTEGER
                )
                """.trimIndent()
            )
        }

        override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            database.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
            onCreate(database)
        }
    }

    private companion object {
        const val DATABASE_NAME = "agent_stream_snapshots.db"
        const val DATABASE_VERSION = 1
        const val TABLE_NAME = "agent_stream_snapshots"
        const val COLUMN_STREAM_ID = "stream_id"
        const val COLUMN_SCHEMA_VERSION = "schema_version"
        const val COLUMN_STATUS = "status"
        const val COLUMN_SNAPSHOT_JSON = "snapshot_json"
        const val COLUMN_UPDATED_AT_MS = "updated_at_ms"
        const val COLUMN_COMPLETED_AT_MS = "completed_at_ms"
    }
}

private val StreamStatus.wireValue: String
    get() = when (this) {
        StreamStatus.Idle -> "idle"
        StreamStatus.Loading -> "loading"
        StreamStatus.Streaming -> "streaming"
        StreamStatus.Completed -> "completed"
        StreamStatus.Failed -> "failed"
        StreamStatus.Cancelled -> "cancelled"
    }

/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.miniapp.impl

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File

/**
 * Android SQLite helper for the mini-app `sqlite3` bridge method.
 *
 * Mirrors iOS `WebView+SQLite.swift` — exposes the same set of operations:
 * - `execute`        — run any DDL/DML SQL statement.
 * - `query`          — SELECT and return rows as a list of maps.
 * - `insert`         — typed INSERT with a values map.
 * - `update`         — typed UPDATE with a values map and WHERE clause.
 * - `delete`         — DELETE with a WHERE clause.
 * - `saveAppModel`   — upsert a JSON blob keyed by appId into `app_models`.
 * - `getAppModel`    — fetch the JSON blob for a given appId.
 * - `deleteAppModel` — remove the row for a given appId.
 *
 * Databases are stored in `<filesDir>/miniapp/databases/<dbName>`.
 */
internal class MiniAppSqlite(private val context: Context) {

    /**
     * Dispatch the JS `sqlite3` payload to the correct operation.
     * Returns a `Result<Map<String, Any>>` — the map is serialised into the JS response.
     */
    fun dispatch(data: JSONObject): Result<Map<String, Any>> {
        val operation = data.optString("operation").ifBlank {
            return Result.failure(IllegalArgumentException("Missing operation type"))
        }
        val dbName = data.optString("dbName").ifBlank { "default.db" }

        return runCatching {
            when (operation) {
                "execute"      -> execute(dbName, data)
                "query"        -> query(dbName, data)
                "insert"       -> insert(dbName, data)
                "update"       -> update(dbName, data)
                "delete"       -> delete(dbName, data)
                "saveAppModel" -> saveAppModel(dbName, data)
                "getAppModel"  -> getAppModel(dbName, data)
                "deleteAppModel" -> deleteAppModel(dbName, data)
                else           -> error("Unknown sqlite3 operation: $operation")
            }
        }
    }

    // ── Operations ─────────────────────────────────────────────────────────────

    private fun execute(dbName: String, data: JSONObject): Map<String, Any> {
        val sql = data.optString("sql").ifBlank { error("Missing sql") }
        openDb(dbName).use { db -> db.execSQL(sql) }
        return mapOf("success" to true)
    }

    private fun query(dbName: String, data: JSONObject): Map<String, Any> {
        val sql = data.optString("sql").ifBlank { error("Missing sql") }
        val results = mutableListOf<Map<String, Any?>>()
        openDb(dbName).use { db ->
            db.rawQuery(sql, null).use { cursor ->
                val columns = cursor.columnNames
                while (cursor.moveToNext()) {
                    val row = mutableMapOf<String, Any?>()
                    columns.forEachIndexed { i, col ->
                        row[col] = when (cursor.getType(i)) {
                            android.database.Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(i)
                            android.database.Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(i)
                            android.database.Cursor.FIELD_TYPE_STRING -> cursor.getString(i)
                            android.database.Cursor.FIELD_TYPE_NULL -> null
                            else -> cursor.getString(i)
                        }
                    }
                    results.add(row)
                }
            }
        }
        return mapOf("success" to true, "results" to results)
    }

    private fun insert(dbName: String, data: JSONObject): Map<String, Any> {
        val table = data.optString("table").ifBlank { error("Missing table") }
        val values = data.optJSONObject("values") ?: error("Missing values")
        val cv = values.toContentValues()
        var rowId = -1L
        openDb(dbName).use { db ->
            rowId = db.insert(table, null, cv)
            if (rowId == -1L) error("Insert failed")
        }
        return mapOf("success" to true, "rowId" to rowId)
    }

    private fun update(dbName: String, data: JSONObject): Map<String, Any> {
        val table = data.optString("table").ifBlank { error("Missing table") }
        val values = data.optJSONObject("values") ?: error("Missing values")
        val where = data.optString("where").ifBlank { error("Missing where clause") }
        val cv = values.toContentValues()
        var changes = 0
        openDb(dbName).use { db ->
            changes = db.update(table, cv, where, null)
        }
        return mapOf("success" to true, "changes" to changes)
    }

    private fun delete(dbName: String, data: JSONObject): Map<String, Any> {
        val table = data.optString("table").ifBlank { error("Missing table") }
        val where = data.optString("where").ifBlank { error("Missing where clause") }
        var changes = 0
        openDb(dbName).use { db ->
            changes = db.delete(table, where, null)
        }
        return mapOf("success" to true, "changes" to changes)
    }

    // ── AppModel helpers ───────────────────────────────────────────────────────

    private fun saveAppModel(dbName: String, data: JSONObject): Map<String, Any> {
        val appId = data.optLong("appId", -1L).takeIf { it >= 0 }
            ?: error("Missing appId")
        val modelData = data.optJSONObject("data") ?: error("Missing data")
        val jsonStr = modelData.toString()
        val now = System.currentTimeMillis() / 1000L

        openDb(dbName).use { db ->
            db.execSQL(CREATE_APP_MODELS_TABLE)

            // Check if row exists to preserve created_at.
            val exists = db.rawQuery(
                "SELECT created_at FROM app_models WHERE app_id = ?", arrayOf(appId.toString())
            ).use { c -> c.moveToFirst() }

            if (exists) {
                val cv = ContentValues().apply {
                    put("data", jsonStr)
                    put("updated_at", now)
                }
                db.update("app_models", cv, "app_id = ?", arrayOf(appId.toString()))
            } else {
                val cv = ContentValues().apply {
                    put("app_id", appId)
                    put("data", jsonStr)
                    put("created_at", now)
                    put("updated_at", now)
                }
                db.insert("app_models", null, cv)
            }
        }
        return mapOf("success" to true, "appId" to appId)
    }

    private fun getAppModel(dbName: String, data: JSONObject): Map<String, Any> {
        val appId = data.optLong("appId", -1L).takeIf { it >= 0 }
            ?: error("Missing appId")

        openDb(dbName).use { db ->
            db.execSQL(CREATE_APP_MODELS_TABLE)
            db.rawQuery(
                "SELECT data, created_at, updated_at FROM app_models WHERE app_id = ?",
                arrayOf(appId.toString())
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    val jsonStr = cursor.getString(0)
                    val createdAt = cursor.getLong(1)
                    val updatedAt = cursor.getLong(2)
                    val parsed = JSONObject(jsonStr).toMap()
                    return mapOf(
                        "success" to true,
                        "appId" to appId,
                        "data" to parsed,
                        "createdAt" to createdAt,
                        "updatedAt" to updatedAt,
                    )
                }
            }
        }
        return mapOf("success" to false, "appId" to appId, "message" to "AppModel not found")
    }

    private fun deleteAppModel(dbName: String, data: JSONObject): Map<String, Any> {
        val appId = data.optLong("appId", -1L).takeIf { it >= 0 }
            ?: error("Missing appId")
        var deleted = false
        openDb(dbName).use { db ->
            db.execSQL(CREATE_APP_MODELS_TABLE)
            deleted = db.delete("app_models", "app_id = ?", arrayOf(appId.toString())) > 0
        }
        return mapOf("success" to true, "appId" to appId, "deleted" to deleted)
    }

    // ── Reusable helpers ───────────────────────────────────────────────────────

    /**
     * Open (or create) the database at `<filesDir>/miniapp/databases/<dbName>`.
     * The returned [SQLiteDatabase] must be closed by the caller.
     */
    private fun openDb(dbName: String): SQLiteDatabase {
        val dir = File(context.filesDir, "miniapp/databases")
        dir.mkdirs()
        val path = File(dir, dbName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")).absolutePath
        return SQLiteDatabase.openOrCreateDatabase(path, null)
    }

    /**
     * Persist an [AppBundleInfo]-like map to the standard `app.db` database.
     * Called by [MiniAppNode] after a successful `pkg.app.check.update` response.
     */
    fun saveAppModelFromBundleInfo(appId: Long, modelMap: Map<String, Any>) {
        runCatching {
            val dbName = "app.db"
            val jsonStr = JSONObject(modelMap).toString()
            val now = System.currentTimeMillis() / 1000L

            openDb(dbName).use { db ->
                db.execSQL(CREATE_APP_MODELS_TABLE)
                val cv = ContentValues().apply {
                    put("app_id", appId)
                    put("data", jsonStr)
                    put("created_at", now)
                    put("updated_at", now)
                }
                db.insertWithOnConflict("app_models", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
        }.onFailure { Timber.e(it, "MiniApp: failed to persist app model for appId=%d", appId) }
    }

    // ── JSON ↔ ContentValues ───────────────────────────────────────────────────

    private fun JSONObject.toContentValues(): ContentValues {
        val cv = ContentValues()
        keys().forEach { key ->
            when (val v = opt(key)) {
                is Int -> cv.put(key, v)
                is Long -> cv.put(key, v)
                is Double -> cv.put(key, v)
                is Boolean -> cv.put(key, v)
                is String -> cv.put(key, v)
                null -> cv.putNull(key)
                else -> cv.put(key, v.toString())
            }
        }
        return cv
    }

    private fun JSONObject.toMap(): Map<String, Any?> = buildMap {
        keys().forEach { key -> put(key, opt(key)) }
    }

    private companion object {
        const val CREATE_APP_MODELS_TABLE = """
            CREATE TABLE IF NOT EXISTS app_models (
                app_id     INTEGER PRIMARY KEY,
                data       TEXT    NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
        """
    }
}

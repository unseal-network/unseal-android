/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.miniapp.impl

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Parsed form of the JSON payload sent from JS → native.
 *
 * JS sends:
 * ```json
 * { "handle": "uuid", "data": { ... } }
 * ```
 * `handleId` is the callback identifier the JS layer is waiting on.
 * `data` is the method-specific payload, or an empty object when absent.
 */
internal class FromJsData(rawJson: String) {
    val handleId: String
    val data: JSONObject

    init {
        var h = ""
        var d = JSONObject()
        try {
            val obj = JSONObject(rawJson)
            // JS sends "handleId" (co.ts postMessage); accept "handle" as fallback.
            h = obj.optString("handleId", "").ifEmpty { obj.optString("handle", "") }
            val raw = obj.opt("data")
            d = when (raw) {
                is JSONObject -> raw
                is String -> runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
                else -> JSONObject()
            }
        } catch (_: JSONException) {
            // malformed payload — use defaults
        }
        handleId = h
        data = d
    }
}

/**
 * Response payload sent from native → JS via `window.__webkitNotification(json)`.
 *
 * Shape:
 * ```json
 * { "handle": "uuid", "code": 200, "msg": "", "data": <any> }
 * ```
 * Use [event] instead of [handle] when emitting push events (e.g. upload progress).
 */
internal class ToJsData(
    private val handle: String? = null,
    private val event: String? = null,
) {
    var code: Int = 200
    var msg: String = ""
    /** Accepts JSONObject, JSONArray, String, Number, Boolean, or null. */
    var data: Any = ""

    fun toJsonString(): String {
        val obj = JSONObject()
        handle?.let { obj.put("handle", it) }
        event?.let { obj.put("event", it) }
        obj.put("code", code)
        obj.put("msg", msg)
        // JSONObject.put() only accepts primitives, JSONObject, JSONArray, or null.
        when (val d = data) {
            is JSONObject -> obj.put("data", d)
            is JSONArray -> obj.put("data", d)
            is Map<*, *> -> obj.put("data", d.toJsonObject())
            is List<*> -> obj.put("data", d.toJsonArray())
            is Boolean -> obj.put("data", d)
            is Number -> obj.put("data", d)
            else -> obj.put("data", d.toString())
        }
        return obj.toString()
    }

    override fun toString(): String = toJsonString()
}

// ── JSON helpers ──────────────────────────────────────────────────────────────

internal fun Map<*, *>.toJsonObject(): JSONObject {
    val obj = JSONObject()
    forEach { (k, v) ->
        if (k != null) obj.put(k.toString(), v.toJsonValue())
    }
    return obj
}

internal fun List<*>.toJsonArray(): JSONArray {
    val arr = JSONArray()
    forEach { arr.put(it.toJsonValue()) }
    return arr
}

private fun Any?.toJsonValue(): Any =
    when (this) {
        null -> JSONObject.NULL
        is Map<*, *> -> toJsonObject()
        is List<*> -> toJsonArray()
        is JSONObject, is JSONArray, is Boolean, is Number -> this
        else -> toString()
    }

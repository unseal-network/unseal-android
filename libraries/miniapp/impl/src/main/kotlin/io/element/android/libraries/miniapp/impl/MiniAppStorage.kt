/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.miniapp.impl

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONException
import org.json.JSONObject

/**
 * Per-app key–value storage backed by [SharedPreferences].
 *
 * Maps to iOS `WebView+Storage.swift` (`setItem`, `getItem`, `removeItem`).
 *
 * Storage keys are namespaced by [appId] to isolate apps from each other,
 * mirroring the iOS `"\(self.appId)-\(key)"` convention.
 * The special key `__auth` uses a shared namespace (`sslt_-__auth`) so that
 * auth tokens remain accessible across apps (matching iOS behavior).
 */
internal class MiniAppStorage(context: Context, private val appId: Long) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("miniapp_storage", Context.MODE_PRIVATE)

    /**
     * Get a stored value.
     *
     * JS call: `window.webkit.storageGet(JSON.stringify({ key }))`
     * Returns: `JSON.stringify({ value: <stored> })` or `""` when not found.
     */
    fun get(params: String): String {
        val key = parseKey(params) ?: return ""
        val prefKey = prefKey(key)
        val raw = prefs.getString(prefKey, null) ?: return ""
        return try {
            // Re-encode as { "value": <stored> } to match the iOS `{ "value": v }` dict
            JSONObject().apply { put("value", raw) }.toString()
        } catch (_: JSONException) {
            ""
        }
    }

    /**
     * Set a value.
     *
     * JS call: `window.webkit.storageSet(JSON.stringify({ key, value }))`
     */
    fun set(params: String) {
        try {
            val obj = JSONObject(params)
            val key = obj.getString("key")
            val value = obj.opt("value")?.toString() ?: return
            prefs.edit().putString(prefKey(key), value).apply()
        } catch (_: JSONException) {
            // ignore malformed input
        }
    }

    /**
     * Remove a value.
     *
     * JS call: `window.webkit.storageRemove(JSON.stringify({ key }))`
     */
    fun remove(params: String) {
        val key = parseKey(params) ?: return
        prefs.edit().remove(prefKey(key)).apply()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun prefKey(key: String): String =
        if (key == "__auth") "sslt_-__auth" else "$appId-$key"

    private fun parseKey(params: String): String? = try {
        JSONObject(params).getString("key")
    } catch (_: JSONException) {
        null
    }
}

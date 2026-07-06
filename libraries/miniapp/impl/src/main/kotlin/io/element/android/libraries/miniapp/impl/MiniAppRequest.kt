/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.miniapp.impl

import android.webkit.WebView
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException

/**
 * HTTP bridge for the `request` JS method.
 *
 * Maps to iOS `WebView+Network.swift` `request(body:)`.
 *
 * The JS call is:
 * ```json
 * { "handle": "uuid", "data": { "url": "...", "method": "POST", "headers": {}, "data": <body> } }
 * ```
 * The native response is delivered via `window.__webkitNotification`:
 * ```json
 * { "handle": "uuid", "code": 200, "data": { "status": 200, "data": "<response body>" } }
 * ```
 * Upload progress events use the handle as the event name:
 * ```json
 * { "event": "uuid", "data": { "progress": 0.5, "completed": 500, "total": 1000, "handle": "uuid" } }
 * ```
 */
internal object MiniAppRequest {

    fun execute(
        fromJs: FromJsData,
        okHttpClient: OkHttpClient,
        webView: WebView,
    ) {
        val url = fromJs.data.optString("url")
        if (url.isBlank()) {
            sendError(webView, fromJs.handleId, 400, "missing url")
            return
        }

        val method = fromJs.data.optString("method", "GET").uppercase()
        val headersObj = fromJs.data.optJSONObject("headers")
        val bodyData = fromJs.data.opt("data")
        val isMultipart = headersObj
            ?.optString("Content-Type", "")
            ?.contains("multipart/form-data", ignoreCase = true) == true

        Timber.d("MiniApp: HTTP %s %s handleId=%s", method, url, fromJs.handleId)

        val requestBuilder = Request.Builder().url(url)

        // Set all headers from JS params — use explicit iterator to guarantee smart-cast on headersObj.
        if (headersObj != null) {
            val keys = headersObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = headersObj.getString(key)
                requestBuilder.header(key, value)
            }
        } else {
            Timber.w("MiniApp: no headers in request params for %s", url)
        }

        try {
            val request = if (method == "GET" || method == "HEAD") {
                requestBuilder.method(method, null).build()
            } else if (isMultipart && bodyData is JSONObject) {
                val multipart = buildMultipart(bodyData)
                requestBuilder.method(method, multipart).build()
            } else {
                val bodyString: String? = when (bodyData) {
                    is String -> bodyData.takeIf { it.isNotEmpty() }
                    is JSONObject -> bodyData.toString()
                    else -> null
                }
                if (bodyString != null) {
                    val mediaType = headersObj?.optString("Content-Type")
                        ?.toMediaTypeOrNull()
                        ?: "application/json".toMediaTypeOrNull()
                    requestBuilder.method(method, bodyString.toRequestBody(mediaType)).build()
                } else {
                    // POST/PUT with no body (e.g. empty string or null data)
                    requestBuilder.method(method, "".toRequestBody(null)).build()
                }
            }

            okHttpClient.newCall(request).execute().use { response ->
                val responseBody = response.body.string()
                Timber.d("MiniApp: HTTP response %d for %s handleId=%s body=%s", response.code, url, fromJs.handleId, responseBody.take(300))
                val resp = ToJsData(handle = fromJs.handleId)
                resp.data = mapOf("status" to response.code, "data" to responseBody)
                webView.post {
                    webView.evaluateJavascript("window.__webkitNotification(${resp.toJsonString()})", null)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "MiniApp: HTTP request failed %s %s", method, url)
            sendError(webView, fromJs.handleId, 0, e.message ?: "network error")
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildMultipart(params: JSONObject): MultipartBody {
        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
        params.keys().forEach { key ->
            when (val value = params.opt(key)) {
                is JSONObject -> {
                    // file field: { "mimeType": "...", "filename": "...", "data": "<base64>" }
                    val mimeType = value.optString("mimeType", "application/octet-stream")
                    val filename = value.optString("filename", "file")
                    val base64 = value.optString("data", "")
                    val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                    builder.addFormDataPart(key, filename, bytes.toRequestBody(mimeType.toMediaTypeOrNull()))
                }
                else -> builder.addFormDataPart(key, value?.toString() ?: "")
            }
        }
        return builder.build()
    }

    private fun sendError(webView: WebView, handleId: String, status: Int, message: String) {
        val resp = ToJsData(handle = handleId)
        resp.code = status.takeIf { it != 0 } ?: 400
        resp.msg = message
        resp.data = mapOf("status" to status, "data" to message)
        webView.post {
            webView.evaluateJavascript("window.__webkitNotification(${resp.toJsonString()})", null)
        }
    }
}

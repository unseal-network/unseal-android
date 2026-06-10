/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbot.impl

import io.element.android.libraries.chatbot.api.ChatbotApiError
import io.element.android.libraries.matrix.api.MatrixClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import timber.log.Timber
import java.io.IOException

internal class ChatbotHttpClient(
    private val baseUrl: String,
    private val matrixClient: MatrixClient,
    private val okHttpClient: OkHttpClient,
    private val tokenProvider: ChatbotAccessTokenProvider,
) {
    suspend fun requestRaw(pathWithQuery: String, method: ChatbotHttpMethod, body: String? = null): Result<String> {
        return requestRaw(pathWithQuery, method, body, accept = "application/json")
    }

    suspend fun streamRaw(pathWithQuery: String, onChunk: suspend (String) -> Unit): Result<Unit> {
        return requestRaw(pathWithQuery, ChatbotHttpMethod.GET, body = null, accept = "text/event-stream", onChunk = onChunk).map { }
    }

    private suspend fun requestRaw(
        pathWithQuery: String,
        method: ChatbotHttpMethod,
        body: String? = null,
        accept: String,
        onChunk: (suspend (String) -> Unit)? = null,
    ): Result<String> {
        val base = baseUrl.toHttpUrlOrNull() ?: return Result.failure(ChatbotApiError.InvalidBaseUrl)
        val token = tokenProvider.accessToken(matrixClient)?.takeIf { it.isNotBlank() }
            ?: return Result.failure(ChatbotApiError.MissingAccessToken)
        val relative = pathWithQuery.removePrefix("/")
        val url = base.newBuilder().addEncodedPathSegments(relative.substringBefore("?")).apply {
            relative.substringAfter("?", missingDelimiterValue = "")
                .takeIf { it.isNotEmpty() }
                ?.let { encodedQuery(it) }
        }.build()

        val requestBody = when {
            body != null -> body.toRequestBody("application/json".toMediaType())
            method == ChatbotHttpMethod.POST || method == ChatbotHttpMethod.PUT -> ByteArray(0).toRequestBody("application/json".toMediaType())
            else -> null
        }
        val request = Request.Builder()
            .url(url)
            .header("Accept", accept)
            .header("Authorization", "Bearer $token")
            .header("Cache-Control", "no-cache")
            .apply {
                if (body != null) {
                    header("Content-Type", "application/json")
                }
                method(method.name, requestBody)
            }
            .build()

        return withContext(Dispatchers.IO) {
            try {
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val responseBody = response.body.string()
                        val redacted = ChatbotRedactor.redact(responseBody)
                        Timber.w("Chatbot HTTP %d %s %s -> %s", response.code, method.name, url.encodedPath, redacted.take(800))
                        return@withContext Result.failure(ChatbotApiError.HttpError(response.code, redacted))
                    }
                    val responseBody = if (onChunk == null) {
                        response.body.string()
                    } else {
                        val builder = StringBuilder()
                        val source = response.body.source()
                        while (true) {
                            val line = source.readUtf8Line() ?: break
                            val chunk = "$line\n"
                            builder.append(chunk)
                            onChunk(chunk)
                        }
                        builder.toString()
                    }
                    Result.success(responseBody)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Result.failure(ChatbotApiError.NetworkError(e.message.orEmpty(), e))
            } catch (e: Exception) {
                Result.failure(ChatbotApiError.NetworkError(e.message.orEmpty(), e))
            }
        }
    }

    suspend inline fun <reified T> requestJson(pathWithQuery: String, method: ChatbotHttpMethod, body: String? = null): Result<T> {
        return requestRaw(pathWithQuery, method, body).mapCatching { raw ->
            try {
                ChatbotJson.decode<T>(raw)
            } catch (e: Exception) {
                throw ChatbotApiError.DecodingError(ChatbotRedactor.redact(raw).take(2_000))
            }
        }
    }
}

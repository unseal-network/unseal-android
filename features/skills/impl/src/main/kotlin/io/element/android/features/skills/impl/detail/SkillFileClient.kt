/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.skills.impl.detail

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import io.element.android.libraries.di.SessionScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

interface SkillFileClient {
    suspend fun load(url: String): Result<String>
    suspend fun save(url: String, fileName: String, content: String): Result<Unit>
}

@ContributesBinding(SessionScope::class, binding = binding<SkillFileClient>())
@Inject
class DefaultSkillFileClient(
    private val okHttpClient: () -> OkHttpClient,
) : SkillFileClient {
    override suspend fun load(url: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(url)
                .header("Cache-Control", "no-cache")
                .get()
                .build()
            okHttpClient().newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("HTTP ${response.code}")
                }
                val bytes = response.body.bytes()
                bytes.toString(Charsets.UTF_8)
            }
        }
    }

    override suspend fun save(url: String, fileName: String, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val body = content.toByteArray(Charsets.UTF_8)
            val request = Request.Builder()
                .url(url)
                .put(body.toRequestBody(contentTypeFor(fileName).toMediaType()))
                .header("Content-Length", body.size.toString())
                .build()
            okHttpClient().newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("HTTP ${response.code}")
                }
            }
        }
    }
}

internal fun contentTypeFor(fileName: String): String {
    return when (fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()) {
        "json" -> "application/json"
        "xml" -> "application/xml"
        "html", "htm" -> "text/html; charset=utf-8"
        "md", "markdown" -> "text/markdown; charset=utf-8"
        "yaml", "yml" -> "application/yaml"
        "py", "js", "ts", "swift", "go", "rs", "java", "c", "cpp", "h",
        "sh", "bash", "zsh", "rb", "php", "cs", "kt", "scala", "r" -> "text/plain; charset=utf-8"
        else -> "text/plain; charset=utf-8"
    }
}

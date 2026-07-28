/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.element.android.libraries.chatbot.api.ChatbotBaseUrlResolver
import io.element.android.libraries.di.RoomScope
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.gameapi.api.AppBundleInfo
import io.element.android.libraries.gameapi.impl.DefaultGameApiService
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.miniapp.api.MiniAppConfig
import io.element.android.libraries.miniapp.api.MiniAppToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/** Numeric app IDs that match iOS constants (pptEditorId=5, docxEditorId=1, etc.). */
object MiniAppIds {
    const val DOCX  = 1L
    const val EXCEL = 4L
    const val PPT   = 5L
    const val PDF   = 6L
}

/**
 * Resolves a [MiniAppConfig] for any document type, abstracting the
 * [pkg.app.check.update] bundle fetch from MiniApp-aware callers.
 * Reusable for PPT (appId=5), Word (appId=1), Excel (appId=4), PDF (appId=6).
 */
interface MiniAppDocumentLauncher {
    /** Resolve bundle config for [appId]; [options] is injected as `window.___options`. */
    suspend fun buildConfig(appId: Long, options: Map<String, Any> = emptyMap()): MiniAppConfig
    /** Returns the OkHttpClient needed by [MiniAppView] for bundle download. */
    fun okHttpClient(): OkHttpClient
}

/** CompositionLocal carrying the launcher down the Compose tree. Null when not wired. */
val LocalDocumentLauncher = compositionLocalOf<MiniAppDocumentLauncher?> { null }

@ContributesBinding(RoomScope::class)
@Inject
class DefaultMiniAppDocumentLauncher(
    private val matrixClient: MatrixClient,
    private val baseUrlResolver: ChatbotBaseUrlResolver,
    private val okHttp: () -> OkHttpClient,
    @ApplicationContext private val context: Context,
) : MiniAppDocumentLauncher {

    override fun okHttpClient(): OkHttpClient = okHttp()

    override suspend fun buildConfig(appId: Long, options: Map<String, Any>): MiniAppConfig {
        val token = matrixClient.currentAccessToken()
            .onFailure { Timber.e(it, "DocLauncher: token fetch failed") }
            .getOrNull()
            ?.let { MiniAppToken(accessToken = it) }

        if (appId <= 0L) {
            return MiniAppConfig(appId = appId, url = "", options = options, token = token)
        }

        val homeserverUrl = runCatching {
            baseUrlResolver.resolveHomeserverBaseUrl(matrixClient.userIdServerName())
        }.getOrNull()

        if (homeserverUrl.isNullOrBlank()) {
            Timber.w("DocLauncher: homeserver unavailable, falling back for appId=%d", appId)
            return MiniAppConfig(appId = appId, url = "", options = options, token = token)
        }

        // enrichOptionsWithDocId needs homeserverUrl for server fallback, so called after resolution.
        val enrichedOptions = enrichOptionsWithDocId(appId, options, homeserverUrl)

        val service = DefaultGameApiService(
            homeserverUrl = homeserverUrl,
            matrixClient = matrixClient,
            okHttpClient = okHttp(),
            context = context,
        )

        return service.fetchAppBundle(appId.toInt())
            .map { info ->
                Timber.d("DocLauncher: appId=%d mode=%s", appId, info.loadMode)
                when (info.loadMode) {
                    AppBundleInfo.LoadMode.Remote -> MiniAppConfig(
                        appId = appId,
                        url = info.remoteUrl ?: "",
                        options = enrichedOptions,
                        token = token,
                        appBundleData = info.toBundleDataMap(),
                        homeserver = homeserverUrl,
                    )
                    AppBundleInfo.LoadMode.Local -> MiniAppConfig(
                        appId = appId,
                        url = info.remoteUrl ?: "",
                        zipUrl = info.zipUrl,
                        options = enrichedOptions,
                        token = token,
                        appBundleData = info.toBundleDataMap(),
                        bundleVersion = info.version,
                        homeserver = homeserverUrl,
                    )
                }
            }
            .getOrElse { error ->
                Timber.e(error, "DocLauncher: fetchAppBundle failed appId=%d", appId)
                MiniAppConfig(appId = appId, url = "", options = enrichedOptions, token = token)
            }
    }

    /**
     * Injects a saved `doc_id` into [options] for a given [streamId].
     *
     * Resolution order:
     * 1. If options already contains `doc_id` — return as-is (caller already has it).
     * 2. Local SharedPreferences hit — return immediately (fast path).
     * 3. Server query (`pkg.doc.query`) — save to SP then return enriched options.
     * 4. Server miss / failure — return original options (mini-app will prompt user to create).
     */
    private suspend fun enrichOptionsWithDocId(
        appId: Long,
        options: Map<String, Any>,
        homeserverUrl: String,
    ): Map<String, Any> {
        if (options.containsKey("doc_id")) return options
        val streamId = options["stream_id"]?.toString()?.takeIf { it.isNotBlank() } ?: return options
        val spKey = "${appId}_$streamId"
        val prefs = context.getSharedPreferences("miniapp_doc_ids", Context.MODE_PRIVATE)

        val localDocId = prefs.getString(spKey, null)?.takeIf { it.isNotBlank() }
        if (localDocId != null) {
            Timber.d("DocLauncher: injecting local docId appId=%d streamId=%s", appId, streamId)
            return options + ("doc_id" to localDocId)
        }

        // Local miss — fall back to server.
        Timber.d("DocLauncher: SP miss for appId=%d streamId=%s, querying server", appId, streamId)
        val serverDocId = queryDocFromServer(streamId, homeserverUrl)
        if (serverDocId != null) {
            prefs.edit().putString(spKey, serverDocId).apply()
            Timber.d("DocLauncher: server docId cached appId=%d streamId=%s", appId, streamId)
            return options + ("doc_id" to serverDocId)
        }

        return options
    }

    /**
     * GET pkg.doc.query to look up the docId bound to [streamId] on the server.
     * Returns the first `doc_id` from the response array, or null on any failure/empty result.
     */
    private suspend fun queryDocFromServer(streamId: String, homeserverUrl: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val homeserverHost = homeserverUrl
                    .removePrefix("https://")
                    .removePrefix("http://")
                    .trimEnd('/')
                val encodedUkey = java.net.URLEncoder.encode(streamId, "UTF-8")
                val request = Request.Builder()
                    .url("$homeserverUrl/app-mgr/package/json?method=pkg.doc.query&ukey=$encodedUkey")
                    .header("APP-U", "s=$homeserverHost")
                    .get()
                    .build()
                val responseBody = okHttp().newCall(request).execute().use { it.body?.string().orEmpty() }
                val root = JSONObject(responseBody)
                if (root.optInt("code") != 0) {
                    Timber.w("DocLauncher: pkg.doc.query code=%d", root.optInt("code"))
                    return@runCatching null
                }
                val data = root.optJSONArray("data") ?: JSONArray()
                if (data.length() == 0) return@runCatching null
                data.getJSONObject(0).optString("doc_id").takeIf { it.isNotBlank() }
            }.getOrElse { error ->
                Timber.e(error, "DocLauncher: pkg.doc.query failed streamId=%s", streamId)
                null
            }
        }
}

private fun AppBundleInfo.toBundleDataMap(): Map<String, Any> = buildMap {
    put("id", appId)
    put("version", version)
    put("load_mode", loadMode.name.lowercase())
    remoteUrl?.let { put("remote_url", it) }
    zipUrl?.let { put("zip_url", it) }
}

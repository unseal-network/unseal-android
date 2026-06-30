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
import okhttp3.OkHttpClient
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
                        options = options,
                        token = token,
                        appBundleData = info.toBundleDataMap(),
                    )
                    AppBundleInfo.LoadMode.Local -> MiniAppConfig(
                        appId = appId,
                        url = info.remoteUrl ?: "",
                        zipUrl = info.zipUrl,
                        options = options,
                        token = token,
                        appBundleData = info.toBundleDataMap(),
                        bundleVersion = info.version,
                    )
                }
            }
            .getOrElse { error ->
                Timber.e(error, "DocLauncher: fetchAppBundle failed appId=%d", appId)
                MiniAppConfig(appId = appId, url = "", options = options, token = token)
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

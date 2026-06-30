/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.miniapp.impl

import android.content.Context
import android.content.Context.MODE_PRIVATE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Manages the download, extraction, and local caching of mini-app ZIP bundles.
 *
 * Mirrors the iOS `WebView+Download.swift` flow:
 * ```
 * checkLoad() → loadMiniApp() → download() / unzipLocalZip() → loadLocal()
 * ```
 *
 * ## Cache structure
 * ```
 * <filesDir>/
 *   miniapp/
 *     app_<appId>/          ← extracted bundle directory
 *       index.html
 *       assets/
 *       ...
 * <cacheDir>/
 *   miniapp/
 *     app_<appId>.zip       ← transient download, deleted after extraction
 * ```
 *
 * ## Usage
 * ```kotlin
 * val bundleManager = MiniAppBundleManager(context, okHttpClient)
 * bundleManager.prepareBundle(
 *     appId  = 42L,
 *     zipUrl = "https://cdn.example.com/games/42/bundle.zip",
 *     onProgress = { progress -> /* 0.0..1.0 */ },
 * ).onSuccess { indexFile ->
 *     webView.loadUrl(indexFile.toUri().toString())
 * }.onFailure { error ->
 *     // show error UI
 * }
 * ```
 *
 * @param context       Application or activity context for accessing [Context.getFilesDir].
 * @param okHttpClient  OkHttp client for ZIP downloads.
 */
internal class MiniAppBundleManager(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
) {

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Prepare the local bundle for [appId].
     *
     * 1. If the extracted bundle exists on disk AND [serverVersion] matches the stored local
     *    version — returns `index.html` immediately (cache hit, mirrors iOS no-op path).
     * 2. If the bundle exists but [serverVersion] is newer — clears the cache and re-downloads.
     * 3. If no bundle exists — downloads the ZIP from [zipUrl], extracts it, saves version.
     *
     * Mirrors iOS `ControllerManager.download()`: `compareVersion` guards the download,
     * `updateLocalVersion` is called after successful extraction.
     *
     * @param appId          Numeric app ID; determines the local cache directory.
     * @param zipUrl         HTTP/HTTPS URL of the ZIP bundle to download.
     * @param serverVersion  Version string from the server (e.g. "1.0.5"). Empty = always use cache.
     * @param onProgress     Called with values in `[0.0, 1.0]` during download.
     * @return               [Result.success] with the local `index.html` [File], or
     *                       [Result.failure] with the underlying [Exception].
     */
    suspend fun prepareBundle(
        appId: Long,
        zipUrl: String,
        serverVersion: String = "",
        onProgress: (Float) -> Unit = {},
    ): Result<File> = withContext(Dispatchers.IO) {
        val destDir = localDir(appId)
        val indexFile = File(destDir, "index.html")

        if (indexFile.exists()) {
            val localVersion = getLocalVersion(appId)
            if (!needsUpdate(localVersion, serverVersion)) {
                Timber.d("MiniApp: cache hit appId=$appId version=$localVersion (server=$serverVersion)")
                onProgress(1f)
                return@withContext Result.success(indexFile)
            }
            Timber.d("MiniApp: version update appId=$appId local=$localVersion → server=$serverVersion — clearing cache")
            destDir.deleteRecursively()
        }

        Timber.d("MiniApp: no cache for appId=$appId — downloading ZIP from $zipUrl")
        onProgress(0f)

        val zipFile = downloadZip(zipUrl, appId, onProgress).getOrElse { error ->
            Timber.e(error, "MiniApp: ZIP download failed for appId=$appId")
            return@withContext Result.failure(error)
        }

        return@withContext try {
            Timber.d("MiniApp: extracting ZIP to ${destDir.absolutePath}")
            extractZip(zipFile, destDir)
            zipFile.delete()
            val resolved = if (indexFile.exists()) indexFile else findIndexHtml(destDir)
            if (resolved != null) {
                // Always save a local version marker so the next launch gets a cache hit.
                // When the server returns no version (""), store "0" as a sentinel — this
                // satisfies needsUpdate("0","") → false (keep cache) and still upgrades
                // correctly when the server later returns a real version like "1.0.5" (0 < 1).
                saveLocalVersion(appId, serverVersion.ifEmpty { "0" })
                Timber.d("MiniApp: bundle ready → ${resolved.absolutePath} version=$serverVersion")
                onProgress(1f)
                Result.success(resolved)
            } else {
                Result.failure(IOException("index.html not found after extracting ZIP for appId=$appId"))
            }
        } catch (e: Exception) {
            zipFile.delete()
            destDir.deleteRecursively()   // clean up partial extraction
            Timber.e(e, "MiniApp: ZIP extraction failed for appId=$appId")
            Result.failure(e)
        }
    }

    /**
     * Delete the cached bundle for [appId], forcing a fresh download on the next
     * [prepareBundle] call. Mirrors iOS `clearCache()`.
     */
    fun clearCache(appId: Long) {
        val dir = localDir(appId)
        if (dir.deleteRecursively()) {
            Timber.d("MiniApp: cleared bundle cache for appId=$appId")
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Returns true if [serverVersion] is strictly newer than [localVersion].
     * Mirrors iOS `ControllerManager.compareVersion(nowVersion:newVersion:)`.
     * Empty [localVersion] always returns true (no local cache → must download).
     * Empty [serverVersion] always returns false (server has no version info → keep cache).
     */
    private fun needsUpdate(localVersion: String, serverVersion: String): Boolean {
        if (serverVersion.isEmpty()) return localVersion.isEmpty()  // no server version: only download if nothing cached
        if (localVersion.isEmpty()) return true
        val local = localVersion.split(".").map { it.toIntOrNull() ?: 0 }
        val server = serverVersion.split(".").map { it.toIntOrNull() ?: 0 }
        val len = maxOf(local.size, server.size)
        for (i in 0 until len) {
            val l = local.getOrElse(i) { 0 }
            val s = server.getOrElse(i) { 0 }
            if (s > l) return true
            if (s < l) return false
        }
        return false
    }

    private fun getLocalVersion(appId: Long): String =
        context.getSharedPreferences("miniapp_versions", MODE_PRIVATE)
            .getString("version_$appId", "") ?: ""

    private fun saveLocalVersion(appId: Long, version: String) {
        context.getSharedPreferences("miniapp_versions", MODE_PRIVATE)
            .edit()
            .putString("version_$appId", version)
            .apply()
    }

    /** Persistent extracted-bundle directory: `<filesDir>/miniapp/app_<appId>/` */
    private fun localDir(appId: Long): File =
        File(context.filesDir, "miniapp/app_$appId")

    /** Transient download location: `<cacheDir>/miniapp/app_<appId>.zip` */
    private fun zipCacheFile(appId: Long): File =
        File(context.cacheDir, "miniapp/app_$appId.zip").also { it.parentFile?.mkdirs() }

    /**
     * Download the ZIP at [url] to a temp file, reporting byte progress.
     *
     * Uses `suspendCoroutine` + `OkHttp.enqueue` so the coroutine is cancellable
     * and does not block a thread pool thread while waiting for the response.
     */
    private suspend fun downloadZip(
        url: String,
        appId: Long,
        onProgress: (Float) -> Unit,
    ): Result<File> = runCatching {
        val request = Request.Builder().url(url).build()

        val response: Response = suspendCoroutine { continuation ->
            val call = okHttpClient.newCall(request)
            call.enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) =
                    continuation.resume(response)
                override fun onFailure(call: Call, e: IOException) =
                    continuation.resumeWithException(e)
            })
        }

        if (!response.isSuccessful) {
            response.body.close()
            throw IOException("ZIP download failed: HTTP ${response.code} for $url")
        }

        val body = response.body
        val contentLength = body.contentLength()

        val zipFile = zipCacheFile(appId)
        var bytesWritten = 0L

        body.byteStream().use { input ->
            zipFile.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)   // 8 KB
                var bytes: Int
                while (input.read(buffer).also { bytes = it } != -1) {
                    output.write(buffer, 0, bytes)
                    bytesWritten += bytes
                    if (contentLength > 0) {
                        // Reserve last 5 % for extraction so progress reaches 1.0 only when done
                        onProgress((bytesWritten.toFloat() / contentLength * 0.95f).coerceIn(0f, 0.95f))
                    }
                }
            }
        }

        Timber.d("MiniApp: downloaded $bytesWritten bytes → ${zipFile.absolutePath}")
        zipFile
    }

    /**
     * Extract all files from [zipFile] into [destDir].
     *
     * Path traversal is prevented: entries whose canonical path falls outside
     * [destDir] are silently skipped.
     *
     * Mirrors iOS `PackageManager.unzip(zipURL:to:)`.
     */
    private fun extractZip(zipFile: File, destDir: File) {
        destDir.mkdirs()
        val canonicalDest = destDir.canonicalPath

        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name.trimStart('/', '\\').replace("..", "")
                if (name.isNotBlank() && !entry.isDirectory) {
                    val outFile = File(destDir, name)
                    // Guard: ensure the resolved path is inside destDir
                    if (outFile.canonicalPath.startsWith(canonicalDest)) {
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().use { out -> zis.copyTo(out) }
                    } else {
                        Timber.w("MiniApp: skipping dangerous ZIP entry '${entry.name}'")
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    /**
     * Search common sub-directory conventions used by bundlers (Vite, CRA, etc.)
     * in case the ZIP root is not the app root but a single folder like `dist/`.
     */
    private fun findIndexHtml(dir: File): File? {
        // Depth-first: prefer root, then first subdirectory that contains index.html
        val direct = File(dir, "index.html")
        if (direct.exists()) return direct
        dir.listFiles()?.sortedBy { it.name }?.forEach { sub ->
            if (sub.isDirectory) {
                val nested = File(sub, "index.html")
                if (nested.exists()) return nested
            }
        }
        return null
    }
}

/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.miniapp.api

/**
 * Configuration for a mini-app WebView session.
 *
 * Maps to the constructor parameters of iOS `WebViewController`.
 *
 * ## Load modes
 *
 * Two mutually exclusive load paths exist, mirroring iOS `checkLoad()`:
 *
 * ### Remote mode (`zipUrl == null`)
 * The WebView loads [url] directly as a remote URL (http/https).
 * This is used when the game or app is a live hosted URL.
 *
 * ### Bundle mode (`zipUrl != null`)
 * The WebView host first downloads the ZIP bundle from [zipUrl], extracts it
 * to local storage, then loads `index.html` from the extracted directory via a
 * `file://` URL. Subsequent launches use the cached bundle (no re-download).
 * This mirrors iOS `loadMiniApp()` → `download()` → `unzipLocalZip()` → `loadLocal()`.
 *
 * @param appId     Numeric app ID (0 = generic/doc-editor mode).
 * @param url       Remote URL used when [zipUrl] is null. In bundle mode this
 *                  can serve as a fallback URL if local extraction fails.
 * @param zipUrl    HTTP(S) URL of the ZIP bundle to download and cache locally.
 *                  When non-null the bundle-mode load path is used.
 * @param token     Optional authentication tokens injected as `window.___token`.
 * @param user      Optional current-user info injected as `window.___user`.
 * @param options   Arbitrary extra options injected as `window.___options` (e.g.
 *                  `doc_id`, `create_type`, `file_base64`).
 */
data class MiniAppConfig(
    val appId: Long = 0L,
    val url: String,
    val zipUrl: String? = null,
    val token: MiniAppToken? = null,
    val user: MiniAppUser? = null,
    val options: Map<String, Any> = emptyMap(),
    /**
     * Pre-fetched app bundle metadata returned to JS via the `app.data` bridge method.
     * Mirrors the `AppModel` dict returned by iOS `WebView+Base.swift requestAppModelFromServer`.
     * Populated by MiniAppNode from the [AppBundleInfo] result of `pkg.app.check.update`.
     * Null when the mini-app was opened without a valid appId.
     */
    val appBundleData: Map<String, Any>? = null,

    /**
     * Server-reported bundle version (e.g. "1.0.5"), populated for [LoadMode.Local] only.
     * Used by [MiniAppBundleManager] to skip re-download when the cached version matches.
     * Mirrors iOS `ControllerManager.compareVersion` / `updateLocalVersion`.
     */
    val bundleVersion: String? = null,

    /**
     * Homeserver base URL (e.g. "https://matrix.example.com"), exposed to JS via the
     * `appInfo` bridge so mini-apps can resolve server-relative API paths.
     */
    val homeserver: String? = null,
)

/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.gameapi.api

/**
 * Result of [GameApiService.fetchAppBundle] — mirrors iOS `AppModel` returned by
 * `pkg.app.check.update`.
 *
 * Callers use [loadMode] to decide how to open the mini-app:
 *
 * - [LoadMode.Remote] → load [remoteUrl] directly in the WebView (no ZIP download).
 * - [LoadMode.Local]  → download [zipUrl], extract, and load `index.html` locally.
 *
 * @param appId      Numeric app / game ID.
 * @param version    Bundle version string from the server (e.g. "1.0.5").
 * @param loadMode   Whether to load remote or from a local bundle.
 * @param remoteUrl  Direct WebView URL when [loadMode] == [LoadMode.Remote].
 * @param zipUrl     Full HTTPS URL to the ZIP bundle when [loadMode] == [LoadMode.Local].
 *                   Null when the server does not provide a download URL.
 * @param screenOrientation Raw `screen_orientation` from the server: 1 = portrait (default),
 *                   2 = landscape, 3 = auto. Missing values are reported as 1; unknown values
 *                   are passed through and treated as portrait by the mini-app host.
 */
data class AppBundleInfo(
    val appId: Int,
    val version: String,
    val loadMode: LoadMode,
    val remoteUrl: String?,
    val zipUrl: String?,
    val screenOrientation: Int = SCREEN_ORIENTATION_PORTRAIT,
) {
    companion object {
        const val SCREEN_ORIENTATION_PORTRAIT = 1
        const val SCREEN_ORIENTATION_LANDSCAPE = 2
        const val SCREEN_ORIENTATION_AUTO = 3
    }

    enum class LoadMode {
        /** Load [AppBundleInfo.remoteUrl] directly in the WebView. */
        Remote,

        /** Download [AppBundleInfo.zipUrl], extract, load local `index.html`. */
        Local,
    }
}

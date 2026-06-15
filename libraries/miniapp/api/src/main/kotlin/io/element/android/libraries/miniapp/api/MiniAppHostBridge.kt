/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.miniapp.api

/**
 * Callbacks from the mini-app WebView to the host application.
 *
 * Maps 1-to-1 to iOS `WebViewControllerwDelegate`:
 *
 * | Android                            | iOS                            |
 * |------------------------------------|-------------------------------|
 * | [getAccessToken]                   | `getAccessToken()`             |
 * | [getMembers]                       | `getMembers()`                 |
 * | [getMember]                        | `getMember(userId:)`           |
 * | [getGameInfo]                      | `getGameInfo()`                |
 * | [sendMessage]                      | `sendMessage(_:)`              |
 * | [closeApp]                         | `closeApp()`                   |
 * | [openWeb]                          | handled inline by WebViewController |
 */
interface MiniAppHostBridge {
    /** Return the current Matrix access token for the session. */
    suspend fun getAccessToken(): String

    /** Return all room members. Returns null if not available. */
    suspend fun getMembers(): List<MiniAppUser>?

    /**
     * Return a single room member by Matrix user ID.
     * Synchronous — must not block the main thread for long.
     */
    fun getMember(userId: String): MiniAppUser?

    /**
     * Return game-session metadata to the mini-app.
     * Synchronous — must not block the main thread for long.
     */
    fun getGameInfo(): Map<String, Any>

    /**
     * Forward a message composed by the mini-app to the Matrix room.
     * @param data Raw JS payload (keys are mini-app defined).
     */
    suspend fun sendMessage(data: Map<String, Any>)

    /** Called when the mini-app requests to close itself (bridge `back` command). */
    fun closeApp()

    /**
     * Open an external URL in a new mini-app WebView instance or browser.
     * @param url   Target URL.
     * @param params Full JS params map (may contain `title`, `navbarBgColor`, etc.).
     */
    fun openWeb(url: String, params: Map<String, Any>)

    /**
     * Open an external app or URL scheme (iOS `open.app` bridge handler).
     * Default: no-op (non-game hosts may not support this).
     */
    fun openApp(url: String) {}

    /**
     * Open another mini-app by appId (iOS `open` bridge handler).
     * Default: no-op.
     */
    fun openMiniApp(appId: Int, params: Map<String, Any>) {}

    /**
     * Return app bundle metadata for the JS `app.data` bridge method.
     * Default: returns null — callers fall back to [MiniAppConfig.appBundleData].
     */
    suspend fun fetchAppData(appId: Int): Map<String, Any>? = null
}

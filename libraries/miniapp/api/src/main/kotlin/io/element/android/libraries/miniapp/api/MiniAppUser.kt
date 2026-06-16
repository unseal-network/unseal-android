/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.miniapp.api

/**
 * A Matrix room member, as seen by the mini-app WebView.
 * Maps to iOS `PUser`.
 */
data class MiniAppUser(
    val userId: String,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val powerLevel: Int? = null,
    val avatarBgColor: String? = null,
    val avatarTextColor: String? = null,
    val isAgent: Boolean? = null,
    val isSelf: Boolean? = null,
    val agentId: String? = null,
)

/** Convert to the JSON-object shape the JS bridge expects. */
fun MiniAppUser.toMap(): Map<String, Any> = buildMap {
    put("userId", userId)
    displayName?.let { put("displayName", it) }
    avatarUrl?.let { put("avatarUrl", it) }
    powerLevel?.let { put("powerLevel", it) }
    avatarBgColor?.let { put("avatarBgColor", it) }
    avatarTextColor?.let { put("avatarTextColor", it) }
    isAgent?.let { put("isAgent", it) }
    isSelf?.let { put("isSelf", it) }
    agentId?.let { put("agentId", it) }
}

/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbot.api

interface ChatbotBaseUrlResolver {
    /**
     * The Unseal **agent-api** base URL (credits, voice, environment, vault, connectors, triggers),
     * resolved from `.well-known/matrix/client` `org.unseal.api.base_url`.
     */
    suspend fun resolveUnsealApiBaseUrl(serverName: String?): String

    /**
     * The Matrix **homeserver** base URL (agent & skill management endpoints live here),
     * resolved from `.well-known/matrix/client` `m.homeserver.base_url`.
     */
    suspend fun resolveHomeserverBaseUrl(serverName: String?): String
}

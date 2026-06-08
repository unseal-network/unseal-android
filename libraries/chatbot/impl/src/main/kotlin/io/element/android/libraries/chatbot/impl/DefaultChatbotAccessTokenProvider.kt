/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbot.impl

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.sessionstorage.api.SessionStore

@ContributesBinding(AppScope::class)
class DefaultChatbotAccessTokenProvider(
    private val sessionStore: SessionStore,
) : ChatbotAccessTokenProvider {
    override suspend fun accessToken(matrixClient: MatrixClient): String? {
        return sessionStore.getSession(matrixClient.sessionId.value)
            ?.accessToken
            ?.takeIf { it.isNotBlank() }
    }
}

/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbot.test

import io.element.android.libraries.chatbot.api.ChatbotBaseUrlResolver
import io.element.android.libraries.chatbot.api.ChatbotConfig
import io.element.android.tests.testutils.simulateLongTask

class FakeChatbotBaseUrlResolver : ChatbotBaseUrlResolver {
    var resolveResult: (String?) -> String = { ChatbotConfig.UNSEAL_API_FALLBACK_BASE_URL }
    val seenServerNames = mutableListOf<String?>()

    override suspend fun resolveUnsealApiBaseUrl(serverName: String?): String = simulateLongTask {
        seenServerNames += serverName
        resolveResult(serverName)
    }

    override suspend fun resolveHomeserverBaseUrl(serverName: String?): String = simulateLongTask {
        seenServerNames += serverName
        resolveResult(serverName)
    }
}

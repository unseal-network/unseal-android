/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbot.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.sessionstorage.test.InMemorySessionStore
import io.element.android.libraries.sessionstorage.test.aSessionData
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultChatbotAccessTokenProviderTest {
    @Test
    fun `accessToken - returns current session access token`() = runTest {
        val provider = DefaultChatbotAccessTokenProvider(
            InMemorySessionStore(
                initialList = listOf(aSessionData(sessionId = A_SESSION_ID.value, accessToken = "mx-token"))
            )
        )

        assertThat(provider.accessToken(FakeMatrixClient())).isEqualTo("mx-token")
    }

    @Test
    fun `accessToken - returns null when session is missing or token is blank`() = runTest {
        val missingProvider = DefaultChatbotAccessTokenProvider(InMemorySessionStore())
        val blankProvider = DefaultChatbotAccessTokenProvider(
            InMemorySessionStore(
                initialList = listOf(aSessionData(sessionId = A_SESSION_ID.value, accessToken = " "))
            )
        )

        assertThat(missingProvider.accessToken(FakeMatrixClient())).isNull()
        assertThat(blankProvider.accessToken(FakeMatrixClient())).isNull()
    }
}

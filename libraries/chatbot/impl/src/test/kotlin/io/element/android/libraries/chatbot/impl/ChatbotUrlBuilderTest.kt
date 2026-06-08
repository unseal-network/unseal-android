/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbot.impl

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChatbotUrlBuilderTest {
    @Test
    fun `path - encodes Matrix identifiers as path segments`() {
        val path = ChatbotUrlBuilder.path(
            "/rooms/{roomId}/agents/{agentId}",
            mapOf(
                "roomId" to "!room:id",
                "agentId" to "@agent:server",
            )
        )

        assertThat(path).isEqualTo("/rooms/%21room%3Aid/agents/%40agent%3Aserver")
    }

    @Test
    fun `query - omits null values and encodes present values`() {
        val query = ChatbotUrlBuilder.query(
            mapOf(
                "search" to "calendar sync",
                "cursor" to null,
                "limit" to "20",
            )
        )

        assertThat(query).isEqualTo("?search=calendar%20sync&limit=20")
    }
}

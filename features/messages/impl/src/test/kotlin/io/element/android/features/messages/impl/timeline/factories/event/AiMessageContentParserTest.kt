/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.factories.event

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AiMessageContentParserTest {
    @Test
    fun `parse - keeps stream initiator for runtime controls`() {
        val content = AiMessageContentParser().parse(
            originalJson = """
                {
                  "type": "m.room.message",
                  "content": {
                    "msgtype": "m.text",
                    "body": "Thinking…",
                    "stream": { "id": "stream-1" },
                    "target_user_id": "@alice:example.org"
                  }
                }
            """.trimIndent(),
            isEdited = false,
        )

        assertThat(content?.streamId).isEqualTo("stream-1")
        assertThat(content?.targetUserId).isEqualTo("@alice:example.org")
    }
}

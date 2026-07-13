/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.utils.UnsealAgentProfileLink
import org.json.JSONObject
import org.junit.Test

class TimelineItemAgentProfilePreviewViewTest {
    private val profileLink = UnsealAgentProfileLink(
        botName = "jelf-agent",
        profileUrl = "https://keepsecret.io/@jelf-agent",
        jsonUrl = "https://keepsecret.io/@jelf-agent.json",
        mdUrl = "https://keepsecret.io/@jelf-agent.md",
    )

    @Test
    fun `sanitizeAgentProfilePreviewMetadataUrl accepts only absolute web URLs`() {
        assertThat(sanitizeAgentProfilePreviewMetadataUrl("https://example.org/avatar.png"))
            .isEqualTo("https://example.org/avatar.png")
        assertThat(sanitizeAgentProfilePreviewMetadataUrl(" http://example.org/profile "))
            .isEqualTo("http://example.org/profile")

        assertThat(sanitizeAgentProfilePreviewMetadataUrl("javascript:alert(1)")).isNull()
        assertThat(sanitizeAgentProfilePreviewMetadataUrl("data:text/html,hello")).isNull()
        assertThat(sanitizeAgentProfilePreviewMetadataUrl("/relative/path")).isNull()
        assertThat(sanitizeAgentProfilePreviewMetadataUrl("https://")).isNull()
    }

    @Test
    fun `agent profile metadata requires a name but allows missing soul and channels`() {
        val nameOnly = JSONObject(
            """{"name":"Name Only Agent","soul":"   ","channels":[]}"""
        ).toAgentProfilePreviewMetadata(profileLink)
        val missingName = JSONObject(
            """{"soul":"Stay useful.","channels":[{"platform":"telegram","url":"https://t.me/example"}]}"""
        ).toAgentProfilePreviewMetadata(profileLink)
        val invalidChannel = JSONObject(
            """{"name":"Invalid Channel Agent","channels":[{"platform":"telegram","url":"javascript:alert(1)"}]}"""
        ).toAgentProfilePreviewMetadata(profileLink)

        assertThat(nameOnly?.title).isEqualTo("Name Only Agent")
        assertThat(nameOnly?.soul).isNull()
        assertThat(nameOnly?.channels).isEmpty()
        assertThat(missingName).isNull()
        assertThat(invalidChannel?.title).isEqualTo("Invalid Channel Agent")
        assertThat(invalidChannel?.channels).isEmpty()
    }
}

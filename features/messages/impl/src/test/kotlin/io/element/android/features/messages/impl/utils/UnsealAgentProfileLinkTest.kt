/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UnsealAgentProfileLinkTest {
    @Test
    fun `parses agent profile URLs from any hostname`() {
        assertThat("https://unseal.network/@kimi-claw".toUnsealAgentProfileLink())
            .isEqualTo(
                UnsealAgentProfileLink(
                    botName = "kimi-claw",
                    profileUrl = "https://unseal.network/@kimi-claw",
                    jsonUrl = "https://unseal.network/@kimi-claw.json",
                    mdUrl = "https://unseal.network/@kimi-claw.md",
                )
            )
        assertThat("https://keepsecret.io/@jelf-agent".toUnsealAgentProfileLink())
            .isEqualTo(
                UnsealAgentProfileLink(
                    botName = "jelf-agent",
                    profileUrl = "https://keepsecret.io/@jelf-agent",
                    jsonUrl = "https://keepsecret.io/@jelf-agent.json",
                    mdUrl = "https://keepsecret.io/@jelf-agent.md",
                )
            )
    }

    @Test
    fun `parses profile URL with uppercase host as canonical Unseal URL`() {
        assertThat("https://UNSEAL.NETWORK/@kimi-claw".toUnsealAgentProfileLink())
            .isEqualTo(
                UnsealAgentProfileLink(
                    botName = "kimi-claw",
                    profileUrl = "https://unseal.network/@kimi-claw",
                    jsonUrl = "https://unseal.network/@kimi-claw.json",
                    mdUrl = "https://unseal.network/@kimi-claw.md",
                )
            )
    }

    @Test
    fun `rejects mention-like text that is not a URL`() {
        assertThat("@kimi-claw".toUnsealAgentProfileLink()).isNull()
        assertThat("@kimi-claw:unseal.network".toUnsealAgentProfileLink()).isNull()
    }

    @Test
    fun `rejects vanity resources and decorated profile URLs`() {
        assertThat("https://unseal.network/@kimi-claw.md".toUnsealAgentProfileLink()).isNull()
        assertThat("https://unseal.network/@kimi-claw.json".toUnsealAgentProfileLink()).isNull()
        assertThat("https://unseal.network/@kimi-claw/".toUnsealAgentProfileLink()).isNull()
        assertThat("https://unseal.network/@kimi-claw?utm=1".toUnsealAgentProfileLink()).isNull()
        assertThat("https://unseal.network/@kimi-claw#about".toUnsealAgentProfileLink()).isNull()
        assertThat("https://evil.example@unseal.network/@kimi-claw".toUnsealAgentProfileLink()).isNull()
    }

    @Test
    fun `rejects encoded slash and blank localpart`() {
        assertThat("https://unseal.network/%40kimi-claw".toUnsealAgentProfileLink()).isNull()
        assertThat("https://unseal.network/@kimi%2Fclaw".toUnsealAgentProfileLink()).isNull()
        assertThat("https://unseal.network/@".toUnsealAgentProfileLink()).isNull()
        assertThat("https://unseal.network/@%20".toUnsealAgentProfileLink()).isNull()
    }

    @Test
    fun `rejects whitespace control vanity suffixes and Matrix ID shaped localparts`() {
        assertThat("https://unseal.network/@kimi%09claw".toUnsealAgentProfileLink()).isNull()
        assertThat("https://unseal.network/@kimi%00claw".toUnsealAgentProfileLink()).isNull()
        assertThat("https://unseal.network/@kimi-claw.MD".toUnsealAgentProfileLink()).isNull()
        assertThat("https://unseal.network/@kimi:unseal.network".toUnsealAgentProfileLink()).isNull()
    }

    @Test
    fun `rejects encoded delimiters spaces and unsafe localparts`() {
        assertThat("https://unseal.network/@kimi%3Fclaw".toUnsealAgentProfileLink()).isNull()
        assertThat("https://unseal.network/@kimi%23claw".toUnsealAgentProfileLink()).isNull()
        assertThat("https://unseal.network/@kimi%20claw".toUnsealAgentProfileLink()).isNull()
        assertThat("https://unseal.network/@kimi.claw".toUnsealAgentProfileLink()).isNull()
        assertThat("https://unseal.network/@kimi+claw".toUnsealAgentProfileLink()).isNull()
    }

    @Test
    fun `rejects non web schemes`() {
        assertThat("ftp://unseal.network/@kimi-claw".toUnsealAgentProfileLink()).isNull()
    }
}

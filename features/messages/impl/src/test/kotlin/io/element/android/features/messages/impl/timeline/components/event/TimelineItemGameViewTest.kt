/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import com.google.common.truth.Truth.assertThat
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test

class TimelineItemGameViewTest {
    @Test
    fun `appUHeaderValueForUrl returns header for matching homeserver host`() {
        val header = appUHeaderValueForUrl(
            homeserverHost = "Matrix.Example.COM",
            requestUrl = "https://matrix.example.com/app-mgr/upload/sign?key=game.png".toHttpUrl(),
        )

        assertThat(header).isEqualTo("s=matrix.example.com")
    }

    @Test
    fun `appUHeaderValueForUrl preserves explicit homeserver port`() {
        val header = appUHeaderValueForUrl(
            homeserverHost = "HTTPS://Matrix.Example.COM:8448/some/path",
            requestUrl = "https://matrix.example.com:8448/app-mgr/upload/sign?key=game.png".toHttpUrl(),
        )

        assertThat(header).isEqualTo("s=matrix.example.com:8448")
    }

    @Test
    fun `appUHeaderValueForUrl omits header for third party icon hosts`() {
        val header = appUHeaderValueForUrl(
            homeserverHost = "matrix.example.com",
            requestUrl = "https://cdn.example.com/game.png".toHttpUrl(),
        )

        assertThat(header).isNull()
    }

    @Test
    fun `appUHeaderValueForUrl omits header for mismatched explicit port`() {
        val header = appUHeaderValueForUrl(
            homeserverHost = "matrix.example.com:8448",
            requestUrl = "https://matrix.example.com/app-mgr/upload/sign?key=game.png".toHttpUrl(),
        )

        assertThat(header).isNull()
    }

    @Test
    fun `appUHeaderValueForUrl rejects header unsafe homeserver values`() {
        val header = appUHeaderValueForUrl(
            homeserverHost = "matrix.example.com\r\nX-Injected: yes",
            requestUrl = "https://matrix.example.com/app-mgr/upload/sign?key=game.png".toHttpUrl(),
        )

        assertThat(header).isNull()
    }
}

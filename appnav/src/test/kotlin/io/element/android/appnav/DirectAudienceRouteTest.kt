/*
 * Copyright (c) 2026 Unseal
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.appnav

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.test.A_SESSION_ID
import org.junit.Test

class DirectAudienceRouteTest {
    @Test
    fun `logged in audience link immediately creates a listener route`() {
        val callData = directAudienceCallData(A_SESSION_ID, "bcast_demo")

        assertThat(callData?.sessionId).isEqualTo(A_SESSION_ID)
        assertThat(callData?.audienceBroadcastId).isEqualTo("bcast_demo")
    }

    @Test
    fun `logged out audience link waits for authentication`() {
        assertThat(directAudienceCallData(null, "bcast_demo")).isNull()
    }
}

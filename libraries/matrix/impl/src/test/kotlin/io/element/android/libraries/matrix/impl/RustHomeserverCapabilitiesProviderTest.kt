/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RustHomeserverCapabilitiesProviderTest {
    @Test
    fun `refresh succeeds without FFI capabilities`() = runTest {
        val provider = RustHomeserverCapabilitiesProvider()

        assertThat(provider.refresh().isSuccess).isTrue()
    }

    @Test
    fun `canChangeDisplayName returns conservative true`() = runTest {
        val provider = RustHomeserverCapabilitiesProvider()

        assertThat(provider.canChangeDisplayName().getOrNull()).isTrue()
    }

    @Test
    fun `canChangeAvatarUrl returns conservative true`() = runTest {
        val provider = RustHomeserverCapabilitiesProvider()

        assertThat(provider.canChangeAvatarUrl().getOrNull()).isTrue()
    }
}

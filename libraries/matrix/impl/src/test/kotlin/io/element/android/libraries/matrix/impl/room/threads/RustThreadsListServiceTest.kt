/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.room.threads

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.room.threads.ThreadListPaginationStatus
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RustThreadsListServiceTest {
    @Test
    fun `subscribing to item updates returns an empty list`() = runTest {
        val service = RustThreadsListService()

        service.subscribeToItemUpdates().test {
            assertThat(awaitItem()).isEmpty()
        }
    }

    @Test
    fun `subscribing to pagination updates returns idle without more items`() = runTest {
        val service = RustThreadsListService()

        service.subscribeToPaginationUpdates().test {
            assertThat(awaitItem()).isEqualTo(ThreadListPaginationStatus.Idle(hasMoreToLoad = false))
        }
    }

    @Test
    fun `paginate succeeds without FFI thread list support`() = runTest {
        val service = RustThreadsListService()

        assertThat(service.paginate().isSuccess).isTrue()
    }

    @Test
    fun `reset succeeds without FFI thread list support`() = runTest {
        val service = RustThreadsListService()

        assertThat(service.reset().isSuccess).isTrue()
    }

    @Test
    fun `destroy succeeds without FFI thread list support`() {
        val service = RustThreadsListService()

        service.destroy()
    }
}

/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.room

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.test.A_USER_ID
import io.element.android.libraries.matrix.test.A_USER_ID_2
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TypingMembersExpirationTest {
    @Test
    fun `typing members expire when the SDK does not send a clear update`() = runTest {
        val source = MutableSharedFlow<List<String>>()
        source.expireTypingMembers(
            timeoutMillis = 30_000,
            nowMillis = { testScheduler.currentTime },
        ).test {
            source.emit(listOf(A_USER_ID.value))

            assertThat(awaitItem()).containsExactly(A_USER_ID.value)

            advanceTimeBy(29_999)
            runCurrent()
            expectNoEvents()

            advanceTimeBy(1)
            runCurrent()
            assertThat(awaitItem()).isEmpty()
        }
    }

    @Test
    fun `typing members are cleared immediately when the SDK sends an empty update`() = runTest {
        val source = MutableSharedFlow<List<String>>()
        source.expireTypingMembers(
            timeoutMillis = 30_000,
            nowMillis = { testScheduler.currentTime },
        ).test {
            source.emit(listOf(A_USER_ID.value))
            assertThat(awaitItem()).containsExactly(A_USER_ID.value)

            source.emit(emptyList())

            assertThat(awaitItem()).isEmpty()
        }
    }

    @Test
    fun `typing member timeout is refreshed by later SDK updates`() = runTest {
        val source = MutableSharedFlow<List<String>>()
        source.expireTypingMembers(
            timeoutMillis = 30_000,
            nowMillis = { testScheduler.currentTime },
        ).test {
            source.emit(listOf(A_USER_ID.value))
            assertThat(awaitItem()).containsExactly(A_USER_ID.value)

            advanceTimeBy(20_000)
            source.emit(listOf(A_USER_ID.value, A_USER_ID_2.value))
            assertThat(awaitItem()).containsExactly(A_USER_ID.value, A_USER_ID_2.value).inOrder()

            advanceTimeBy(29_999)
            runCurrent()
            expectNoEvents()

            advanceTimeBy(1)
            runCurrent()
            assertThat(awaitItem()).isEmpty()
        }
    }
}

/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.roomkey

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class RoomKeyRecoveryProgressStoreTest {
    private val clock = MutableClock()
    private val store = RoomKeyRecoveryProgressStore(clock = clock)

    @Test
    fun `record stores checking state`() {
        val record = store.record(aRequest())

        assertThat(record.currentStage).isEqualTo(RoomKeyRecoveryDisplayStage.CheckingDeviceVerification)
        assertThat(record.request).isEqualTo(aRequest())
        assertThat(record.planStages).isEmpty()
        assertThat(record.deadline).isNull()
        assertThat(record.failedAt).isNull()
        assertThat(store.get(aRequest())).isEqualTo(record)
    }

    @Test
    fun `startStage stores active stage and remaining time`() {
        val planStages = listOf(RoomKeyRecoveryDisplayStage.Backup, RoomKeyRecoveryDisplayStage.Sender)

        val record = store.startStage(
            stage = RoomKeyRecoveryDisplayStage.Backup,
            request = aRequest(),
            planStages = planStages,
            duration = 60.seconds,
        )

        assertThat(record.currentStage).isEqualTo(RoomKeyRecoveryDisplayStage.Backup)
        assertThat(record.planStages).isEqualTo(planStages)
        assertThat(store.remainingInterval(aRequest())).isEqualTo(60.seconds)

        clock.advanceBy(45.seconds)

        assertThat(store.remainingInterval(aRequest())).isEqualTo(15.seconds)
    }

    @Test
    fun `remainingInterval returns null after deadline`() {
        store.startStage(
            stage = RoomKeyRecoveryDisplayStage.Sender,
            request = aRequest(),
            planStages = listOf(RoomKeyRecoveryDisplayStage.Sender),
            duration = 60.seconds,
        )

        clock.advanceBy(60.seconds)

        assertThat(store.remainingInterval(aRequest())).isNull()
    }

    @Test
    fun `markFailed stores failed record`() {
        val planStages = listOf(RoomKeyRecoveryDisplayStage.Sender)

        val record = store.markFailed(aRequest(), planStages)

        assertThat(record.currentStage).isEqualTo(RoomKeyRecoveryDisplayStage.Failed)
        assertThat(record.planStages).isEqualTo(planStages)
        assertThat(record.deadline).isNull()
        assertThat(record.failedAt).isNotNull()
        assertThat(store.get(aRequest())).isEqualTo(record)
    }

    @Test
    fun `remove clears request`() {
        store.record(aRequest())

        store.remove(aRequest())

        assertThat(store.get(aRequest())).isNull()
    }

    @Test
    fun `retainOnly removes requests that are no longer visible`() {
        val retained = aRequest(sessionId = "retained")
        val removed = aRequest(sessionId = "removed")
        store.record(retained)
        store.record(removed)

        store.retainOnly(listOf(retained))

        assertThat(store.get(retained)).isNotNull()
        assertThat(store.get(removed)).isNull()
    }
}

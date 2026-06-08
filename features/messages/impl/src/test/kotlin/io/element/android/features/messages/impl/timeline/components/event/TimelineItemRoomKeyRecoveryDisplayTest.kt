/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.roomkey.RoomKeyRecoveryDisplayStage
import io.element.android.features.messages.impl.roomkey.RoomKeyRecoveryStatus
import io.element.android.features.messages.impl.roomkey.aRequest
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemRoomKeyRecovery
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemRoomKeyRecoveryState
import io.element.android.features.messages.impl.timeline.model.event.toTimelineItemRoomKeyRecovery
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class TimelineItemRoomKeyRecoveryDisplayTest {
    @Test
    fun `device unverified state asks to verify device`() {
        val recovery = TimelineItemRoomKeyRecovery(
            request = aRequest(),
            eventCount = 1,
            state = TimelineItemRoomKeyRecoveryState.DeviceUnverified,
        )

        val display = recovery.display()

        assertThat(display.title).contains("Verify this device")
        assertThat(display.action).isEqualTo(RoomKeyRecoveryAction.VerifyDevice)
    }

    @Test
    fun `active state explains the current recovery stage`() {
        val recovery = TimelineItemRoomKeyRecovery(
            request = aRequest(),
            eventCount = 3,
            state = TimelineItemRoomKeyRecoveryState.Active,
            planStages = listOf(RoomKeyRecoveryDisplayStage.Backup, RoomKeyRecoveryDisplayStage.Sender, RoomKeyRecoveryDisplayStage.Members),
            currentStage = RoomKeyRecoveryDisplayStage.Sender,
            remaining = 42.seconds,
        )

        val display = recovery.display()

        assertThat(display.title).isEqualTo("Requesting keys from the sender")
        assertThat(display.detail).contains("42s")
        assertThat(display.detail).contains("3 messages")
    }

    @Test
    fun `failed state exposes retry action`() {
        val recovery = TimelineItemRoomKeyRecovery(
            request = aRequest(),
            eventCount = 2,
            state = TimelineItemRoomKeyRecoveryState.Failed,
        )

        val display = recovery.display()

        assertThat(display.title).isEqualTo("Key recovery failed")
        assertThat(display.action).isEqualTo(RoomKeyRecoveryAction.Retry)
    }

    @Test
    fun `coordinator status converts to timeline recovery model`() {
        val request = aRequest()
        val status = RoomKeyRecoveryStatus.Active(
            request = request,
            eventCount = 2,
            currentStage = RoomKeyRecoveryDisplayStage.Backup,
            planStages = listOf(RoomKeyRecoveryDisplayStage.Backup, RoomKeyRecoveryDisplayStage.Sender),
            remaining = 30.seconds,
        )

        val recovery = status.toTimelineItemRoomKeyRecovery()

        assertThat(recovery.request).isEqualTo(request)
        assertThat(recovery.eventCount).isEqualTo(2)
        assertThat(recovery.state).isEqualTo(TimelineItemRoomKeyRecoveryState.Active)
        assertThat(recovery.currentStage).isEqualTo(RoomKeyRecoveryDisplayStage.Backup)
    }
}

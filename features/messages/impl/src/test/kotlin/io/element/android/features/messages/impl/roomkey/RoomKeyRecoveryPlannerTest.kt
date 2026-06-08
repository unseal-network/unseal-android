/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.roomkey

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.encryption.roomkey.RoomKeyRecoveryRequest
import org.junit.Test

class RoomKeyRecoveryPlannerTest {
    private val planner = RoomKeyRecoveryPlanner()

    @Test
    fun `buildPlan uses own devices for own messages`() {
        assertThat(
            planner.buildPlan(
                isOwnMessage = true,
                canUseKeyBackup = true,
                hasMemberFallback = false,
            ).stages
        ).containsExactly(RoomKeyRecoveryDisplayStage.OwnDevices).inOrder()
    }

    @Test
    fun `buildPlan uses backup then sender for other user messages when backup is usable`() {
        assertThat(
            planner.buildPlan(
                isOwnMessage = false,
                canUseKeyBackup = true,
                hasMemberFallback = false,
            ).stages
        ).containsExactly(RoomKeyRecoveryDisplayStage.Backup, RoomKeyRecoveryDisplayStage.Sender).inOrder()
    }

    @Test
    fun `buildPlan skips backup when backup is not usable`() {
        assertThat(
            planner.buildPlan(
                isOwnMessage = false,
                canUseKeyBackup = false,
                hasMemberFallback = false,
            ).stages
        ).containsExactly(RoomKeyRecoveryDisplayStage.Sender).inOrder()
    }

    @Test
    fun `buildPlan appends members when member fallback exists`() {
        assertThat(
            planner.buildPlan(
                isOwnMessage = false,
                canUseKeyBackup = true,
                hasMemberFallback = true,
            ).stages
        ).containsExactly(RoomKeyRecoveryDisplayStage.Backup, RoomKeyRecoveryDisplayStage.Sender, RoomKeyRecoveryDisplayStage.Members).inOrder()
    }

    @Test
    fun `senderTarget keeps device for same homeserver when latest devices are unknown`() {
        val target = aRequest(senderUserId = UserId("@alice:example.org")).senderTarget(
            ownUserId = UserId("@me:example.org"),
        )

        assertThat(target.userId).isEqualTo(UserId("@alice:example.org"))
        assertThat(target.deviceId).isEqualTo("ALICEDEVICE")
    }

    @Test
    fun `senderTarget drops device for different homeserver`() {
        val target = aRequest(senderUserId = UserId("@alice:elsewhere.org")).senderTarget(
            ownUserId = UserId("@me:example.org"),
            latestSenderDeviceIds = setOf("ALICEDEVICE"),
        )

        assertThat(target.userId).isEqualTo(UserId("@alice:elsewhere.org"))
        assertThat(target.deviceId).isNull()
    }

    @Test
    fun `senderTarget keeps device when it is still current`() {
        val target = aRequest().senderTarget(
            ownUserId = UserId("@me:example.org"),
            latestSenderDeviceIds = setOf("ALICEDEVICE"),
        )

        assertThat(target.deviceId).isEqualTo("ALICEDEVICE")
    }

    @Test
    fun `senderTarget drops stale device`() {
        val target = aRequest().senderTarget(
            ownUserId = UserId("@me:example.org"),
            latestSenderDeviceIds = setOf("OTHERDEVICE"),
        )

        assertThat(target.deviceId).isNull()
    }

    private fun aRequest(
        senderUserId: UserId = UserId("@alice:example.org"),
        senderDeviceId: String? = "ALICEDEVICE",
    ) = RoomKeyRecoveryRequest(
        roomId = RoomId("!room:example.org"),
        senderUserId = senderUserId,
        senderDeviceId = senderDeviceId,
        senderKey = "senderKey",
        sessionId = "session",
        ciphertext = "ciphertext",
    )
}

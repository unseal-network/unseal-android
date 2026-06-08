/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.model.event

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.features.messages.impl.roomkey.RoomKeyRecoveryDisplayStage
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.encryption.roomkey.RoomKeyRecoveryRequest
import io.element.android.libraries.matrix.api.timeline.item.event.UnableToDecryptContent
import io.element.android.libraries.matrix.api.timeline.item.event.UtdCause
import kotlin.time.Duration.Companion.seconds

open class TimelineItemEncryptedContentProvider : PreviewParameterProvider<TimelineItemEncryptedContent> {
    override val values: Sequence<TimelineItemEncryptedContent>
        get() = sequenceOf(
            aTimelineItemEncryptedContent(),
            aTimelineItemEncryptedContent(
                data = UnableToDecryptContent.Data.MegolmV1AesSha2(
                    sessionId = "sessionId",
                    utdCause = UtdCause.SentBeforeWeJoined,
                )
            ),
            aTimelineItemEncryptedContent(
                data = UnableToDecryptContent.Data.MegolmV1AesSha2(
                    sessionId = "sessionId",
                    utdCause = UtdCause.VerificationViolation,
                )
            ),
            aTimelineItemEncryptedContent(
                data = UnableToDecryptContent.Data.MegolmV1AesSha2(
                    sessionId = "sessionId",
                    utdCause = UtdCause.UnsignedDevice,
                )
            ),
            aTimelineItemEncryptedContent(
                data = UnableToDecryptContent.Data.MegolmV1AesSha2(
                    sessionId = "sessionId",
                    utdCause = UtdCause.HistoricalMessageAndBackupIsDisabled,
                )
            ),
            aTimelineItemEncryptedContent(
                data = UnableToDecryptContent.Data.MegolmV1AesSha2(
                    sessionId = "sessionId",
                    utdCause = UtdCause.HistoricalMessageAndDeviceIsUnverified,
                )
            ),
            aTimelineItemEncryptedContent(
                data = UnableToDecryptContent.Data.MegolmV1AesSha2(
                    sessionId = "sessionId",
                    utdCause = UtdCause.WithheldUnverifiedOrInsecureDevice,
                )
            ),
            aTimelineItemEncryptedContent(
                data = UnableToDecryptContent.Data.MegolmV1AesSha2(
                    sessionId = "sessionId",
                    utdCause = UtdCause.WithheldBySender,
                )
            ),
            aTimelineItemEncryptedContent(
                data = UnableToDecryptContent.Data.MegolmV1AesSha2(
                    sessionId = "sessionId",
                    utdCause = UtdCause.Unknown,
                )
            ),
            aTimelineItemEncryptedContent(
                recovery = TimelineItemRoomKeyRecovery(
                    request = aRecoveryRequest(),
                    eventCount = 1,
                    state = TimelineItemRoomKeyRecoveryState.DeviceUnverified,
                    currentStage = RoomKeyRecoveryDisplayStage.DeviceUnverified,
                )
            ),
            aTimelineItemEncryptedContent(
                recovery = TimelineItemRoomKeyRecovery(
                    request = aRecoveryRequest(),
                    eventCount = 3,
                    state = TimelineItemRoomKeyRecoveryState.Active,
                    planStages = listOf(RoomKeyRecoveryDisplayStage.Backup, RoomKeyRecoveryDisplayStage.Sender, RoomKeyRecoveryDisplayStage.Members),
                    currentStage = RoomKeyRecoveryDisplayStage.Sender,
                    remaining = 42.seconds,
                )
            ),
            aTimelineItemEncryptedContent(
                recovery = TimelineItemRoomKeyRecovery(
                    request = aRecoveryRequest(),
                    eventCount = 2,
                    state = TimelineItemRoomKeyRecoveryState.Pending,
                    remaining = 90.seconds,
                )
            ),
            aTimelineItemEncryptedContent(
                recovery = TimelineItemRoomKeyRecovery(
                    request = aRecoveryRequest(),
                    eventCount = 2,
                    state = TimelineItemRoomKeyRecoveryState.Failed,
                    planStages = listOf(RoomKeyRecoveryDisplayStage.Backup, RoomKeyRecoveryDisplayStage.Sender),
                    currentStage = RoomKeyRecoveryDisplayStage.Failed,
                )
            ),
        )
}

private fun aTimelineItemEncryptedContent(
    data: UnableToDecryptContent.Data = UnableToDecryptContent.Data.Unknown,
    recovery: TimelineItemRoomKeyRecovery? = null,
) = TimelineItemEncryptedContent(
    data = data,
    recovery = recovery,
)

private fun aRecoveryRequest() = RoomKeyRecoveryRequest(
    roomId = RoomId("!room:example.org"),
    senderUserId = UserId("@alice:example.org"),
    senderDeviceId = "ALICEDEVICE",
    senderKey = "senderKey",
    sessionId = "sessionId",
    ciphertext = "ciphertext",
)

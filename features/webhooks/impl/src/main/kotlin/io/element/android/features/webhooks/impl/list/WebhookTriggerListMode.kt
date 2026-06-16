/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.webhooks.impl.list

import android.os.Parcelable
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.parcelize.Parcelize

sealed interface WebhookTriggerListMode : Parcelable {
    @Parcelize
    data object Global : WebhookTriggerListMode

    @Parcelize
    data class Room(val roomId: RoomId, val roomName: String) : WebhookTriggerListMode
}

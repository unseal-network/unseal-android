/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.roomkey

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.di.SessionScope

@SingleIn(SessionScope::class)
@Inject
class RoomKeyRecoveryStores {
    val pendingStore = RoomKeyRecoveryPendingStore()
    val agentPendingStore = AgentRoomKeyRecoveryPendingStore()
    val progressStore = RoomKeyRecoveryProgressStore()
}

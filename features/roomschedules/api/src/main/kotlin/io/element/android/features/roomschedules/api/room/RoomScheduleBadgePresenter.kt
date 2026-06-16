/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.roomschedules.api.room

import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.JoinedRoom

interface RoomScheduleBadgePresenter : Presenter<RoomScheduleBadgeState> {
    interface Factory {
        fun create(roomId: RoomId, joinedRoom: JoinedRoom): RoomScheduleBadgePresenter
    }
}

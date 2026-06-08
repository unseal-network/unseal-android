/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.roomschedules.impl.edit

import android.os.Parcelable
import io.element.android.libraries.chatbot.api.model.schedules.ChatbotSchedule
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

sealed interface ScheduleEditMode : Parcelable {
    @Parcelize
    data object Create : ScheduleEditMode

    @Parcelize
    data class Edit(val schedule: @RawValue ChatbotSchedule) : ScheduleEditMode
}

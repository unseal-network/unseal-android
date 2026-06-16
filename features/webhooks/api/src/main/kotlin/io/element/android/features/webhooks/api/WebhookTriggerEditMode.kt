/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.webhooks.api

import android.os.Parcelable
import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotWebhookTrigger
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

sealed interface WebhookTriggerEditMode : Parcelable {
    @Parcelize
    data class Create(val prefilledRoomId: RoomId? = null) : WebhookTriggerEditMode

    @Parcelize
    data class Edit(val trigger: @RawValue ChatbotWebhookTrigger) : WebhookTriggerEditMode
}

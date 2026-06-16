/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.webhooks.impl.list

import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotWebhookTrigger
import io.element.android.libraries.matrix.api.core.RoomId

interface WebhookTriggerListNavigator {
    fun onCreateTrigger(prefilledRoomId: RoomId?)
    fun onEditTrigger(trigger: ChatbotWebhookTrigger)
    fun onDone()
    fun onTriggersChanged()
}

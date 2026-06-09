/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.connectors.impl.manage

import io.element.android.libraries.chatbot.api.model.connectors.ChatbotConnectedAccount
import kotlinx.collections.immutable.ImmutableList

data class ConnectorManageState(
    val toolkitName: String,
    val accounts: ImmutableList<ChatbotConnectedAccount>,
    val isLoading: Boolean,
    val connecting: Boolean,
    val disconnectingId: String?,
    val confirmingDisconnectId: String?,
    val error: String?,
    val eventSink: (ConnectorManageEvents) -> Unit,
)

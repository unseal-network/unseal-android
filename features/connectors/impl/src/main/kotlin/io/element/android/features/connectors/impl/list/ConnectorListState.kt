/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.connectors.impl.list

import io.element.android.libraries.chatbot.api.model.connectors.ChatbotToolkit
import io.element.android.libraries.chatbot.api.model.connectors.ChatbotToolkitCategory
import kotlinx.collections.immutable.ImmutableList

data class ConnectorListState(
    val toolkits: ImmutableList<ChatbotToolkit>,
    val categories: ImmutableList<ChatbotToolkitCategory>,
    // Mirror iOS `selectedCategoryID`: empty string means the "All" chip is selected.
    val selectedCategoryId: String,
    val searchQuery: String,
    val isLoading: Boolean,
    val isLoadingMore: Boolean,
    val hasMore: Boolean,
    val connectingSlug: String?,
    val error: String?,
    val successMessage: String?,
    val eventSink: (ConnectorListEvents) -> Unit,
)

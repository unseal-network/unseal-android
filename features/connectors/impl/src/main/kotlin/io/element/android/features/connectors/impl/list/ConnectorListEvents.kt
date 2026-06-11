/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.connectors.impl.list

import io.element.android.libraries.chatbot.api.model.connectors.ChatbotToolkit

sealed interface ConnectorListEvents {
    data object OnAppear : ConnectorListEvents
    data object Refresh : ConnectorListEvents
    data class SearchChanged(val query: String) : ConnectorListEvents

    /** Mirror iOS `.selectCategory`: empty [categoryId] selects the "All" chip. */
    data class SelectCategory(val categoryId: String) : ConnectorListEvents
    data object LoadMore : ConnectorListEvents
    data class Connect(val toolkit: ChatbotToolkit) : ConnectorListEvents
    data class Manage(val toolkit: ChatbotToolkit) : ConnectorListEvents
    data object ClearError : ConnectorListEvents
    data object Dismiss : ConnectorListEvents
}

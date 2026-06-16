/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.connectors.impl.manage

sealed interface ConnectorManageEvents {
    data object OnAppear : ConnectorManageEvents
    data object Refresh : ConnectorManageEvents
    data class ConfirmDisconnect(val accountId: String) : ConnectorManageEvents
    data object CancelDisconnect : ConnectorManageEvents
    data class Disconnect(val accountId: String) : ConnectorManageEvents
    data object Connect : ConnectorManageEvents
    data object ClearError : ConnectorManageEvents
    data object Dismiss : ConnectorManageEvents
}

/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.connectors.impl.list

interface ConnectorListNavigator {
    fun onManageToolkit(toolkitSlug: String, toolkitName: String)
    fun onOpenConnectUrl(url: String)
    fun onDone()
}

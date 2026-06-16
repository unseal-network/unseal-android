/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.vault.edit

sealed interface VaultEditEvents {
    data object OnAppear : VaultEditEvents
    data class KeyChanged(val value: String) : VaultEditEvents
    data class ValueChanged(val value: String) : VaultEditEvents
    data class DescriptionChanged(val value: String) : VaultEditEvents
    data object ToggleValueVisibility : VaultEditEvents
    data object Save : VaultEditEvents
    data object ClearError : VaultEditEvents
}

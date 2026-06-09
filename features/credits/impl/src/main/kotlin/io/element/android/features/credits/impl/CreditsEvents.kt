/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.credits.impl

import io.element.android.features.credits.api.CreditsEntryPoint

sealed interface CreditsEvents {
    data object OnAppear : CreditsEvents
    data class SelectTab(val tab: CreditsEntryPoint.CreditsTab) : CreditsEvents
    data object Dismiss : CreditsEvents
    data object RequestTopUp : CreditsEvents
}

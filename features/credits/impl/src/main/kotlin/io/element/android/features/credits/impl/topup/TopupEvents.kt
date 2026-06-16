/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.credits.impl.topup

sealed interface TopupEvents {
    data object OnAppear : TopupEvents
    data class SelectPreset(val cents: Int) : TopupEvents
    data class SelectCustomAmount(val amount: String) : TopupEvents
    data object Pay : TopupEvents
    data object PaymentSheetCompleted : TopupEvents
    data object PaymentSheetCanceled : TopupEvents
    data class PaymentSheetFailed(val reason: String) : TopupEvents
    data object ClearError : TopupEvents
    data object Dismiss : TopupEvents
}

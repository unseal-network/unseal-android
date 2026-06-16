/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.credits.impl.topup

import io.element.android.features.credits.impl.model.prefixedDollar
import io.element.android.libraries.chatbot.api.model.credits.CreditBalance
import io.element.android.libraries.chatbot.api.model.credits.CreditPaymentIntentResponse
import java.math.BigDecimal
import java.math.RoundingMode

data class TopupPresetAmount(
    val cents: Int,
    val isPopular: Boolean,
) {
    val dollars: String = "$${cents / 100}"
    val creditsLabel: String = "+$dollars credits"

    companion object {
        val presets = listOf(
            TopupPresetAmount(cents = 1_000, isPopular = false),
            TopupPresetAmount(cents = 2_000, isPopular = false),
            TopupPresetAmount(cents = 5_000, isPopular = true),
            TopupPresetAmount(cents = 10_000, isPopular = false),
            TopupPresetAmount(cents = 20_000, isPopular = false),
            TopupPresetAmount(cents = 30_000, isPopular = false),
        )
    }
}

sealed interface TopupSelection {
    data class Preset(val cents: Int) : TopupSelection
    data object Custom : TopupSelection
}

sealed interface TopupPhase {
    data object Selecting : TopupPhase
    data object Processing : TopupPhase
    data class AwaitingPaymentSheet(
        val amountCents: Int,
        val paymentIntentId: String?,
        val params: CreditPaymentIntentResponse,
    ) : TopupPhase
    data class Confirming(val amountCents: Int) : TopupPhase
    data class Success(val amountCents: Int) : TopupPhase
    data class Failed(val reason: String) : TopupPhase
}

data class TopupState(
    val balance: CreditBalance?,
    val phase: TopupPhase,
    val selection: TopupSelection,
    val customAmount: String,
    val isBalanceLoading: Boolean,
    val error: String?,
    val eventSink: (TopupEvents) -> Unit,
) {
    val effectiveAmountCents: Int? = when (selection) {
        is TopupSelection.Preset -> selection.cents
        TopupSelection.Custom -> customAmount.toAmountCents()
    }

    val canPay: Boolean = phase == TopupPhase.Selecting && effectiveAmountCents != null

    val customAmountInvalid: Boolean =
        selection == TopupSelection.Custom && customAmount.isNotBlank() && customAmount.toAmountCents() == null

    val payButtonLabel: String = effectiveAmountCents
        ?.let { "Pay ${formatCents(it)} with Stripe" }
        ?: "Pay with Stripe"

    val formattedBalance: String = balance?.balanceUsd.orEmpty().prefixedDollar()

    companion object {
        fun formatCents(cents: Int): String = "$${BigDecimal(cents).movePointLeft(2).setScale(2, RoundingMode.HALF_UP)}"
    }
}

private fun String.toAmountCents(): Int? {
    val decimal = runCatching { BigDecimal(trim()) }.getOrNull() ?: return null
    val cents = decimal.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toInt()
    return cents.takeIf { it in 100..50_000 }
}

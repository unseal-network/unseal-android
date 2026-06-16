/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbot.api.model.credits

import io.element.android.libraries.chatbot.api.model.json.ChatbotJsonMap
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreditBalance(
    @SerialName("user_id")
    val userId: String,
    @SerialName("balance_micros")
    val balanceMicros: String,
    @SerialName("balance_usd")
    val balanceUsd: String,
)

@Serializable
data class CreditLedgerItem(
    val id: String,
    @SerialName("delta_micros")
    val deltaMicros: String,
    @SerialName("balance_after_micros")
    val balanceAfterMicros: String,
    val source: String,
    val category: String,
    val title: String,
    val description: String? = null,
    @SerialName("related_table")
    val relatedTable: String? = null,
    @SerialName("related_id")
    val relatedId: String? = null,
    val metadata: ChatbotJsonMap? = null,
    val ts: String,
)

@Serializable
data class CreditLedgerResponse(
    val items: List<CreditLedgerItem> = emptyList(),
    val nextCursor: String? = null,
)

@Serializable
data class CreditDailyBucket(
    val start: Int,
    val usageMicros: String,
)

@Serializable
data class CreditDailyUsageResponse(
    val start: Int,
    val end: Int,
    val daily: List<CreditDailyBucket> = emptyList(),
    val totalUsageMicros: String,
)

@Serializable
data class CreditPaymentIntentRequest(
    val amountCents: Int,
)

@Serializable
data class CreditPaymentIntentResponse(
    val paymentIntentClientSecret: String,
    val ephemeralKeySecret: String,
    val customerId: String,
    val publishableKey: String? = null,
)

@Serializable
data class CreditPaymentIntentStatusResponse(
    val status: String,
    val amountCents: Int,
    val ledgerSettled: Boolean,
)

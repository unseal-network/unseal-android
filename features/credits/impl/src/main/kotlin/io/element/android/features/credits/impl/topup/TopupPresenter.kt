/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.credits.impl.topup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.chatbot.api.ChatbotApiService
import io.element.android.libraries.chatbot.api.ChatbotApiServiceFactory
import io.element.android.libraries.chatbot.api.model.credits.CreditBalance
import io.element.android.libraries.matrix.api.MatrixClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

interface TopupNavigator {
    fun onCompleted()
    fun onCancel()
}

@AssistedInject
class TopupPresenter(
    @Assisted private val initialBalance: CreditBalance?,
    @Assisted private val navigator: TopupNavigator,
    private val matrixClient: MatrixClient,
    private val chatbotApiServiceFactory: ChatbotApiServiceFactory,
) : Presenter<TopupState> {
    @AssistedFactory
    interface Factory {
        fun create(
            initialBalance: CreditBalance?,
            navigator: TopupNavigator,
        ): TopupPresenter
    }

    @Composable
    override fun present(): TopupState {
        val coroutineScope = rememberCoroutineScope()
        var hasAppeared by remember { mutableStateOf(false) }
        var balance by remember { mutableStateOf(initialBalance) }
        var phase by remember { mutableStateOf<TopupPhase>(TopupPhase.Selecting) }
        var selection by remember { mutableStateOf<TopupSelection>(TopupSelection.Preset(TopupPresetAmount.presets.first().cents)) }
        var customAmount by remember { mutableStateOf("") }
        var isBalanceLoading by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }

        fun errorMessage(throwable: Throwable): String {
            return throwable.message ?: throwable::class.simpleName ?: throwable.toString()
        }

        suspend fun api(): ChatbotApiService = chatbotApiServiceFactory.createForUnsealApi(matrixClient)

        fun loadBalance() = coroutineScope.launch {
            if (balance != null || isBalanceLoading) return@launch
            isBalanceLoading = true
            api().getBalance()
                .onSuccess {
                    balance = it
                    error = null
                }
                .onFailure {
                    error = errorMessage(it)
                }
            isBalanceLoading = false
        }

        suspend fun confirmPayment(paymentIntentId: String?, amountCents: Int) {
            if (paymentIntentId == null) {
                phase = TopupPhase.Success(amountCents)
                return
            }
            phase = TopupPhase.Confirming(amountCents)
            repeat(MAX_STATUS_POLL_ATTEMPTS) { attempt ->
                val shouldContinue = api().getPaymentIntentStatus(paymentIntentId)
                    .fold(
                        onSuccess = { status ->
                            if (status.ledgerSettled || status.status != "succeeded") {
                                phase = TopupPhase.Success(amountCents)
                                false
                            } else {
                                true
                            }
                        },
                        onFailure = {
                            error = errorMessage(it)
                            true
                        },
                    )
                if (!shouldContinue) return
                if (attempt < MAX_STATUS_POLL_ATTEMPTS - 1) {
                    delay(STATUS_POLL_DELAY_MILLIS)
                }
            }
            phase = TopupPhase.Success(amountCents)
        }

        fun startPayment(amountCents: Int) = coroutineScope.launch {
            phase = TopupPhase.Processing
            api().createPaymentIntent(amountCents)
                .onSuccess { params ->
                    val paymentIntentId = extractPaymentIntentId(params.paymentIntentClientSecret)
                    if (paymentIntentId == null) {
                        phase = TopupPhase.Success(amountCents)
                    } else {
                        phase = TopupPhase.AwaitingPaymentSheet(
                            amountCents = amountCents,
                            paymentIntentId = paymentIntentId,
                            params = params,
                        )
                    }
                    error = null
                }
                .onFailure {
                    phase = TopupPhase.Failed(errorMessage(it))
                    error = errorMessage(it)
                }
        }

        fun handleEvent(event: TopupEvents) {
            when (event) {
                TopupEvents.OnAppear -> if (!hasAppeared) {
                    hasAppeared = true
                    loadBalance()
                }
                is TopupEvents.SelectPreset -> {
                    selection = TopupSelection.Preset(event.cents)
                    customAmount = ""
                    phase = TopupPhase.Selecting
                }
                is TopupEvents.SelectCustomAmount -> {
                    selection = TopupSelection.Custom
                    customAmount = event.amount
                    phase = TopupPhase.Selecting
                }
                TopupEvents.Pay -> {
                    val amountCents = TopupState(
                        balance = balance,
                        phase = phase,
                        selection = selection,
                        customAmount = customAmount,
                        isBalanceLoading = isBalanceLoading,
                        error = error,
                        eventSink = {},
                    ).effectiveAmountCents ?: return
                    startPayment(amountCents)
                }
                TopupEvents.PaymentSheetCompleted -> {
                    val awaiting = phase as? TopupPhase.AwaitingPaymentSheet ?: return
                    coroutineScope.launch {
                        confirmPayment(awaiting.paymentIntentId, awaiting.amountCents)
                    }
                }
                TopupEvents.PaymentSheetCanceled -> phase = TopupPhase.Selecting
                is TopupEvents.PaymentSheetFailed -> {
                    phase = TopupPhase.Failed(event.reason)
                    error = event.reason
                }
                TopupEvents.ClearError -> error = null
                TopupEvents.Dismiss -> when (phase) {
                    is TopupPhase.Success -> navigator.onCompleted()
                    else -> navigator.onCancel()
                }
            }
        }

        return TopupState(
            balance = balance,
            phase = phase,
            selection = selection,
            customAmount = customAmount,
            isBalanceLoading = isBalanceLoading,
            error = error,
            eventSink = ::handleEvent,
        )
    }

    companion object {
        private const val MAX_STATUS_POLL_ATTEMPTS = 10
        private const val STATUS_POLL_DELAY_MILLIS = 1_000L

        fun extractPaymentIntentId(clientSecret: String): String? {
            val id = clientSecret.substringBefore("_secret_", missingDelimiterValue = "")
            return id.takeIf { it.startsWith("pi_") }
        }
    }
}

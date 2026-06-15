/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.credits.impl.topup

import app.cash.turbine.TurbineTestContext
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.chatbot.api.model.credits.CreditPaymentIntentResponse
import io.element.android.libraries.chatbot.api.model.credits.CreditPaymentIntentStatusResponse
import io.element.android.libraries.chatbot.test.FakeChatbotApiService
import io.element.android.libraries.chatbot.test.FakeChatbotApiServiceFactory
import io.element.android.libraries.chatbot.test.aCreditBalance
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class TopupPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `present - on appear loads balance when initial balance is missing`() = runTest {
        val service = FakeChatbotApiService().apply {
            getBalanceResult = { Result.success(aCreditBalance(balanceMicros = "3400000").copy(balanceUsd = "3.40")) }
        }
        val presenter = createPresenter(service = service, initialBalance = null)

        presenter.test {
            awaitItem().eventSink(TopupEvents.OnAppear)
            val loaded = awaitStateWhere { it.balance?.balanceUsd == "3.40" && !it.isBalanceLoading }
            assertThat(loaded.formattedBalance).isEqualTo("$3.40")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - custom amount validates iOS one to five hundred dollar bounds`() = runTest {
        val presenter = createPresenter()

        presenter.test {
            val initial = awaitItem()
            initial.eventSink(TopupEvents.SelectCustomAmount("0.50"))
            val invalid = awaitStateWhere { it.customAmount == "0.50" }
            assertThat(invalid.effectiveAmountCents).isNull()
            assertThat(invalid.customAmountInvalid).isTrue()

            invalid.eventSink(TopupEvents.SelectCustomAmount("12.34"))
            val valid = awaitStateWhere { it.customAmount == "12.34" && it.effectiveAmountCents == 1234 }
            assertThat(valid.canPay).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - pay creates payment intent and waits for payment sheet`() = runTest {
        val requestedAmounts = mutableListOf<Int>()
        val service = FakeChatbotApiService().apply {
            createPaymentIntentResult = { amount ->
                requestedAmounts += amount
                Result.success(paymentIntentResponse())
            }
        }
        val presenter = createPresenter(service = service)

        presenter.test {
            val initial = awaitItem()
            initial.eventSink(TopupEvents.SelectPreset(5_000))
            val selected = awaitStateWhere { it.selection == TopupSelection.Preset(5_000) }
            selected.eventSink(TopupEvents.Pay)

            val awaiting = awaitStateWhere { it.phase is TopupPhase.AwaitingPaymentSheet }
            val phase = awaiting.phase as TopupPhase.AwaitingPaymentSheet
            assertThat(phase.amountCents).isEqualTo(5_000)
            assertThat(phase.paymentIntentId).isEqualTo("pi_test")
            assertThat(requestedAmounts).containsExactly(5_000)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - payment completed polls status until ledger is settled`() = runTest {
        var statusCalls = 0
        val service = FakeChatbotApiService().apply {
            createPaymentIntentResult = { Result.success(paymentIntentResponse()) }
            getPaymentIntentStatusResult = {
                statusCalls++
                Result.success(
                    CreditPaymentIntentStatusResponse(
                        status = "succeeded",
                        amountCents = 1_000,
                        ledgerSettled = statusCalls >= 2,
                    ),
                )
            }
        }
        val presenter = createPresenter(service = service)

        presenter.test {
            awaitItem().eventSink(TopupEvents.Pay)
            val awaiting = awaitStateWhere { it.phase is TopupPhase.AwaitingPaymentSheet }
            awaiting.eventSink(TopupEvents.PaymentSheetCompleted)
            awaitStateWhere { it.phase is TopupPhase.Confirming }
            advanceTimeBy(1_000)
            val success = awaitStateWhere { it.phase is TopupPhase.Success }
            assertThat((success.phase as TopupPhase.Success).amountCents).isEqualTo(1_000)
            assertThat(statusCalls).isEqualTo(2)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - dismiss dispatches completed only after success`() = runTest {
        var completed = 0
        var canceled = 0
        val service = FakeChatbotApiService().apply {
            createPaymentIntentResult = { Result.success(paymentIntentResponse(paymentIntentClientSecret = "bad_secret")) }
        }
        val presenter = createPresenter(
            service = service,
            navigator = object : TopupNavigator {
                override fun onCompleted() {
                    completed++
                }

                override fun onCancel() {
                    canceled++
                }
            },
        )

        presenter.test {
            val initial = awaitItem()
            initial.eventSink(TopupEvents.Dismiss)
            assertThat(canceled).isEqualTo(1)

            initial.eventSink(TopupEvents.Pay)
            val success = awaitStateWhere { it.phase is TopupPhase.Success }
            success.eventSink(TopupEvents.Dismiss)
            assertThat(completed).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `extractPaymentIntentId mirrors iOS client secret parsing`() {
        assertThat(TopupPresenter.extractPaymentIntentId("pi_123_secret_abc")).isEqualTo("pi_123")
        assertThat(TopupPresenter.extractPaymentIntentId("seti_123_secret_abc")).isNull()
        assertThat(TopupPresenter.extractPaymentIntentId("pi_123")).isNull()
    }

    private fun createPresenter(
        service: FakeChatbotApiService = FakeChatbotApiService(),
        initialBalance: io.element.android.libraries.chatbot.api.model.credits.CreditBalance? = aCreditBalance(),
        navigator: TopupNavigator = object : TopupNavigator {
            override fun onCompleted() = Unit
            override fun onCancel() = Unit
        },
    ): TopupPresenter {
        val factory = FakeChatbotApiServiceFactory(service)
        return TopupPresenter(
            initialBalance = initialBalance,
            navigator = navigator,
            matrixClient = FakeMatrixClient(),
            chatbotApiServiceFactory = factory,
        )
    }

    private fun paymentIntentResponse(
        paymentIntentClientSecret: String = "pi_test_secret_abc",
    ) = CreditPaymentIntentResponse(
        paymentIntentClientSecret = paymentIntentClientSecret,
        ephemeralKeySecret = "ek_test",
        customerId = "cus_test",
        publishableKey = "pk_test",
    )
}

private suspend fun TurbineTestContext<TopupState>.awaitStateWhere(
    predicate: (TopupState) -> Boolean,
): TopupState {
    while (true) {
        val state = awaitItem()
        if (predicate(state)) {
            return state
        }
    }
}

/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package io.element.android.features.credits.impl.topup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.stripe.android.PaymentConfiguration
import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.PaymentSheetResult
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.credits.impl.R
import io.element.android.libraries.chatbot.api.model.credits.CreditBalance
import io.element.android.libraries.chatbot.api.model.credits.CreditPaymentIntentResponse
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
fun TopupView(
    state: TopupState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val latestEventSink by rememberUpdatedState(state.eventSink)
    val paymentSheet = PaymentSheet.Builder { result ->
        when (result) {
            is PaymentSheetResult.Canceled -> latestEventSink(TopupEvents.PaymentSheetCanceled)
            is PaymentSheetResult.Completed -> latestEventSink(TopupEvents.PaymentSheetCompleted)
            is PaymentSheetResult.Failed -> latestEventSink(
                TopupEvents.PaymentSheetFailed(result.error.localizedMessage ?: result.error.message ?: result.error.toString()),
            )
        }
    }.build()

    LaunchedEffect(Unit) {
        state.eventSink(TopupEvents.OnAppear)
    }

    LaunchedEffect(state.phase) {
        val awaiting = state.phase as? TopupPhase.AwaitingPaymentSheet ?: return@LaunchedEffect
        val params = awaiting.params
        val publishableKey = params.publishableKey.orEmpty()
        if (publishableKey.isBlank()) {
            state.eventSink(TopupEvents.PaymentSheetFailed(context.getString(R.string.topup_error_missing_stripe_key)))
            return@LaunchedEffect
        }

        PaymentConfiguration.init(context, publishableKey)
        paymentSheet.presentWithPaymentIntent(
            paymentIntentClientSecret = params.paymentIntentClientSecret,
            configuration = PaymentSheet.Configuration.Builder(merchantDisplayName = "Unseal")
                .customer(
                    PaymentSheet.CustomerConfiguration(
                        id = params.customerId,
                        ephemeralKeySecret = params.ephemeralKeySecret,
                    ),
                )
                .build(),
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.topup_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { state.eventSink(TopupEvents.Dismiss) }) {
                        Icon(CompoundIcons.ChevronLeft(), contentDescription = stringResource(CommonStrings.action_go_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BalanceSummary(state)
            PresetGrid(state)
            CustomAmountField(state)
            PhaseStatus(state)
            PayButton(state)
        }
    }
}

@Composable
private fun BalanceSummary(state: TopupState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.topup_current_balance),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.isBalanceLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                } else {
                    Text(
                        text = state.formattedBalance.ifBlank { "$0.00" },
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetGrid(state: TopupState) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        maxItemsInEachRow = 2,
    ) {
        TopupPresetAmount.presets.forEach { preset ->
            val selected = state.selection == TopupSelection.Preset(preset.cents)
            Surface(
                modifier = Modifier.weight(1f),
                onClick = { state.eventSink(TopupEvents.SelectPreset(preset.cents)) },
                shape = RoundedCornerShape(16.dp),
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = preset.dollars,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        if (preset.isPopular) {
                            Text(
                                text = stringResource(R.string.topup_popular),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Text(
                        text = preset.creditsLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomAmountField(state: TopupState) {
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = state.customAmount,
        onValueChange = { state.eventSink(TopupEvents.SelectCustomAmount(it)) },
        label = { Text(stringResource(R.string.topup_custom_amount)) },
        placeholder = { Text(stringResource(R.string.topup_amount_placeholder)) },
        prefix = { Text("$") },
        isError = state.customAmountInvalid,
        supportingText = {
            if (state.customAmountInvalid) {
                Text(stringResource(R.string.topup_amount_range_error))
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
    )
}

@Composable
private fun PhaseStatus(state: TopupState) {
    state.error?.let {
        Text(
            text = it,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    when (val phase = state.phase) {
        TopupPhase.Selecting -> Unit
        TopupPhase.Processing -> InlineProgress(stringResource(R.string.topup_creating_payment_request))
        is TopupPhase.AwaitingPaymentSheet -> Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.topup_payment_request_created),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                InlineProgress(stringResource(R.string.topup_opening_stripe))
            }
        }
        is TopupPhase.Confirming -> InlineProgress(stringResource(R.string.topup_confirming, TopupState.formatCents(phase.amountCents)))
        is TopupPhase.Success -> Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Text(
                modifier = Modifier.padding(16.dp),
                text = stringResource(R.string.topup_success, TopupState.formatCents(phase.amountCents)),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        is TopupPhase.Failed -> Text(
            text = phase.reason,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun InlineProgress(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PayButton(state: TopupState) {
    Spacer(Modifier.height(4.dp))
    Button(
        modifier = Modifier.fillMaxWidth().height(52.dp),
        onClick = { state.eventSink(TopupEvents.Pay) },
        enabled = state.canPay,
    ) {
        Text(state.payButtonLabel)
    }
}

private class TopupStateProvider : PreviewParameterProvider<TopupState> {
    override val values: Sequence<TopupState>
        get() = sequenceOf(
            aTopupState(),
            aTopupState(phase = TopupPhase.Processing),
            aTopupState(
                phase = TopupPhase.AwaitingPaymentSheet(
                    amountCents = 5_000,
                    paymentIntentId = "pi_123",
                    params = CreditPaymentIntentResponse(
                        paymentIntentClientSecret = "pi_123_secret_abc",
                        ephemeralKeySecret = "ek_test",
                        customerId = "cus_test",
                    ),
                ),
            ),
            aTopupState(phase = TopupPhase.Success(5_000)),
        )
}

private fun aTopupState(
    phase: TopupPhase = TopupPhase.Selecting,
) = TopupState(
    balance = CreditBalance(
        userId = "@alice:unseal.network",
        balanceMicros = "12500000",
        balanceUsd = "12.50",
    ),
    phase = phase,
    selection = TopupSelection.Preset(5_000),
    customAmount = "",
    isBalanceLoading = false,
    error = null,
    eventSink = {},
)

@PreviewsDayNight
@Composable
internal fun TopupViewPreview(@PreviewParameter(TopupStateProvider::class) state: TopupState) = ElementPreview {
    TopupView(state = state)
}

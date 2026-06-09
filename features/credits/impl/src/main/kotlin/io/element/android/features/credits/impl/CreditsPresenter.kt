/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.credits.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.features.credits.api.CreditsEntryPoint
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.chatbot.api.model.credits.CreditBalance

interface CreditsNavigator {
    fun onDone()
    fun onTopUpRequested(balance: CreditBalance?)
}

@AssistedInject
class CreditsPresenter(
    @Assisted private val initialTab: CreditsEntryPoint.CreditsTab,
    @Assisted private val navigator: CreditsNavigator,
) : Presenter<CreditsState> {
    @AssistedFactory
    interface Factory {
        fun create(
            initialTab: CreditsEntryPoint.CreditsTab,
            navigator: CreditsNavigator,
        ): CreditsPresenter
    }

    @Composable
    override fun present(): CreditsState {
        var selectedTab by remember { mutableStateOf(initialTab) }
        val balance by remember { mutableStateOf<CreditBalance?>(null) }

        fun handleEvent(event: CreditsEvents) {
            when (event) {
                CreditsEvents.OnAppear -> Unit
                is CreditsEvents.SelectTab -> selectedTab = event.tab
                CreditsEvents.Dismiss -> navigator.onDone()
                CreditsEvents.RequestTopUp -> navigator.onTopUpRequested(balance)
            }
        }

        return CreditsState(
            selectedTab = selectedTab,
            balance = balance,
            eventSink = ::handleEvent,
        )
    }
}

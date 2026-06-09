/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.credits.impl

import io.element.android.features.credits.api.CreditsEntryPoint
import io.element.android.libraries.chatbot.api.model.credits.CreditBalance

data class CreditsState(
    val selectedTab: CreditsEntryPoint.CreditsTab,
    val balance: CreditBalance?,
    val eventSink: (CreditsEvents) -> Unit,
)

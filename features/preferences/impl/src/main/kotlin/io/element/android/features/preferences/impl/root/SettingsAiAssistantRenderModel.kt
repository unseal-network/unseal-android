/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.root

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class SettingsAiAssistantRenderModel(
    val creditBalanceLoadState: CreditBalanceLoadState,
    val entries: ImmutableList<SettingsAiAssistantEntry>,
) {
    companion object {
        fun from(
            creditBalanceLoadState: CreditBalanceLoadState,
        ): SettingsAiAssistantRenderModel {
            return SettingsAiAssistantRenderModel(
                creditBalanceLoadState = creditBalanceLoadState,
                entries = iOSOrderedEntries,
            )
        }

        val iOSOrderedEntries = persistentListOf(
            SettingsAiAssistantEntry.AgentManagement,
            SettingsAiAssistantEntry.VoiceLibrary,
            SettingsAiAssistantEntry.SkillsManagement,
            SettingsAiAssistantEntry.VaultManagement,
            SettingsAiAssistantEntry.Connectors,
            SettingsAiAssistantEntry.WebhookTriggers,
        )
    }
}

enum class SettingsAiAssistantEntry {
    AgentManagement,
    VoiceLibrary,
    SkillsManagement,
    VaultManagement,
    Connectors,
    WebhookTriggers,
}

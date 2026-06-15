/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.root

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SettingsAiAssistantRenderModelTest {
    @Test
    fun `from - entries follow iOS Settings AI section order`() {
        val model = SettingsAiAssistantRenderModel.from(CreditBalanceLoadState.Loaded("12.50"))

        assertThat(model.entries).containsExactly(
            SettingsAiAssistantEntry.AgentManagement,
            SettingsAiAssistantEntry.VoiceLibrary,
            SettingsAiAssistantEntry.SkillsManagement,
            SettingsAiAssistantEntry.VaultManagement,
            SettingsAiAssistantEntry.Connectors,
            SettingsAiAssistantEntry.WebhookTriggers,
        ).inOrder()
        assertThat(model.creditBalanceLoadState).isEqualTo(CreditBalanceLoadState.Loaded("12.50"))
    }
}

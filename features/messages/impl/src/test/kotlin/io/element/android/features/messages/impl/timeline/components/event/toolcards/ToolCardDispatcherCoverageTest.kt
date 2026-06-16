/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event.toolcards

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ToolCardDispatcherCoverageTest {
    @Test
    fun `all root dispatch card types have dispatcher coverage`() {
        val covered = TOOL_CARD_DISPATCHER_CARD_TYPES

        assertThat(covered).containsAtLeastElementsIn(ROOT_DISPATCH_CARD_TYPES)
    }

    @Test
    fun `suspended cards have display only coverage`() {
        assertThat(TOOL_CARD_DISPATCHER_CARD_TYPES).containsAtLeastElementsIn(STANDALONE_SUSPENDED_CARD_TYPES)
    }
}

/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.roomdata

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RoomUnsealRefreshReasonTest {
    @Test
    fun `initial refresh is the only non-forced room context refresh reason`() {
        val nonForcedReasons = RoomUnsealRefreshReason.entries.filterNot { it.force }

        assertThat(nonForcedReasons).containsExactly(RoomUnsealRefreshReason.Initial)
    }

    @Test
    fun `room data mutation reasons force a context refresh`() {
        assertThat(RoomUnsealRefreshReason.ScheduleChanged.force).isTrue()
        assertThat(RoomUnsealRefreshReason.WebhookChanged.force).isTrue()
        assertThat(RoomUnsealRefreshReason.SkillCatalogChanged.force).isTrue()
        assertThat(RoomUnsealRefreshReason.MembersChanged.force).isTrue()
        assertThat(RoomUnsealRefreshReason.AppResumed.force).isTrue()
        assertThat(RoomUnsealRefreshReason.RoomConfigChanged.force).isTrue()
        assertThat(RoomUnsealRefreshReason.ComposerMentionStarted.force).isTrue()
        assertThat(RoomUnsealRefreshReason.Manual.force).isTrue()
    }
}

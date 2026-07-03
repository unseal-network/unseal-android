/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.cache

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BoundedTimelineCacheTest {
    @Test
    fun `cache evicts least recently used entries once full`() {
        val cache = BoundedTimelineCache<String, Int>(maxEntries = 2)

        cache["first"] = 1
        cache["second"] = 2
        assertThat(cache["first"]).isEqualTo(1)

        cache["third"] = 3

        assertThat(cache["first"]).isEqualTo(1)
        assertThat(cache["second"]).isNull()
        assertThat(cache["third"]).isEqualTo(3)
        assertThat(cache.size).isEqualTo(2)
    }
}

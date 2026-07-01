/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.location.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.features.location.api.BuildConfig
import org.junit.Test

class DefaultLocationServiceTest {
    @Test
    fun `canShareLocation should require BuildConfig MAPTILER_API_KEY`() {
        val locationService = DefaultLocationService()
        assertThat(locationService.canShareLocation()).isEqualTo(BuildConfig.MAPTILER_API_KEY.isNotEmpty())
    }

    @Test
    fun `canRenderMaps should return value depending on BuildConfig MAPTILER_API_KEY`() {
        val locationService = DefaultLocationService()
        assertThat(locationService.canRenderMaps()).isEqualTo(BuildConfig.MAPTILER_API_KEY.isNotEmpty())
        assertThat(locationService.isServiceAvailable()).isEqualTo(locationService.canRenderMaps())
    }
}

/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.appnav.root

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme

/**
 * Native port of the iOS Unseal splash animation (UnsealSplashAnimationView /
 * UnsealLogoPathProvider): the logo is drawn progressively like a signature (arc → upper lock →
 * diagonal → lower lock), then pulses once. The geometry lives in [UnsealLogoMark] (shared with the
 * post-login welcome flow); here it is centered on a full-screen bgCanvasDefault background with the
 * splash pulse flourish enabled.
 */
@Composable
fun UnsealSplashView(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ElementTheme.colors.bgCanvasDefault),
        contentAlignment = Alignment.Center,
    ) {
        UnsealLogoMark(
            modifier = Modifier.size(200.dp),
            animated = true,
            pulse = true,
        )
    }
}

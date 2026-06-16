/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.appnav.root

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Native port of the iOS Unseal splash animation (UnsealSplashAnimationView /
 * UnsealLogoPathProvider): the logo is drawn progressively like a signature (arc → upper lock →
 * diagonal → lower lock), then pulses once. Geometry is the original 480x480 SVG, stroke color is
 * [ElementTheme]'s textPrimary on a bgCanvasDefault background, matching iOS Compound tokens.
 */
private const val LOGO_CANVAS = 480f
private const val LOGO_STROKE = 5f
private const val STROKE_MS = 1000

@Composable
fun UnsealSplashView(modifier: Modifier = Modifier) {
    val arc = remember { Animatable(0f) }
    val upperLock = remember { Animatable(0f) }
    val diagonal = remember { Animatable(0f) }
    val lowerLock = remember { Animatable(0f) }
    val pulse = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        launch { arc.animateTo(1f, tween(STROKE_MS)) }
        launch { delay(500); upperLock.animateTo(1f, tween(STROKE_MS)) }
        launch { delay(800); diagonal.animateTo(1f, tween(STROKE_MS)) }
        launch { delay(1000); lowerLock.animateTo(1f, tween(STROKE_MS)) }
        launch {
            delay(2000)
            pulse.animateTo(1.08f, tween(250))
            pulse.animateTo(1f, tween(250))
        }
    }

    val strokeColor = ElementTheme.colors.textPrimary

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ElementTheme.colors.bgCanvasDefault),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(200.dp)) {
            val drawScale = size.minDimension / LOGO_CANVAS
            val stroke = Stroke(width = LOGO_STROKE * drawScale, cap = StrokeCap.Round, join = StrokeJoin.Round)
            scale(pulse.value, pivot = center) {
                drawAnimatedPath(arcPath1(drawScale), arc.value, strokeColor, stroke)
                drawAnimatedPath(arcPath2(drawScale), arc.value, strokeColor, stroke)
                drawAnimatedPath(upperLockPath(drawScale), upperLock.value, strokeColor, stroke)
                drawAnimatedPath(diagonalPath(drawScale), diagonal.value, strokeColor, stroke)
                drawAnimatedPath(lowerLockPath(drawScale), lowerLock.value, strokeColor, stroke)
            }
        }
    }
}

/** Draws [path] (a single contour) from its start up to [fraction] of its length. */
private fun DrawScope.drawAnimatedPath(
    path: Path,
    fraction: Float,
    color: Color,
    stroke: Stroke,
) {
    if (fraction <= 0f) return
    val measure = PathMeasure()
    measure.setPath(path, false)
    val length = measure.length
    if (length <= 0f) return
    val segment = Path()
    measure.getSegment(0f, length * fraction, segment, true)
    drawPath(segment, color = color, style = stroke)
}

// First arc segment (top-right to top).
private fun arcPath1(s: Float): Path = Path().apply {
    moveTo(383.309f * s, 299.088f * s)
    cubicTo(390.844f * s, 280.878f * s, 395f * s, 260.923f * s, 395f * s, 240f * s)
    cubicTo(395f * s, 154.396f * s, 325.425f * s, 85f * s, 239.599f * s, 85f * s)
    cubicTo(205.229f * s, 85f * s, 173.465f * s, 96.129f * s, 147.732f * s, 114.971f * s)
}

// Second arc segment (bottom-left).
private fun arcPath2(s: Float): Path = Path().apply {
    moveTo(261.922f * s, 393.413f * s)
    cubicTo(254.632f * s, 394.459f * s, 247.179f * s, 395f * s, 239.599f * s, 395f * s)
    cubicTo(159.136f * s, 395f * s, 92.9556f * s, 334.005f * s, 85f * s, 255.843f * s)
}

private fun upperLockPath(s: Float): Path = Path().apply {
    moveTo(166.354f * s, 164.213f * s)
    lineTo(166.354f * s, 202.749f * s)
    cubicTo(166.354f * s, 215.282f * s, 176.514f * s, 225.442f * s, 189.047f * s, 225.442f * s)
    cubicTo(201.58f * s, 225.442f * s, 211.74f * s, 215.282f * s, 211.74f * s, 202.749f * s)
    lineTo(211.74f * s, 164.213f * s)
}

private fun lowerLockPath(s: Float): Path = Path().apply {
    moveTo(276.823f * s, 263.122f * s)
    lineTo(276.823f * s, 300.31f * s)
    cubicTo(276.823f * s, 312.405f * s, 286.6f * s, 322.21f * s, 298.66f * s, 322.21f * s)
    cubicTo(310.72f * s, 322.21f * s, 320.497f * s, 312.405f * s, 320.497f * s, 300.31f * s)
    lineTo(320.497f * s, 263.122f * s)
}

private fun diagonalPath(s: Float): Path = Path().apply {
    moveTo(192.472f * s, 296.947f * s)
    lineTo(286.243f * s, 184.765f * s)
}

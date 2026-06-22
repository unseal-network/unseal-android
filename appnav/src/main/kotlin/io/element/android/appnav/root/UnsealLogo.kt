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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import io.element.android.compound.theme.ElementTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The Unseal brand mark, ported 1:1 from the iOS UnsealLogoPathProvider geometry (original 480x480
 * SVG). Drawn as progressive strokes like a signature (arc → upper lock → diagonal → lower lock).
 *
 * Shared by the launch splash ([UnsealSplashView]) and the post-login welcome flow so both render
 * the exact same logo. When [animated] is true the strokes draw on once; otherwise the fully drawn
 * mark is shown immediately. When [pulse] is true the mark scales up once after the draw completes
 * (the splash flourish); the welcome flow keeps it off.
 *
 * The brand geometry lives in [Path]s built once and cached; per-frame the draw only scales them
 * and slices each contour to its current stroke fraction, reusing a single [PathMeasure] and
 * segment [Path] so the animation (which runs on every cold start) allocates nothing per frame.
 */
private const val LOGO_CANVAS = 480f
private const val LOGO_STROKE = 5f
private const val STROKE_MS = 1000

@Composable
fun UnsealLogoMark(
    modifier: Modifier = Modifier,
    animated: Boolean = true,
    pulse: Boolean = false,
    strokeColor: Color = ElementTheme.colors.textPrimary,
) {
    val arc = remember { Animatable(if (animated) 0f else 1f) }
    val upperLock = remember { Animatable(if (animated) 0f else 1f) }
    val diagonal = remember { Animatable(if (animated) 0f else 1f) }
    val lowerLock = remember { Animatable(if (animated) 0f else 1f) }
    val pulseScale = remember { Animatable(1f) }

    if (animated) {
        LaunchedEffect(Unit) {
            launch { arc.animateTo(1f, tween(STROKE_MS)) }
            launch { delay(500); upperLock.animateTo(1f, tween(STROKE_MS)) }
            launch { delay(800); diagonal.animateTo(1f, tween(STROKE_MS)) }
            launch { delay(1000); lowerLock.animateTo(1f, tween(STROKE_MS)) }
            if (pulse) {
                launch {
                    delay(2000)
                    pulseScale.animateTo(1.08f, tween(250))
                    pulseScale.animateTo(1f, tween(250))
                }
            }
        }
    }

    // Base geometry in the original 480x480 space, built once. Scaling to the canvas happens in the
    // draw scope so the strokes never need to be rebuilt as the size or animation progresses.
    val arcPath1 = remember { arcPath1() }
    val arcPath2 = remember { arcPath2() }
    val upperLockPath = remember { upperLockPath() }
    val lowerLockPath = remember { lowerLockPath() }
    val diagonalPath = remember { diagonalPath() }
    val measure = remember { PathMeasure() }
    val segment = remember { Path() }

    Canvas(modifier = modifier) {
        val drawScale = size.minDimension / LOGO_CANVAS
        val stroke = Stroke(width = LOGO_STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round)
        // Pulse scales the whole mark around its center; drawScale maps the 480-space geometry into
        // the canvas from the origin (the logo fills its own 480 box).
        scale(pulseScale.value, pivot = center) {
            scale(drawScale, pivot = Offset.Zero) {
                drawLogoStroke(arcPath1, arc.value, strokeColor, stroke, measure, segment)
                drawLogoStroke(arcPath2, arc.value, strokeColor, stroke, measure, segment)
                drawLogoStroke(upperLockPath, upperLock.value, strokeColor, stroke, measure, segment)
                drawLogoStroke(diagonalPath, diagonal.value, strokeColor, stroke, measure, segment)
                drawLogoStroke(lowerLockPath, lowerLock.value, strokeColor, stroke, measure, segment)
            }
        }
    }
}

/**
 * Draws [path] from its start up to [fraction] of its length. For a fully drawn stroke
 * (fraction >= 1) it draws the path directly, skipping the [PathMeasure] slice entirely; the
 * supplied [measure]/[segment] are reused to avoid per-frame allocation while animating.
 */
private fun DrawScope.drawLogoStroke(
    path: Path,
    fraction: Float,
    color: Color,
    stroke: Stroke,
    measure: PathMeasure,
    segment: Path,
) {
    if (fraction <= 0f) return
    if (fraction >= 1f) {
        drawPath(path, color = color, style = stroke)
        return
    }
    measure.setPath(path, false)
    val length = measure.length
    if (length <= 0f) return
    segment.rewind()
    measure.getSegment(0f, length * fraction, segment, true)
    drawPath(segment, color = color, style = stroke)
}

// First arc segment (top-right to top).
private fun arcPath1(): Path = Path().apply {
    moveTo(383.309f, 299.088f)
    cubicTo(390.844f, 280.878f, 395f, 260.923f, 395f, 240f)
    cubicTo(395f, 154.396f, 325.425f, 85f, 239.599f, 85f)
    cubicTo(205.229f, 85f, 173.465f, 96.129f, 147.732f, 114.971f)
}

// Second arc segment (bottom-left).
private fun arcPath2(): Path = Path().apply {
    moveTo(261.922f, 393.413f)
    cubicTo(254.632f, 394.459f, 247.179f, 395f, 239.599f, 395f)
    cubicTo(159.136f, 395f, 92.9556f, 334.005f, 85f, 255.843f)
}

private fun upperLockPath(): Path = Path().apply {
    moveTo(166.354f, 164.213f)
    lineTo(166.354f, 202.749f)
    cubicTo(166.354f, 215.282f, 176.514f, 225.442f, 189.047f, 225.442f)
    cubicTo(201.58f, 225.442f, 211.74f, 215.282f, 211.74f, 202.749f)
    lineTo(211.74f, 164.213f)
}

private fun lowerLockPath(): Path = Path().apply {
    moveTo(276.823f, 263.122f)
    lineTo(276.823f, 300.31f)
    cubicTo(276.823f, 312.405f, 286.6f, 322.21f, 298.66f, 322.21f)
    cubicTo(310.72f, 322.21f, 320.497f, 312.405f, 320.497f, 300.31f)
    lineTo(320.497f, 263.122f)
}

private fun diagonalPath(): Path = Path().apply {
    moveTo(192.472f, 296.947f)
    lineTo(286.243f, 184.765f)
}

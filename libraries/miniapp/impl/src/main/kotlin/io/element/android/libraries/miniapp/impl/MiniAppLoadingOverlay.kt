/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.miniapp.impl

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.element.android.compound.theme.ElementTheme

// ── Theme ─────────────────────────────────────────────────────────────────────

enum class MiniAppLoadingTheme { Dark, Light }

private val MiniAppLoadingTheme.bgColor get() = if (this == MiniAppLoadingTheme.Dark) Color.Black else Color.White
private val MiniAppLoadingTheme.fgColor get() = if (this == MiniAppLoadingTheme.Dark) Color.White else Color.Black
private val MiniAppLoadingTheme.labelColor get() = if (this == MiniAppLoadingTheme.Dark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.45f)
private val MiniAppLoadingTheme.btnBgColor get() = if (this == MiniAppLoadingTheme.Dark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.08f)

// ── State ─────────────────────────────────────────────────────────────────────

sealed interface MiniAppLoadingState {
    /** [progress] in [0,1]; null = indeterminate (page loading phase). */
    data class Loading(val progress: Float? = null) : MiniAppLoadingState
    data class Error(val message: String, val onRetry: () -> Unit) : MiniAppLoadingState
}

// ── Public composable ─────────────────────────────────────────────────────────

/**
 * Full-screen loading / error overlay for MiniApp, mirroring iOS `LoadingOverlayView`.
 *
 * Loading state: app icon with pulse animation + dual spinning arcs + close button.
 * Error state:   static icon + error message + retry button + close button.
 */
@Composable
fun MiniAppLoadingOverlay(
    state: MiniAppLoadingState,
    onClose: () -> Unit,
    theme: MiniAppLoadingTheme = MiniAppLoadingTheme.Dark,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(theme.bgColor),
    ) {
        // Close button — top-right corner, always visible
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 12.dp, end = 16.dp)
                .size(32.dp)
                .clip(CircleShape)
                .background(theme.btnBgColor),
        ) {
            Icon(
                painter = painterResource(id = io.element.android.compound.R.drawable.ic_compound_close),
                contentDescription = stringResource(R.string.miniapp_action_close),
                tint = theme.fgColor,
                modifier = Modifier.size(16.dp),
            )
        }

        // Center content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.Center),
        ) {
            // Logo + spinner
            Box(contentAlignment = Alignment.Center) {
                // Dual spinning arcs (only during Loading)
                if (state is MiniAppLoadingState.Loading) {
                    SpinnerArcs(fgColor = theme.fgColor)
                }
                // App icon with pulse
                AppIconWithPulse(
                    isAnimating = state is MiniAppLoadingState.Loading,
                    fgColor = theme.fgColor,
                    theme = theme,
                )
            }

            Spacer(Modifier.height(24.dp))

            // Download progress percentage
            if (state is MiniAppLoadingState.Loading && state.progress != null) {
                Text(
                    text = "${(state.progress * 100).toInt()}%",
                    color = theme.labelColor,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
            }

            // Error message
            if (state is MiniAppLoadingState.Error) {
                Text(
                    text = state.message,
                    color = theme.labelColor,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .widthIn(max = 260.dp)
                        .padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(20.dp))
                RetryButton(
                    fgColor = theme.fgColor,
                    onClick = state.onRetry,
                )
            }
        }
    }
}

// ── Sub-composables ───────────────────────────────────────────────────────────

@Composable
private fun AppIconWithPulse(
    isAnimating: Boolean,
    fgColor: Color,
    theme: MiniAppLoadingTheme,
) {
    val scale: Float
    val alpha: Float

    if (isAnimating) {
        val transition = rememberInfiniteTransition(label = "icon_pulse")
        val animScale by transition.animateFloat(
            initialValue = 0.93f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                tween(2200, easing = FastOutSlowInEasing),
                RepeatMode.Reverse,
            ),
            label = "scale",
        )
        val animAlpha by transition.animateFloat(
            initialValue = 0.6f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                tween(2200, easing = FastOutSlowInEasing),
                RepeatMode.Reverse,
            ),
            label = "alpha",
        )
        scale = animScale
        alpha = animAlpha
    } else {
        scale = 1f
        alpha = 1f
    }

    Box(
        modifier = Modifier
            .size(88.dp)
            .scale(scale)
            .alpha(alpha),
        contentAlignment = Alignment.Center,
    ) {
        // App icon placeholder — shows the Unseal icon tinted to fg
        Icon(
            painter = painterResource(id = io.element.android.compound.R.drawable.ic_compound_chat_problem),
            contentDescription = null,
            tint = fgColor,
            modifier = Modifier.size(56.dp),
        )
    }
}

@Composable
private fun SpinnerArcs(fgColor: Color) {
    val transition = rememberInfiniteTransition(label = "spinner")

    val rotationInner by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 1100, easing = LinearEasing),
        ),
        label = "inner_arc",
    )
    val rotationOuter by transition.animateFloat(
        initialValue = 0f,
        targetValue = -360f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 1700, easing = LinearEasing),
        ),
        label = "outer_arc",
    )

    androidx.compose.foundation.Canvas(modifier = Modifier.size(200.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val innerRadius = 58.dp.toPx()
        val outerRadius = 76.dp.toPx()

        // Static guide circle
        drawCircle(
            color = fgColor.copy(alpha = 0.08f),
            radius = innerRadius,
            center = center,
            style = Stroke(width = 1.dp.toPx()),
        )

        // Inner arc — 90°, clockwise
        rotate(degrees = rotationInner, pivot = center) {
            drawArc(
                color = fgColor.copy(alpha = 0.92f),
                startAngle = -90f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(center.x - innerRadius, center.y - innerRadius),
                size = Size(innerRadius * 2, innerRadius * 2),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )
        }

        // Outer arc — 40°, counter-clockwise
        rotate(degrees = rotationOuter, pivot = center) {
            drawArc(
                color = fgColor.copy(alpha = 0.48f),
                startAngle = 0f,
                sweepAngle = 40f,
                useCenter = false,
                topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                size = Size(outerRadius * 2, outerRadius * 2),
                style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun RetryButton(fgColor: Color, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .height(36.dp)
            .widthIn(min = 96.dp)
            .border(
                width = 1.dp,
                color = fgColor.copy(alpha = 0.5f),
                shape = RoundedCornerShape(18.dp),
            ),
    ) {
        Text(
            text = stringResource(R.string.miniapp_action_retry),
            color = fgColor,
            fontSize = 14.sp,
        )
    }
}

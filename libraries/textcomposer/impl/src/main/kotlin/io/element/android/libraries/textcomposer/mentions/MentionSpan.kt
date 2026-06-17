/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.textcomposer.mentions

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.ReplacementSpan
import androidx.core.text.getSpans
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomIdOrAlias
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.wysiwyg.view.spans.CustomMentionSpan
import kotlin.math.roundToInt

/**
 * A span that represents a mention (user, room, etc.) in text.
 * @param type The type of mention this span represents.
 */
class MentionSpan(
    val type: MentionType,
) : ReplacementSpan() {
    private val backgroundPaint = Paint()
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

    private var backgroundColor: Int = 0
    private var textColor: Int = 0
    private var startPadding: Int = 0
    private var endPadding: Int = 0
    private var typeface: Typeface = Typeface.DEFAULT
    private var avatarBitmap: Bitmap? = null
    private var avatarSize: Int = 0
    private var avatarGap: Int = 0

    private var measuredTextWidth = 0

    // The formatted display text, will be set by the formatter
    var displayText: CharSequence = ""
        private set

    /**
     * Updates the visual properties of this span.
     */
    fun updateTheme(mentionSpanTheme: MentionSpanTheme) {
        val isCurrentUser = when (type) {
            is MentionType.User -> type.userId == mentionSpanTheme.currentUserId
            else -> false
        }

        backgroundColor = when (type) {
            is MentionType.User -> if (isCurrentUser) mentionSpanTheme.currentUserBackgroundColor else mentionSpanTheme.otherBackgroundColor
            is MentionType.Everyone -> mentionSpanTheme.otherBackgroundColor
            is MentionType.Room -> mentionSpanTheme.otherBackgroundColor
            is MentionType.Message -> mentionSpanTheme.otherBackgroundColor
        }

        textColor = when (type) {
            is MentionType.User -> if (isCurrentUser) mentionSpanTheme.currentUserTextColor else mentionSpanTheme.otherTextColor
            is MentionType.Everyone -> mentionSpanTheme.otherTextColor
            is MentionType.Room -> mentionSpanTheme.otherTextColor
            is MentionType.Message -> mentionSpanTheme.otherTextColor
        }

        val (startPaddingPx, endPaddingPx) = mentionSpanTheme.paddingValuesPx.value
        startPadding = startPaddingPx
        endPadding = endPaddingPx
        typeface = mentionSpanTheme.typeface.value
        if (type is MentionType.User) {
            avatarBitmap = mentionSpanTheme.defaultUserAvatarBitmap(type.userId)
            avatarSize = mentionSpanTheme.userMentionAvatarSizePx.value
            avatarGap = mentionSpanTheme.userMentionAvatarGapPx.value
        } else {
            avatarBitmap = null
            avatarSize = 0
            avatarGap = 0
        }
    }

    /**
     * Updates the display text using a formatter.
     */
    fun updateDisplayText(formatter: MentionSpanFormatter) {
        displayText = formatter.formatDisplayText(type)
    }

    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        textPaint.set(paint)
        textPaint.typeface = typeface
        // Measure the full text width without truncation
        measuredTextWidth = textPaint.measureText(displayText, 0, displayText.length).roundToInt()
        return if (type is MentionType.User) {
            resolvedAvatarSize(paint) + avatarGap + measuredTextWidth
        } else {
            measuredTextWidth + startPadding + endPadding
        }
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        val availableWidth = (canvas.width - x).coerceAtLeast(0f)
        if (type is MentionType.User) {
            drawUserMention(canvas, x, top, y, bottom, paint, availableWidth)
            return
        }

        val measuredWidth = measuredTextWidth + startPadding + endPadding
        val pillWidth = minOf(availableWidth, measuredWidth.toFloat())

        backgroundPaint.color = backgroundColor
        val rect = RectF(x, top.toFloat(), x + pillWidth, bottom.toFloat())
        val radius = rect.height() / 2
        canvas.drawRoundRect(rect, radius, radius, backgroundPaint)

        textPaint.set(paint)
        textPaint.color = textColor
        textPaint.typeface = typeface

        val availableWidthForText = availableWidth - startPadding - endPadding
        val textToDraw = if (measuredTextWidth > availableWidthForText) {
            TextUtils.ellipsize(
                displayText,
                textPaint,
                availableWidthForText,
                TextUtils.TruncateAt.END
            )
        } else {
            displayText
        }
        canvas.drawText(textToDraw, 0, textToDraw.length, x + startPadding, y.toFloat(), textPaint)
    }

    private fun drawUserMention(
        canvas: Canvas,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint,
        availableWidth: Float,
    ) {
        val lineHeight = bottom - top
        val resolvedAvatarSize = resolvedAvatarSize(paint, lineHeight)
        val avatarTop = top + (lineHeight - resolvedAvatarSize) / 2f
        val avatarRect = RectF(x, avatarTop, x + resolvedAvatarSize, avatarTop + resolvedAvatarSize)
        avatarBitmap?.let { bitmap ->
            val save = canvas.save()
            val path = Path().apply { addOval(avatarRect, Path.Direction.CW) }
            canvas.clipPath(path)
            canvas.drawBitmap(bitmap, Rect(0, 0, bitmap.width, bitmap.height), avatarRect, null)
            canvas.restoreToCount(save)
        }

        textPaint.set(paint)
        textPaint.color = textColor
        textPaint.typeface = typeface

        val textX = x + resolvedAvatarSize + avatarGap
        val availableWidthForText = (availableWidth - resolvedAvatarSize - avatarGap).coerceAtLeast(0f)
        val textToDraw = if (measuredTextWidth > availableWidthForText) {
            TextUtils.ellipsize(
                displayText,
                textPaint,
                availableWidthForText,
                TextUtils.TruncateAt.END
            )
        } else {
            displayText
        }
        canvas.drawText(textToDraw, 0, textToDraw.length, textX, y.toFloat(), textPaint)
    }

    private fun resolvedAvatarSize(paint: Paint, lineHeight: Int? = null): Int {
        val fontMetrics = paint.fontMetricsInt
        val fontHeight = (fontMetrics.descent - fontMetrics.ascent).coerceAtLeast(1)
        val maxInlineSize = ((lineHeight ?: fontHeight) * 0.58f).roundToInt().coerceAtLeast(1)
        return (avatarSize.takeIf { it > 0 } ?: maxInlineSize).coerceAtMost(maxInlineSize)
    }
}

/**
 * Sealed interface representing different types of mentions.
 */
sealed interface MentionType {
    data class User(val userId: UserId) : MentionType
    data class Room(val roomIdOrAlias: RoomIdOrAlias) : MentionType
    data class Message(val roomIdOrAlias: RoomIdOrAlias, val eventId: EventId) : MentionType
    data object Everyone : MentionType
}

/**
 * Extension function to get all MentionSpans from a CharSequence.
 */
fun CharSequence.getMentionSpans(start: Int = 0, end: Int = length): List<MentionSpan> {
    return if (this is android.text.Spanned) {
        // If we have custom mention spans created by the RTE, we need to extract the provided spans and filter them
        val customMentionSpans = getSpans<CustomMentionSpan>(start, end)
            .map { it.providedSpan }
            .filterIsInstance<MentionSpan>()
        // Collect all direct mention spans
        val directMentionSpans = getSpans<MentionSpan>(start, end)
        // Return the union of both
        customMentionSpans + directMentionSpans
    } else {
        emptyList()
    }
}

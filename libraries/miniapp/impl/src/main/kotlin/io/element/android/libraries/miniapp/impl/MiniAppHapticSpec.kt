/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.miniapp.impl

import android.annotation.SuppressLint
import android.os.VibrationEffect
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * One haptic primitive of a [VibrationEffect.Composition] (API 30+).
 * @param scale 0…1
 * @param delayMs delay since the previous primitive
 */
internal data class HapticPrimitive(val id: Int, val scale: Float, val delayMs: Int = 0)

/**
 * A waveform (API 26+): alternating segments of [timings] (ms) played at [amplitudes]
 * (0 = off, 1…255, or [VibrationEffect.DEFAULT_AMPLITUDE]).
 */
internal class HapticWaveform(val timings: LongArray, val amplitudes: IntArray) {
    override fun equals(other: Any?) =
        other is HapticWaveform && timings.contentEquals(other.timings) && amplitudes.contentEquals(other.amplitudes)

    override fun hashCode() = 31 * timings.contentHashCode() + amplitudes.contentHashCode()

    override fun toString() = "HapticWaveform(timings=${timings.contentToString()}, amplitudes=${amplitudes.contentToString()})"
}

/**
 * How to play one HapticSpec, best tier first; the player picks the first tier the device supports:
 * [primitives] (API 30+, closest to iOS generators) → [predefined] (API 29+) → [waveform] (API 26+).
 */
internal data class HapticRecipe(
    val waveform: HapticWaveform,
    val primitives: List<HapticPrimitive>? = null,
    val predefined: Int? = null,
) {
    companion object {
        /** `type: "none"` — nothing to play, but counts as played (same as iOS). */
        val NONE = HapticRecipe(HapticWaveform(LongArray(0), IntArray(0)))
    }
}

/**
 * Maps the mini-app HapticSpec (JS SDK `co.haptic`, same JSON as iOS `WebView+Haptic.swift`)
 * to an Android [HapticRecipe]. Pure: no device access, so it can be unit tested.
 *
 * Supported `type`s: impact / notification / selection / systemSound / pattern / vibrate / none.
 * Returns null when the spec can't be played (unknown type or name, empty pattern…), so the
 * caller tries the spec's `fallback` — same contract as iOS `HapticPlayer.playOne`.
 */
@SuppressLint("InlinedApi")
internal object MiniAppHapticSpec {
    /** Length of one `transient` pattern event (Core Haptics transients are ~10–20 ms). */
    private const val TRANSIENT_MS = 15L
    private const val MAX_SEGMENT_MS = 5_000L
    private const val MAX_SEGMENTS = 64

    fun map(spec: JSONObject): HapticRecipe? {
        val name = spec.optString("name")
        return when (spec.optString("type")) {
            "none" -> HapticRecipe.NONE
            "impact" -> impact(name, spec.intensity())
            "notification" -> notification(name)
            "selection" -> selection()
            "systemSound" -> systemSound(spec.optInt("id", 0))
            "pattern" -> pattern(spec)
            "vibrate" -> vibrate(spec.optJSONArray("timings"))
            else -> null
        }
    }

    /** `intensity` < 0 or missing = the platform default (iOS: `impactOccurred()` without intensity). */
    private fun JSONObject.intensity(): Float? {
        if (!has("intensity")) return null
        val value = optDouble("intensity", -1.0)
        return if (value < 0) null else value.coerceAtMost(1.0).toFloat()
    }

    // iOS UIImpactFeedbackGenerator styles; unknown names fall back to heavy, like iOS `impactStyle`.
    private fun impact(name: String, intensity: Float?): HapticRecipe {
        // iOS plays an impact at intensity 0 as nothing.
        if (intensity == 0f) return HapticRecipe.NONE
        val scale = intensity ?: 1f
        val (primitive, predefined, oneShot) = when (name) {
            "light" -> Triple(HapticPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.5f), VibrationEffect.EFFECT_TICK, 10L to 80)
            "medium" -> Triple(HapticPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.7f), VibrationEffect.EFFECT_CLICK, 20L to 150)
            "soft" -> Triple(HapticPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.7f), VibrationEffect.EFFECT_TICK, 15L to 100)
            "rigid" -> Triple(HapticPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f), VibrationEffect.EFFECT_HEAVY_CLICK, 12L to 255)
            else -> Triple(HapticPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f), VibrationEffect.EFFECT_HEAVY_CLICK, 30L to 255)
        }
        return HapticRecipe(
            primitives = listOf(primitive.copy(scale = (primitive.scale * scale).coerceIn(0f, 1f))),
            // Predefined effects can't be scaled: only use them at the default intensity.
            predefined = predefined.takeIf { intensity == null },
            waveform = oneShot(oneShot.first, amplitude(oneShot.second * scale)),
        )
    }

    // iOS UINotificationFeedbackGenerator; unknown names can't be played (iOS returns false).
    private fun notification(name: String): HapticRecipe? = when (name) {
        "success" -> HapticRecipe(
            primitives = listOf(
                HapticPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.8f),
                HapticPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.8f, delayMs = 60),
            ),
            predefined = VibrationEffect.EFFECT_DOUBLE_CLICK,
            waveform = waveform(20L to 180, 60L to 0, 20L to 180),
        )
        "warning" -> HapticRecipe(
            primitives = listOf(
                HapticPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.9f),
                HapticPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.6f, delayMs = 80),
            ),
            waveform = waveform(30L to 200, 80L to 0, 20L to 120),
        )
        "error" -> HapticRecipe(
            primitives = listOf(
                HapticPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f),
                HapticPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f, delayMs = 50),
                HapticPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f, delayMs = 50),
            ),
            waveform = waveform(30L to 255, 50L to 0, 30L to 255, 50L to 0, 30L to 255),
        )
        else -> null
    }

    // iOS UISelectionFeedbackGenerator.
    private fun selection() = HapticRecipe(
        primitives = listOf(HapticPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.3f)),
        predefined = VibrationEffect.EFFECT_TICK,
        waveform = oneShot(8L, 60),
    )

    // iOS AudioServicesPlaySystemSound haptic ids: 1519 peek, 1520 pop, 1521 nope. Other ids → fallback.
    private fun systemSound(id: Int): HapticRecipe? = when (id) {
        1519 -> selection()
        1520 -> impact("medium", null)
        1521 -> notification("error")
        else -> null
    }

    /**
     * iOS Core Haptics pattern → waveform. `transient` events are short pulses, `continuous` events
     * last `duration`; amplitude = event intensity × the `curve` (intensity control) at the event time.
     * `sharpness` has no Android equivalent and is ignored.
     */
    private fun pattern(spec: JSONObject): HapticRecipe? {
        val events = spec.optJSONArray("events") ?: return null
        val curve = curvePoints(spec.optJSONArray("curve"))
        data class Event(val startMs: Long, val durationMs: Long, val amplitude: Int)

        val parsed = (0 until events.length()).mapNotNull { i ->
            val event = events.optJSONObject(i) ?: return@mapNotNull null
            val time = event.optDouble("time", 0.0).coerceAtLeast(0.0)
            val duration = if (event.optString("type") == "continuous") {
                (event.optDouble("duration", 0.0) * 1000).toLong()
            } else {
                TRANSIENT_MS
            }
            val intensity = event.optDouble("intensity", 1.0).coerceIn(0.0, 1.0) * curveValue(curve, time)
            Event((time * 1000).toLong(), duration.coerceIn(0L, MAX_SEGMENT_MS), amplitude(intensity * 255))
        }.filter { it.durationMs > 0 && it.amplitude > 0 }.sortedBy { it.startMs }
        if (parsed.isEmpty()) return null

        val segments = mutableListOf<Pair<Long, Int>>()
        var cursor = 0L
        for (event in parsed) {
            if (event.startMs > cursor) segments += (event.startMs - cursor).coerceAtMost(MAX_SEGMENT_MS) to 0
            // Overlapping events start where the previous one ended.
            val end = maxOf(cursor, event.startMs) + event.durationMs
            segments += (end - maxOf(cursor, event.startMs)) to event.amplitude
            cursor = end
            if (segments.size >= MAX_SEGMENTS) break
        }
        return HapticRecipe(waveform = waveform(*segments.toTypedArray()))
    }

    /** Browser-style `navigator.vibrate` timings: [on, off, on, …] in ms, played at the default amplitude. */
    private fun vibrate(timings: JSONArray?): HapticRecipe? {
        if (timings == null || timings.length() == 0) return null
        val segments = (0 until minOf(timings.length(), MAX_SEGMENTS)).map { i ->
            val amplitude = if (i % 2 == 0) VibrationEffect.DEFAULT_AMPLITUDE else 0
            timings.optLong(i, 0L).coerceIn(0L, MAX_SEGMENT_MS) to amplitude
        }
        if (segments.none { it.second != 0 && it.first > 0 }) return null
        return HapticRecipe(waveform = waveform(*segments.toTypedArray()))
    }

    private fun curvePoints(curve: JSONArray?): List<Pair<Double, Double>> {
        if (curve == null) return emptyList()
        return (0 until curve.length()).mapNotNull { i ->
            val point = curve.optJSONArray(i) ?: return@mapNotNull null
            if (point.length() != 2) return@mapNotNull null
            point.optDouble(0, 0.0) to point.optDouble(1, 1.0)
        }.sortedBy { it.first }
    }

    /** Linear interpolation of the intensity-control curve; 1 when there is no curve. */
    private fun curveValue(curve: List<Pair<Double, Double>>, time: Double): Double {
        if (curve.isEmpty()) return 1.0
        if (time <= curve.first().first) return curve.first().second.coerceIn(0.0, 1.0)
        if (time >= curve.last().first) return curve.last().second.coerceIn(0.0, 1.0)
        val next = curve.indexOfFirst { it.first >= time }
        val (t0, v0) = curve[next - 1]
        val (t1, v1) = curve[next]
        val value = if (t1 == t0) v1 else v0 + (v1 - v0) * (time - t0) / (t1 - t0)
        return value.coerceIn(0.0, 1.0)
    }

    /** 0…255 amplitude; any positive intensity gives at least 1 so it isn't silently dropped. */
    private fun amplitude(value: Number): Int {
        val v = value.toDouble()
        return if (v <= 0) 0 else v.roundToInt().coerceIn(1, 255)
    }

    private fun oneShot(durationMs: Long, amplitude: Int) = waveform(durationMs to amplitude)

    private fun waveform(vararg segments: Pair<Long, Int>) = HapticWaveform(
        timings = LongArray(segments.size) { segments[it].first },
        amplitudes = IntArray(segments.size) { segments[it].second },
    )
}

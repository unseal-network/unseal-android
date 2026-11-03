/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.miniapp.impl

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresApi
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/**
 * Plays mini-app haptics (`haptic.play` / `haptic.probe`, JS SDK `co.haptic`) — the Android
 * counterpart of iOS `HapticPlayer` (`WebView+Haptic.swift`); keep the two in step.
 *
 * A spec is mapped by [MiniAppHapticSpec] and played with the best tier the device supports:
 * composition primitives (API 30+) → predefined effects (API 29+) → waveform (API 26+).
 * When a spec can't be played its `fallback` (spec or [spec]) is tried, like iOS.
 *
 * Vibrations use touch usage, so they follow the system "touch feedback" setting.
 */
internal class MiniAppHapticPlayer(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }
    private val handler = Handler(Looper.getMainLooper())

    /** Last play time per spec kind; games may call every frame and a new vibration cancels the previous one. */
    private val lastPlayedAt = HashMap<String, Long>()

    val hasVibrator: Boolean get() = vibrator?.hasVibrator() == true
    val hasAmplitudeControl: Boolean get() = vibrator?.hasAmplitudeControl() == true
    val supportsPrimitives: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && vibrator?.areAllPrimitivesSupported(
            VibrationEffect.Composition.PRIMITIVE_CLICK,
            VibrationEffect.Composition.PRIMITIVE_TICK,
        ) == true

    /** Plays `spec`, or its fallbacks when it can't be played. False when nothing played. */
    fun play(spec: JSONObject): Boolean {
        if (!hasVibrator) return false
        return playWithFallback(spec, depth = 0)
    }

    /** `haptic.probe` reply. `coreHaptics` is iOS-only and always false here. */
    fun capabilities(): JSONObject = JSONObject()
        .put("platform", "android")
        .put("version", 1)
        .put("coreHaptics", false)
        .put("vibrator", hasVibrator)
        .put("amplitudeControl", hasAmplitudeControl)
        .put("primitives", supportsPrimitives)

    fun release() {
        handler.removeCallbacksAndMessages(null)
        runCatching { vibrator?.cancel() }
    }

    private fun playWithFallback(spec: JSONObject, depth: Int): Boolean {
        if (playOne(spec)) return true
        if (depth >= MAX_FALLBACK_DEPTH) return false
        return when (val fallback = spec.opt("fallback")) {
            is JSONObject -> playWithFallback(fallback, depth + 1)
            is JSONArray -> {
                var any = false
                for (i in 0 until fallback.length()) {
                    val item = fallback.optJSONObject(i) ?: continue
                    any = playWithFallback(item, depth + 1) || any
                }
                any
            }
            else -> false
        }
    }

    private fun playOne(spec: JSONObject): Boolean {
        val recipe = MiniAppHapticSpec.map(spec) ?: return false
        if (recipe === HapticRecipe.NONE) return true
        if (isThrottled(spec)) return true
        val effect = runCatching { buildEffect(recipe) }
            .onFailure { Timber.w(it, "MiniApp: invalid haptic spec %s", spec) }
            .getOrNull() ?: return false
        val delayMs = (spec.optDouble("delay", 0.0) * 1000).toLong()
        if (delayMs <= 0) {
            vibrate(effect)
        } else {
            handler.postDelayed({ vibrate(effect) }, delayMs)
        }
        return true
    }

    private fun isThrottled(spec: JSONObject): Boolean {
        val key = spec.optString("type") + ":" + spec.optString("name") + ":" + spec.optInt("id", 0)
        val now = SystemClock.uptimeMillis()
        synchronized(lastPlayedAt) {
            val last = lastPlayedAt[key]
            if (last != null && now - last < THROTTLE_MS) return true
            lastPlayedAt[key] = now
        }
        return false
    }

    private fun buildEffect(recipe: HapticRecipe): VibrationEffect {
        val primitives = recipe.primitives
        if (primitives != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && supports(primitives)) {
            return composition(primitives)
        }
        val predefined = recipe.predefined
        if (predefined != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return VibrationEffect.createPredefined(predefined)
        }
        val waveform = recipe.waveform
        return if (waveform.timings.size == 1) {
            VibrationEffect.createOneShot(waveform.timings[0], waveform.amplitudes[0])
        } else {
            VibrationEffect.createWaveform(waveform.timings, waveform.amplitudes, -1)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun supports(primitives: List<HapticPrimitive>): Boolean =
        vibrator?.areAllPrimitivesSupported(*primitives.map { it.id }.distinct().toIntArray()) == true

    @RequiresApi(Build.VERSION_CODES.R)
    private fun composition(primitives: List<HapticPrimitive>): VibrationEffect {
        val composition = VibrationEffect.startComposition()
        primitives.forEach { composition.addPrimitive(it.id, it.scale, it.delayMs) }
        return composition.compose()
    }

    private fun vibrate(effect: VibrationEffect) {
        val vibrator = vibrator ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                vibrator.vibrate(effect, touchAttributes())
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(effect, SONIFICATION_ATTRIBUTES)
            }
        }.onFailure { Timber.w(it, "MiniApp: haptic vibrate failed") }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun touchAttributes(): VibrationAttributes = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH)

    private companion object {
        const val THROTTLE_MS = 15L
        const val MAX_FALLBACK_DEPTH = 4
        val SONIFICATION_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }
}

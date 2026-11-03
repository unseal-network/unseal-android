/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.miniapp.impl

import android.os.VibrationEffect
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MiniAppHapticSpecTest {
    private fun map(json: String) = MiniAppHapticSpec.map(JSONObject(json))

    @Test
    fun `none is played as nothing`() {
        assertThat(map("""{"type":"none"}""")).isSameInstanceAs(HapticRecipe.NONE)
    }

    @Test
    fun `unknown type cannot be played`() {
        assertThat(map("""{"type":"unknown"}""")).isNull()
        assertThat(map("""{}""")).isNull()
    }

    @Test
    fun `impact light maps to tick on every tier`() {
        val recipe = map("""{"type":"impact","name":"light"}""")!!
        assertThat(recipe.primitives).containsExactly(HapticPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.5f))
        assertThat(recipe.predefined).isEqualTo(VibrationEffect.EFFECT_TICK)
        assertThat(recipe.waveform).isEqualTo(HapticWaveform(longArrayOf(10), intArrayOf(80)))
    }

    @Test
    fun `impact with unknown name falls back to heavy like iOS`() {
        val recipe = map("""{"type":"impact","name":"whatever"}""")!!
        assertThat(recipe.primitives).containsExactly(HapticPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f))
        assertThat(recipe.predefined).isEqualTo(VibrationEffect.EFFECT_HEAVY_CLICK)
        assertThat(recipe.waveform).isEqualTo(HapticWaveform(longArrayOf(30), intArrayOf(255)))
    }

    @Test
    fun `impact intensity scales primitive and amplitude and skips predefined`() {
        val recipe = map("""{"type":"impact","name":"medium","intensity":0.5}""")!!
        assertThat(recipe.primitives).containsExactly(HapticPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.35f))
        assertThat(recipe.predefined).isNull()
        assertThat(recipe.waveform).isEqualTo(HapticWaveform(longArrayOf(20), intArrayOf(75)))
    }

    @Test
    fun `negative intensity means platform default`() {
        val recipe = map("""{"type":"impact","name":"medium","intensity":-1}""")!!
        assertThat(recipe.predefined).isEqualTo(VibrationEffect.EFFECT_CLICK)
        assertThat(recipe.waveform).isEqualTo(HapticWaveform(longArrayOf(20), intArrayOf(150)))
    }

    @Test
    fun `impact at intensity zero plays nothing`() {
        assertThat(map("""{"type":"impact","name":"heavy","intensity":0}""")).isSameInstanceAs(HapticRecipe.NONE)
    }

    @Test
    fun `notification success is a double click`() {
        val recipe = map("""{"type":"notification","name":"success"}""")!!
        assertThat(recipe.primitives).hasSize(2)
        assertThat(recipe.primitives!![1].delayMs).isEqualTo(60)
        assertThat(recipe.predefined).isEqualTo(VibrationEffect.EFFECT_DOUBLE_CLICK)
        assertThat(recipe.waveform).isEqualTo(HapticWaveform(longArrayOf(20, 60, 20), intArrayOf(180, 0, 180)))
    }

    @Test
    fun `notification error is three clicks without predefined effect`() {
        val recipe = map("""{"type":"notification","name":"error"}""")!!
        assertThat(recipe.primitives).hasSize(3)
        assertThat(recipe.predefined).isNull()
        assertThat(recipe.waveform.timings.size).isEqualTo(5)
    }

    @Test
    fun `notification with unknown name cannot be played`() {
        assertThat(map("""{"type":"notification","name":"other"}""")).isNull()
    }

    @Test
    fun `selection is a light tick`() {
        val recipe = map("""{"type":"selection"}""")!!
        assertThat(recipe.primitives).containsExactly(HapticPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.3f))
        assertThat(recipe.predefined).isEqualTo(VibrationEffect.EFFECT_TICK)
    }

    @Test
    fun `system sound haptic ids map to generator equivalents`() {
        assertThat(map("""{"type":"systemSound","id":1519}""")).isEqualTo(map("""{"type":"selection"}"""))
        assertThat(map("""{"type":"systemSound","id":1520}""")).isEqualTo(map("""{"type":"impact","name":"medium"}"""))
        assertThat(map("""{"type":"systemSound","id":1521}""")).isEqualTo(map("""{"type":"notification","name":"error"}"""))
        assertThat(map("""{"type":"systemSound","id":1000}""")).isNull()
    }

    @Test
    fun `pattern events become a waveform with gaps`() {
        val recipe = map(
            """{"type":"pattern","events":[
                {"type":"transient","time":0,"intensity":1},
                {"type":"continuous","time":0.1,"duration":0.2,"intensity":0.5}
            ]}"""
        )!!
        assertThat(recipe.primitives).isNull()
        assertThat(recipe.predefined).isNull()
        assertThat(recipe.waveform).isEqualTo(HapticWaveform(longArrayOf(15, 85, 200), intArrayOf(255, 0, 128)))
    }

    @Test
    fun `pattern curve scales event intensity`() {
        val recipe = map(
            """{"type":"pattern","events":[
                {"type":"transient","time":0,"intensity":1},
                {"type":"transient","time":0.5,"intensity":1}
            ],"curve":[[0,1],[1,0]]}"""
        )!!
        assertThat(recipe.waveform.amplitudes.toList()).containsExactly(255, 0, 128).inOrder()
    }

    @Test
    fun `overlapping pattern events start after the previous one`() {
        val recipe = map(
            """{"type":"pattern","events":[
                {"type":"continuous","time":0,"duration":0.1,"intensity":1},
                {"type":"transient","time":0.05,"intensity":1}
            ]}"""
        )!!
        assertThat(recipe.waveform).isEqualTo(HapticWaveform(longArrayOf(100, 15), intArrayOf(255, 255)))
    }

    @Test
    fun `empty pattern cannot be played`() {
        assertThat(map("""{"type":"pattern","events":[]}""")).isNull()
        assertThat(map("""{"type":"pattern"}""")).isNull()
    }

    @Test
    fun `vibrate timings alternate on and off at default amplitude`() {
        val recipe = map("""{"type":"vibrate","timings":[100,50,100]}""")!!
        assertThat(recipe.waveform).isEqualTo(
            HapticWaveform(
                longArrayOf(100, 50, 100),
                intArrayOf(VibrationEffect.DEFAULT_AMPLITUDE, 0, VibrationEffect.DEFAULT_AMPLITUDE),
            )
        )
    }

    @Test
    fun `vibrate without on time cannot be played`() {
        assertThat(map("""{"type":"vibrate","timings":[]}""")).isNull()
        assertThat(map("""{"type":"vibrate","timings":[0,100]}""")).isNull()
    }
}

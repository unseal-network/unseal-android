/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.miniapp.impl

import android.content.Context
import android.content.pm.ActivityInfo
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.miniapp.api.MiniAppOrientation
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MiniAppOrientationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `server value 1 is portrait, 2 landscape, 3 auto, anything else portrait`() {
        assertThat(MiniAppOrientation.fromServerValue(1)).isEqualTo(MiniAppOrientation.PORTRAIT)
        assertThat(MiniAppOrientation.fromServerValue(2)).isEqualTo(MiniAppOrientation.LANDSCAPE)
        assertThat(MiniAppOrientation.fromServerValue(3)).isEqualTo(MiniAppOrientation.AUTO)
        assertThat(MiniAppOrientation.fromServerValue(0)).isEqualTo(MiniAppOrientation.PORTRAIT)
        assertThat(MiniAppOrientation.fromServerValue(4)).isEqualTo(MiniAppOrientation.PORTRAIT)
        assertThat(MiniAppOrientation.fromServerValue(null)).isEqualTo(MiniAppOrientation.PORTRAIT)
    }

    @Test
    fun `bridge names parse, invalid names are null`() {
        assertThat(MiniAppOrientation.fromWireName("portrait")).isEqualTo(MiniAppOrientation.PORTRAIT)
        assertThat(MiniAppOrientation.fromWireName("landscape")).isEqualTo(MiniAppOrientation.LANDSCAPE)
        assertThat(MiniAppOrientation.fromWireName("auto")).isEqualTo(MiniAppOrientation.AUTO)
        assertThat(MiniAppOrientation.fromWireName("Landscape")).isNull()
        assertThat(MiniAppOrientation.fromWireName("")).isNull()
        assertThat(MiniAppOrientation.fromWireName(null)).isNull()
    }

    @Test
    fun `orientations map to activity orientations`() {
        assertThat(MiniAppOrientation.PORTRAIT.toActivityOrientation()).isEqualTo(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        assertThat(MiniAppOrientation.LANDSCAPE.toActivityOrientation()).isEqualTo(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
        assertThat(MiniAppOrientation.AUTO.toActivityOrientation()).isEqualTo(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
    }

    @Test
    fun `target reached`() {
        assertThat(isOrientationReached(MiniAppOrientation.LANDSCAPE, 800f, 400f)).isTrue()
        assertThat(isOrientationReached(MiniAppOrientation.LANDSCAPE, 400f, 800f)).isFalse()
        assertThat(isOrientationReached(MiniAppOrientation.LANDSCAPE, 500f, 500f)).isFalse()
        assertThat(isOrientationReached(MiniAppOrientation.PORTRAIT, 400f, 800f)).isTrue()
        assertThat(isOrientationReached(MiniAppOrientation.PORTRAIT, 800f, 400f)).isFalse()
        assertThat(isOrientationReached(MiniAppOrientation.PORTRAIT, 500f, 500f)).isFalse()
        assertThat(isOrientationReached(MiniAppOrientation.AUTO, 800f, 400f)).isTrue()
        assertThat(isOrientationReached(MiniAppOrientation.AUTO, 400f, 800f)).isTrue()
    }

    @Test
    fun `orientation json reports the actual and requested orientation and the size in dp`() {
        val landscape = MiniAppGeometry(800.4f, 399.6f, MiniAppInsets(0f, 0f, 0f, 0f))
        val json = orientationJson(MiniAppOrientation.AUTO, landscape)
        assertThat(json.getString("orientation")).isEqualTo("landscape")
        assertThat(json.getString("requested")).isEqualTo("auto")
        assertThat(json.getInt("width")).isEqualTo(800)
        assertThat(json.getInt("height")).isEqualTo(400)
        assertThat(json.length()).isEqualTo(4)

        val portrait = orientationJson(MiniAppOrientation.LANDSCAPE, MiniAppGeometry(400f, 800f, landscape.insets))
        assertThat(portrait.getString("orientation")).isEqualTo("portrait")
        assertThat(portrait.getString("requested")).isEqualTo("landscape")
    }

    @Test
    fun `safe area json reports the insets and size in dp`() {
        val json = safeAreaJson(MiniAppGeometry(800f, 400f, MiniAppInsets(top = 0f, bottom = 20.4f, left = 32f, right = 0f)))
        assertThat(json.getInt("top")).isEqualTo(0)
        assertThat(json.getInt("bottom")).isEqualTo(20)
        assertThat(json.getInt("left")).isEqualTo(32)
        assertThat(json.getInt("right")).isEqualTo(0)
        assertThat(json.getInt("width")).isEqualTo(800)
        assertThat(json.getInt("height")).isEqualTo(400)
    }

    @Test
    fun `controller set rejects invalid values and replies at once when already reached`() {
        val controller = MiniAppOrientationController(context, MiniAppOrientation.LANDSCAPE)
        assertThat(JSONObject(controller.get()).getString("requested")).isEqualTo("landscape")

        assertThat(controller.set("sideways", null)).isFalse()
        assertThat(controller.requested).isEqualTo(MiniAppOrientation.LANDSCAPE)

        var reply: JSONObject? = null
        assertThat(controller.set("auto") { reply = it }).isTrue()
        assertThat(controller.requested).isEqualTo(MiniAppOrientation.AUTO)
        assertThat(reply?.getString("requested")).isEqualTo("auto")
    }
}

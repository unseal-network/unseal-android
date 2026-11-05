/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.miniapp.impl

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MiniAppCapsuleTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `rect is 87x32 dp, 7 dp from the right edge, and reports visibility`() {
        val density = context.resources.displayMetrics.density
        val controller = MiniAppCapsuleController(context) { (400 * density).toInt() }

        val hidden = JSONObject(controller.rect())
        assertThat(hidden.getBoolean("visible")).isFalse()
        assertThat(hidden.getDouble("width")).isEqualTo(87.0)
        assertThat(hidden.getDouble("height")).isEqualTo(32.0)
        assertThat(hidden.getDouble("right")).isWithin(0.01).of(393.0)
        assertThat(hidden.getDouble("left")).isWithin(0.01).of(306.0)
        assertThat(hidden.getDouble("bottom") - hidden.getDouble("top")).isWithin(0.01).of(32.0)
        assertThat(hidden.getDouble("top")).isAtLeast(4.0)

        val shown = JSONObject(controller.show("""{"theme":"light"}"""))
        assertThat(shown.getBoolean("visible")).isTrue()
        assertThat(controller.isVisible).isTrue()
        assertThat(controller.theme).isEqualTo(MiniAppCapsuleTheme.Light)

        assertThat(controller.hide()).isEmpty()
        assertThat(JSONObject(controller.rect()).getBoolean("visible")).isFalse()
        assertThat(controller.isVisible).isFalse()
    }

    @Test
    fun `theme defaults to dark, or follows a darkContent status bar`() {
        val controller = MiniAppCapsuleController(context) { 0 }
        controller.show("{}")
        assertThat(controller.theme).isEqualTo(MiniAppCapsuleTheme.Dark)

        controller.statusBarStyle = "darkContent"
        controller.show("")
        assertThat(controller.theme).isEqualTo(MiniAppCapsuleTheme.Light)

        controller.show("""{"theme":"dark"}""")
        assertThat(controller.theme).isEqualTo(MiniAppCapsuleTheme.Dark)
    }

    @Test
    fun `share keeps the page's share info`() {
        val controller = MiniAppCapsuleController(context) { 0 }
        assertThat(controller.share("""{"title":"T","desc":"D","imageUrl":"https://i","query":"a=1","other":1}""")).isEmpty()
        assertThat(controller.shareInfo).containsExactly("title", "T", "desc", "D", "imageUrl", "https://i", "query", "a=1")
    }

    @Test
    fun `storage clear only removes this app's entries`() {
        val app1 = MiniAppStorage(context, 1L)
        val app11 = MiniAppStorage(context, 11L)
        app1.set("""{"key":"score","value":"3"}""")
        app1.set("""{"key":"__auth","value":"token"}""")
        app11.set("""{"key":"score","value":"5"}""")

        app1.clear()

        assertThat(app1.get("""{"key":"score"}""")).isEmpty()
        assertThat(app1.get("""{"key":"__auth"}""")).isNotEmpty()
        assertThat(app11.get("""{"key":"score"}""")).isNotEmpty()
    }
}

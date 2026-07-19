/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.ui

import android.view.KeyEvent
import android.webkit.PermissionRequest
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.AndroidComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.element.android.features.call.impl.audience.AudienceManifest
import io.element.android.features.call.impl.audience.AudiencePlaybackState
import io.element.android.features.call.impl.audience.AudiencePresentation
import io.element.android.features.call.impl.audience.AudiencePresentationKind
import io.element.android.features.call.impl.audience.AudienceRendition
import io.element.android.features.call.impl.pip.PictureInPictureEvent
import io.element.android.features.call.impl.pip.PictureInPictureState
import io.element.android.features.call.impl.pip.aPictureInPictureState
import io.element.android.features.call.impl.ui.CallScreenEvent
import io.element.android.features.call.impl.ui.CallScreenState
import io.element.android.features.call.impl.ui.CallScreenView
import io.element.android.features.call.impl.ui.JavascriptBackHandlerBridge
import io.element.android.features.call.impl.ui.aCallScreenState
import io.element.android.features.call.impl.ui.handleCallWebPermissionRequest
import io.element.android.tests.testutils.EventsRecorder
import io.element.android.tests.testutils.pressBackKey
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Resetter
import org.robolectric.shadows.ShadowWebView

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class CallScreenViewTest {
    @Test
    fun `legacy audience web permission request is denied without requesting Android media permissions`() {
        val request = mockk<PermissionRequest>(relaxed = true)
        var androidPermissionRequestCount = 0

        handleCallWebPermissionRequest(
            isAudience = true,
            request = request,
            requestPermissions = { _, _ -> androidPermissionRequestCount++ },
        )

        verify(exactly = 1) { request.deny() }
        verify(exactly = 0) { request.grant(any()) }
        assertEquals(0, androidPermissionRequestCount)
    }

    @Test
    fun `listener management is hidden without meeting control permission`() = runAndroidComposeUiTest {
        setCallScreenView(
            state = aCallScreenState(canManageAudience = false),
            useInspectionMode = true,
        )
        onNodeWithContentDescription("Manage meeting listeners").assertDoesNotExist()
    }

    @Test
    fun `listener management is visible with meeting control permission`() = runAndroidComposeUiTest {
        setCallScreenView(
            state = aCallScreenState(canManageAudience = true),
            useInspectionMode = true,
        )
        onNodeWithContentDescription("Manage meeting listeners").assertExists()
    }

    @Test
    fun `native audience renders participant and screen presentations without publishing controls`() = runAndroidComposeUiTest {
        val userRendition = AudienceRendition("https://keepsecret.io/live/user.m3u8", null, 1280, 720)
        val screenRendition = AudienceRendition("https://keepsecret.io/live/screen.m3u8", null, 1920, 1080)
        val manifest = AudienceManifest(
            broadcastId = "bcast_demo",
            meetingInstanceId = "4d1c64a7-6d0a-4fac-91f8-5bcbf2fc6a9d",
            generation = 1,
            revision = 1,
            presentations = listOf(
                AudiencePresentation(
                    presentationId = "user:alice",
                    kind = AudiencePresentationKind.User,
                    matrixUserId = "@alice:keepsecret.io",
                    matrixDeviceId = "ALICE1",
                    displayName = "Alice",
                    avatarUrl = null,
                    sourceId = null,
                    audio = userRendition,
                    video = userRendition,
                    activeSpeaker = true,
                ),
                AudiencePresentation(
                    presentationId = "screen:alice",
                    kind = AudiencePresentationKind.Screen,
                    matrixUserId = "@alice:keepsecret.io",
                    matrixDeviceId = "ALICE1",
                    displayName = "Alice's screen",
                    avatarUrl = null,
                    sourceId = "screen-1",
                    audio = null,
                    video = screenRendition,
                    activeSpeaker = false,
                ),
            ),
        )

        setCallScreenView(
            state = aCallScreenState(
                isAudience = true,
                audiencePlaybackState = AudiencePlaybackState.Live(manifest),
            ),
            useInspectionMode = true,
        )

        onNodeWithContentDescription("Leave listener mode").assertExists()
        onNodeWithContentDescription("Manage meeting listeners").assertDoesNotExist()
        onNodeWithText("Alice").performScrollTo().assertExists()
        onNodeWithText("Alice's screen").assertExists()
    }

    @Test
    fun `pressing back key triggers hangup when no web view is available and pip is unsupported`() = runAndroidComposeUiTest {
        val callEvents = EventsRecorder<CallScreenEvent>()

        setCallScreenView(
            state = aCallScreenState(eventSink = callEvents),
            useInspectionMode = true,
        )

        pressBackKey()

        callEvents.assertEmpty()
    }

    @Config(shadows = [RecordingShadowWebView::class])
    @Test
    fun `pressing back key dispatches escape key events to web view when pip is unsupported`() = runAndroidComposeUiTest {
        setCallScreenView(
            state = aCallScreenState(),
            useInspectionMode = false,
            pipState = aPictureInPictureState(supportPip = false),
        )

        pressBackKey()

        val dispatchedEvents = RecordingShadowWebView.dispatchedEvents
        assertEquals(2, dispatchedEvents.size)
        assertEquals(KeyEvent.ACTION_DOWN, dispatchedEvents[0].action)
        assertEquals(KeyEvent.KEYCODE_ESCAPE, dispatchedEvents[0].keyCode)
        assertEquals(KeyEvent.ACTION_UP, dispatchedEvents[1].action)
        assertEquals(KeyEvent.KEYCODE_ESCAPE, dispatchedEvents[1].keyCode)
    }

    @Config(shadows = [RecordingShadowWebView::class])
    @Test
    fun `web view javascript back handler emits pip event when pip is supported`() = runAndroidComposeUiTest {
        val pipEvents = EventsRecorder<PictureInPictureEvent>()

        setCallScreenView(
            state = aCallScreenState(),
            useInspectionMode = false,
            pipState = aPictureInPictureState(
                supportPip = true,
                eventSink = pipEvents,
            ),
        )

        runOnIdle {
            RecordingShadowWebView.invokeJavascriptBackHandler()
        }

        pipEvents.assertSize(2)
        pipEvents.assertTrue(0) { it is PictureInPictureEvent.SetPipController }
        pipEvents.assertTrue(1) { it is PictureInPictureEvent.EnterPictureInPicture }
    }
}

@OptIn(ExperimentalTestApi::class)
private fun <A : ComponentActivity> AndroidComposeUiTest<A>.setCallScreenView(
    state: CallScreenState,
    useInspectionMode: Boolean,
    pipState: PictureInPictureState = aPictureInPictureState(supportPip = false),
) {
    setContent {
        // Inspection mode disables AndroidView creation; keep it configurable per test.
        CompositionLocalProvider(LocalInspectionMode provides useInspectionMode) {
            CallScreenView(
                state = state,
                pipState = pipState,
                onConsoleMessage = {},
                requestPermissions = { _, _ -> },
            )
        }
    }
}

@Implements(WebView::class)
internal class RecordingShadowWebView : ShadowWebView() {
    companion object {
        val dispatchedEvents = mutableListOf<KeyEvent>()
        private var backHandlerJavascriptInterface: JavascriptBackHandlerBridge? = null

        @Resetter
        @JvmStatic
        @Suppress("unused")
        fun resetRecordedEvents() {
            dispatchedEvents.clear()
            backHandlerJavascriptInterface = null
        }

        fun invokeJavascriptBackHandler() {
            val backHandler = checkNotNull(backHandlerJavascriptInterface) { "Expected backHandler JavaScript interface to be registered" }
            backHandler.onBackPressed()
        }
    }

    @Implementation
    protected override fun addJavascriptInterface(`object`: Any, name: String) {
        super.addJavascriptInterface(`object`, name)
        if (name == "backHandler") {
            backHandlerJavascriptInterface = `object` as? JavascriptBackHandlerBridge
        }
    }

    @Implementation
    @Suppress("unused")
    fun dispatchKeyEvent(event: KeyEvent): Boolean {
        dispatchedEvents += KeyEvent(event)
        return false
    }
}

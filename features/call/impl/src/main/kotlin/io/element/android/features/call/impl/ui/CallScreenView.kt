/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl.ui

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.call.api.AudienceAccessMode
import io.element.android.features.call.impl.R
import io.element.android.features.call.impl.pip.PictureInPictureEvent
import io.element.android.features.call.impl.pip.PictureInPictureState
import io.element.android.features.call.impl.pip.aPictureInPictureState
import io.element.android.features.call.impl.utils.InvalidAudioDeviceReason
import io.element.android.features.call.impl.utils.WebViewAudioManager
import io.element.android.features.call.impl.utils.WebViewPipController
import io.element.android.features.call.impl.utils.WebViewWidgetMessageInterceptor
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.designsystem.components.ProgressDialog
import io.element.android.libraries.designsystem.components.dialogs.ErrorDialog
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.ui.strings.CommonStrings
import timber.log.Timber

typealias RequestPermissionCallback = (Array<String>) -> Unit

interface CallScreenNavigator {
    fun close()
}

@Composable
internal fun CallScreenView(
    state: CallScreenState,
    pipState: PictureInPictureState,
    audiencePlaybackEnabled: Boolean = true,
    onConsoleMessage: (ConsoleMessage) -> Unit,
    requestPermissions: (Array<String>, RequestPermissionCallback) -> Unit,
    modifier: Modifier = Modifier,
) {
    var callWebView by remember { mutableStateOf<WebView?>(null) }

    fun handleBack(fromNative: Boolean = false) {
        if (state.isAudience) {
            state.eventSink(CallScreenEvent.Hangup)
            return
        }
        when (CallScreenBackPressPolicy.resolve(supportPip = pipState.supportPip, hasWebView = callWebView != null, fromNative)) {
            CallScreenBackPressAction.EnterPictureInPicture ->
                pipState.eventSink(PictureInPictureEvent.EnterPictureInPicture)
            CallScreenBackPressAction.DispatchEscapeToWebView ->
                callWebView?.dispatchEscKeyEvent()
            null -> Timber.d("Back press with unsupported pip is a no-op")
        }
    }

    BackHandler {
        handleBack(fromNative = true)
    }
    LaunchedEffect(state.isAudience, audiencePlaybackEnabled, callWebView) {
        if (state.isAudience) {
            if (audiencePlaybackEnabled) {
                callWebView?.onResume()
            } else {
                callWebView?.onPause()
            }
        }
    }
    if (state.webViewError != null) {
        ErrorDialog(
            content = buildString {
                append(stringResource(CommonStrings.error_unknown))
                state.webViewError.takeIf { it.isNotEmpty() }?.let { append("\n\n").append(it) }
            },
            onSubmit = { state.eventSink(CallScreenEvent.Hangup) },
        )
    } else {
        var webViewAudioManager by remember { mutableStateOf<WebViewAudioManager?>(null) }
        val coroutineScope = rememberCoroutineScope()

        var invalidAudioDeviceReason by remember { mutableStateOf<InvalidAudioDeviceReason?>(null) }
        invalidAudioDeviceReason?.let {
            InvalidAudioDeviceDialog(invalidAudioDeviceReason = it) {
                invalidAudioDeviceReason = null
            }
        }

        var showListenerSettings by remember { mutableStateOf(false) }
        Box(modifier = modifier.consumeWindowInsets(WindowInsets.systemBars).fillMaxSize()) {
            CallWebView(
                modifier = Modifier.fillMaxSize(),
                url = state.urlState,
                userAgent = state.userAgent,
                onPermissionsRequest = { request ->
                    handleCallWebPermissionRequest(state.isAudience, request, requestPermissions)
                },
                onConsoleMessage = onConsoleMessage,
                onCreateWebView = { webView ->
                    callWebView = webView
                    webView.addBackHandler(onBackPressed = ::handleBack)
                    val interceptor = WebViewWidgetMessageInterceptor(
                        webView = webView,
                        onUrlLoaded = { url ->
                            webView.evaluateJavascript("controls.onBackButtonPressed = () => { backHandler.onBackPressed() }", null)
                            if (!state.isAudience && webViewAudioManager?.isInCallMode?.get() == false) {
                                Timber.d("URL $url is loaded, starting in-call audio mode")
                                webViewAudioManager?.onCallStarted()
                            } else if (!state.isAudience) {
                                Timber.d("Can't start in-call audio mode since the app is already in it.")
                            }
                        },
                        onError = { state.eventSink(CallScreenEvent.OnWebViewError(it)) },
                    )
                    if (!state.isAudience) {
                        webViewAudioManager = WebViewAudioManager(
                            webView = webView,
                            coroutineScope = coroutineScope,
                            onInvalidAudioDeviceAdded = { invalidAudioDeviceReason = it },
                        )
                    }
                    state.eventSink(CallScreenEvent.SetupMessageChannels(interceptor))
                    val pipController = WebViewPipController(webView)
                    pipState.eventSink(PictureInPictureEvent.SetPipController(pipController))
                },
                onDestroyWebView = {
                    callWebView = null
                    webViewAudioManager?.onCallStopped()
                }
            )
            if (!state.isAudience && state.canManageAudience && state.urlState is AsyncData.Success) {
                FloatingActionButton(
                    onClick = { showListenerSettings = true },
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                    containerColor = if (state.audienceHostControl.isEnabled) {
                        ElementTheme.colors.bgActionPrimaryRest
                    } else {
                        ElementTheme.colors.bgCanvasDefault
                    },
                ) {
                    Icon(
                        imageVector = CompoundIcons.HeadphonesSolid(),
                        contentDescription = stringResource(R.string.call_manage_listeners),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
        if (showListenerSettings) {
            CallListenerSettingsDialog(
                isUpdating = state.audienceHostControl.isUpdating,
                isEnabled = state.audienceHostControl.isEnabled,
                errorMessage = state.audienceHostControl.errorMessage,
                onSetAccessMode = { state.eventSink(CallScreenEvent.SetAudienceRelay(it)) },
                onDismiss = { if (!state.audienceHostControl.isUpdating) showListenerSettings = false },
            )
        }
        when (state.urlState) {
            AsyncData.Uninitialized,
            is AsyncData.Loading ->
                ProgressDialog(text = stringResource(id = CommonStrings.common_please_wait))
            is AsyncData.Failure -> {
                Timber.e(state.urlState.error, "WebView failed to load URL: ${state.urlState.error.message}")
                ErrorDialog(
                    content = state.urlState.error.message.orEmpty(),
                    onSubmit = { state.eventSink(CallScreenEvent.Hangup) },
                )
            }
            is AsyncData.Success -> Unit
        }
    }
}

internal fun handleCallWebPermissionRequest(
    isAudience: Boolean,
    request: PermissionRequest,
    requestPermissions: (Array<String>, RequestPermissionCallback) -> Unit,
) {
    if (isAudience) {
        request.deny()
    } else {
        val callback: RequestPermissionCallback = { request.grant(it) }
        val androidPermissions = mapWebkitPermissions(request.resources)
        requestPermissions(androidPermissions.toTypedArray(), callback)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CallListenerSettingsDialog(
    isUpdating: Boolean,
    isEnabled: Boolean,
    errorMessage: String?,
    onSetAccessMode: (AudienceAccessMode?) -> Unit,
    onDismiss: () -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge) {
            Column(
                modifier = Modifier.padding(24.dp).widthIn(min = 280.dp, max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.call_listener_access_title), style = ElementTheme.typography.fontHeadingMdBold)
                Text(stringResource(R.string.call_listener_access_description), color = ElementTheme.colors.textSecondary)
                errorMessage?.let { Text(it, color = ElementTheme.colors.textCriticalPrimary) }
                TextButton(
                    enabled = !isUpdating,
                    onClick = { onSetAccessMode(AudienceAccessMode.Authenticated) },
                ) { Text(stringResource(R.string.call_listener_authenticated)) }
                TextButton(
                    enabled = !isUpdating,
                    onClick = { onSetAccessMode(AudienceAccessMode.RoomMembers) },
                ) { Text(stringResource(R.string.call_listener_room_members)) }
                if (isEnabled) {
                    TextButton(enabled = !isUpdating, onClick = { onSetAccessMode(null) }) {
                        Text(stringResource(R.string.call_listener_disable))
                    }
                }
                if (isUpdating) {
                    Text(stringResource(R.string.call_listener_updating), color = ElementTheme.colors.textSecondary)
                } else {
                    TextButton(onClick = onDismiss) { Text(stringResource(CommonStrings.action_cancel)) }
                }
            }
        }
    }
}

@Composable
private fun InvalidAudioDeviceDialog(
    invalidAudioDeviceReason: InvalidAudioDeviceReason,
    onDismiss: () -> Unit,
) {
    ErrorDialog(
        content = when (invalidAudioDeviceReason) {
            InvalidAudioDeviceReason.BT_AUDIO_DEVICE_DISABLED -> {
                stringResource(R.string.call_invalid_audio_device_bluetooth_devices_disabled)
            }
        },
        onSubmit = onDismiss,
    )
}

@Composable
private fun CallWebView(
    url: AsyncData<String>,
    userAgent: String,
    onPermissionsRequest: (PermissionRequest) -> Unit,
    onConsoleMessage: (ConsoleMessage) -> Unit,
    onCreateWebView: (WebView) -> Unit,
    onDestroyWebView: (WebView) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (LocalInspectionMode.current) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("WebView - can't be previewed")
        }
    } else {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                WebView(context).apply {
                    onCreateWebView(this)
                    setup(
                        userAgent = userAgent,
                        onPermissionsRequested = onPermissionsRequest,
                        onConsoleMessage = onConsoleMessage,
                    )
                }
            },
            update = { webView ->
                if (url is AsyncData.Success && webView.url != url.data) {
                    webView.loadUrl(url.data)
                }
            },
            onRelease = { webView ->
                onDestroyWebView(webView)
                webView.destroy()
            }
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.setup(
    userAgent: String,
    onPermissionsRequested: (PermissionRequest) -> Unit,
    onConsoleMessage: (ConsoleMessage) -> Unit,
) {
    layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
    )

    with(settings) {
        javaScriptEnabled = true
        allowContentAccess = true
        allowFileAccess = true
        domStorageEnabled = true
        mediaPlaybackRequiresUserGesture = false
        @Suppress("DEPRECATION")
        databaseEnabled = true
        loadsImagesAutomatically = true
        userAgentString = userAgent
    }

    webChromeClient = object : WebChromeClient() {
        override fun onPermissionRequest(request: PermissionRequest) {
            onPermissionsRequested(request)
        }

        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
            onConsoleMessage(consoleMessage)
            return true
        }
    }
}

private fun WebView.addBackHandler(onBackPressed: () -> Unit) {
    addJavascriptInterface(
        JavascriptBackHandlerBridge(callback = onBackPressed),
        "backHandler"
    )
}

private fun WebView.dispatchEscKeyEvent() {
    dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ESCAPE))
    dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_ESCAPE))
}

@PreviewsDayNight
@Composable
internal fun CallScreenViewPreview(
    @PreviewParameter(CallScreenStateProvider::class) state: CallScreenState,
) = ElementPreview {
    CallScreenView(
        state = state,
        pipState = aPictureInPictureState(),
        requestPermissions = { _, _ -> },
        onConsoleMessage = {},
    )
}

@PreviewsDayNight
@Composable
internal fun InvalidAudioDeviceDialogPreview() = ElementPreview {
    InvalidAudioDeviceDialog(invalidAudioDeviceReason = InvalidAudioDeviceReason.BT_AUDIO_DEVICE_DISABLED) {}
}

internal class JavascriptBackHandlerBridge(
    private val callback: () -> Unit,
) {
    @JavascriptInterface
    fun onBackPressed() {
        callback()
    }
}

/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import im.vector.app.features.analytics.plan.MobileScreen
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.call.api.CallData
import io.element.android.features.call.impl.audience.AudienceBroadcastHttpClient
import io.element.android.features.call.impl.audience.AudienceBroadcastRequestContract
import io.element.android.features.call.impl.audience.AudienceHttpException
import io.element.android.features.call.impl.data.WidgetMessage
import io.element.android.features.call.impl.utils.ActiveCallManager
import io.element.android.features.call.impl.utils.CallWidgetProvider
import io.element.android.features.call.impl.utils.WidgetMessageInterceptor
import io.element.android.features.call.impl.utils.WidgetMessageSerializer
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.architecture.runCatchingUpdatingState
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.di.annotations.AppCoroutineScope
import io.element.android.libraries.matrix.api.MatrixClientProvider
import io.element.android.libraries.matrix.api.sync.SyncState
import io.element.android.libraries.matrix.api.widget.MatrixWidgetDriver
import io.element.android.libraries.network.useragent.UserAgentProvider
import io.element.android.services.analytics.api.ScreenTracker
import io.element.android.services.appnavstate.api.AppForegroundStateService
import io.element.android.services.toolbox.api.systemclock.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import timber.log.Timber
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

@AssistedInject
class CallScreenPresenter(
    @Assisted private val callData: CallData,
    @Assisted private val navigator: CallScreenNavigator,
    private val callWidgetProvider: CallWidgetProvider,
    userAgentProvider: UserAgentProvider,
    private val clock: SystemClock,
    private val dispatchers: CoroutineDispatchers,
    private val matrixClientsProvider: MatrixClientProvider,
    private val screenTracker: ScreenTracker,
    private val activeCallManager: ActiveCallManager,
    private val languageTagProvider: LanguageTagProvider,
    private val appForegroundStateService: AppForegroundStateService,
    @AppCoroutineScope
    private val appCoroutineScope: CoroutineScope,
    private val widgetMessageSerializer: WidgetMessageSerializer,
    private val audienceBroadcastHttpClient: AudienceBroadcastHttpClient,
) : Presenter<CallScreenState> {
    @AssistedFactory
    interface Factory {
        fun create(callData: CallData, navigator: CallScreenNavigator): CallScreenPresenter
    }

    private val userAgent = userAgentProvider.provide()

    @Composable
    override fun present(): CallScreenState {
        val coroutineScope = rememberCoroutineScope()
        val urlState = remember { mutableStateOf<AsyncData<String>>(AsyncData.Uninitialized) }
        val callWidgetDriver = remember { mutableStateOf<MatrixWidgetDriver?>(null) }
        val messageInterceptor = remember { mutableStateOf<WidgetMessageInterceptor?>(null) }
        var isWidgetLoaded by rememberSaveable { mutableStateOf(false) }
        var ignoreWebViewError by rememberSaveable { mutableStateOf(false) }
        var webViewError by remember { mutableStateOf<String?>(null) }
        val languageTag = languageTagProvider.provideLanguageTag()
        val theme = if (ElementTheme.isLightTheme) "light" else "dark"
        val widgetClientId = rememberSaveable(callData.audienceBroadcastId) {
            if (callData.audienceBroadcastId == null) {
                UUID.randomUUID().toString()
            } else {
                "client_${UUID.randomUUID().toString().replace("-", "")}" 
            }
        }
        DisposableEffect(Unit) {
            coroutineScope.launch {
                callData.audienceBroadcastId?.let { broadcastId ->
                    Timber.i("Audience listener screen started: broadcastId=%s", broadcastId)
                }
                if (callData.audienceBroadcastId == null) {
                    // Sets the call as joined
                    activeCallManager.joinedCall(callData)
                }
                fetchRoomCallUrl(
                    callData = callData,
                    urlState = urlState,
                    callWidgetDriver = callWidgetDriver,
                    languageTag = languageTag,
                    theme = theme,
                    clientId = widgetClientId,
                )
            }
            onDispose {
                if (callData.audienceBroadcastId == null) {
                    appCoroutineScope.launch { activeCallManager.hangUpCall(callData) }
                }
            }
        }
        screenTracker.TrackScreen(screen = MobileScreen.ScreenName.RoomCall)
        HandleMatrixClientSyncState()

        callWidgetDriver.value?.let { driver ->
            // A listener can leave and re-enter without this Compose presenter
            // being recreated. Key this bridge by the concrete driver so a new
            // audience route never keeps forwarding WebView messages to the
            // previous broadcast's driver.
            LaunchedEffect(driver) {
                driver.incomingMessages
                    .onEach {
                        // Relay message to the WebView
                        messageInterceptor.value?.sendMessage(it)
                    }
                    .launchIn(this)

                driver.run()
            }
        }

        messageInterceptor.value?.let { interceptor ->
            // WebView recreation replaces its interceptor independently from
            // the presenter. Rebind so requests for the new listener page are
            // not consumed by a stale channel.
            LaunchedEffect(interceptor) {
                interceptor.interceptedMessages
                    .onEach {
                        // We are receiving messages from the WebView, consider that the application is loaded
                        ignoreWebViewError = true
                        val parsedMessage = parseMessage(it)
                        // A listener is not a MatrixRTC participant. Its embedded page sends the
                        // standard close message when the user hangs up, but forwarding that
                        // message to the room widget can wait on the Matrix transport before the
                        // Activity is finished. Close the listener UI first; its driver teardown
                        // remains best-effort in [close].
                        if (callData.audienceBroadcastId != null &&
                            parsedMessage?.direction == WidgetMessage.Direction.FromWidget &&
                            parsedMessage.action == WidgetMessage.Action.Close
                        ) {
                            close(callWidgetDriver.value, navigator)
                        } else if (parsedMessage.isAudienceBroadcastRequest()) {
                            // A normal MatrixRTC widget has no direct access to the
                            // Matrix bearer token. Route this narrow, allow-listed
                            // request through the Android host so its standard call
                            // header can show the relay's live listener count.
                            interceptor.sendMessage(
                                widgetMessageSerializer.serialize(
                                    handleAudienceBroadcastRequest(requireNotNull(parsedMessage)),
                                ),
                            )
                        } else if (parsedMessage.isOptionalHostControl()) {
                            // The Matrix SDK version embedded by Android predates these optional
                            // Element Call host controls. They do not change call media state, so
                            // acknowledge them locally instead of forwarding an unsupported action
                            // to the driver (which otherwise crashes Element Call's error boundary).
                            interceptor.sendMessage(
                                widgetMessageSerializer.serialize(
                                    parsedMessage!!.copy(response = parsedMessage.optionalHostControlResponse()),
                                ),
                            )
                        } else {
                            // Relay all protocol actions to the Matrix widget driver.
                            callWidgetDriver.value?.send(it)
                        }

                        if (parsedMessage?.direction == WidgetMessage.Direction.FromWidget) {
                            if (parsedMessage.action == WidgetMessage.Action.Close) {
                                if (callData.audienceBroadcastId == null) {
                                    close(callWidgetDriver.value, navigator)
                                }
                            } else if (parsedMessage.action == WidgetMessage.Action.ContentLoaded) {
                                isWidgetLoaded = true
                            }
                        }
                    }
                    .launchIn(this)
            }

            if (callData.audienceBroadcastId == null && urlState.value is AsyncData.Success) {
                LaunchedEffect(interceptor, urlState.value) {
                    // The MatrixRTC widget must explicitly confirm it has loaded before the
                    // room call can safely be considered active. Audience playback has its own
                    // connection and media states in the embedded app; it can legitimately take
                    // longer than this while negotiating a receiver and must not be destroyed
                    // after media has already become visible.
                    delay(10.seconds)

                    if (!isWidgetLoaded) {
                        Timber.w("The call took too long to load. Displaying an error before exiting.")

                        // This will display a simple 'Sorry, an error occurred' dialog and force the user to exit the call
                        webViewError = ""
                    }
                }
            }
        }

        fun handleEvent(event: CallScreenEvent) {
            when (event) {
                is CallScreenEvent.Hangup -> {
                    // Audience playback never joins the MatrixRTC membership. It must not wait
                    // for the embedded call page to acknowledge a MatrixRTC hangup: there is no
                    // membership to leave, and waiting here leaves the listener looking stuck.
                    if (callData.audienceBroadcastId != null) {
                        close(callWidgetDriver.value, navigator)
                        return
                    }
                    val widgetId = callWidgetDriver.value?.id
                    val interceptor = messageInterceptor.value
                    if (widgetId != null && interceptor != null && isWidgetLoaded) {
                        // If the call was joined, we need to hang up first. Then the UI will be dismissed automatically.
                        sendHangupMessage(widgetId, interceptor)
                        isWidgetLoaded = false

                        coroutineScope.launch {
                            // Wait for a couple of seconds to receive the hangup message
                            // If we don't get it in time, we close the screen anyway
                            delay(2.seconds)
                            close(callWidgetDriver.value, navigator)
                        }
                    } else {
                        coroutineScope.launch {
                            close(callWidgetDriver.value, navigator)
                        }
                    }
                }
                is CallScreenEvent.SetupMessageChannels -> {
                    messageInterceptor.value = event.widgetMessageInterceptor
                }
                is CallScreenEvent.OnWebViewError -> {
                    if (!ignoreWebViewError) {
                        webViewError = event.description.orEmpty()
                    }
                    // Else ignore the error, give a chance the Element Call to recover by itself.
                }
            }
        }

        return CallScreenState(
            urlState = urlState.value,
            webViewError = webViewError,
            userAgent = userAgent,
            // Listener playback must stay active while the widget is connecting. Reporting it
            // inactive pauses the WebView before it can send content_loaded, which deadlocks
            // audience startup.
            isCallActive = if (callData.audienceBroadcastId != null) {
                urlState.value !is AsyncData.Failure && webViewError == null
            } else {
                isWidgetLoaded
            },
            isAudience = callData.audienceBroadcastId != null,
            eventSink = ::handleEvent,
        )
    }

    private suspend fun fetchRoomCallUrl(
        callData: CallData,
        urlState: MutableState<AsyncData<String>>,
        callWidgetDriver: MutableState<MatrixWidgetDriver?>,
        languageTag: String?,
        theme: String?,
        clientId: String,
    ) {
        urlState.runCatchingUpdatingState {
            val result = callWidgetProvider.getWidget(
                sessionId = callData.sessionId,
                roomId = callData.roomId,
                clientId = clientId,
                isAudioCall = callData.isAudioCall,
                languageTag = languageTag,
                theme = theme,
                audienceBroadcastId = callData.audienceBroadcastId,
            ).getOrThrow()
            callWidgetDriver.value = result.driver
            Timber.d("Call widget driver initialized for sessionId: ${callData.sessionId}, roomId: ${callData.roomId}")
            result.url
        }
    }

    @Composable
    private fun HandleMatrixClientSyncState() {
        val coroutineScope = rememberCoroutineScope()
        DisposableEffect(Unit) {
            val client = matrixClientsProvider.getOrNull(callData.sessionId) ?: return@DisposableEffect onDispose {
                Timber.w("No MatrixClient found for sessionId, can't send call notification: ${callData.sessionId}")
            }
            coroutineScope.launch {
                Timber.d("Observing sync state in-call for sessionId: ${callData.sessionId}")
                client.syncService.syncState
                    .collect { state ->
                        if (state != SyncState.Running) {
                            appForegroundStateService.updateIsInCallState(true)
                        }
                    }
            }
            onDispose {
                Timber.d("Stopped observing sync state in-call for sessionId: ${callData.sessionId}")
                // Make sure we mark the call as ended in the app state
                appForegroundStateService.updateIsInCallState(false)
            }
        }
    }

    private fun parseMessage(message: String): WidgetMessage? {
        return widgetMessageSerializer.deserialize(message).getOrNull()
    }

    private fun WidgetMessage?.isOptionalHostControl(): Boolean {
        return this?.direction == WidgetMessage.Direction.FromWidget &&
            action in setOf(
                WidgetMessage.Action.SetAlwaysOnScreen,
                WidgetMessage.Action.DeviceMute,
            )
    }

    private fun WidgetMessage?.isAudienceBroadcastRequest(): Boolean {
        return this?.direction == WidgetMessage.Direction.FromWidget &&
            action == WidgetMessage.Action.AudienceBroadcastRequest
    }

    private suspend fun handleAudienceBroadcastRequest(message: WidgetMessage): WidgetMessage {
        val data = message.data as? JsonObject
        val method = data?.string("method")
        val path = data?.string("path")
        if (data == null || data.keys.any { it !in AUDIENCE_REQUEST_FIELDS } || method == null || path == null) {
            return message.copy(response = audienceFailureResponse(400, "invalid_audience_widget_request", "Invalid audience request"))
        }
        val broadcastId = BROADCAST_ID_IN_AUDIENCE_PATH.matchEntire(path)?.groupValues?.get(1)
            ?: return message.copy(
                response = audienceFailureResponse(400, "invalid_audience_widget_request", "Invalid audience request"),
            )

        return try {
            // Do not log the URL, request body, or Matrix bearer token.  The method, route
            // class and final status are sufficient to distinguish an authorization denial
            // from a host bridge or relay-lifecycle failure on a physical device.
            Timber.i("Audience widget proxy request: method=%s, path=%s", method, path.substringBefore('?'))
            val response = audienceBroadcastHttpClient.requestAudienceWidget(
                sessionId = callData.sessionId,
                broadcastId = broadcastId,
                method = method,
                path = path,
                body = data["body"]?.takeUnless { it is JsonNull },
            )
            Timber.i(
                "Audience widget proxy response: method=%s, path=%s, status=%d",
                method,
                path.substringBefore('?'),
                response.code,
            )
            if (!response.isSuccessful) {
                val error = audienceJson.parseToJsonElement(response.body) as? JsonObject
                val problem = error?.get("error") as? JsonObject
                message.copy(
                    response = audienceFailureResponse(
                        status = response.code,
                        code = problem?.string("code") ?: "unknown",
                        message = problem?.string("message") ?: "Audience request failed",
                        retryable = problem?.boolean("retryable") ?: false,
                        details = problem?.get("details"),
                    ),
                )
            } else {
                val payload = if (AudienceBroadcastRequestContract.parseEventRequest(method, path) != null) {
                    JsonPrimitive(response.body)
                } else {
                    response.body.takeIf(String::isNotBlank)
                        ?.let { audienceJson.parseToJsonElement(it) }
                        ?: JsonNull
                }
                message.copy(
                    response = JsonObject(
                        mapOf(
                            "ok" to JsonPrimitive(true),
                            "response" to payload,
                        ),
                    ),
                )
            }
        } catch (failure: Throwable) {
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            Timber.w(
                failure,
                "Audience widget proxy failed: method=%s, path=%s, status=%d",
                method,
                path.substringBefore('?'),
                (failure as? AudienceHttpException)?.statusCode ?: 0,
            )
            message.copy(
                response = audienceFailureResponse(
                    status = (failure as? AudienceHttpException)?.statusCode ?: 0,
                    code = "audience_transport_unavailable",
                    message = "Audience service is unavailable",
                    retryable = true,
                ),
            )
        }
    }

    private fun sendHangupMessage(widgetId: String, messageInterceptor: WidgetMessageInterceptor) {
        val message = WidgetMessage(
            direction = WidgetMessage.Direction.ToWidget,
            widgetId = widgetId,
            requestId = "widgetapi-${clock.epochMillis()}",
            action = WidgetMessage.Action.HangUp,
            data = null,
        )
        messageInterceptor.sendMessage(widgetMessageSerializer.serialize(message))
    }

    private fun close(widgetDriver: MatrixWidgetDriver?, navigator: CallScreenNavigator) {
        // `navigator.close()` finishes ElementCallActivity. Activity navigation is a UI
        // operation and must run on the presenter/main dispatcher. Dispatching it to IO
        // made the host close race the WebView teardown and could visibly delay hangup.
        navigator.close()
        // Driver teardown does not mutate the Activity and can outlive the call screen.
        // Keep it off the main thread without delaying the visible exit.
        appCoroutineScope.launch(dispatchers.io) {
            widgetDriver?.close()
        }
    }
}

private fun WidgetMessage.optionalHostControlResponse(): JsonObject = when (action) {
    WidgetMessage.Action.SetAlwaysOnScreen -> JsonObject(mapOf("success" to JsonPrimitive(true)))
    WidgetMessage.Action.DeviceMute -> JsonObject(emptyMap())
    else -> error("Unexpected non-optional widget action: $action")
}

private val audienceJson = Json { ignoreUnknownKeys = true }
private val BROADCAST_ID_IN_AUDIENCE_PATH = Regex(
    "^/meeting-broadcast/v1/broadcasts/(bcast_[A-Za-z0-9_-]+)(?:/.*)?(?:\\?.*)?$",
)
private val AUDIENCE_REQUEST_FIELDS = setOf("method", "path", "body")

private fun audienceFailureResponse(
    status: Int,
    code: String,
    message: String,
    retryable: Boolean = false,
    details: JsonElement? = null,
): JsonObject = JsonObject(
    mapOf(
        "ok" to JsonPrimitive(false),
        "error" to JsonObject(
            mapOf(
                "status" to JsonPrimitive(status),
                "code" to JsonPrimitive(code),
                "message" to JsonPrimitive(message),
                "retryable" to JsonPrimitive(retryable),
                "details" to (details ?: JsonNull),
            ),
        ),
    ),
)

private fun JsonObject.string(name: String): String? =
    (this[name] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.boolean(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()

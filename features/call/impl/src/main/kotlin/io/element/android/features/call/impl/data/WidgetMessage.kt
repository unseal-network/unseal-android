/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class WidgetMessage(
    @SerialName("api") val direction: Direction,
    @SerialName("widgetId") val widgetId: String,
    @SerialName("requestId") val requestId: String,
    @SerialName("action") val action: Action,
    @SerialName("data") val data: JsonElement? = null,
    @SerialName("response") val response: JsonElement? = null,
) {
    @Serializable
    enum class Direction {
        @SerialName("fromWidget")
        FromWidget,

        @SerialName("toWidget")
        ToWidget
    }

    @Serializable
    enum class Action {
        @SerialName("io.element.join")
        Join,

        @SerialName("im.vector.hangup")
        HangUp,

        @SerialName("io.element.close")
        Close,

        @SerialName("send_event")
        SendEvent,

        @SerialName("content_loaded")
        ContentLoaded,

        /**
         * These are optional Element Call host controls. The Android embed does
         * not need to change process-wide state for either one, but it must
         * acknowledge them so an embedded call cannot fail while joining.
         */
        @SerialName("set_always_on_screen")
        SetAlwaysOnScreen,

        @SerialName("io.element.device_mute")
        DeviceMute,

        /**
         * Authenticated relay status requests made by Unseal Call while a
         * regular MatrixRTC participant is in the meeting.  This must be
         * handled by the native host: the embedded widget never receives the
         * Matrix access token directly.
         */
        @SerialName("io.element.unseal.meeting_broadcast_request")
        AudienceBroadcastRequest,
    }
}

/*
 * Copyright (c) 2026 Unseal
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl.audience

/** The single canonical parser for the rolling SSE audience endpoint. */
internal object AudienceBroadcastRequestContract {
    data class EventRequest(
        val broadcastId: String,
        val audienceSessionId: String,
    )

    fun parseEventRequest(method: String, path: String): EventRequest? {
        if (method != "GET") return null
        val match = EVENT_PATH.matchEntire(path) ?: return null
        return EventRequest(
            broadcastId = match.groupValues[1],
            audienceSessionId = match.groupValues[2],
        )
    }

    private val EVENT_PATH = Regex(
        "^/meeting-broadcast/v1/broadcasts/(bcast_[A-Za-z0-9_-]+)/" +
            "audience-sessions/(aud_[A-Za-z0-9_-]+)/events" +
            "\\?generation=(0|[1-9][0-9]*)&after_revision=(0|[1-9][0-9]*)$",
    )
}

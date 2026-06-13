/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.factories.event

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemAiContent
import io.element.android.libraries.di.RoomScope

@SingleIn(RoomScope::class)
@Inject
class AiStreamContentCache {
    private val contents = linkedMapOf<String, TimelineItemAiContent>()
    private val lock = Any()

    fun get(streamId: String): TimelineItemAiContent? {
        return synchronized(lock) {
            contents.remove(streamId)?.also { content ->
                contents[streamId] = content
            }
        }
    }

    fun put(content: TimelineItemAiContent) {
        val streamId = content.streamId ?: return
        synchronized(lock) {
            contents[streamId] = content
            while (contents.size > MAX_CACHED_CONTENTS) {
                val eldestKey = contents.keys.firstOrNull() ?: return@synchronized
                contents.remove(eldestKey)
            }
        }
    }

    private companion object {
        const val MAX_CACHED_CONTENTS = 256
    }
}

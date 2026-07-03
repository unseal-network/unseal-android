/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.factories

import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.features.messages.impl.roomkey.RoomKeyRecoveryStatus
import io.element.android.features.messages.impl.timeline.diff.TimelineItemsCacheInvalidator
import io.element.android.features.messages.impl.timeline.factories.event.TimelineItemEventFactory
import io.element.android.features.messages.impl.timeline.factories.virtual.TimelineItemVirtualFactory
import io.element.android.features.messages.impl.timeline.groups.TimelineItemGrouper
import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemEncryptedContent
import io.element.android.libraries.androidutils.diff.DiffCacheUpdater
import io.element.android.libraries.androidutils.diff.MutableListDiffCache
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.room.RoomMember
import io.element.android.libraries.matrix.api.timeline.MatrixTimelineItem
import io.element.android.libraries.matrix.api.timeline.item.event.FailedToParseStateContent
import io.element.android.libraries.matrix.api.timeline.item.event.LiveLocationContent
import io.element.android.libraries.matrix.api.timeline.item.event.OtherState
import io.element.android.libraries.matrix.api.timeline.item.event.StateContent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@AssistedInject
class TimelineItemsFactory(
    @Assisted private val config: TimelineItemsFactoryConfig,
    eventItemFactoryCreator: TimelineItemEventFactory.Creator,
    private val dispatchers: CoroutineDispatchers,
    private val virtualItemFactory: TimelineItemVirtualFactory,
    private val timelineItemGrouper: TimelineItemGrouper,
) {
    @AssistedFactory
    interface Creator {
        fun create(config: TimelineItemsFactoryConfig): TimelineItemsFactory
    }

    private val eventItemFactory = eventItemFactoryCreator.create(config)
    private val _timelineItems = MutableSharedFlow<ImmutableList<TimelineItem>>(replay = 1)
    private val lock = Mutex()
    private val diffCache = MutableListDiffCache<TimelineItem>()
    private val diffCacheUpdater = DiffCacheUpdater<MatrixTimelineItem, TimelineItem>(
        diffCache = diffCache,
        detectMoves = false,
        cacheInvalidator = TimelineItemsCacheInvalidator()
    ) { old, new ->
        if (old is MatrixTimelineItem.Event && new is MatrixTimelineItem.Event) {
            old.uniqueId == new.uniqueId
        } else {
            false
        }
    }

    val timelineItems: Flow<ImmutableList<TimelineItem>> = _timelineItems.distinctUntilChanged()

    suspend fun replaceWith(
        timelineItems: List<MatrixTimelineItem>,
        roomMembers: List<RoomMember>,
        roomKeyRecoveryStatuses: Map<String, RoomKeyRecoveryStatus> = emptyMap(),
    ) = withContext(dispatchers.computation) {
        lock.withLock {
            diffCacheUpdater.updateWith(timelineItems)
            buildAndEmitTimelineItemStates(timelineItems, roomMembers, roomKeyRecoveryStatuses)
        }
    }

    private suspend fun buildAndEmitTimelineItemStates(
        timelineItems: List<MatrixTimelineItem>,
        roomMembers: List<RoomMember>,
        roomKeyRecoveryStatuses: Map<String, RoomKeyRecoveryStatus>,
    ) {
        val roomMembersByUserId = roomMembersByUserId(roomMembers)
        val newTimelineItemStates = ArrayList<TimelineItem>()
        for (index in diffCache.indices().reversed()) {
            val matrixTimelineItem = timelineItems[index]
            if (matrixTimelineItem is MatrixTimelineItem.Event && matrixTimelineItem.isHiddenMetadataEvent()) {
                diffCache[index] = null
                continue
            }
            val cacheItem = diffCache.get(index)
            if (cacheItem == null) {
                buildAndCacheItem(timelineItems, index, roomMembersByUserId, roomKeyRecoveryStatuses)?.also { timelineItemState ->
                    newTimelineItemStates.add(timelineItemState)
                }
            } else {
                val updatedItem = if (cacheItem is TimelineItem.Event && shouldUpdateCachedEvent(cacheItem, matrixTimelineItem, roomMembersByUserId)) {
                    eventItemFactory.update(
                        timelineItem = cacheItem,
                        receivedMatrixTimelineItem = matrixTimelineItem as MatrixTimelineItem.Event,
                        roomMembersByUserId = roomMembersByUserId,
                        roomKeyRecoveryStatuses = roomKeyRecoveryStatuses,
                    )
                } else {
                    cacheItem
                }
                newTimelineItemStates.add(updatedItem)
            }
        }
        val result = timelineItemGrouper.group(newTimelineItemStates).toImmutableList()
        this._timelineItems.emit(result)
    }

    private suspend fun buildAndCacheItem(
        timelineItems: List<MatrixTimelineItem>,
        index: Int,
        roomMembersByUserId: Map<UserId, RoomMember>,
        roomKeyRecoveryStatuses: Map<String, RoomKeyRecoveryStatus>,
    ): TimelineItem? {
        val timelineItem =
            when (val currentTimelineItem = timelineItems[index]) {
                is MatrixTimelineItem.Event -> eventItemFactory.create(currentTimelineItem, index, timelineItems, roomMembersByUserId, roomKeyRecoveryStatuses)
                is MatrixTimelineItem.Virtual -> virtualItemFactory.create(currentTimelineItem)
                MatrixTimelineItem.Other -> null
            }
        diffCache[index] = timelineItem
        return timelineItem
    }

    private fun shouldUpdateCachedEvent(
        cachedItem: TimelineItem.Event,
        matrixTimelineItem: MatrixTimelineItem,
        roomMembersByUserId: Map<UserId, RoomMember>,
    ): Boolean {
        if (matrixTimelineItem !is MatrixTimelineItem.Event) return false
        if (cachedItem.content is TimelineItemEncryptedContent) return true
        return config.computeReadReceipts &&
            roomMembersByUserId.isNotEmpty() &&
            matrixTimelineItem.event.receipts.isNotEmpty()
    }

    private fun roomMembersByUserId(roomMembers: List<RoomMember>): Map<UserId, RoomMember> {
        if (!config.computeReadReceipts || roomMembers.isEmpty()) {
            return emptyMap()
        }
        return roomMembers.associateBy { it.userId }
    }

    private fun MatrixTimelineItem.Event.isHiddenMetadataEvent(): Boolean {
        if (event.content !is LiveLocationContent && event.timelineItemDebugInfoProvider().originalJson?.contains(EVENT_TYPE_BEACON_INFO) == true) {
            return true
        }
        return event.content.isBeaconInfoStateContent()
    }

    private fun io.element.android.libraries.matrix.api.timeline.item.event.EventContent.isBeaconInfoStateContent(): Boolean {
        if (this is FailedToParseStateContent) {
            return eventType == EVENT_TYPE_BEACON_INFO
        }
        val stateContent = this as? StateContent ?: return false
        val customState = stateContent.content as? OtherState.Custom ?: return false
        return customState.eventType == EVENT_TYPE_BEACON_INFO
    }
}

private const val EVENT_TYPE_BEACON_INFO = "org.matrix.msc3672.beacon_info"

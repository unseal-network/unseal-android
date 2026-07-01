/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.factories.event

import dev.zacsweers.metro.Inject
import io.element.android.features.location.api.Location
import io.element.android.features.messages.impl.timeline.model.event.RtcNotificationState
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemEventContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemLegacyCallInviteContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemLocationContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemRtcNotificationContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemAiContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemUnknownContent
import io.element.android.features.messages.impl.roomkey.RoomKeyRecoveryRequestParser
import io.element.android.features.messages.impl.roomkey.RoomKeyRecoveryStatus
import io.element.android.libraries.dateformatter.api.DateFormatter
import io.element.android.libraries.dateformatter.api.DateFormatterMode
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.room.location.AssetType
import io.element.android.libraries.matrix.api.timeline.item.event.CallNotifyContent
import io.element.android.libraries.matrix.api.timeline.item.event.EventContent
import io.element.android.libraries.matrix.api.timeline.item.event.EventTimelineItem
import io.element.android.libraries.matrix.api.timeline.item.event.FailedToParseMessageLikeContent
import io.element.android.libraries.matrix.api.timeline.item.event.FailedToParseStateContent
import io.element.android.libraries.matrix.api.timeline.item.event.LegacyCallInviteContent
import io.element.android.libraries.matrix.api.timeline.item.event.LiveLocationContent
import io.element.android.libraries.matrix.api.timeline.item.event.MessageContent
import io.element.android.libraries.matrix.api.timeline.item.event.PollContent
import io.element.android.libraries.matrix.api.timeline.item.event.ProfileChangeContent
import io.element.android.libraries.matrix.api.timeline.item.event.ProfileDetails
import io.element.android.libraries.matrix.api.timeline.item.event.RedactedContent
import io.element.android.libraries.matrix.api.timeline.item.event.RoomMembershipContent
import io.element.android.libraries.matrix.api.timeline.item.event.StateContent
import io.element.android.libraries.matrix.api.timeline.item.event.StickerContent
import io.element.android.libraries.matrix.api.timeline.item.event.UnableToDecryptContent
import io.element.android.libraries.matrix.api.timeline.item.event.UnknownContent
import io.element.android.libraries.matrix.api.timeline.item.event.getDisambiguatedDisplayName
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.services.toolbox.api.strings.StringProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import timber.log.Timber

@Inject
class TimelineItemContentFactory(
    private val messageFactory: TimelineItemContentMessageFactory,
    private val aiMessageContentParser: AiMessageContentParser,
    private val aiStreamHandleStore: AiStreamHandleStore,
    private val aiStreamContentCache: AiStreamContentCache,
    private val aiSdkStreamReducer: AiSdkStreamReducer,
    private val gameMessageContentParser: GameMessageContentParser,
    private val redactedMessageFactory: TimelineItemContentRedactedFactory,
    private val stickerFactory: TimelineItemContentStickerFactory,
    private val pollFactory: TimelineItemContentPollFactory,
    private val utdFactory: TimelineItemContentUTDFactory,
    private val roomMembershipFactory: TimelineItemContentRoomMembershipFactory,
    private val profileChangeFactory: TimelineItemContentProfileChangeFactory,
    private val stateFactory: TimelineItemContentStateFactory,
    private val failedToParseMessageFactory: TimelineItemContentFailedToParseMessageFactory,
    private val failedToParseStateFactory: TimelineItemContentFailedToParseStateFactory,
    private val sessionId: SessionId,
    private val dateFormatter: DateFormatter,
    private val stringProvider: StringProvider,
) {
    private val roomKeyRecoveryRequestParser = RoomKeyRecoveryRequestParser()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun create(
        eventTimelineItem: EventTimelineItem,
        roomKeyRecoveryStatuses: Map<String, RoomKeyRecoveryStatus> = emptyMap(),
    ): TimelineItemEventContent {
        // Unseal AI/assistant messages can arrive either as "m.aisdk.protocol" or as a normal
        // message with a content.stream pointer. The Matrix SDK maps the latter to regular text,
        // so the original JSON must be inspected before falling back to normal rendering.
        val itemContent = eventTimelineItem.content
        val originalJson = eventTimelineItem.timelineItemDebugInfoProvider().originalJson
        aiMessageContentParser.parse(
            originalJson = originalJson,
            isEdited = itemContent.isEdited(),
            fallbackSender = eventTimelineItem.sender.value,
        )?.let { aiContent ->
            return hydrateAiContent(aiContent)
        }

        parseBeaconInfoContent(eventTimelineItem, originalJson)?.let { liveLocationContent ->
            return liveLocationContent
        }

        // Game invite messages use custom fields not exposed by the typed SDK.
        // Parse from the original JSON before falling back to OtherMessageType → plain text.
        if (itemContent is MessageContent) {
            gameMessageContentParser.parse(
                originalJson = originalJson,
                senderUserId = eventTimelineItem.sender,
            )?.let { gameContent ->
                Timber.tag("TimelineItemContentFactory").d(
                    "Game message parsed: gameId=%d gameRoomId=%s",
                    gameContent.gameId,
                    gameContent.gameRoomId,
                )
                return gameContent
            }
        }

        return create(
            itemContent = eventTimelineItem.content,
            eventId = eventTimelineItem.eventId,
            isEditable = eventTimelineItem.isEditable,
            sender = eventTimelineItem.sender,
            senderProfile = eventTimelineItem.senderProfile,
            roomKeyRecoveryStatus = eventTimelineItem.roomKeyRecoveryStatus(roomKeyRecoveryStatuses),
        )
    }

    private suspend fun hydrateAiContent(aiContent: TimelineItemAiContent): TimelineItemAiContent {
        val streamId = aiContent.streamId ?: return aiContent
        // Only skip DB load when the full stream snapshot has already been mapped into parts.
        // hasRichParts also triggers on toolCalls populated from the Matrix event JSON, which does
        // NOT include visibleParts — so we must check parts directly.
        if (aiContent.isTerminal && aiContent.parts.isNotEmpty()) {
            Timber.tag("WsDbg").d("hydrateAi SKIP stream=%s parts=%d (snapshot already mapped)", streamId, aiContent.parts.size)
            return aiContent
        }
        aiStreamContentCache.get(streamId)?.let {
            Timber.tag("WsDbg").d("hydrateAi CACHE HIT stream=%s parts=%d", streamId, it.parts.size)
            return it.withFallbackMetadata(aiContent)
        }
        val cachedSnapshot = aiStreamHandleStore.cachedCompletedSnapshot(streamId)
        if (cachedSnapshot == null) {
            Timber.tag("WsDbg").w("hydrateAi NO SNAPSHOT stream=%s body=%s", streamId, aiContent.body.take(40))
            return aiContent
        }
        Timber.tag("WsDbg").d("hydrateAi LOADED snapshot stream=%s parts=%d", streamId, cachedSnapshot.parts.size)
        return aiSdkStreamReducer.mapSnapshot(
            snapshot = cachedSnapshot,
            isEdited = aiContent.isEdited,
            sender = aiContent.sender,
        )
            .withFallbackMetadata(aiContent)
            .also {
                Timber.tag("WsDbg").d("hydrateAi MAPPED stream=%s visibleParts=%d", streamId, it.visibleParts.size)
                aiStreamContentCache.put(it)
            }
    }

    suspend fun create(
        itemContent: EventContent,
        eventId: EventId?,
        isEditable: Boolean,
        sender: UserId,
        senderProfile: ProfileDetails,
        roomKeyRecoveryStatus: RoomKeyRecoveryStatus? = null,
    ): TimelineItemEventContent {
        val isOutgoing = sessionId == sender
        return when (itemContent) {
            is FailedToParseMessageLikeContent -> failedToParseMessageFactory.create(itemContent)
            is FailedToParseStateContent -> failedToParseStateFactory.create(itemContent)
            is MessageContent -> {
                messageFactory.create(
                    senderId = sender,
                    senderProfile = senderProfile,
                    content = itemContent,
                    eventId = eventId,
                    isOutgoing = isOutgoing,
                )
            }
            is ProfileChangeContent -> {
                val senderDisambiguatedDisplayName = senderProfile.getDisambiguatedDisplayName(sender)
                profileChangeFactory.create(itemContent, isOutgoing, sender, senderDisambiguatedDisplayName)
            }
            is RedactedContent -> redactedMessageFactory.create(itemContent)
            is RoomMembershipContent -> {
                val senderDisambiguatedDisplayName = senderProfile.getDisambiguatedDisplayName(sender)
                roomMembershipFactory.create(itemContent, isOutgoing, sender, senderDisambiguatedDisplayName)
            }
            is LegacyCallInviteContent -> TimelineItemLegacyCallInviteContent
            is StateContent -> {
                val senderDisambiguatedDisplayName = senderProfile.getDisambiguatedDisplayName(sender)
                stateFactory.create(itemContent, isOutgoing, sender, senderDisambiguatedDisplayName)
            }
            is StickerContent -> stickerFactory.create(itemContent)
            is PollContent -> pollFactory.create(eventId, isEditable, isOutgoing, itemContent)
            is UnableToDecryptContent -> utdFactory.create(itemContent, roomKeyRecoveryStatus)
            is CallNotifyContent -> TimelineItemRtcNotificationContent(
                callIntent = itemContent.callIntent,
                state = if (itemContent.declinedBy.isEmpty()) {
                    RtcNotificationState.Started
                } else {
                    RtcNotificationState.Declined(itemContent.declinedBy.any { it == sessionId })
                }
            )
            is UnknownContent -> TimelineItemUnknownContent
            is LiveLocationContent -> {
                val lastKnownLocation = itemContent.locations.mapNotNull { beacon ->
                    Location.fromGeoUri(beacon.geoUri)
                }.lastOrNull()

                val endsAt = dateFormatter.format(
                    timestamp = itemContent.endTimestamp,
                    mode = DateFormatterMode.TimeOnly
                )
                // Always create content, location can be null for "loading/waiting" state
                TimelineItemLocationContent(
                    description = itemContent.description?.trimEnd(),
                    assetType = itemContent.assetType,
                    senderId = sender,
                    senderProfile = senderProfile,
                    mode = TimelineItemLocationContent.Mode.Live(
                        lastKnownLocation = lastKnownLocation,
                        isActive = itemContent.isLive,
                        endsAt = stringProvider.getString(CommonStrings.common_ends_at, endsAt),
                        endTimestamp = itemContent.endTimestamp,
                        isOwnUser = sessionId == sender
                    ),
                )
            }
        }
    }

    private fun parseBeaconInfoContent(
        eventTimelineItem: EventTimelineItem,
        originalJson: String?,
    ): TimelineItemLocationContent? {
        val raw = originalJson?.takeIf { it.isNotBlank() } ?: return null
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
        if (root.string("type") != EVENT_TYPE_BEACON_INFO) return null
        val content = root.obj("content") ?: return null
        val beaconInfo = content.obj("m.beacon_info") ?: content
        val startTimestamp = content.long("org.matrix.msc3488.ts")
            ?: content.long("m.ts")
            ?: root.long("origin_server_ts")
            ?: eventTimelineItem.timestamp
        val timeout = beaconInfo.long("timeout") ?: content.long("timeout") ?: 0L
        val endTimestamp = startTimestamp + timeout
        val lastKnownLocation = content.location() ?: beaconInfo.location()
        val endsAt = dateFormatter.format(
            timestamp = endTimestamp,
            mode = DateFormatterMode.TimeOnly
        )
        return TimelineItemLocationContent(
            description = beaconInfo.string("description")?.trimEnd()
                ?: content.string("description")?.trimEnd(),
            assetType = beaconInfo.assetType() ?: content.assetType(),
            senderId = eventTimelineItem.sender,
            senderProfile = eventTimelineItem.senderProfile,
            mode = TimelineItemLocationContent.Mode.Live(
                lastKnownLocation = lastKnownLocation,
                isActive = beaconInfo.boolean("live") ?: content.boolean("live") ?: true,
                endsAt = stringProvider.getString(CommonStrings.common_ends_at, endsAt),
                endTimestamp = endTimestamp,
                isOwnUser = sessionId == eventTimelineItem.sender,
            ),
        )
    }

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    private fun JsonObject.long(key: String): Long? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()

    private fun JsonObject.boolean(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()

    private fun JsonObject.assetType(): AssetType? {
        return when (obj("m.asset")?.string("type") ?: string("asset_type")) {
            "m.self", "sender" -> AssetType.SENDER
            "m.pin", "pin" -> AssetType.PIN
            null -> null
            else -> AssetType.UNKNOWN
        }
    }

    private fun JsonObject.location(): Location? {
        val geoUri = obj("m.location")?.string("uri")
            ?: obj("org.matrix.msc3488.location")?.string("uri")
            ?: string("geo_uri")
        return geoUri?.let(Location::fromGeoUri)
    }

    private fun EventTimelineItem.roomKeyRecoveryStatus(
        roomKeyRecoveryStatuses: Map<String, RoomKeyRecoveryStatus>,
    ): RoomKeyRecoveryStatus? {
        if (content !is UnableToDecryptContent) return null
        val request = roomKeyRecoveryRequestParser.parse(timelineItemDebugInfoProvider().originalJson) ?: return null
        return roomKeyRecoveryStatuses[request.identityKey]
    }

    private fun EventContent.isEdited(): Boolean {
        return when (this) {
            is MessageContent -> isEdited
            is PollContent -> isEdited
            else -> false
        }
    }
}

private fun TimelineItemAiContent.withFallbackMetadata(fallback: TimelineItemAiContent): TimelineItemAiContent {
    return copy(
        isEdited = fallback.isEdited,
        sender = sender ?: fallback.sender,
        roomId = roomId ?: fallback.roomId,
        eventId = eventId ?: fallback.eventId,
    )
}

private const val EVENT_TYPE_BEACON_INFO = "org.matrix.msc3672.beacon_info"

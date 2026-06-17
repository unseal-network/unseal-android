/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.fixtures

import io.element.android.features.messages.impl.messagesummary.FakeMessageSummaryFormatter
import io.element.android.features.messages.impl.timeline.factories.TimelineItemsFactory
import io.element.android.features.messages.impl.timeline.factories.TimelineItemsFactoryConfig
import io.element.android.features.messages.impl.timeline.factories.event.AiMessageContentParser
import io.element.android.features.messages.impl.timeline.factories.event.AiSdkStreamReducer
import io.element.android.features.messages.impl.timeline.factories.event.AiStreamContentCache
import io.element.android.features.messages.impl.timeline.factories.event.AiStreamHandleStore
import io.element.android.features.messages.impl.timeline.factories.event.GameMessageContentParser
import io.element.android.features.messages.impl.timeline.factories.event.TimelineItemContentFactory
import io.element.android.features.messages.impl.timeline.factories.event.TimelineItemContentFailedToParseMessageFactory
import io.element.android.features.messages.impl.timeline.factories.event.TimelineItemContentFailedToParseStateFactory
import io.element.android.features.messages.impl.timeline.factories.event.TimelineItemContentMessageFactory
import io.element.android.features.messages.impl.timeline.factories.event.TimelineItemContentPollFactory
import io.element.android.features.messages.impl.timeline.factories.event.TimelineItemContentProfileChangeFactory
import io.element.android.features.messages.impl.timeline.factories.event.TimelineItemContentRedactedFactory
import io.element.android.features.messages.impl.timeline.factories.event.TimelineItemContentRoomMembershipFactory
import io.element.android.features.messages.impl.timeline.factories.event.TimelineItemContentStateFactory
import io.element.android.features.messages.impl.timeline.factories.event.TimelineItemContentStickerFactory
import io.element.android.features.messages.impl.timeline.factories.event.TimelineItemContentUTDFactory
import io.element.android.features.messages.impl.timeline.factories.event.TimelineItemEventFactory
import io.element.android.features.messages.impl.timeline.factories.virtual.TimelineItemDaySeparatorFactory
import io.element.android.features.messages.impl.timeline.factories.virtual.TimelineItemVirtualFactory
import io.element.android.features.messages.impl.timeline.groups.TimelineItemGrouper
import io.element.android.features.messages.impl.utils.FakeTextPillificationHelper
import io.element.android.features.messages.test.timeline.FakeHtmlConverterProvider
import io.element.android.features.poll.test.pollcontent.FakePollContentStateFactory
import io.element.android.libraries.androidutils.filesize.FakeFileSizeFormatter
import io.element.android.libraries.dateformatter.test.FakeDateFormatter
import io.element.android.libraries.eventformatter.api.TimelineEventFormatter
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.timeline.item.event.EventContent
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.permalink.FakePermalinkParser
import io.element.android.libraries.agentstream.api.AGENT_STREAM_SCHEMA_VERSION
import io.element.android.libraries.agentstream.api.AgentStreamClient
import io.element.android.libraries.agentstream.api.StreamHandle
import io.element.android.libraries.agentstream.api.StreamListener
import io.element.android.libraries.agentstream.api.StreamRequest
import io.element.android.libraries.agentstream.api.StreamSnapshot
import io.element.android.libraries.agentstream.api.StreamStatus
import io.element.android.libraries.agentstream.api.StreamStorageProvider
import io.element.android.libraries.agentstream.api.StreamSubscription
import io.element.android.libraries.mediaviewer.test.util.FileExtensionExtractorWithoutValidation
import io.element.android.services.toolbox.test.strings.FakeStringProvider
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.test.TestScope

internal fun TestScope.aTimelineItemsFactoryCreator(): TimelineItemsFactory.Creator {
    return object : TimelineItemsFactory.Creator {
        override fun create(config: TimelineItemsFactoryConfig): TimelineItemsFactory {
            return aTimelineItemsFactory(config)
        }
    }
}

internal fun aTimelineItemContentFactory(
    timelineEventFormatter: TimelineEventFormatter = aTimelineEventFormatter(),
    matrixClient: FakeMatrixClient = FakeMatrixClient(),
    aiStreamHandleStore: AiStreamHandleStore = AiStreamHandleStore(NoopAgentStreamClient, NoopStreamStorageProvider),
    aiStreamContentCache: AiStreamContentCache = AiStreamContentCache(),
    aiSdkStreamReducer: AiSdkStreamReducer = AiSdkStreamReducer(),
): TimelineItemContentFactory = TimelineItemContentFactory(
    messageFactory = TimelineItemContentMessageFactory(
        fileSizeFormatter = FakeFileSizeFormatter(),
        fileExtensionExtractor = FileExtensionExtractorWithoutValidation(),
        htmlConverterProvider = FakeHtmlConverterProvider(),
        permalinkParser = FakePermalinkParser(),
        textPillificationHelper = FakeTextPillificationHelper(),
    ),
    aiMessageContentParser = AiMessageContentParser(),
    aiStreamHandleStore = aiStreamHandleStore,
    aiStreamContentCache = aiStreamContentCache,
    aiSdkStreamReducer = aiSdkStreamReducer,
    gameMessageContentParser = GameMessageContentParser(),
    redactedMessageFactory = TimelineItemContentRedactedFactory(),
    stickerFactory = TimelineItemContentStickerFactory(
        fileSizeFormatter = FakeFileSizeFormatter(),
        fileExtensionExtractor = FileExtensionExtractorWithoutValidation()
    ),
    pollFactory = TimelineItemContentPollFactory(FakePollContentStateFactory()),
    utdFactory = TimelineItemContentUTDFactory(),
    roomMembershipFactory = TimelineItemContentRoomMembershipFactory(timelineEventFormatter),
    profileChangeFactory = TimelineItemContentProfileChangeFactory(timelineEventFormatter),
    stateFactory = TimelineItemContentStateFactory(timelineEventFormatter),
    failedToParseMessageFactory = TimelineItemContentFailedToParseMessageFactory(),
    failedToParseStateFactory = TimelineItemContentFailedToParseStateFactory(),
    sessionId = matrixClient.sessionId,
    dateFormatter = FakeDateFormatter(),
    stringProvider = FakeStringProvider(),
)

internal fun TestScope.aTimelineItemsFactory(
    config: TimelineItemsFactoryConfig,
    aiStreamHandleStore: AiStreamHandleStore = AiStreamHandleStore(NoopAgentStreamClient, NoopStreamStorageProvider),
): TimelineItemsFactory {
    val matrixClient = FakeMatrixClient()
    return TimelineItemsFactory(
        dispatchers = testCoroutineDispatchers(),
        eventItemFactoryCreator = object : TimelineItemEventFactory.Creator {
            override fun create(config: TimelineItemsFactoryConfig): TimelineItemEventFactory {
                return TimelineItemEventFactory(
                    contentFactory = aTimelineItemContentFactory(
                        matrixClient = matrixClient,
                        aiStreamHandleStore = aiStreamHandleStore,
                    ),
                    matrixClient = matrixClient,
                    dateFormatter = FakeDateFormatter(),
                    permalinkParser = FakePermalinkParser(),
                    config = config,
                    summaryFormatter = FakeMessageSummaryFormatter(),
                )
            }
        },
        virtualItemFactory = TimelineItemVirtualFactory(
            daySeparatorFactory = TimelineItemDaySeparatorFactory(
                FakeDateFormatter()
            ),
        ),
        timelineItemGrouper = TimelineItemGrouper(),
        config = config
    )
}

private object NoopAgentStreamClient : AgentStreamClient {
    override fun getStream(request: StreamRequest): StreamHandle {
        return object : StreamHandle {
            private val snapshot = StreamSnapshot(
                schemaVersion = AGENT_STREAM_SCHEMA_VERSION,
                streamId = request.streamId,
                status = StreamStatus.Loading,
                parts = emptyList(),
                rawEvents = emptyList(),
                updatedAtMs = 0L,
                completedAtMs = null,
                error = null,
            )

            override fun snapshot(): StreamSnapshot = snapshot
            override fun subscribe(listener: StreamListener): StreamSubscription {
                listener.onSnapshot(snapshot)
                return object : StreamSubscription {
                    override fun cancel() = Unit
                }
            }
            override fun refresh() = Unit
            override fun cancel() = Unit
        }
    }
}

private object NoopStreamStorageProvider : StreamStorageProvider {
    override suspend fun load(streamId: String): StreamSnapshot? = null
    override suspend fun save(snapshot: StreamSnapshot) = Unit
    override suspend fun delete(streamId: String) = Unit
}

internal fun aTimelineEventFormatter(): TimelineEventFormatter {
    return object : TimelineEventFormatter {
        override fun format(content: EventContent, isOutgoing: Boolean, sender: UserId, senderDisambiguatedDisplayName: String): CharSequence? {
            return ""
        }
    }
}

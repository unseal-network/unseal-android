/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.R
import io.element.android.features.messages.impl.timeline.model.event.AiDataStreamPart
import io.element.android.features.messages.impl.timeline.model.event.CardResponseState
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemAiContent
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class TimelineItemAiViewLogicTest {
    @Test
    fun `stream placeholder body is not rendered as markdown fallback`() {
        assertThat(aiContent(body = "thinking", streamId = "stream-1").shouldRenderBodyFallback()).isFalse()
        assertThat(aiContent(body = "Thinking...", streamId = "stream-1").shouldRenderBodyFallback()).isFalse()
        assertThat(aiContent(body = "Thinking…", streamId = "stream-1").shouldRenderBodyFallback()).isFalse()
        assertThat(aiContent(body = "loading", streamId = "stream-1").shouldRenderBodyFallback()).isFalse()
        assertThat(aiContent(body = "stream-1", streamId = "stream-1").shouldRenderBodyFallback()).isFalse()
    }

    @Test
    fun `real stream body is rendered as markdown fallback before parts arrive`() {
        assertThat(aiContent(body = "Let me check Gmail.", streamId = "stream-1").shouldRenderBodyFallback()).isTrue()
    }

    @Test
    fun `non stream body still renders as markdown fallback`() {
        assertThat(aiContent(body = "thinking", streamId = null).shouldRenderBodyFallback()).isTrue()
    }

    @Test
    fun `duplicate tool card payload text is detected without hiding summaries`() {
        val jsonPayload = """
            {
              "cards": [
                { "hotel_name": "Upper House Chengdu", "rating": 4.8 }
              ],
              "component": "HotelBookingCard"
            }
        """.trimIndent()
        val fencedPayload = """
            ```json
            $jsonPayload
            ```
        """.trimIndent()

        assertThat(jsonPayload.looksLikeDuplicateToolCardPayload()).isTrue()
        assertThat(fencedPayload.looksLikeDuplicateToolCardPayload()).isTrue()
        assertThat("Here are the current market quotes for Apple and NVIDIA.".looksLikeDuplicateToolCardPayload()).isFalse()
    }

    @Test
    fun `streaming reveal advances in small adaptive steps`() {
        assertThat(streamingRevealCodePointStep(remainingTextUnits = 12, totalTextUnits = 120)).isEqualTo(1)
        assertThat(streamingRevealCodePointStep(remainingTextUnits = 60, totalTextUnits = 120)).isEqualTo(2)
        assertThat(streamingRevealCodePointStep(remainingTextUnits = 180, totalTextUnits = 240)).isEqualTo(4)
        assertThat(streamingRevealCodePointStep(remainingTextUnits = 400, totalTextUnits = 600)).isEqualTo(8)
        assertThat(streamingRevealCodePointStep(remainingTextUnits = 900, totalTextUnits = 1_000)).isEqualTo(16)
        assertThat(streamingRevealCodePointStep(remainingTextUnits = 1_800, totalTextUnits = 2_000)).isEqualTo(32)
    }

    @Test
    fun `streaming reveal caps frame rate for long markdown`() {
        assertThat(streamingRevealFrameDelayMs(120)).isEqualTo(28L)
        assertThat(streamingRevealFrameDelayMs(800)).isEqualTo(40L)
        assertThat(streamingRevealFrameDelayMs(1_600)).isEqualTo(56L)
    }

    @Test
    fun `streaming reveal does not split surrogate pairs`() {
        val text = "A\uD83E\uDD16B"

        val first = nextStreamingRevealEndIndex(currentEndIndex = 0, text = text)
        val second = nextStreamingRevealEndIndex(currentEndIndex = first, text = text)

        assertThat(text.substring(0, first)).isEqualTo("A")
        assertThat(text.substring(0, second)).isEqualTo("A\uD83E\uDD16")
    }

    @Test
    fun `streaming reveal catches up large snapshots without showing the whole paragraph at once`() {
        val text = "x".repeat(320)

        val nextIndex = nextStreamingRevealEndIndex(currentEndIndex = 0, text = text)

        assertThat(nextIndex).isEqualTo(8)
    }

    @Test
    fun `streaming reveal catches up very large snapshots in bounded chunks`() {
        val text = "x".repeat(2_000)

        val nextIndex = nextStreamingRevealEndIndex(currentEndIndex = 0, text = text)

        assertThat(nextIndex).isEqualTo(32)
    }

    @Test
    fun `suspended moltbook card sends response once and disables after success`() = runAndroidComposeUiTest<ComponentActivity> {
        val requests = mutableListOf<Pair<String, String>>()
        val content = aiContentWithSuspendedMoltbookCard()

        setContent {
            TimelineItemAiView(
                content = content,
                onLinkClick = {},
                onLinkLongClick = {},
                onLongClick = null,
                onSendCardResponse = { eventId, actionId ->
                    requests += eventId to actionId
                    true
                },
            )
        }

        onNodeWithText(activity!!.getString(R.string.screen_room_timeline_ai_continue)).performClick()
        waitForIdle()

        assertThat(requests).containsExactly("\$event-1" to "continue")
        onDisabledButtonWithText(activity!!.getString(R.string.screen_room_timeline_tool_card_linear_status_done)).assertIsNotEnabled()
    }

    @Test
    fun `suspended moltbook card with response marker starts disabled`() = runAndroidComposeUiTest<ComponentActivity> {
        var requestCount = 0
        val content = aiContentWithSuspendedMoltbookCard(
            cardResponseState = CardResponseState(actioned = true, actionId = "continue", eventId = "\$response-1")
        )

        setContent {
            TimelineItemAiView(
                content = content,
                onLinkClick = {},
                onLinkLongClick = {},
                onLongClick = null,
                onSendCardResponse = { _, _ ->
                    requestCount++
                    true
                },
            )
        }

        onDisabledButtonWithText(activity!!.getString(R.string.screen_room_timeline_tool_card_linear_status_done)).assertIsNotEnabled()
        assertThat(requestCount).isEqualTo(0)
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteractionsProvider.onDisabledButtonWithText(text: String) =
        onNode(hasText(text) and hasClickAction())

    private fun aiContent(body: String, streamId: String?): TimelineItemAiContent {
        return TimelineItemAiContent(
            body = body,
            isEdited = false,
            isStreaming = true,
            streamId = streamId,
            thinkingSteps = persistentListOf(),
            toolCalls = persistentListOf(),
            sources = persistentListOf(),
            quickActions = persistentListOf(),
        )
    }

    private fun aiContentWithSuspendedMoltbookCard(
        cardResponseState: CardResponseState = CardResponseState(),
    ): TimelineItemAiContent {
        val part = AiDataStreamPart(
            id = "suspend-moltbook",
            state = "output-available",
            type = "data-tool-call-suspended",
            payload = """
                {
                  "toolCallId": "moltbook-register-1",
                  "toolName": "moltbookRegister",
                  "targetUserId": "@ruihan:keepsecret.io",
                  "title": "Connect Moltbook",
                  "reason": "Enter your Moltbook credentials to register this agent.",
                  "suspendPayload": {
                    "kind": "moltbookRegister",
                    "agentId": "agent-mail",
                    "moltyName": "Mail Agent",
                    "claimUrl": "https://moltbook.example/claim/abc",
                    "verificationCode": "842193"
                  }
                }
            """.trimIndent(),
        )
        return TimelineItemAiContent(
            body = "",
            isEdited = false,
            isStreaming = false,
            isTerminal = true,
            streamId = "stream-1",
            roomId = "!room:keepsecret.io",
            eventId = "\$event-1",
            thinkingSteps = persistentListOf(),
            toolCalls = persistentListOf(),
            sources = persistentListOf(),
            quickActions = persistentListOf(),
            parts = persistentListOf(part),
            visibleParts = persistentListOf(part),
            cardResponseState = cardResponseState,
        )
    }
}

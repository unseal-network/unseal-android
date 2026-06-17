/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.agentstream.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class AgentStreamSseFixtureReplayTest {
    @Test
    fun `native reducer replays compose email list fixture`() {
        assumeTrue("Native agent stream reducer is unavailable in this unit test runtime", isNativeReducerAvailable())
        val fixture = AgentStreamSseFixtures.load("compose-email-list")
        val parser = StreamSnapshotParser(clock = { 1000L })

        AgentStreamSession(streamId = fixture.id, includeRawEvents = true).use { session ->
            var latest = parser.parse(session.snapshot())
            fixture.asSseChunks().forEach { chunk ->
                latest = parser.parse(session.applySseChunk(chunk))
            }
            latest = parser.parse(session.finish())

            assertEquals(fixture.id, latest.streamId)
            assertEquals(StreamStatus.Completed, latest.status)
            assertTrue(latest.parts.any { part ->
                part is StreamPart.Tool &&
                    part.toolName == "GMAIL_FETCH_EMAILS" &&
                    part.toolState == ToolPartState.OutputAvailable.wireValue
            })
        }
    }

    private fun isNativeReducerAvailable(): Boolean {
        return runCatching { AgentStreamSession.isNativeAvailable() }.getOrDefault(false)
    }
}

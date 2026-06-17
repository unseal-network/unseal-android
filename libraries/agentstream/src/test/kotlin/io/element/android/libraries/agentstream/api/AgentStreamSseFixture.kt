/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.agentstream.api

import java.io.File

internal data class AgentStreamSseFixture(
    val id: String,
    val events: List<String>,
) {
    fun asSseChunks(): List<String> {
        return events.map { line -> "data: $line\n\n" }
    }
}

internal object AgentStreamSseFixtures {
    private val root = File("docs/agent-stream-fixtures/fixtures")

    fun load(id: String): AgentStreamSseFixture {
        val file = root.resolve("$id.sse.jsonl")
        require(file.isFile) { "Missing fixture file: ${file.absolutePath}" }
        val events = file.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        require(events.isNotEmpty()) { "Fixture has no events: ${file.absolutePath}" }
        return AgentStreamSseFixture(id = id, events = events)
    }
}

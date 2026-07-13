/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.model.event

import androidx.compose.runtime.Immutable
import io.element.android.features.messages.impl.utils.UnsealAgentProfileLink

enum class AgentProfilePreviewDisplayMode {
    Standalone,
    Attached,
}

@Immutable
data class AgentProfilePreviewPlan(
    val profiles: List<UnsealAgentProfileLink>,
    val displayMode: AgentProfilePreviewDisplayMode,
) {
    companion object {
        val Empty = AgentProfilePreviewPlan(
            profiles = emptyList(),
            displayMode = AgentProfilePreviewDisplayMode.Attached,
        )

        internal fun build(
            agentCandidates: List<AgentProfilePreviewCandidate>,
            genericPreviewUrls: List<String>,
            visibleText: String,
        ): AgentProfilePreviewPlan {
            val profiles = agentCandidates
                .distinctBy { it.profileLink.profileUrl }
                .take(MAX_AGENT_PROFILE_PREVIEWS)
                .map { it.profileLink }
            if (profiles.isEmpty()) return Empty

            val displayMode = if (isStandalone(agentCandidates, genericPreviewUrls, visibleText)) {
                AgentProfilePreviewDisplayMode.Standalone
            } else {
                AgentProfilePreviewDisplayMode.Attached
            }
            return AgentProfilePreviewPlan(
                profiles = profiles,
                displayMode = displayMode,
            )
        }

        private fun isStandalone(
            agentCandidates: List<AgentProfilePreviewCandidate>,
            genericPreviewUrls: List<String>,
            visibleText: String,
        ): Boolean {
            if (agentCandidates.isEmpty() || genericPreviewUrls.isNotEmpty()) return false
            if (agentCandidates.size > MAX_AGENT_PROFILE_PREVIEWS) return false
            if (agentCandidates.any { !it.hasUrlVisibleText() }) return false

            val remainingText = agentCandidates.fold(visibleText) { remaining, candidate ->
                remaining.replaceFirst(candidate.visibleText, "")
            }
            return remainingText.isBlank()
        }

        private const val MAX_AGENT_PROFILE_PREVIEWS = 5
    }
}

internal data class AgentProfilePreviewCandidate(
    val profileLink: UnsealAgentProfileLink,
    val url: String,
    val visibleText: String,
) {
    fun hasUrlVisibleText(): Boolean {
        val trimmedVisibleText = visibleText.trim()
        return trimmedVisibleText.equals(url, ignoreCase = true) ||
            trimmedVisibleText == profileLink.profileUrl
    }
}

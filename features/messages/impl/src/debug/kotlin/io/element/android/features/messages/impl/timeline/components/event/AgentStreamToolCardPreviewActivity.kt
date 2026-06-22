/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.theme.Theme
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemAiContent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

class AgentStreamToolCardPreviewActivity : ComponentActivity() {
    private val fixtureIndexState = mutableIntStateOf(-1)
    private val viewModeState = mutableStateOf(PreviewViewMode.Card)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fixtureIndexState.intValue = intent.fixtureIndex()
        viewModeState.value = intent.viewMode()
        setContent {
            AgentStreamToolCardPreviewScreen(
                fixtureIndex = fixtureIndexState.intValue,
                viewMode = viewModeState.value,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        fixtureIndexState.intValue = intent.fixtureIndex()
        viewModeState.value = intent.viewMode()
    }
}

private enum class PreviewViewMode {
    Card,
    Timeline,
}

private fun Intent.fixtureIndex(): Int = getIntExtra("fixtureIndex", -1)

private fun Intent.viewMode(): PreviewViewMode {
    return if (getStringExtra("viewMode") == "timeline") {
        PreviewViewMode.Timeline
    } else {
        PreviewViewMode.Card
    }
}

@Composable
private fun AgentStreamToolCardPreviewScreen(
    fixtureIndex: Int,
    viewMode: PreviewViewMode,
) {
    val contents: ImmutableList<TimelineItemAiContent> = remember(fixtureIndex, viewMode) {
        if (fixtureIndex >= 0) {
            listOfNotNull(agentStreamParityPreviewContent(fixtureIndex)).toImmutableList()
        } else {
            when (viewMode) {
                PreviewViewMode.Card -> agentStreamParityPreviewInteractiveContents()
                PreviewViewMode.Timeline -> agentStreamParityPreviewContents()
            }.toImmutableList()
        }
    }
    ElementTheme(theme = if (isSystemInDarkTheme()) Theme.Dark else Theme.Light) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Text(
                text = when {
                    fixtureIndex >= 0 && viewMode == PreviewViewMode.Timeline -> "Timeline Tool Card ${fixtureIndex + 1}"
                    fixtureIndex >= 0 -> "Agent Stream Tool Card ${fixtureIndex + 1}"
                    viewMode == PreviewViewMode.Timeline -> "Timeline Tool Cards"
                    else -> "Agent Stream Tool Cards"
                },
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            contents.forEach { content ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AgentStreamParityPreview(content = content, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

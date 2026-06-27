/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package io.element.android.features.skills.impl.managementhub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.skills.impl.R
import io.element.android.libraries.designsystem.components.management.ManagementListRow
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
fun SkillsManagementHubView(
    onBackClick: () -> Unit,
    onOpenAgentManagement: () -> Unit,
    onOpenSkills: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.skills_hub_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = CompoundIcons.ChevronLeft(), contentDescription = stringResource(CommonStrings.action_go_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            HubRow(
                title = stringResource(R.string.skills_hub_agent_management),
                subtitle = stringResource(R.string.skills_hub_agent_management_subtitle),
                icon = CompoundIcons.Labs(),
                onClick = onOpenAgentManagement,
            )
            HubRow(
                title = stringResource(R.string.skills_hub_skill_management),
                subtitle = stringResource(R.string.skills_hub_skill_management_subtitle),
                icon = CompoundIcons.ListBulleted(),
                onClick = onOpenSkills,
            )
        }
    }
}

@Composable
private fun HubRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    ManagementListRow(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(24.dp))
            }
        },
        trailingContent = {
            Icon(CompoundIcons.ChevronRight(), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        },
    )
}

internal class SkillsManagementHubStateProvider : PreviewParameterProvider<Unit> {
    override val values: Sequence<Unit> = sequenceOf(Unit)
}

@PreviewsDayNight
@Composable
internal fun SkillsManagementHubViewPreview(@PreviewParameter(SkillsManagementHubStateProvider::class) unused: Unit) = ElementPreview {
    SkillsManagementHubView(
        onBackClick = {},
        onOpenAgentManagement = {},
        onOpenSkills = {},
    )
}

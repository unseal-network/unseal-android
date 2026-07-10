/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer.skills

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.messages.impl.R
import io.element.android.features.messages.impl.components.SelectedStatePill
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.Surface
import io.element.android.libraries.designsystem.theme.components.Text
import kotlinx.collections.immutable.persistentListOf

@Composable
internal fun ComposerAgentSkillPickerView(
    state: ComposerAgentSkillState,
    onTogglePicker: () -> Unit,
    onReloadPicker: () -> Unit,
    onSelectTarget: (String) -> Unit,
    onSelectSkill: (ComposerAgentSkillCandidate) -> Unit,
    onRemoveSkill: (ComposerSelectedAgentSkill) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visibleCandidates = remember(state.candidates, state.selectedSkills, state.activeAgentMxid) {
        ComposerAgentSkillReducer.visibleSkillCandidates(
            candidates = state.candidates,
            selectedSkills = state.selectedSkills,
            activeAgentMxid = state.activeAgentMxid,
        )
    }
    val shouldShow = state.isPresented || state.selectedSkills.isNotEmpty()
    if (!shouldShow) return

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        color = ElementTheme.colors.bgSubtleSecondary,
        border = BorderStroke(1.dp, ElementTheme.colors.borderDisabled),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PickerHeader(
                state = state,
                visibleCandidateCount = visibleCandidates.size,
                onTogglePicker = onTogglePicker,
                onReloadPicker = onReloadPicker,
            )
            if (state.selectedSkills.isNotEmpty()) {
                SelectedSkillsRow(
                    selectedSkills = state.selectedSkills,
                    onRemoveSkill = onRemoveSkill,
                )
            }
            if (state.isPresented) {
                if (state.targets.size > 1) {
                    AgentTargetRow(
                        state = state,
                        onSelectTarget = onSelectTarget,
                    )
                }
                when {
                    state.isCatalogLoading -> LoadingRow()
                    state.isWorkspaceLoading && visibleCandidates.isEmpty() -> LoadingRow()
                    state.error != null -> PickerMessage(text = state.error)
                    visibleCandidates.isEmpty() -> PickerMessage(text = stringResource(R.string.screen_room_agent_skill_picker_empty))
                    else -> CandidateList(
                        candidates = visibleCandidates,
                        onSelectSkill = onSelectSkill,
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerHeader(
    state: ComposerAgentSkillState,
    visibleCandidateCount: Int,
    onTogglePicker: () -> Unit,
    onReloadPicker: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.screen_room_agent_skill_picker_title),
                style = ElementTheme.typography.fontBodyMdMedium,
                color = ElementTheme.colors.textPrimary,
            )
            Text(
                text = stringResource(R.string.screen_room_agent_skill_picker_summary, state.targets.size, visibleCandidateCount),
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (state.isPresented) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(
                    modifier = Modifier.size(48.dp),
                    onClick = onReloadPicker,
                    enabled = !state.isCatalogLoading && !state.isWorkspaceLoading,
                ) {
                    if (state.isWorkspaceLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = ElementTheme.colors.iconPrimary,
                        )
                    } else {
                        Icon(
                            imageVector = CompoundIcons.Restart(),
                            contentDescription = stringResource(R.string.a11y_room_agent_skill_picker_update),
                        )
                    }
                }
                IconButton(
                    modifier = Modifier.size(48.dp),
                    onClick = onTogglePicker,
                ) {
                    Icon(
                        imageVector = CompoundIcons.Close(),
                        contentDescription = stringResource(R.string.a11y_room_agent_skill_picker_close),
                    )
                }
            }
        } else {
            IconButton(
                modifier = Modifier.size(48.dp),
                onClick = onTogglePicker,
            ) {
                Icon(
                    imageVector = CompoundIcons.Extensions(),
                    contentDescription = stringResource(R.string.a11y_room_agent_skill_picker_open),
                )
            }
        }
    }
}

@Composable
private fun SelectedSkillsRow(
    selectedSkills: List<ComposerSelectedAgentSkill>,
    onRemoveSkill: (ComposerSelectedAgentSkill) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        selectedSkills.forEach { selected ->
            val chipShape = RoundedCornerShape(18.dp)
            Surface(
                shape = chipShape,
                color = ElementTheme.colors.bgAccentRest,
            ) {
                Row(
                    modifier = Modifier
                        .clip(chipShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true),
                            onClick = { onRemoveSkill(selected) },
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = selected.skillName,
                        style = ElementTheme.typography.fontBodySmMedium,
                        color = ElementTheme.colors.textOnSolidPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "×",
                        style = ElementTheme.typography.fontBodySmMedium,
                        color = ElementTheme.colors.textOnSolidPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun AgentTargetRow(
    state: ComposerAgentSkillState,
    onSelectTarget: (String) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.targets.forEach { target ->
            val selected = target.mxid == state.activeAgentMxid
            SelectedStatePill(
                selected = selected,
                onClick = { onSelectTarget(target.mxid) },
                selectedColor = ElementTheme.colors.bgActionPrimaryRest,
                unselectedColor = ElementTheme.colors.bgCanvasDefault,
                selectedContentColor = ElementTheme.colors.textOnSolidPrimary,
                unselectedContentColor = ElementTheme.colors.textPrimary,
                selectedBorder = BorderStroke(1.dp, ElementTheme.colors.bgActionPrimaryRest),
                unselectedBorder = BorderStroke(1.dp, ElementTheme.colors.borderDisabled),
            ) {
                Text(
                    modifier = Modifier
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    text = target.label,
                    style = ElementTheme.typography.fontBodySmMedium,
                    color = androidx.compose.material3.LocalContentColor.current,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun LoadingRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.screen_room_agent_skill_picker_loading),
            style = ElementTheme.typography.fontBodySmRegular,
            color = ElementTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun PickerMessage(text: String) {
    Text(
        modifier = Modifier.padding(vertical = 8.dp),
        text = text,
        style = ElementTheme.typography.fontBodySmRegular,
        color = ElementTheme.colors.textSecondary,
    )
}

@Composable
private fun CandidateList(
    candidates: List<ComposerAgentSkillCandidate>,
    onSelectSkill: (ComposerAgentSkillCandidate) -> Unit,
) {
    Column(
        modifier = Modifier
            .heightIn(max = 220.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        candidates.forEach { candidate ->
            CandidateRow(
                candidate = candidate,
                onSelectSkill = onSelectSkill,
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun CandidateRow(
    candidate: ComposerAgentSkillCandidate,
    onSelectSkill: (ComposerAgentSkillCandidate) -> Unit,
) {
    val rowShape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = { onSelectSkill(candidate) },
            )
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = candidate.skillName,
                style = ElementTheme.typography.fontBodyMdMedium,
                color = ElementTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${candidate.agent.label} · ${candidate.source.value} · ${candidate.relation.value}",
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "Add",
            style = ElementTheme.typography.fontBodySmMedium,
            color = ElementTheme.colors.textActionAccent,
        )
    }
}

@PreviewsDayNight
@Composable
internal fun ComposerAgentSkillPickerViewPreview() = ElementPreview {
    val agent = ComposerAgentDescriptor(
        agentId = "@mail:server.org",
        mxid = "@mail:server.org",
        label = "Mail Agent",
    )
    ComposerAgentSkillPickerView(
        state = ComposerAgentSkillState(
            targets = persistentListOf(agent),
            candidates = persistentListOf(
                ComposerAgentSkillCandidate(
                    agent = agent,
                    skillKey = "mail",
                    skillName = "mail",
                    source = ComposerAgentSkillSource.Workspace,
                    relation = ComposerAgentSkillRelation.Runtime,
                    path = "mail/SKILL.md",
                    directoryName = "mail",
                    runtimeVisible = true,
                ),
            ),
            isPresented = true,
        ),
        onTogglePicker = {},
        onReloadPicker = {},
        onSelectTarget = {},
        onSelectSkill = {},
        onRemoveSkill = {},
    )
}

/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.terminal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.messages.impl.R
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.Text

@Composable
internal fun DeviceAgentTerminalPanel(
    panel: DeviceAgentTerminalPanelState?,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onInputChange: (String) -> Unit,
    onSendInput: () -> Unit,
    onCloseSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = panel?.isExpanded == true,
            enter = fadeIn(animationSpec = spring()) + slideInVertically(
                animationSpec = spring(dampingRatio = 0.86f),
                initialOffsetY = { it / 2 },
            ),
            exit = fadeOut(animationSpec = spring()) + slideOutVertically(
                animationSpec = spring(dampingRatio = 0.86f),
                targetOffsetY = { it / 2 },
            ),
        ) {
            panel?.let {
                DeviceAgentTerminalExpandedPanel(
                    panel = it,
                    onDismiss = onDismiss,
                    onOpen = onOpen,
                    onInputChange = onInputChange,
                    onSendInput = onSendInput,
                    onCloseSession = onCloseSession,
                )
            }
        }

        AnimatedVisibility(
            visible = panel != null && !panel.isExpanded,
            enter = fadeIn(animationSpec = spring()) + slideInVertically(
                animationSpec = spring(dampingRatio = 0.86f),
                initialOffsetY = { it / 2 },
            ),
            exit = fadeOut(animationSpec = spring()),
        ) {
            panel?.let {
                Surface(
                    onClick = onExpand,
                    shape = CircleShape,
                    color = ElementTheme.colors.bgSubtleSecondary.copy(alpha = 0.96f),
                    shadowElevation = 6.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            modifier = Modifier.size(18.dp),
                            imageVector = CompoundIcons.Code(),
                            contentDescription = null,
                            tint = ElementTheme.colors.iconAccentPrimary,
                        )
                        Text(
                            text = it.statusTitle,
                            style = ElementTheme.typography.fontBodySmMedium,
                            color = ElementTheme.colors.textPrimary,
                            maxLines = 1,
                        )
                        Icon(
                            modifier = Modifier.size(16.dp),
                            imageVector = CompoundIcons.ChevronUp(),
                            contentDescription = stringResource(R.string.screen_room_topbar_expand_remote_terminal),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceAgentTerminalExpandedPanel(
    panel: DeviceAgentTerminalPanelState,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onInputChange: (String) -> Unit,
    onSendInput: () -> Unit,
    onCloseSession: () -> Unit,
) {
    val panelShape = RoundedCornerShape(8.dp)
    val outputScrollState = rememberScrollState()
    LaunchedEffect(panel.outputText) {
        outputScrollState.scrollTo(outputScrollState.maxValue)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 420.dp)
            .shadow(12.dp, panelShape)
            .background(ElementTheme.colors.bgSubtleSecondary, panelShape)
            .border(1.dp, ElementTheme.colors.borderInteractivePrimary, panelShape)
            .clip(panelShape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                modifier = Modifier.size(20.dp),
                imageVector = CompoundIcons.Code(),
                contentDescription = null,
                tint = ElementTheme.colors.iconAccentPrimary,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = panel.title,
                    style = ElementTheme.typography.fontBodySmMedium,
                    color = ElementTheme.colors.textPrimary,
                    maxLines = 1,
                )
                Text(
                    text = panel.subtitle,
                    style = ElementTheme.typography.fontBodyXsRegular,
                    color = ElementTheme.colors.textSecondary,
                    maxLines = 2,
                )
            }
            IconButton(
                modifier = Modifier.size(40.dp),
                onClick = onOpen,
                enabled = panel.canOpenTerminal,
            ) {
                Icon(
                    modifier = Modifier.size(20.dp),
                    imageVector = when (panel.status) {
                        DeviceAgentTerminalPanelState.Status.WaitingForDevice,
                        DeviceAgentTerminalPanelState.Status.Opening -> CompoundIcons.Time()
                        else -> CompoundIcons.Play()
                    },
                    contentDescription = stringResource(R.string.screen_room_topbar_remote_terminal),
                )
            }
            IconButton(
                modifier = Modifier.size(40.dp),
                onClick = onCloseSession,
                enabled = panel.canCloseTerminal,
            ) {
                Icon(
                    modifier = Modifier.size(20.dp),
                    imageVector = CompoundIcons.Close(),
                    contentDescription = stringResource(R.string.screen_room_topbar_close_remote_terminal),
                )
            }
            IconButton(
                modifier = Modifier.size(40.dp),
                onClick = onDismiss,
            ) {
                Icon(
                    modifier = Modifier.size(20.dp),
                    imageVector = CompoundIcons.ChevronDown(),
                    contentDescription = stringResource(R.string.screen_room_topbar_collapse_remote_terminal),
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(Color.Black)
                .verticalScroll(outputScrollState)
                .padding(12.dp),
        ) {
            SelectionContainer {
                Text(
                    text = panel.outputText,
                    style = ElementTheme.typography.fontBodyXsRegular.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Normal,
                    ),
                    color = Color(0xFF45E06F),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ElementTheme.colors.bgCanvasDefault)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val inputEnabled = panel.sessionId != null
            BasicTextField(
                value = panel.inputText,
                onValueChange = onInputChange,
                singleLine = true,
                enabled = inputEnabled,
                textStyle = ElementTheme.typography.fontBodySmRegular.copy(
                    color = if (inputEnabled) ElementTheme.colors.textPrimary else ElementTheme.colors.textDisabled,
                    fontFamily = FontFamily.Monospace,
                ),
                cursorBrush = SolidColor(ElementTheme.colors.textActionAccent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (panel.canSendInput) onSendInput()
                    }
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .border(
                        width = 1.dp,
                        color = if (inputEnabled) ElementTheme.colors.borderInteractivePrimary else ElementTheme.colors.borderDisabled,
                        shape = RoundedCornerShape(6.dp),
                    )
                    .background(ElementTheme.colors.bgCanvasDefault),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (panel.inputText.isEmpty()) {
                            Text(
                                text = "Command",
                                style = ElementTheme.typography.fontBodySmRegular.copy(fontFamily = FontFamily.Monospace),
                                color = ElementTheme.colors.textDisabled,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            IconButton(
                modifier = Modifier.size(44.dp),
                onClick = onSendInput,
                enabled = panel.canSendInput,
            ) {
                Icon(
                    modifier = Modifier.size(22.dp),
                    imageVector = CompoundIcons.SendSolid(),
                    contentDescription = null,
                )
            }
        }
    }
}

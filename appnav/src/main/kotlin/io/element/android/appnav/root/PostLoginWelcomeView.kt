/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.appnav.root

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.Theme
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.theme.components.Text

private enum class PostLoginWelcomeStep {
    Welcome,
    Theme,
    Updates,
}

enum class PostLoginWelcomeTheme(
    val storedTheme: Theme,
    val title: String,
    val description: String,
) {
    Light(Theme.Light, "Light", "Always light interface"),
    Dark(Theme.Dark, "Dark", "Always dark interface"),
    System(Theme.System, "System", "Follows system setting"),
}

private data class FeatureHighlight(
    val first: String,
    val second: String,
)

private data class SubscriptionOption(
    val title: String,
    val description: String,
    val enabled: Boolean,
    val onToggle: () -> Unit,
)

data class PostLoginWelcomeCompletion(
    val selectedTheme: PostLoginWelcomeTheme,
    val subscribeChangelog: Boolean,
    val subscribeMarketing: Boolean,
)

@Composable
fun PostLoginWelcomeView(
    onComplete: (PostLoginWelcomeCompletion) -> Unit,
    modifier: Modifier = Modifier,
    initialTheme: PostLoginWelcomeTheme = PostLoginWelcomeTheme.System,
    onThemeSelected: (PostLoginWelcomeTheme) -> Unit = {},
    onFollowX: () -> Unit = {},
) {
    var currentStep by rememberSaveable { mutableStateOf(PostLoginWelcomeStep.Welcome) }
    var selectedTheme by rememberSaveable { mutableStateOf(initialTheme) }
    var subscribeChangelog by rememberSaveable { mutableStateOf(false) }
    var subscribeMarketing by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ElementTheme.colors.bgCanvasDefault),
    ) {
        AnimatedContent(
            targetState = currentStep,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            transitionSpec = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = spring(dampingRatio = 0.86f),
                ) togetherWith slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = spring(dampingRatio = 0.86f),
                )
            },
            label = "post-login-welcome-step",
        ) { step ->
            when (step) {
                PostLoginWelcomeStep.Welcome -> WelcomeStep()
                PostLoginWelcomeStep.Theme -> ThemeStep(
                    selectedTheme = selectedTheme,
                    onSelectTheme = { theme ->
                        selectedTheme = theme
                        onThemeSelected(theme)
                    },
                )
                PostLoginWelcomeStep.Updates -> UpdatesStep(
                    changelogEnabled = subscribeChangelog,
                    marketingEnabled = subscribeMarketing,
                    onToggleChangelog = { subscribeChangelog = !subscribeChangelog },
                    onToggleMarketing = { subscribeMarketing = !subscribeMarketing },
                    onFollowX = onFollowX,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 12.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StepIndicator(currentStep)
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElementTheme.colors.bgActionPrimaryRest,
                    contentColor = ElementTheme.colors.textOnSolidPrimary,
                ),
                onClick = {
                    currentStep = when (currentStep) {
                        PostLoginWelcomeStep.Welcome -> PostLoginWelcomeStep.Theme
                        PostLoginWelcomeStep.Theme -> PostLoginWelcomeStep.Updates
                        PostLoginWelcomeStep.Updates -> {
                            onComplete(
                                PostLoginWelcomeCompletion(
                                    selectedTheme = selectedTheme,
                                    subscribeChangelog = subscribeChangelog,
                                    subscribeMarketing = subscribeMarketing,
                                )
                            )
                            PostLoginWelcomeStep.Updates
                        }
                    }
                },
            ) {
                Text(
                    text = when (currentStep) {
                        PostLoginWelcomeStep.Welcome -> "Get started"
                        PostLoginWelcomeStep.Theme -> "Continue"
                        PostLoginWelcomeStep.Updates -> "Continue"
                    },
                    style = ElementTheme.typography.fontBodyLgMedium,
                    color = ElementTheme.colors.textOnSolidPrimary,
                )
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    StepScrollScaffold(
        title = "Welcome to Unseal",
        primarySubtitle = "Chat with humans, agents, and multi-agent rooms.",
        secondarySubtitle = "Your private rooms, AI tools, and device workflows stay in one workspace.",
    ) {
        FeatureHighlightsGrid(
            highlights = listOf(
                FeatureHighlight("human", "human"),
                FeatureHighlight("human", "agent"),
                FeatureHighlight("agent", "agent"),
                FeatureHighlight("group", "multi-agent"),
            ),
        )
    }
}

@Composable
private fun ThemeStep(
    selectedTheme: PostLoginWelcomeTheme,
    onSelectTheme: (PostLoginWelcomeTheme) -> Unit,
) {
    StepScrollScaffold(
        title = "Choose your space",
        secondarySubtitle = "Unseal follows your system theme by default. The room chrome, stream cards, and composer keep the same visual language across devices.",
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PostLoginWelcomeTheme.entries.forEach { theme ->
                ThemeOptionRow(
                    theme = theme,
                    isSelected = selectedTheme == theme,
                    onClick = { onSelectTheme(theme) },
                )
            }
        }
    }
}

@Composable
private fun UpdatesStep(
    changelogEnabled: Boolean,
    marketingEnabled: Boolean,
    onToggleChangelog: () -> Unit,
    onToggleMarketing: () -> Unit,
    onFollowX: () -> Unit,
) {
    StepScrollScaffold(
        title = "Stay close to the work",
        secondarySubtitle = "Choose what you want to hear about. You can manage notifications, agents, schedules, and webhooks later from settings.",
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SubscriptionCard(
                option = SubscriptionOption(
                    title = "Product changelog",
                    description = "Major releases, stream rendering updates, and new agent workflows.",
                    enabled = changelogEnabled,
                    onToggle = onToggleChangelog,
                ),
                badge = "N",
            )
            SubscriptionCard(
                option = SubscriptionOption(
                    title = "Tips and announcements",
                    description = "Occasional notes about features, templates, and product news.",
                    enabled = marketingEnabled,
                    onToggle = onToggleMarketing,
                ),
                badge = "M",
            )
            FollowXCard(
                title = "Follow Unseal on X",
                description = "See product updates and release notes as they ship.",
                actionTitle = "Follow",
                onClick = onFollowX,
            )
        }
    }
}

@Composable
private fun StepScrollScaffold(
    title: String,
    primarySubtitle: String? = null,
    secondarySubtitle: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        UnsealMark(
            modifier = Modifier
                .padding(top = 96.dp, bottom = 56.dp)
                .size(192.dp),
        )

        Text(
            text = title,
            style = ElementTheme.typography.fontHeadingLgBold,
            color = ElementTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (primarySubtitle != null) {
            Text(
                text = primarySubtitle,
                style = ElementTheme.typography.fontBodyLgMedium,
                color = ElementTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Text(
            text = secondarySubtitle,
            style = ElementTheme.typography.fontBodyLgRegular,
            color = ElementTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(28.dp))
        content()
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun FeatureHighlightsGrid(highlights: List<FeatureHighlight>) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        highlights.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowItems.forEach { highlight ->
                    FeatureCard(
                        highlight = highlight,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun FeatureCard(
    highlight: FeatureHighlight,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ElementTheme.colors.bgSubtleSecondary)
            .padding(horizontal = 12.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = highlight.first,
            style = ElementTheme.typography.fontBodyMdMedium,
            color = ElementTheme.colors.textPrimary,
            maxLines = 1,
        )
        Text(
            text = " <-> ",
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.textPrimary,
            maxLines = 1,
        )
        Text(
            text = highlight.second,
            style = ElementTheme.typography.fontBodyMdMedium,
            color = ElementTheme.colors.textPrimary,
            maxLines = 1,
        )
    }
}

@Composable
private fun ThemeOptionRow(
    theme: PostLoginWelcomeTheme,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) ElementTheme.colors.borderInteractivePrimary else Color.Transparent,
        label = "welcome-theme-border",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isSelected) {
                    ElementTheme.colors.bgActionPrimaryRest.copy(alpha = 0.08f)
                } else {
                    ElementTheme.colors.bgSubtleSecondary
                }
            )
            .border(1.5.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ThemePreview(
            theme = theme,
            isSelected = isSelected,
            modifier = Modifier.size(width = 72.dp, height = 52.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = theme.title,
                style = ElementTheme.typography.fontBodyLgMedium,
                color = ElementTheme.colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = theme.description,
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textSecondary,
            )
        }
        SelectionIndicator(isSelected)
    }
}

@Composable
private fun ThemePreview(
    theme: PostLoginWelcomeTheme,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
) {
    val canvasColor = when (theme) {
        PostLoginWelcomeTheme.Light -> ElementTheme.colors.bgCanvasDefault
        PostLoginWelcomeTheme.Dark -> Color(0xFF111318)
        PostLoginWelcomeTheme.System -> ElementTheme.colors.bgSubtleSecondary
    }
    val sidebarColor = when (theme) {
        PostLoginWelcomeTheme.Light -> ElementTheme.colors.bgSubtleSecondary
        PostLoginWelcomeTheme.Dark -> Color(0xFF272A30)
        PostLoginWelcomeTheme.System -> ElementTheme.colors.bgSubtlePrimary
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(canvasColor)
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) ElementTheme.colors.borderInteractivePrimary else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .width(20.dp)
                .align(Alignment.CenterStart)
                .background(sidebarColor),
        )
        Column(
            modifier = Modifier
                .padding(start = 28.dp, top = 8.dp, end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(3) { index ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(ElementTheme.colors.bgSubtlePrimary),
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(ElementTheme.colors.bgSubtlePrimary.copy(alpha = if (index == 1) 0.6f else 1f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectionIndicator(isSelected: Boolean) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .border(
                width = 2.dp,
                color = if (isSelected) ElementTheme.colors.borderInteractivePrimary else ElementTheme.colors.borderInteractiveSecondary,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(ElementTheme.colors.iconPrimary),
            )
        }
    }
}

@Composable
private fun SubscriptionCard(
    option: SubscriptionOption,
    badge: String,
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (option.enabled) {
            ElementTheme.colors.bgActionPrimaryRest.copy(alpha = 0.08f)
        } else {
            ElementTheme.colors.bgSubtleSecondary
        },
        label = "welcome-subscription-background",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(backgroundColor)
            .border(
                width = 1.5.dp,
                color = if (option.enabled) ElementTheme.colors.borderInteractivePrimary else Color.Transparent,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = option.onToggle)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        BadgeBox(
            label = badge,
            isActive = option.enabled,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = option.title,
                style = ElementTheme.typography.fontBodyMdMedium,
                color = ElementTheme.colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = option.description,
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textSecondary,
            )
        }
        Switch(
            checked = option.enabled,
            onCheckedChange = { option.onToggle() },
        )
    }
}

@Composable
private fun FollowXCard(
    title: String,
    description: String,
    actionTitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ElementTheme.colors.bgSubtleSecondary)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        BadgeBox(label = "X", isActive = false)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = ElementTheme.typography.fontBodyMdMedium,
                color = ElementTheme.colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = description,
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textSecondary,
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(ElementTheme.colors.bgActionPrimaryRest)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 7.dp),
        ) {
            Text(
                text = actionTitle,
                style = ElementTheme.typography.fontBodySmMedium,
                color = ElementTheme.colors.textOnSolidPrimary,
            )
        }
    }
}

@Composable
private fun BadgeBox(
    label: String,
    isActive: Boolean,
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isActive) ElementTheme.colors.bgActionPrimaryRest else ElementTheme.colors.bgSubtlePrimary),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = ElementTheme.typography.fontBodyMdMedium,
            color = if (isActive) ElementTheme.colors.textOnSolidPrimary else ElementTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun StepIndicator(currentStep: PostLoginWelcomeStep) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PostLoginWelcomeStep.entries.forEach { step ->
            val width by animateDpAsState(
                targetValue = if (step == currentStep) 24.dp else 8.dp,
                label = "welcome-step-indicator-width",
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (step == currentStep) ElementTheme.colors.iconPrimary else ElementTheme.colors.iconQuaternary
                    )
                    .size(width = width, height = 8.dp)
            )
        }
    }
}

@Composable
private fun UnsealMark(modifier: Modifier = Modifier) {
    val strokeColor = ElementTheme.colors.textPrimary
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        drawArc(
            color = strokeColor,
            startAngle = 210f,
            sweepAngle = 300f,
            useCenter = false,
            topLeft = Offset(size.width * 0.08f, size.height * 0.08f),
            size = Size(size.width * 0.84f, size.height * 0.84f),
            style = stroke,
        )
        drawLine(
            color = strokeColor,
            start = Offset(size.width * 0.36f, size.height * 0.68f),
            end = Offset(size.width * 0.68f, size.height * 0.28f),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawRoundRect(
            color = strokeColor,
            topLeft = Offset(size.width * 0.30f, size.height * 0.32f),
            size = Size(size.width * 0.18f, size.height * 0.26f),
            style = stroke,
        )
        drawRoundRect(
            color = strokeColor,
            topLeft = Offset(size.width * 0.58f, size.height * 0.50f),
            size = Size(size.width * 0.18f, size.height * 0.26f),
            style = stroke,
        )
    }
}

@Preview
@Composable
private fun PostLoginWelcomeViewPreview() = ElementPreview {
    PostLoginWelcomeView(onComplete = { _ -> })
}

/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.root

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.preferences.impl.R
import io.element.android.features.preferences.impl.user.UserPreferences
import io.element.android.libraries.architecture.coverage.ExcludeFromCoverage
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.components.preferences.PreferencePage
import io.element.android.libraries.designsystem.preview.ElementPreviewDark
import io.element.android.libraries.designsystem.preview.ElementPreviewLight
import io.element.android.libraries.designsystem.preview.PreviewWithLargeHeight
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.ButtonSize
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.ListItemStyle
import io.element.android.libraries.designsystem.theme.components.Surface
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.designsystem.utils.CommonDrawables
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarHost
import io.element.android.libraries.designsystem.utils.snackbar.rememberSnackbarHostState
import io.element.android.libraries.matrix.api.core.DeviceId
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.matrix.ui.components.MatrixUserRow
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
fun PreferencesRootView(
    state: PreferencesRootState,
    onBackClick: () -> Unit,
    onAddAccountClick: () -> Unit,
    onSecureBackupClick: () -> Unit,
    onManageAccountClick: (url: String) -> Unit,
    onLinkNewDeviceClick: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenRageShake: () -> Unit,
    onOpenLockScreenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenDeveloperSettings: () -> Unit,
    onOpenAdvancedSettings: () -> Unit,
    onOpenLabs: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenWebhookTriggers: () -> Unit,
    onOpenConnectors: () -> Unit,
    onOpenVoiceLibrary: () -> Unit,
    onOpenAgentManagement: () -> Unit,
    onOpenSkills: () -> Unit,
    onOpenVaultManagement: () -> Unit,
    onOpenUserProfile: (MatrixUser) -> Unit,
    onOpenBlockedUsers: () -> Unit,
    onOpenCreditsTopUp: () -> Unit,
    onOpenCreditsBilling: () -> Unit,
    onOpenCreditsUsage: () -> Unit,
    onSignOutClick: () -> Unit,
    onDeactivateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = rememberSnackbarHostState(snackbarMessage = state.snackbarMessage)

    // Include pref from other modules
    PreferencePage(
        modifier = modifier,
        onBackClick = onBackClick,
        title = stringResource(id = CommonStrings.common_settings),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) {
        // iOS-aligned grouped layout: subtle page background with elevated rounded "cards"
        // per section.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    // Android has no `bgSubtleSecondaryLevel0`; use a theme-aware page colour so the
                    // elevated cards (bgCanvasDefaultLevel1) contrast in BOTH light and dark, like iOS.
                    if (ElementTheme.isLightTheme) {
                        ElementTheme.colors.bgSubtleSecondary
                    } else {
                        ElementTheme.colors.bgCanvasDefault
                    }
                )
                .padding(top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Profile header card
            SettingsCard {
                SettingsUserHeader(
                    matrixUser = state.myUser,
                    onClick = { onOpenUserProfile(state.myUser) },
                )
                if (state.isMultiAccountEnabled) {
                    MultiAccountSection(
                        state = state,
                        onAddAccountClick = onAddAccountClick,
                    )
                }
            }
            // 'AI Assistant' section (right after the profile, mirroring iOS)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsSectionHeader(
                    title = stringResource(id = R.string.screen_preferences_ai_assistant_section_title),
                )
                CreditBalanceCard(
                    loadState = state.creditBalanceLoadState,
                    onOpenCreditsTopUp = onOpenCreditsTopUp,
                    onOpenCreditsBilling = onOpenCreditsBilling,
                    onOpenCreditsUsage = onOpenCreditsUsage,
                )
                SettingsCard {
                    AiAssistantRows(
                        onOpenAgentManagement = onOpenAgentManagement,
                        onOpenVoiceLibrary = onOpenVoiceLibrary,
                        onOpenSkills = onOpenSkills,
                        onOpenVaultManagement = onOpenVaultManagement,
                        onOpenConnectors = onOpenConnectors,
                        onOpenWebhookTriggers = onOpenWebhookTriggers,
                    )
                }
            }
            // 'Manage my app' section
            SettingsCard {
                ManageAppSection(
                    state = state,
                    onOpenNotificationSettings = onOpenNotificationSettings,
                    onOpenLockScreenSettings = onOpenLockScreenSettings,
                    onSecureBackupClick = onSecureBackupClick,
                )
            }
            // 'Account' section
            if (state.accountManagementUrl != null || state.showLinkNewDevice || state.showBlockedUsersItem) {
                SettingsCard {
                    ManageAccountSection(
                        state = state,
                        onManageAccountClick = onManageAccountClick,
                        onLinkNewDeviceClick = onLinkNewDeviceClick,
                        onOpenBlockedUsers = onOpenBlockedUsers,
                    )
                }
            }
            // General section
            SettingsCard {
                GeneralSection(
                    state = state,
                    onOpenAbout = onOpenAbout,
                    onOpenAnalytics = onOpenAnalytics,
                    onOpenRageShake = onOpenRageShake,
                    onOpenAdvancedSettings = onOpenAdvancedSettings,
                    onOpenLabs = onOpenLabs,
                )
            }
            // Sign out / deactivate (separate destructive card, like iOS)
            SettingsCard {
                AccountActionsSection(
                    state = state,
                    onSignOutClick = onSignOutClick,
                    onDeactivateClick = onDeactivateClick,
                )
            }
            if (state.showDeveloperSettings) {
                SettingsCard {
                    DeveloperPreferencesView(onOpenDeveloperSettings)
                }
            }
            // Version
            Footer(
                version = state.version,
                deviceId = state.deviceId,
                onClick = if (!state.showDeveloperSettings) {
                    { state.eventSink(PreferencesRootEvent.OnVersionInfoClick) }
                } else {
                    null
                }
            )
        }
    }
}

@Composable
private fun ColumnScope.MultiAccountSection(
    state: PreferencesRootState,
    onAddAccountClick: () -> Unit,
) {
    HorizontalDivider()
    state.otherSessions.forEach { matrixUser ->
        MatrixUserRow(
            modifier = Modifier
                .clickable {
                    state.eventSink(PreferencesRootEvent.SwitchToSession(matrixUser.userId))
                }
                .padding(top = 2.dp, bottom = 2.dp, end = 8.dp),
            matrixUser = matrixUser,
            avatarSize = AvatarSize.AccountItem,
            verticalSpaceWidth = 16.dp,
        )
    }
    ListItem(
        leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Plus())),
        headlineContent = {
            Text(stringResource(CommonStrings.common_add_another_account))
        },
        onClick = onAddAccountClick,
    )
}

@Composable
private fun ColumnScope.ManageAppSection(
    state: PreferencesRootState,
    onOpenNotificationSettings: () -> Unit,
    onOpenLockScreenSettings: () -> Unit,
    onSecureBackupClick: () -> Unit,
) {
    NavRow(
        title = stringResource(id = R.string.screen_notification_settings_title),
        icon = CompoundIcons.Notifications(),
        onClick = onOpenNotificationSettings,
    )
    RowSeparator()
    NavRow(
        title = stringResource(id = CommonStrings.common_screen_lock),
        icon = CompoundIcons.Lock(),
        onClick = onOpenLockScreenSettings,
    )
    if (state.showSecureBackup) {
        RowSeparator()
        ListItem(
            headlineContent = { Text(stringResource(id = CommonStrings.common_encryption)) },
            leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Key())),
            trailingContent = ListItemContent.Badge.takeIf { state.showSecureBackupBadge },
            onClick = onSecureBackupClick,
        )
    }
}

/**
 * A grouped, rounded "card" container that holds a set of rows, sitting on the subtle page
 * background — mirrors the iOS Settings inset-grouped style.
 */
@Composable
private fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        color = ElementTheme.colors.bgCanvasDefaultLevel1,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        modifier = modifier.padding(start = 32.dp, end = 16.dp, bottom = 4.dp),
        text = title,
        style = ElementTheme.typography.fontBodyMdMedium,
        color = ElementTheme.colors.textSecondary,
    )
}

/**
 * Profile header (mirrors iOS): avatar + display name + matrix id, with trailing "copy id" and
 * "QR code" icon buttons. Tapping the row (or the QR button) opens the full user profile.
 */
@Composable
private fun SettingsUserHeader(
    matrixUser: MatrixUser,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserPreferences(
            modifier = Modifier.weight(1f),
            matrixUser = matrixUser,
        )
        IconButton(
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(
                    ClipData.newPlainText("user id", matrixUser.userId.value),
                )
            },
        ) {
            Icon(
                imageVector = CompoundIcons.Copy(),
                contentDescription = stringResource(id = CommonStrings.action_copy),
                tint = ElementTheme.colors.iconSecondary,
            )
        }
        IconButton(onClick = onClick) {
            Icon(
                imageVector = CompoundIcons.QrCode(),
                contentDescription = stringResource(id = CommonStrings.a11y_qr_code),
                tint = ElementTheme.colors.iconSecondary,
            )
        }
    }
}

/**
 * A navigation row with a leading icon and a trailing chevron (mirrors the iOS disclosure rows).
 */
@Composable
private fun ColumnScope.NavRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        leadingContent = ListItemContent.Icon(IconSource.Vector(icon)),
        trailingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.ChevronRight())),
        onClick = onClick,
    )
}

/** An inset divider between rows inside a [SettingsCard], aligned past the leading icon (iOS style). */
@Composable
private fun RowSeparator() {
    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
}

/**
 * Credit balance card (mirrors iOS `creditBalanceCard`): large balance amount + primary "Recharge"
 * action, a divider, then "Billing" / "Usage" secondary text buttons split by a vertical divider.
 */
@Composable
private fun ColumnScope.CreditBalanceCard(
    loadState: CreditBalanceLoadState,
    onOpenCreditsTopUp: () -> Unit,
    onOpenCreditsBilling: () -> Unit,
    onOpenCreditsUsage: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        color = ElementTheme.colors.bgCanvasDefaultLevel1,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(id = R.string.screen_preferences_credit_balance_title),
                style = ElementTheme.typography.fontBodyMdMedium,
                color = ElementTheme.colors.textSecondary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = loadState.displayBalance(),
                    style = ElementTheme.typography.fontHeadingLgBold.copy(fontSize = 36.sp, fontWeight = FontWeight.SemiBold),
                    color = if (loadState is CreditBalanceLoadState.Loaded) {
                        ElementTheme.colors.textPrimary
                    } else {
                        ElementTheme.colors.textSecondary
                    },
                    maxLines = 1,
                )
                Button(
                    text = stringResource(id = R.string.screen_preferences_credit_recharge),
                    size = ButtonSize.Small,
                    onClick = onOpenCreditsTopUp,
                )
            }
            HorizontalDivider()
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    text = stringResource(id = R.string.screen_preferences_credit_billing),
                    onClick = onOpenCreditsBilling,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    modifier = Modifier
                        .width(1.dp)
                        .height(24.dp),
                    color = ElementTheme.colors.borderInteractiveSecondary,
                ) {}
                TextButton(
                    text = stringResource(id = R.string.screen_preferences_credit_usage),
                    onClick = onOpenCreditsUsage,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.AiAssistantRows(
    onOpenAgentManagement: () -> Unit,
    onOpenVoiceLibrary: () -> Unit,
    onOpenSkills: () -> Unit,
    onOpenVaultManagement: () -> Unit,
    onOpenConnectors: () -> Unit,
    onOpenWebhookTriggers: () -> Unit,
) {
    NavRow(
        title = stringResource(id = R.string.screen_preferences_agent_management_title),
        icon = CompoundIcons.Admin(),
        onClick = onOpenAgentManagement,
    )
    RowSeparator()
    NavRow(
        title = stringResource(id = R.string.screen_preferences_voice_library_title),
        icon = CompoundIcons.Extensions(),
        onClick = onOpenVoiceLibrary,
    )
    RowSeparator()
    NavRow(
        title = stringResource(id = R.string.screen_preferences_skills_management_title),
        icon = CompoundIcons.Extensions(),
        onClick = onOpenSkills,
    )
    RowSeparator()
    NavRow(
        title = stringResource(id = R.string.screen_preferences_vault_management_title),
        icon = CompoundIcons.Lock(),
        onClick = onOpenVaultManagement,
    )
    RowSeparator()
    NavRow(
        title = stringResource(id = R.string.screen_preferences_connectors_title),
        icon = CompoundIcons.Link(),
        onClick = onOpenConnectors,
    )
    RowSeparator()
    NavRow(
        title = stringResource(id = R.string.screen_preferences_webhook_triggers_title),
        icon = CompoundIcons.Notifications(),
        onClick = onOpenWebhookTriggers,
    )
}

@Composable
private fun ColumnScope.ManageAccountSection(
    state: PreferencesRootState,
    onManageAccountClick: (url: String) -> Unit,
    onLinkNewDeviceClick: () -> Unit,
    onOpenBlockedUsers: () -> Unit,
) {
    var needsSeparator = false
    state.accountManagementUrl?.let { url ->
        ListItem(
            headlineContent = { Text(stringResource(id = CommonStrings.action_manage_account_and_devices)) },
            leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.UserProfile())),
            trailingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.PopOut())),
            onClick = { onManageAccountClick(url) },
        )
        needsSeparator = true
    }
    if (state.showLinkNewDevice) {
        if (needsSeparator) RowSeparator()
        NavRow(
            title = stringResource(id = CommonStrings.common_link_new_device),
            icon = CompoundIcons.Devices(),
            onClick = onLinkNewDeviceClick,
        )
        needsSeparator = true
    }
    if (state.showBlockedUsersItem) {
        if (needsSeparator) RowSeparator()
        ListItem(
            headlineContent = { Text(stringResource(id = CommonStrings.common_blocked_users)) },
            leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Block())),
            onClick = onOpenBlockedUsers,
            trailingContent = ListItemContent.Text(state.nbOfBlockedUsers.toString()),
        )
    }
}

private fun CreditBalanceLoadState.displayBalance(): String {
    return when (this) {
        CreditBalanceLoadState.Loading,
        CreditBalanceLoadState.Unavailable -> "$0.00"
        is CreditBalanceLoadState.Loaded -> balanceUsd.takeIf { it.startsWith("$") } ?: "$$balanceUsd"
    }
}

@Composable
private fun ColumnScope.GeneralSection(
    state: PreferencesRootState,
    onOpenAbout: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenRageShake: () -> Unit,
    onOpenAdvancedSettings: () -> Unit,
    onOpenLabs: () -> Unit,
) {
    NavRow(
        title = stringResource(id = CommonStrings.common_advanced_settings),
        icon = CompoundIcons.Settings(),
        onClick = onOpenAdvancedSettings,
    )
    if (state.showLabsItem) {
        RowSeparator()
        NavRow(
            title = stringResource(id = R.string.screen_labs_title),
            icon = CompoundIcons.Labs(),
            onClick = onOpenLabs,
        )
    }
    RowSeparator()
    NavRow(
        title = stringResource(id = CommonStrings.common_about),
        icon = CompoundIcons.Info(),
        onClick = onOpenAbout,
    )
    if (state.canReportBug) {
        RowSeparator()
        NavRow(
            title = stringResource(id = CommonStrings.common_report_a_problem),
            icon = CompoundIcons.ChatProblem(),
            onClick = onOpenRageShake,
        )
    }
    if (state.showAnalyticsSettings) {
        RowSeparator()
        NavRow(
            title = stringResource(id = CommonStrings.common_analytics),
            icon = CompoundIcons.Chart(),
            onClick = onOpenAnalytics,
        )
    }
}

@Composable
private fun ColumnScope.AccountActionsSection(
    state: PreferencesRootState,
    onSignOutClick: () -> Unit,
    onDeactivateClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(id = CommonStrings.action_signout)) },
        leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.SignOut())),
        style = ListItemStyle.Destructive,
        onClick = onSignOutClick,
    )
    if (state.canDeactivateAccount) {
        RowSeparator()
        ListItem(
            headlineContent = { Text(stringResource(id = CommonStrings.action_delete_account)) },
            leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Delete())),
            style = ListItemStyle.Destructive,
            onClick = onDeactivateClick,
        )
    }
}

@Composable
private fun ColumnScope.Footer(
    version: String,
    deviceId: DeviceId?,
    onClick: (() -> Unit)?,
) {
    val text = remember(version, deviceId) {
        buildString {
            append(version)
            if (deviceId != null) {
                append("\n")
                append(deviceId)
            }
        }
    }
    Text(
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .clickable(enabled = onClick != null, onClick = onClick ?: {})
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        textAlign = TextAlign.Center,
        text = text,
        style = ElementTheme.typography.fontBodySmRegular,
        color = ElementTheme.colors.textSecondary,
    )
}

@Composable
private fun DeveloperPreferencesView(onOpenDeveloperSettings: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(id = CommonStrings.common_developer_options)) },
        leadingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.Code())),
        onClick = onOpenDeveloperSettings
    )
}

@PreviewWithLargeHeight
@Composable
internal fun PreferencesRootViewLightPreview(@PreviewParameter(PreferencesRootStateProvider::class) state: PreferencesRootState) =
    ElementPreviewLight(
        drawableFallbackForImages = CommonDrawables.sample_avatar,
    ) { ContentToPreview(state) }

@PreviewWithLargeHeight
@Composable
internal fun PreferencesRootViewDarkPreview(@PreviewParameter(PreferencesRootStateProvider::class) state: PreferencesRootState) =
    ElementPreviewDark(
        drawableFallbackForImages = CommonDrawables.sample_avatar,
    ) { ContentToPreview(state) }

@ExcludeFromCoverage
@Composable
private fun ContentToPreview(state: PreferencesRootState) {
    PreferencesRootView(
        state = state,
        onBackClick = {},
        onAddAccountClick = {},
        onOpenAnalytics = {},
        onOpenRageShake = {},
        onOpenDeveloperSettings = {},
        onOpenAdvancedSettings = {},
        onOpenLabs = {},
        onOpenAbout = {},
        onSecureBackupClick = {},
        onManageAccountClick = {},
        onLinkNewDeviceClick = {},
        onOpenNotificationSettings = {},
        onOpenLockScreenSettings = {},
        onOpenWebhookTriggers = {},
        onOpenConnectors = {},
        onOpenVoiceLibrary = {},
        onOpenAgentManagement = {},
        onOpenSkills = {},
        onOpenVaultManagement = {},
        onOpenUserProfile = {},
        onOpenBlockedUsers = {},
        onOpenCreditsTopUp = {},
        onOpenCreditsBilling = {},
        onOpenCreditsUsage = {},
        onSignOutClick = {},
        onDeactivateClick = {},
    )
}

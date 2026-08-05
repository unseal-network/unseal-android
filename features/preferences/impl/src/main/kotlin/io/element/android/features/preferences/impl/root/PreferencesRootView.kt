/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.root

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.preferences.impl.R
import io.element.android.libraries.architecture.coverage.ExcludeFromCoverage
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.avatar.AvatarType
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
import io.element.android.libraries.matrix.ui.model.getAvatarData
import io.element.android.libraries.matrix.ui.model.getBestName
import io.element.android.libraries.qrcode.QrCodeImage
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
    onOpenBroadcastUsage: () -> Unit = {},
    onSignOutClick: () -> Unit,
    onDeactivateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = rememberSnackbarHostState(snackbarMessage = state.snackbarMessage)
    var isQrCodeDialogVisible by remember { mutableStateOf(false) }

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
                    onQrCodeClick = { isQrCodeDialogVisible = true },
                )
                if (state.isMultiAccountEnabled) {
                    MultiAccountSection(
                        state = state,
                        onAddAccountClick = onAddAccountClick,
                    )
                }
            }
            // 'AI Assistant' section (right after the profile, mirroring iOS). The billing card and
            // the agent-rows card are spaced like every other settings card (20.dp); only the
            // section header hugs the billing card (8.dp).
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsSectionHeader(
                        title = stringResource(id = R.string.screen_preferences_ai_assistant_section_title),
                    )
                    CreditBalanceCard(
                        loadState = state.aiAssistant.creditBalanceLoadState,
                        onOpenCreditsTopUp = onOpenCreditsTopUp,
                        onOpenCreditsBilling = onOpenCreditsBilling,
                        onOpenCreditsUsage = onOpenCreditsUsage,
                    )
                    SettingsCard {
                        NavRow(
                            title = stringResource(id = R.string.screen_preferences_broadcast_usage),
                            icon = CompoundIcons.VideoCall(),
                            onClick = onOpenBroadcastUsage,
                        )
                    }
                }
                SettingsCard {
                    AiAssistantRows(
                        entries = state.aiAssistant.entries,
                        onEntryClick = { entry ->
                            when (entry) {
                                SettingsAiAssistantEntry.AgentManagement -> onOpenAgentManagement()
                                SettingsAiAssistantEntry.VoiceLibrary -> onOpenVoiceLibrary()
                                SettingsAiAssistantEntry.SkillsManagement -> onOpenSkills()
                                SettingsAiAssistantEntry.VaultManagement -> onOpenVaultManagement()
                                SettingsAiAssistantEntry.Connectors -> onOpenConnectors()
                                SettingsAiAssistantEntry.WebhookTriggers -> onOpenWebhookTriggers()
                            }
                        },
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
    if (isQrCodeDialogVisible) {
        UserQrCodeDialog(
            matrixUser = state.myUser,
            onDismiss = { isQrCodeDialogVisible = false },
        )
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
 * "QR code" icon buttons. Tapping the row opens the full user profile; tapping the QR button shows
 * this account's QR code.
 */
@Composable
private fun SettingsUserHeader(
    matrixUser: MatrixUser,
    onClick: () -> Unit,
    onQrCodeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 8.dp, top = 13.dp, bottom = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            avatarData = matrixUser.getAvatarData(size = AvatarSize.UserPreference),
            avatarType = AvatarType.User,
            contentDescription = matrixUser.getBestName(),
        )
        Spacer(modifier = Modifier.width(13.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = matrixUser.getBestName(),
                style = ElementTheme.typography.fontBodyLgMedium,
                color = ElementTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = matrixUser.userId.value,
                style = ElementTheme.typography.fontBodyMdRegular,
                color = ElementTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
        IconButton(onClick = onQrCodeClick) {
            Icon(
                imageVector = CompoundIcons.QrCode(),
                contentDescription = stringResource(id = CommonStrings.a11y_qr_code),
                tint = ElementTheme.colors.iconSecondary,
            )
        }
    }
}

@Composable
private fun UserQrCodeDialog(
    matrixUser: MatrixUser,
    onDismiss: () -> Unit,
) {
    val qrCodeData = remember(matrixUser.userId) {
        "https://matrix.to/#/${Uri.encode(matrixUser.userId.value)}"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(id = CommonStrings.a11y_qr_code),
                style = ElementTheme.typography.fontHeadingSmMedium,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QrCodeImage(
                    data = qrCodeData,
                    modifier = Modifier.size(236.dp),
                )
                Text(
                    text = matrixUser.getBestName(),
                    style = ElementTheme.typography.fontBodyLgMedium,
                    color = ElementTheme.colors.textPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = matrixUser.userId.value,
                    style = ElementTheme.typography.fontBodySmRegular,
                    color = ElementTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        confirmButton = {
            TextButton(
                text = stringResource(id = CommonStrings.action_close),
                onClick = onDismiss,
            )
        },
    )
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
    entries: List<SettingsAiAssistantEntry>,
    onEntryClick: (SettingsAiAssistantEntry) -> Unit,
) {
    entries.forEachIndexed { index, entry ->
        if (index > 0) {
            RowSeparator()
        }
        NavRow(
            title = stringResource(id = entry.titleRes),
            icon = entry.icon(),
            onClick = { onEntryClick(entry) },
        )
    }
}

private val SettingsAiAssistantEntry.titleRes: Int
    get() = when (this) {
        SettingsAiAssistantEntry.AgentManagement -> R.string.screen_preferences_agent_management_title
        SettingsAiAssistantEntry.VoiceLibrary -> R.string.screen_preferences_voice_library_title
        SettingsAiAssistantEntry.SkillsManagement -> R.string.screen_preferences_skills_management_title
        SettingsAiAssistantEntry.VaultManagement -> R.string.screen_preferences_vault_management_title
        SettingsAiAssistantEntry.Connectors -> R.string.screen_preferences_connectors_title
        SettingsAiAssistantEntry.WebhookTriggers -> R.string.screen_preferences_webhook_triggers_title
    }

@Composable
private fun SettingsAiAssistantEntry.icon(): ImageVector {
    return when (this) {
        SettingsAiAssistantEntry.AgentManagement -> CompoundIcons.Labs()
        SettingsAiAssistantEntry.VoiceLibrary -> CompoundIcons.MicOn()
        SettingsAiAssistantEntry.SkillsManagement -> CompoundIcons.ListBulleted()
        SettingsAiAssistantEntry.VaultManagement -> CompoundIcons.Lock()
        SettingsAiAssistantEntry.Connectors -> CompoundIcons.Link()
        SettingsAiAssistantEntry.WebhookTriggers -> CompoundIcons.Notifications()
    }
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

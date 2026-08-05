/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.root

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import com.bumble.appyx.core.plugin.plugins
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.logout.api.direct.DirectLogoutEvents
import io.element.android.features.logout.api.direct.DirectLogoutView
import io.element.android.libraries.androidutils.browser.openUrlInChromeCustomTab
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.user.MatrixUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

@ContributesNode(SessionScope::class)
@AssistedInject
class PreferencesRootNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    private val presenter: PreferencesRootPresenter,
    private val directLogoutView: DirectLogoutView,
) : Node(buildContext, plugins = plugins) {
    data class CreditBalanceRefreshRequests(
        val flow: Flow<Unit>,
    ) : Plugin

    interface Callback : Plugin {
        fun navigateToAddAccount()
        fun navigateToBugReport()
        fun navigateToSecureBackup()
        fun navigateToAnalyticsSettings()
        fun navigateToAbout()
        fun navigateToDeveloperSettings()
        fun navigateToNotificationSettings()
        fun navigateToLockScreenSettings()
        fun navigateToWebhookTriggers()
        fun navigateToConnectors()
        fun navigateToVoiceLibrary()
        fun navigateToAgentManagement()
        fun navigateToSkills()
        fun navigateToVaultManagement()
        fun navigateToAdvancedSettings()
        fun navigateToLabs()
        fun navigateToLinkNewDevice()
        fun navigateToUserProfile(matrixUser: MatrixUser)
        fun navigateToBlockedUsers()
        fun startSignOutFlow()
        fun startAccountDeactivationFlow()
        fun openCreditsTopUp()
        fun navigateToCreditsBilling()
        fun navigateToCreditsUsage()
        fun navigateToBroadcastUsage()
    }

    private val callback: Callback = callback()
    private val creditBalanceRefreshRequests = plugins<CreditBalanceRefreshRequests>().firstOrNull()?.flow ?: emptyFlow()

    private fun onManageAccountClick(
        activity: Activity,
        url: String?,
        isDark: Boolean,
    ) {
        url?.let {
            activity.openUrlInChromeCustomTab(
                null,
                darkTheme = isDark,
                url = it
            )
        }
    }

    @Composable
    override fun View(modifier: Modifier) {
        val state = presenter.present()
        val activity = requireNotNull(LocalActivity.current)
        val isDark = ElementTheme.isLightTheme.not()
        LaunchedEffect(creditBalanceRefreshRequests) {
            creditBalanceRefreshRequests.collect {
                state.eventSink(PreferencesRootEvent.RefreshCreditBalance)
            }
        }
        PreferencesRootView(
            state = state,
            modifier = modifier,
            onBackClick = this::navigateUp,
            onAddAccountClick = callback::navigateToAddAccount,
            onOpenRageShake = callback::navigateToBugReport,
            onOpenAnalytics = callback::navigateToAnalyticsSettings,
            onOpenAbout = callback::navigateToAbout,
            onSecureBackupClick = callback::navigateToSecureBackup,
            onOpenDeveloperSettings = callback::navigateToDeveloperSettings,
            onOpenAdvancedSettings = callback::navigateToAdvancedSettings,
            onOpenLabs = callback::navigateToLabs,
            onLinkNewDeviceClick = callback::navigateToLinkNewDevice,
            onManageAccountClick = { onManageAccountClick(activity, it, isDark) },
            onOpenNotificationSettings = callback::navigateToNotificationSettings,
            onOpenLockScreenSettings = callback::navigateToLockScreenSettings,
            onOpenWebhookTriggers = callback::navigateToWebhookTriggers,
            onOpenConnectors = callback::navigateToConnectors,
            onOpenVoiceLibrary = callback::navigateToVoiceLibrary,
            onOpenAgentManagement = callback::navigateToAgentManagement,
            onOpenSkills = callback::navigateToSkills,
            onOpenVaultManagement = callback::navigateToVaultManagement,
            onOpenUserProfile = callback::navigateToUserProfile,
            onOpenBlockedUsers = callback::navigateToBlockedUsers,
            onOpenCreditsTopUp = callback::openCreditsTopUp,
            onOpenCreditsBilling = callback::navigateToCreditsBilling,
            onOpenCreditsUsage = callback::navigateToCreditsUsage,
            onOpenBroadcastUsage = callback::navigateToBroadcastUsage,
            onSignOutClick = {
                if (state.directLogoutState.canDoDirectSignOut) {
                    state.directLogoutState.eventSink(DirectLogoutEvents.Logout(ignoreSdkError = false))
                } else {
                    callback.startSignOutFlow()
                }
            },
            onDeactivateClick = callback::startAccountDeactivationFlow
        )

        directLogoutView.Render(state = state.directLogoutState)
    }
}

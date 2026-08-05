/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import com.bumble.appyx.navmodel.backstack.BackStack
import com.bumble.appyx.navmodel.backstack.operation.pop
import com.bumble.appyx.navmodel.backstack.operation.push
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.features.credits.api.CreditsEntryPoint
import io.element.android.features.broadcastusage.api.BroadcastUsageEntryPoint
import io.element.android.features.deactivation.api.AccountDeactivationEntryPoint
import io.element.android.features.licenses.api.OpenSourceLicensesEntryPoint
import io.element.android.features.lockscreen.api.LockScreenEntryPoint
import io.element.android.features.logout.api.LogoutEntryPoint
import io.element.android.features.preferences.api.PreferencesEntryPoint
import io.element.android.features.preferences.impl.about.AboutNode
import io.element.android.features.preferences.impl.advanced.AdvancedSettingsNode
import io.element.android.features.preferences.impl.analytics.AnalyticsSettingsNode
import io.element.android.features.preferences.impl.blockedusers.BlockedUsersNode
import io.element.android.features.preferences.impl.developer.DeveloperSettingsNode
import io.element.android.features.preferences.impl.labs.LabsNode
import io.element.android.features.preferences.impl.notifications.NotificationSettingsNode
import io.element.android.features.preferences.impl.notifications.edit.EditDefaultNotificationSettingNode
import io.element.android.features.preferences.impl.root.PreferencesRootNode
import io.element.android.features.preferences.impl.user.editprofile.EditUserProfileNode
import io.element.android.features.preferences.impl.vault.VaultManagementNode
import io.element.android.features.preferences.impl.vault.edit.VaultEditNode
import io.element.android.features.agentmanagement.api.AgentManagementEntryPoint
import io.element.android.features.connectors.api.ConnectorsEntryPoint
import io.element.android.features.skills.api.SkillsEntryPoint
import io.element.android.features.voicelibrary.api.VoiceLibraryEntryPoint
import io.element.android.features.webhooks.api.WebhookTriggersEntryPoint
import io.element.android.libraries.architecture.BackstackView
import io.element.android.libraries.architecture.BaseFlowNode
import io.element.android.libraries.architecture.appyx.canPop
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.architecture.createNode
import io.element.android.libraries.chatbot.api.model.credits.CreditBalance
import io.element.android.libraries.designsystem.utils.OpenUrlInTabView
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.RoomIdOrAlias
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.troubleshoot.api.NotificationTroubleShootEntryPoint
import io.element.android.libraries.troubleshoot.api.PushHistoryEntryPoint
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.parcelize.Parcelize

@ContributesNode(SessionScope::class)
@AssistedInject
class PreferencesFlowNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    private val lockScreenEntryPoint: LockScreenEntryPoint,
    private val notificationTroubleShootEntryPoint: NotificationTroubleShootEntryPoint,
    private val pushHistoryEntryPoint: PushHistoryEntryPoint,
    private val logoutEntryPoint: LogoutEntryPoint,
    private val openSourceLicensesEntryPoint: OpenSourceLicensesEntryPoint,
    private val accountDeactivationEntryPoint: AccountDeactivationEntryPoint,
    private val webhookTriggersEntryPoint: WebhookTriggersEntryPoint,
    private val connectorsEntryPoint: ConnectorsEntryPoint,
    private val voiceLibraryEntryPoint: VoiceLibraryEntryPoint,
    private val agentManagementEntryPoint: AgentManagementEntryPoint,
    private val skillsEntryPoint: SkillsEntryPoint,
    private val creditsEntryPoint: CreditsEntryPoint,
    private val broadcastUsageEntryPoint: BroadcastUsageEntryPoint,
) : BaseFlowNode<PreferencesFlowNode.NavTarget>(
    backstack = BackStack(
        initialElement = plugins.filterIsInstance<PreferencesEntryPoint.Params>().first().initialElement.toNavTarget(),
        savedStateMap = buildContext.savedStateMap,
    ),
    buildContext = buildContext,
    plugins = plugins
) {
    sealed interface NavTarget : Parcelable {
        @Parcelize
        data object Root : NavTarget

        @Parcelize
        data object DeveloperSettings : NavTarget

        @Parcelize
        data object AdvancedSettings : NavTarget

        @Parcelize
        data object Labs : NavTarget

        @Parcelize
        data object AnalyticsSettings : NavTarget

        @Parcelize
        data object About : NavTarget

        @Parcelize
        data object NotificationSettings : NavTarget

        @Parcelize
        data object TroubleshootNotifications : NavTarget

        @Parcelize
        data object PushHistory : NavTarget

        @Parcelize
        data object LockScreenSettings : NavTarget

        @Parcelize
        data object WebhookTriggers : NavTarget

        @Parcelize
        data object Connectors : NavTarget

        @Parcelize
        data object VoiceLibrary : NavTarget

        @Parcelize
        data object AgentManagement : NavTarget

        @Parcelize
        data class Skills(
            val initialTarget: SkillsEntryPoint.InitialTarget = SkillsEntryPoint.InitialTarget.Home,
        ) : NavTarget

        @Parcelize
        data object VaultManagement : NavTarget

        @Parcelize
        data class VaultEdit(val key: String?, val description: String?) : NavTarget

        @Parcelize
        data class EditDefaultNotificationSetting(val isOneToOne: Boolean) : NavTarget

        @Parcelize
        data class UserProfile(val matrixUser: MatrixUser) : NavTarget

        @Parcelize
        data object BlockedUsers : NavTarget

        @Parcelize
        data object SignOut : NavTarget

        @Parcelize
        data object AccountDeactivation : NavTarget

        @Parcelize
        data object OssLicenses : NavTarget

        @Parcelize
        data class Credits(
            val initialTab: CreditsEntryPoint.CreditsTab,
            val openTopUpInitially: Boolean = false,
        ) : NavTarget

        @Parcelize
        data object BroadcastUsage : NavTarget
    }

    private val callback: PreferencesEntryPoint.Callback = callback()
    private val vaultReloadRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val creditBalanceReloadRequests = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1)

    override fun resolve(navTarget: NavTarget, buildContext: BuildContext): Node {
        return when (navTarget) {
            NavTarget.Root -> {
                val callback = object : PreferencesRootNode.Callback {
                    override fun navigateToAddAccount() {
                        callback.navigateToAddAccount()
                    }

                    override fun navigateToBugReport() {
                        callback.navigateToBugReport()
                    }

                    override fun navigateToSecureBackup() {
                        callback.navigateToSecureBackup()
                    }

                    override fun navigateToAnalyticsSettings() {
                        backstack.push(NavTarget.AnalyticsSettings)
                    }

                    override fun navigateToAbout() {
                        backstack.push(NavTarget.About)
                    }

                    override fun navigateToDeveloperSettings() {
                        backstack.push(NavTarget.DeveloperSettings)
                    }

                    override fun navigateToNotificationSettings() {
                        backstack.push(NavTarget.NotificationSettings)
                    }

                    override fun navigateToLockScreenSettings() {
                        backstack.push(NavTarget.LockScreenSettings)
                    }

                    override fun navigateToWebhookTriggers() {
                        backstack.push(NavTarget.WebhookTriggers)
                    }

                    override fun navigateToConnectors() {
                        backstack.push(NavTarget.Connectors)
                    }

                    override fun navigateToVoiceLibrary() {
                        backstack.push(NavTarget.VoiceLibrary)
                    }

                    override fun navigateToAgentManagement() {
                        backstack.push(NavTarget.AgentManagement)
                    }

                    override fun navigateToSkills() {
                        backstack.push(NavTarget.Skills())
                    }

                    override fun navigateToVaultManagement() {
                        backstack.push(NavTarget.VaultManagement)
                    }

                    override fun navigateToAdvancedSettings() {
                        backstack.push(NavTarget.AdvancedSettings)
                    }

                    override fun navigateToLabs() {
                        backstack.push(NavTarget.Labs)
                    }

                    override fun navigateToLinkNewDevice() {
                        callback.navigateToLinkNewDevice()
                    }

                    override fun navigateToUserProfile(matrixUser: MatrixUser) {
                        backstack.push(NavTarget.UserProfile(matrixUser))
                    }

                    override fun navigateToBlockedUsers() {
                        backstack.push(NavTarget.BlockedUsers)
                    }

                    override fun startSignOutFlow() {
                        backstack.push(NavTarget.SignOut)
                    }

                    override fun startAccountDeactivationFlow() {
                        backstack.push(NavTarget.AccountDeactivation)
                    }

                    override fun navigateToCreditsBilling() {
                        backstack.push(NavTarget.Credits(CreditsEntryPoint.CreditsTab.Balance))
                    }

                    override fun navigateToCreditsUsage() {
                        backstack.push(NavTarget.Credits(CreditsEntryPoint.CreditsTab.DailyUsage))
                    }

                    override fun navigateToBroadcastUsage() {
                        backstack.push(NavTarget.BroadcastUsage)
                    }

                    override fun openCreditsTopUp() {
                        backstack.push(
                            NavTarget.Credits(
                                initialTab = CreditsEntryPoint.CreditsTab.Balance,
                                openTopUpInitially = true,
                            )
                        )
                    }
                }
                createNode<PreferencesRootNode>(
                    buildContext,
                    plugins = listOf(
                        callback,
                        PreferencesRootNode.CreditBalanceRefreshRequests(creditBalanceReloadRequests),
                    ),
                )
            }
            NavTarget.DeveloperSettings -> {
                val developerSettingsCallback = object : DeveloperSettingsNode.Callback {
                    override fun navigateToPushHistory() {
                        backstack.push(NavTarget.PushHistory)
                    }

                    override fun onDone() {
                        if (backstack.canPop()) {
                            backstack.pop()
                        } else {
                            navigateUp()
                        }
                    }
                }
                createNode<DeveloperSettingsNode>(buildContext, listOf(developerSettingsCallback))
            }
            NavTarget.Labs -> {
                val callback = object : LabsNode.Callback {
                    override fun onDone() {
                        backstack.pop()
                    }
                }
                createNode<LabsNode>(buildContext, listOf(callback))
            }
            NavTarget.About -> {
                val callback = object : AboutNode.Callback {
                    override fun navigateToOssLicenses() {
                        backstack.push(NavTarget.OssLicenses)
                    }
                }
                createNode<AboutNode>(buildContext, listOf(callback))
            }
            NavTarget.AnalyticsSettings -> {
                createNode<AnalyticsSettingsNode>(buildContext)
            }
            NavTarget.NotificationSettings -> {
                val notificationSettingsCallback = object : NotificationSettingsNode.Callback {
                    override fun navigateToEditDefaultNotificationSetting(isOneToOne: Boolean) {
                        backstack.push(NavTarget.EditDefaultNotificationSetting(isOneToOne))
                    }

                    override fun navigateToTroubleshootNotifications() {
                        backstack.push(NavTarget.TroubleshootNotifications)
                    }
                }
                createNode<NotificationSettingsNode>(buildContext, listOf(notificationSettingsCallback))
            }
            NavTarget.TroubleshootNotifications -> {
                notificationTroubleShootEntryPoint.createNode(
                    parentNode = this,
                    buildContext = buildContext,
                    callback = object : NotificationTroubleShootEntryPoint.Callback {
                        override fun onDone() {
                            if (backstack.canPop()) {
                                backstack.pop()
                            } else {
                                navigateUp()
                            }
                        }

                        override fun navigateToBlockedUsers() {
                            backstack.push(NavTarget.BlockedUsers)
                        }
                    },
                )
            }
            NavTarget.PushHistory -> {
                pushHistoryEntryPoint.createNode(
                    parentNode = this,
                    buildContext = buildContext,
                    callback = object : PushHistoryEntryPoint.Callback {
                        override fun onDone() {
                            if (backstack.canPop()) {
                                backstack.pop()
                            } else {
                                navigateUp()
                            }
                        }

                        override fun navigateToEvent(roomId: RoomId, eventId: EventId) {
                            callback.navigateToEvent(roomId, eventId)
                        }
                    },
                )
            }
            is NavTarget.EditDefaultNotificationSetting -> {
                val callback = object : EditDefaultNotificationSettingNode.Callback {
                    override fun navigateToRoomNotificationSettings(roomId: RoomId) {
                        callback.navigateToRoomNotificationSettings(roomId)
                    }
                }
                val input = EditDefaultNotificationSettingNode.Inputs(navTarget.isOneToOne)
                createNode<EditDefaultNotificationSettingNode>(buildContext, plugins = listOf(input, callback))
            }
            NavTarget.AdvancedSettings -> {
                createNode<AdvancedSettingsNode>(buildContext)
            }
            is NavTarget.UserProfile -> {
                val inputs = EditUserProfileNode.Inputs(navTarget.matrixUser)
                val callback = object : EditUserProfileNode.Callback {
                    override fun onDone() {
                        backstack.pop()
                    }
                }
                createNode<EditUserProfileNode>(buildContext, listOf(inputs, callback))
            }
            NavTarget.LockScreenSettings -> {
                lockScreenEntryPoint.createNode(
                    parentNode = this,
                    buildContext = buildContext,
                    navTarget = LockScreenEntryPoint.Target.Settings,
                    callback = object : LockScreenEntryPoint.Callback {
                        override fun onSetupDone() {
                            // No op
                        }
                    }
                )
            }
            NavTarget.WebhookTriggers -> {
                webhookTriggersEntryPoint.createNode(
                    parentNode = this,
                    buildContext = buildContext,
                    params = WebhookTriggersEntryPoint.Params(
                        initialTarget = WebhookTriggersEntryPoint.InitialTarget.Global,
                    ),
                    callback = object : WebhookTriggersEntryPoint.Callback {
                        override fun onDone() {
                            if (backstack.canPop()) {
                                backstack.pop()
                            } else {
                                navigateUp()
                            }
                        }

                        override fun onTriggersChanged() = Unit

                        override fun onOpenConnectUrl(url: String) {
                            connectUrl.value = url
                        }
                    },
                )
            }
            NavTarget.Connectors -> {
                connectorsEntryPoint.createNode(
                    parentNode = this,
                    buildContext = buildContext,
                    callback = object : ConnectorsEntryPoint.Callback {
                        override fun onDone() {
                            if (backstack.canPop()) {
                                backstack.pop()
                            } else {
                                navigateUp()
                            }
                        }

                        override fun onOpenConnectUrl(url: String) {
                            connectUrl.value = url
                        }
                    },
                )
            }
            NavTarget.VoiceLibrary -> {
                voiceLibraryEntryPoint.createNode(
                    parentNode = this,
                    buildContext = buildContext,
                    callback = object : VoiceLibraryEntryPoint.Callback {
                        override fun onDone() {
                            if (backstack.canPop()) {
                                backstack.pop()
                            } else {
                                navigateUp()
                            }
                        }
                    },
                )
            }
            NavTarget.AgentManagement -> {
                agentManagementEntryPoint.createNode(
                    parentNode = this,
                    buildContext = buildContext,
                    params = AgentManagementEntryPoint.Params(),
                    callback = object : AgentManagementEntryPoint.Callback {
                        override fun onDone() {
                            if (backstack.canPop()) backstack.pop() else navigateUp()
                        }

                        override fun onOpenRoom(roomIdOrAlias: RoomIdOrAlias) {
                            callback.navigateToRoom(roomIdOrAlias)
                        }

                        override fun onOpenSkills(botName: String?) {
                            backstack.push(
                                NavTarget.Skills(
                                    initialTarget = botName
                                        ?.let(SkillsEntryPoint.InitialTarget::AgentSkills)
                                        ?: SkillsEntryPoint.InitialTarget.ManagementHub
                                )
                            )
                        }

                        override fun onOpenCreatedDirectRoom(roomId: RoomId) {
                            callback.navigateToCreatedDirectRoom(roomId)
                        }
                    },
                )
            }
            is NavTarget.Skills -> {
                skillsEntryPoint.createNode(
                    parentNode = this,
                    buildContext = buildContext,
                    params = SkillsEntryPoint.Params(navTarget.initialTarget),
                    callback = object : SkillsEntryPoint.Callback {
                        override fun onDone() {
                            if (backstack.canPop()) backstack.pop() else navigateUp()
                        }

                        override fun onCreateSkill() = Unit

                        override fun onSkillDeleted(id: String) = Unit

                        override fun onOpenAgentManagement() {
                            backstack.push(NavTarget.AgentManagement)
                        }
                    },
                )
            }
            NavTarget.VaultManagement -> {
                val vaultCallback = object : VaultManagementNode.Callback {
                    override fun onDone() {
                        if (backstack.canPop()) backstack.pop() else navigateUp()
                    }

                    override fun onAddEntry() {
                        backstack.push(NavTarget.VaultEdit(key = null, description = null))
                    }

                    override fun onEditEntry(item: io.element.android.libraries.chatbot.api.model.vault.ChatbotVaultItem) {
                        backstack.push(NavTarget.VaultEdit(key = item.key, description = item.description))
                    }
                }
                createNode<VaultManagementNode>(
                    buildContext,
                    plugins = listOf(
                        VaultManagementNode.Inputs(vaultReloadRequests),
                        vaultCallback,
                    )
                )
            }
            is NavTarget.VaultEdit -> {
                val inputs = if (navTarget.key == null) {
                    VaultEditNode.Inputs.Create
                } else {
                    VaultEditNode.Inputs.Edit(navTarget.key, navTarget.description)
                }
                val vaultEditCallback = object : VaultEditNode.Callback {
                    override fun onDone() {
                        backstack.pop()
                    }

                    override fun onComplete() {
                        backstack.pop()
                        vaultReloadRequests.tryEmit(Unit)
                    }
                }
                createNode<VaultEditNode>(buildContext, plugins = listOf(inputs, vaultEditCallback))
            }
            NavTarget.BlockedUsers -> {
                createNode<BlockedUsersNode>(buildContext)
            }
            NavTarget.SignOut -> {
                val callBack: LogoutEntryPoint.Callback = object : LogoutEntryPoint.Callback {
                    override fun navigateToSecureBackup() {
                        callback.navigateToSecureBackup()
                    }
                }
                logoutEntryPoint.createNode(
                    parentNode = this,
                    buildContext = buildContext,
                    callback = callBack,
                )
            }
            is NavTarget.OssLicenses -> {
                openSourceLicensesEntryPoint.createNode(this, buildContext)
            }
            NavTarget.AccountDeactivation -> {
                accountDeactivationEntryPoint.createNode(this, buildContext)
            }
            is NavTarget.Credits -> {
                creditsEntryPoint.createNode(
                    parentNode = this,
                    buildContext = buildContext,
                    params = CreditsEntryPoint.Params(
                        initialTab = navTarget.initialTab,
                        openTopUpInitially = navTarget.openTopUpInitially,
                    ),
                    callback = object : CreditsEntryPoint.Callback {
                        override fun onDone() {
                            if (backstack.canPop()) {
                                backstack.pop()
                            } else {
                                navigateUp()
                            }
                        }

                        override fun onTopUpRequested(balance: CreditBalance?) {
                            creditBalanceReloadRequests.tryEmit(Unit)
                        }
                    },
                )
            }
            NavTarget.BroadcastUsage -> {
                broadcastUsageEntryPoint.createNode(
                    parentNode = this,
                    buildContext = buildContext,
                    callback = object : BroadcastUsageEntryPoint.Callback {
                        override fun onDone() {
                            if (backstack.canPop()) backstack.pop() else navigateUp()
                        }
                    },
                )
            }
        }
    }

    private val connectUrl = mutableStateOf<String?>(null)

    @Composable
    override fun View(modifier: Modifier) {
        BackstackView()
        OpenUrlInTabView(connectUrl)
    }
}

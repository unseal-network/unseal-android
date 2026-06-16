/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.testing.junit4.util.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import io.element.android.features.agentmanagement.test.FakeAgentManagementEntryPoint
import io.element.android.features.connectors.test.FakeConnectorsEntryPoint
import io.element.android.features.skills.test.FakeSkillsEntryPoint
import io.element.android.features.voicelibrary.test.FakeVoiceLibraryEntryPoint
import io.element.android.features.credits.api.CreditsEntryPoint
import io.element.android.features.credits.test.FakeCreditsEntryPoint
import io.element.android.features.deactivation.test.FakeAccountDeactivationEntryPoint
import io.element.android.features.licenses.test.FakeOpenSourceLicensesEntryPoint
import io.element.android.features.lockscreen.test.FakeLockScreenEntryPoint
import io.element.android.features.logout.test.FakeLogoutEntryPoint
import io.element.android.features.preferences.api.PreferencesEntryPoint
import io.element.android.features.skills.api.SkillsEntryPoint
import io.element.android.features.webhooks.api.WebhookTriggersEntryPoint
import io.element.android.features.webhooks.test.FakeWebhookTriggersEntryPoint
import io.element.android.libraries.matrix.api.core.RoomIdOrAlias
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.toRoomIdOrAlias
import io.element.android.libraries.troubleshoot.test.FakeNotificationTroubleShootEntryPoint
import io.element.android.libraries.troubleshoot.test.FakePushHistoryEntryPoint
import io.element.android.tests.testutils.lambda.lambdaError
import io.element.android.tests.testutils.node.TestParentNode
import org.junit.Rule
import org.junit.Test

class DefaultPreferencesEntryPointTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `test node builder`() {
        val entryPoint = DefaultPreferencesEntryPoint()
        val parentNode = TestParentNode.create { buildContext, plugins ->
            PreferencesFlowNode(
                buildContext = buildContext,
                plugins = plugins,
                lockScreenEntryPoint = FakeLockScreenEntryPoint(),
                notificationTroubleShootEntryPoint = FakeNotificationTroubleShootEntryPoint(),
                pushHistoryEntryPoint = FakePushHistoryEntryPoint(),
                logoutEntryPoint = FakeLogoutEntryPoint(),
                openSourceLicensesEntryPoint = FakeOpenSourceLicensesEntryPoint(),
                accountDeactivationEntryPoint = FakeAccountDeactivationEntryPoint(),
                webhookTriggersEntryPoint = FakeWebhookTriggersEntryPoint(),
                connectorsEntryPoint = FakeConnectorsEntryPoint(),
                voiceLibraryEntryPoint = FakeVoiceLibraryEntryPoint(),
                agentManagementEntryPoint = FakeAgentManagementEntryPoint(),
                skillsEntryPoint = FakeSkillsEntryPoint(),
                creditsEntryPoint = FakeCreditsEntryPoint(),
            )
        }
        val callback = object : PreferencesEntryPoint.Callback {
            override fun navigateToAddAccount() = lambdaError()
            override fun navigateToLinkNewDevice() = lambdaError()
            override fun navigateToBugReport() = lambdaError()
            override fun navigateToSecureBackup() = lambdaError()
            override fun navigateToRoomNotificationSettings(roomId: RoomId) = lambdaError()
            override fun navigateToEvent(roomId: RoomId, eventId: EventId) = lambdaError()
            override fun navigateToRoom(roomIdOrAlias: RoomIdOrAlias) = lambdaError()
            override fun navigateToCreatedDirectRoom(roomId: RoomId) = lambdaError()
        }
        val params = PreferencesEntryPoint.Params(
            initialElement = PreferencesEntryPoint.InitialTarget.NotificationSettings,
        )
        val result = entryPoint.createNode(
            parentNode = parentNode,
            buildContext = BuildContext.root(null),
            params = params,
            callback = callback,
        )
        assertThat(result).isInstanceOf(PreferencesFlowNode::class.java)
        assertThat(result.plugins).contains(params)
        assertThat(result.plugins).contains(callback)
    }

    @Test
    fun `test initial target to nav target mapping`() {
        assertThat(PreferencesEntryPoint.InitialTarget.Root.toNavTarget())
            .isEqualTo(PreferencesFlowNode.NavTarget.Root)
        assertThat(PreferencesEntryPoint.InitialTarget.NotificationSettings.toNavTarget())
            .isEqualTo(PreferencesFlowNode.NavTarget.NotificationSettings)
        assertThat(PreferencesEntryPoint.InitialTarget.NotificationTroubleshoot.toNavTarget())
            .isEqualTo(PreferencesFlowNode.NavTarget.TroubleshootNotifications)
    }

    @Test
    fun `test webhook triggers nav target creates global webhook node`() {
        var capturedParams: WebhookTriggersEntryPoint.Params? = null
        val node = PreferencesFlowNode(
            buildContext = BuildContext.root(null),
            plugins = listOf(
                PreferencesEntryPoint.Params(
                    initialElement = PreferencesEntryPoint.InitialTarget.Root,
                ),
                object : PreferencesEntryPoint.Callback {
                    override fun navigateToAddAccount() = lambdaError()
                    override fun navigateToLinkNewDevice() = lambdaError()
                    override fun navigateToBugReport() = lambdaError()
                    override fun navigateToSecureBackup() = lambdaError()
                    override fun navigateToRoomNotificationSettings(roomId: RoomId) = lambdaError()
                    override fun navigateToEvent(roomId: RoomId, eventId: EventId) = lambdaError()
                    override fun navigateToRoom(roomIdOrAlias: RoomIdOrAlias) = lambdaError()
                    override fun navigateToCreatedDirectRoom(roomId: RoomId) = lambdaError()
                }
            ),
            lockScreenEntryPoint = FakeLockScreenEntryPoint(),
            notificationTroubleShootEntryPoint = FakeNotificationTroubleShootEntryPoint(),
            pushHistoryEntryPoint = FakePushHistoryEntryPoint(),
            logoutEntryPoint = FakeLogoutEntryPoint(),
            openSourceLicensesEntryPoint = FakeOpenSourceLicensesEntryPoint(),
            accountDeactivationEntryPoint = FakeAccountDeactivationEntryPoint(),
            webhookTriggersEntryPoint = FakeWebhookTriggersEntryPoint { parentNode, _, params, _ ->
                capturedParams = params
                parentNode
            },
            connectorsEntryPoint = FakeConnectorsEntryPoint(),
            voiceLibraryEntryPoint = FakeVoiceLibraryEntryPoint(),
            agentManagementEntryPoint = FakeAgentManagementEntryPoint(),
            skillsEntryPoint = FakeSkillsEntryPoint(),
            creditsEntryPoint = FakeCreditsEntryPoint(),
        )

        node.resolve(PreferencesFlowNode.NavTarget.WebhookTriggers, BuildContext.root(null))

        assertThat(capturedParams?.initialTarget)
            .isEqualTo(WebhookTriggersEntryPoint.InitialTarget.Global)
    }

    @Test
    fun `test connectors nav target creates connectors node`() {
        var created = false
        val node = aPreferencesFlowNode(
            connectorsEntryPoint = FakeConnectorsEntryPoint { parentNode, _, _ ->
                created = true
                parentNode
            },
        )

        node.resolve(PreferencesFlowNode.NavTarget.Connectors, BuildContext.root(null))

        assertThat(created).isTrue()
    }

    @Test
    fun `test skills nav target creates skills home node by default`() {
        val skillsEntryPoint = FakeSkillsEntryPoint()
        val node = aPreferencesFlowNode(
            skillsEntryPoint = skillsEntryPoint,
        )

        node.resolve(PreferencesFlowNode.NavTarget.Skills(), BuildContext.root(null))

        assertThat(skillsEntryPoint.lastParams)
            .isEqualTo(SkillsEntryPoint.Params(SkillsEntryPoint.InitialTarget.Home))
    }

    @Test
    fun `test agent management open skills with bot name opens agent skills target`() {
        val skillsEntryPoint = FakeSkillsEntryPoint()
        var capturedAgentCallback: io.element.android.features.agentmanagement.api.AgentManagementEntryPoint.Callback? = null
        val node = aPreferencesFlowNode(
            skillsEntryPoint = skillsEntryPoint,
            agentManagementEntryPoint = FakeAgentManagementEntryPoint { parentNode, _, _, callback ->
                capturedAgentCallback = callback
                parentNode
            },
        )

        node.resolve(PreferencesFlowNode.NavTarget.AgentManagement, BuildContext.root(null))
        capturedAgentCallback?.onOpenSkills("mailAgent")
        node.resolve(PreferencesFlowNode.NavTarget.Skills(SkillsEntryPoint.InitialTarget.AgentSkills("mailAgent")), BuildContext.root(null))

        assertThat(skillsEntryPoint.lastParams)
            .isEqualTo(SkillsEntryPoint.Params(SkillsEntryPoint.InitialTarget.AgentSkills("mailAgent")))
    }

    @Test
    fun `test agent management open skills without bot name opens management hub target`() {
        val skillsEntryPoint = FakeSkillsEntryPoint()
        var capturedAgentCallback: io.element.android.features.agentmanagement.api.AgentManagementEntryPoint.Callback? = null
        val node = aPreferencesFlowNode(
            skillsEntryPoint = skillsEntryPoint,
            agentManagementEntryPoint = FakeAgentManagementEntryPoint { parentNode, _, _, callback ->
                capturedAgentCallback = callback
                parentNode
            },
        )

        node.resolve(PreferencesFlowNode.NavTarget.AgentManagement, BuildContext.root(null))
        capturedAgentCallback?.onOpenSkills(null)
        node.resolve(PreferencesFlowNode.NavTarget.Skills(SkillsEntryPoint.InitialTarget.ManagementHub), BuildContext.root(null))

        assertThat(skillsEntryPoint.lastParams)
            .isEqualTo(SkillsEntryPoint.Params(SkillsEntryPoint.InitialTarget.ManagementHub))
    }

    @Test
    fun `test credits billing nav target creates balance credits node`() {
        var capturedParams: CreditsEntryPoint.Params? = null
        val node = aPreferencesFlowNode(
            creditsEntryPoint = FakeCreditsEntryPoint { parentNode, _, params, _ ->
                capturedParams = params
                parentNode
            },
        )

        node.resolve(
            PreferencesFlowNode.NavTarget.Credits(CreditsEntryPoint.CreditsTab.Balance),
            BuildContext.root(null),
        )

        assertThat(capturedParams).isEqualTo(
            CreditsEntryPoint.Params(initialTab = CreditsEntryPoint.CreditsTab.Balance)
        )
    }

    @Test
    fun `test credits usage nav target creates daily usage credits node`() {
        var capturedParams: CreditsEntryPoint.Params? = null
        val node = aPreferencesFlowNode(
            creditsEntryPoint = FakeCreditsEntryPoint { parentNode, _, params, _ ->
                capturedParams = params
                parentNode
            },
        )

        node.resolve(
            PreferencesFlowNode.NavTarget.Credits(CreditsEntryPoint.CreditsTab.DailyUsage),
            BuildContext.root(null),
        )

        assertThat(capturedParams).isEqualTo(
            CreditsEntryPoint.Params(initialTab = CreditsEntryPoint.CreditsTab.DailyUsage)
        )
    }

    @Test
    fun `test credits top up nav target creates credits node with top up opened`() {
        var capturedParams: CreditsEntryPoint.Params? = null
        val node = aPreferencesFlowNode(
            creditsEntryPoint = FakeCreditsEntryPoint { parentNode, _, params, _ ->
                capturedParams = params
                parentNode
            },
        )

        node.resolve(
            PreferencesFlowNode.NavTarget.Credits(
                initialTab = CreditsEntryPoint.CreditsTab.Balance,
                openTopUpInitially = true,
            ),
            BuildContext.root(null),
        )

        assertThat(capturedParams).isEqualTo(
            CreditsEntryPoint.Params(
                initialTab = CreditsEntryPoint.CreditsTab.Balance,
                openTopUpInitially = true,
            )
        )
    }

    @Test
    fun `test agent management open room bubbles to preferences callback`() {
        val expectedRoomId = RoomId("!agent-room:example.org")
        var capturedAgentCallback: io.element.android.features.agentmanagement.api.AgentManagementEntryPoint.Callback? = null
        var openedRoom: RoomIdOrAlias? = null
        val node = aPreferencesFlowNode(
            callback = object : PreferencesEntryPoint.Callback {
                override fun navigateToAddAccount() = lambdaError()
                override fun navigateToLinkNewDevice() = lambdaError()
                override fun navigateToBugReport() = lambdaError()
                override fun navigateToSecureBackup() = lambdaError()
                override fun navigateToRoomNotificationSettings(roomId: RoomId) = lambdaError()
                override fun navigateToEvent(roomId: RoomId, eventId: EventId) = lambdaError()
                override fun navigateToRoom(roomIdOrAlias: RoomIdOrAlias) {
                    openedRoom = roomIdOrAlias
                }
                override fun navigateToCreatedDirectRoom(roomId: RoomId) = lambdaError()
            },
            agentManagementEntryPoint = FakeAgentManagementEntryPoint { parentNode, _, _, callback ->
                capturedAgentCallback = callback
                parentNode
            },
        )

        node.resolve(PreferencesFlowNode.NavTarget.AgentManagement, BuildContext.root(null))
        capturedAgentCallback?.onOpenRoom(expectedRoomId.toRoomIdOrAlias())

        assertThat(openedRoom).isEqualTo(expectedRoomId.toRoomIdOrAlias())
    }

    @Test
    fun `test agent management created direct room bubbles to preferences callback`() {
        val expectedRoomId = RoomId("!created-agent-room:example.org")
        var capturedAgentCallback: io.element.android.features.agentmanagement.api.AgentManagementEntryPoint.Callback? = null
        var openedRoom: RoomId? = null
        val node = aPreferencesFlowNode(
            callback = object : PreferencesEntryPoint.Callback {
                override fun navigateToAddAccount() = lambdaError()
                override fun navigateToLinkNewDevice() = lambdaError()
                override fun navigateToBugReport() = lambdaError()
                override fun navigateToSecureBackup() = lambdaError()
                override fun navigateToRoomNotificationSettings(roomId: RoomId) = lambdaError()
                override fun navigateToEvent(roomId: RoomId, eventId: EventId) = lambdaError()
                override fun navigateToRoom(roomIdOrAlias: RoomIdOrAlias) = lambdaError()
                override fun navigateToCreatedDirectRoom(roomId: RoomId) {
                    openedRoom = roomId
                }
            },
            agentManagementEntryPoint = FakeAgentManagementEntryPoint { parentNode, _, _, callback ->
                capturedAgentCallback = callback
                parentNode
            },
        )

        node.resolve(PreferencesFlowNode.NavTarget.AgentManagement, BuildContext.root(null))
        capturedAgentCallback?.onOpenCreatedDirectRoom(expectedRoomId)

        assertThat(openedRoom).isEqualTo(expectedRoomId)
    }
}

private fun aPreferencesFlowNode(
    callback: PreferencesEntryPoint.Callback = object : PreferencesEntryPoint.Callback {
        override fun navigateToAddAccount() = lambdaError()
        override fun navigateToLinkNewDevice() = lambdaError()
        override fun navigateToBugReport() = lambdaError()
        override fun navigateToSecureBackup() = lambdaError()
        override fun navigateToRoomNotificationSettings(roomId: RoomId) = lambdaError()
        override fun navigateToEvent(roomId: RoomId, eventId: EventId) = lambdaError()
        override fun navigateToRoom(roomIdOrAlias: RoomIdOrAlias) = lambdaError()
        override fun navigateToCreatedDirectRoom(roomId: RoomId) = lambdaError()
    },
    creditsEntryPoint: CreditsEntryPoint = FakeCreditsEntryPoint(),
    webhookTriggersEntryPoint: WebhookTriggersEntryPoint = FakeWebhookTriggersEntryPoint(),
    connectorsEntryPoint: io.element.android.features.connectors.api.ConnectorsEntryPoint = FakeConnectorsEntryPoint(),
    voiceLibraryEntryPoint: io.element.android.features.voicelibrary.api.VoiceLibraryEntryPoint = FakeVoiceLibraryEntryPoint(),
    agentManagementEntryPoint: io.element.android.features.agentmanagement.api.AgentManagementEntryPoint = FakeAgentManagementEntryPoint(),
    skillsEntryPoint: io.element.android.features.skills.api.SkillsEntryPoint = FakeSkillsEntryPoint(),
) = PreferencesFlowNode(
    buildContext = BuildContext.root(null),
    plugins = listOf(
        PreferencesEntryPoint.Params(
            initialElement = PreferencesEntryPoint.InitialTarget.Root,
        ),
        callback
    ),
    lockScreenEntryPoint = FakeLockScreenEntryPoint(),
    notificationTroubleShootEntryPoint = FakeNotificationTroubleShootEntryPoint(),
    pushHistoryEntryPoint = FakePushHistoryEntryPoint(),
    logoutEntryPoint = FakeLogoutEntryPoint(),
    openSourceLicensesEntryPoint = FakeOpenSourceLicensesEntryPoint(),
    accountDeactivationEntryPoint = FakeAccountDeactivationEntryPoint(),
    webhookTriggersEntryPoint = webhookTriggersEntryPoint,
    connectorsEntryPoint = connectorsEntryPoint,
    voiceLibraryEntryPoint = voiceLibraryEntryPoint,
    agentManagementEntryPoint = agentManagementEntryPoint,
    skillsEntryPoint = skillsEntryPoint,
    creditsEntryPoint = creditsEntryPoint,
)

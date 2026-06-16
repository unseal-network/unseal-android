/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SuspendedToolRenderModelTest {
    @Test
    fun `choose request reads suspend payload choices`() {
        val model = """
            {
              "toolName": "chooseRequest",
              "state": "suspended",
              "suspendPayload": {
                "title": "Pick files",
                "isMultiple": true,
                "isTypeable": true,
                "list": [
                  { "id": "a", "label": "Alpha", "description": "First option" },
                  { "id": "b", "label": "Beta" }
                ]
              }
            }
        """.trimIndent().toSuspendedToolRenderModel()

        assertThat(model.kind).isEqualTo(SuspendedToolKind.ChooseRequest)
        assertThat(model.title).isEqualTo("Pick files")
        assertThat(model.subtitle).isEqualTo("Multiple choice · Custom input allowed")
        assertThat(model.choices.map { it.label }).containsExactly("Alpha", "Beta").inOrder()
        assertThat(model.choices.first().description).isEqualTo("First option")
        assertThat(model.canRespond).isFalse()
    }

    @Test
    fun `delete schedule reads schedule details`() {
        val model = """
            {
              "toolName": "deleteSchedule",
              "suspendPayload": {
                "message": "Delete these schedules?",
                "scheduleNames": ["morning", "night"],
                "schedules": [
                  { "name": "morning", "cron": "0 9 * * *", "timezone": "Asia/Shanghai", "action": "Send report" }
                ]
              }
            }
        """.trimIndent().toSuspendedToolRenderModel()

        assertThat(model.kind).isEqualTo(SuspendedToolKind.DeleteSchedule)
        assertThat(model.title).isEqualTo("Delete Confirmation")
        assertThat(model.reason).isEqualTo("Delete these schedules?")
        assertThat(model.details.map { it.label }).containsAtLeast("Schedules", "morning")
        assertThat(model.details.last().value).contains("Asia/Shanghai")
    }

    @Test
    fun `action fallback reads sandbox and vault entry payloads`() {
        val sandbox = """
            {
              "suspendPayload": {
                "mode": "sandbox",
                "currentMode": "direct",
                "hasSandbox": true,
                "sandboxSource": { "userId": "@rayson:keepsecret.io", "snapshotId": "snap-1" }
              }
            }
        """.trimIndent().toSuspendedToolRenderModel()

        val vault = """
            {
              "suspendPayload": {
                "key": "GMAIL_TOKEN",
                "targetUserId": "@agent:keepsecret.io"
              }
            }
        """.trimIndent().toSuspendedToolRenderModel()

        assertThat(sandbox.kind).isEqualTo(SuspendedToolKind.SetSandboxMode)
        assertThat(sandbox.details.map { it.label }).containsAtLeast("Current", "Requested", "Snapshot")
        assertThat(vault.kind).isEqualTo(SuspendedToolKind.DeleteAgentVaultEntry)
        assertThat(vault.details.map { it.value }).contains("GMAIL_TOKEN")
    }
}

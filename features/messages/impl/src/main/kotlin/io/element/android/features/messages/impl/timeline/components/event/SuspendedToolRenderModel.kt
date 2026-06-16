/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import org.json.JSONArray
import org.json.JSONObject

internal data class SuspendedToolRenderModel(
    val kind: SuspendedToolKind,
    val title: String,
    val subtitle: String?,
    val reason: String?,
    val details: List<SuspendedToolDetail>,
    val choices: List<SuspendedToolChoice>,
    val canRespond: Boolean,
)

internal data class SuspendedToolDetail(
    val label: String,
    val value: String,
)

internal data class SuspendedToolChoice(
    val id: String,
    val label: String,
    val description: String?,
)

internal enum class SuspendedToolKind {
    VaultAuthorization,
    ChooseRequest,
    DeleteSchedule,
    SetSandboxMode,
    DeleteAgentVaultEntry,
    MoltbookRegister,
    Unknown,
}

internal fun String.toSuspendedToolRenderModel(): SuspendedToolRenderModel {
    val root = jsonObjectOrNull() ?: JSONObject()
    val suspendPayload = root.optJSONObject("suspendPayload") ?: root
    val action = suspendPayload.firstNonBlank("action")
        ?: root.firstNonBlank("toolName")
        ?: when {
            suspendPayload.has("mode") -> "setSandboxMode"
            suspendPayload.has("key") -> "deleteAgentVaultEntry"
            else -> null
        }
    val kind = when (action) {
        "requestVaultAuthorization" -> SuspendedToolKind.VaultAuthorization
        "chooseRequest" -> SuspendedToolKind.ChooseRequest
        "deleteSchedule" -> SuspendedToolKind.DeleteSchedule
        "setSandboxMode" -> SuspendedToolKind.SetSandboxMode
        "deleteAgentVaultEntry" -> SuspendedToolKind.DeleteAgentVaultEntry
        "moltbookRegister" -> SuspendedToolKind.MoltbookRegister
        else -> SuspendedToolKind.Unknown
    }
    return when (kind) {
        SuspendedToolKind.VaultAuthorization -> vaultAuthorizationModel(suspendPayload)
        SuspendedToolKind.ChooseRequest -> chooseRequestModel(suspendPayload)
        SuspendedToolKind.DeleteSchedule -> deleteScheduleModel(suspendPayload)
        SuspendedToolKind.SetSandboxMode -> sandboxModeModel(suspendPayload)
        SuspendedToolKind.DeleteAgentVaultEntry -> deleteAgentVaultEntryModel(suspendPayload)
        SuspendedToolKind.MoltbookRegister -> moltbookRegisterModel(suspendPayload)
        SuspendedToolKind.Unknown -> unknownSuspendedModel(root, suspendPayload)
    }
}

private fun vaultAuthorizationModel(payload: JSONObject): SuspendedToolRenderModel {
    return SuspendedToolRenderModel(
        kind = SuspendedToolKind.VaultAuthorization,
        title = "Authorize Vault Access",
        subtitle = "Only you can see it",
        reason = payload.firstNonBlank("reason"),
        details = listOfNotNull(
            payload.firstNonBlank("targetUserId")?.let { SuspendedToolDetail("Target user", it) },
        ),
        choices = emptyList(),
        canRespond = false,
    )
}

private fun chooseRequestModel(payload: JSONObject): SuspendedToolRenderModel {
    val multiple = payload.optBooleanOrNull("isMultiple")
    val typeable = payload.optBooleanOrNull("isTypeable")
    return SuspendedToolRenderModel(
        kind = SuspendedToolKind.ChooseRequest,
        title = payload.firstNonBlank("title") ?: "Choose an option",
        subtitle = listOfNotNull(
            multiple?.let { if (it) "Multiple choice" else "Single choice" },
            typeable?.takeIf { it }?.let { "Custom input allowed" },
        ).joinToString(" · ").takeIf { it.isNotBlank() },
        reason = payload.firstNonBlank("reason"),
        details = emptyList(),
        choices = payload.optJSONArray("list").choiceList(),
        canRespond = false,
    )
}

private fun deleteScheduleModel(payload: JSONObject): SuspendedToolRenderModel {
    val names = payload.optJSONArray("scheduleNames").stringList()
    val schedules = payload.optJSONArray("schedules").objectList()
    val details = buildList {
        if (names.isNotEmpty()) {
            add(SuspendedToolDetail(if (names.size > 1) "Schedules" else "Schedule", names.joinToString(", ")))
        }
        schedules.take(4).forEach { schedule ->
            val title = schedule.firstNonBlank("name") ?: "Schedule"
            val value = listOfNotNull(
                schedule.firstNonBlank("cron"),
                schedule.firstNonBlank("timezone"),
                schedule.firstNonBlank("action"),
            ).joinToString(" · ")
            add(SuspendedToolDetail(title, value.ifBlank { "Pending delete" }))
        }
    }
    return SuspendedToolRenderModel(
        kind = SuspendedToolKind.DeleteSchedule,
        title = "Delete Confirmation",
        subtitle = "Confirm before permanent deletion",
        reason = payload.firstNonBlank("message"),
        details = details,
        choices = emptyList(),
        canRespond = false,
    )
}

private fun sandboxModeModel(payload: JSONObject): SuspendedToolRenderModel {
    val source = payload.optJSONObject("sandboxSource")
    return SuspendedToolRenderModel(
        kind = SuspendedToolKind.SetSandboxMode,
        title = "Sandbox mode",
        subtitle = "Confirm agent sandbox change",
        reason = null,
        details = listOfNotNull(
            payload.firstNonBlank("currentMode")?.let { SuspendedToolDetail("Current", it) },
            payload.firstNonBlank("mode")?.let { SuspendedToolDetail("Requested", it) },
            payload.optBooleanOrNull("hasSandbox")?.let { SuspendedToolDetail("Existing sandbox", if (it) "Yes" else "No") },
            payload.firstNonBlank("sandboxCreatedAt")?.let { SuspendedToolDetail("Created", it) },
            source?.firstNonBlank("userId")?.let { SuspendedToolDetail("Source user", it) },
            source?.firstNonBlank("snapshotId")?.let { SuspendedToolDetail("Snapshot", it) },
        ),
        choices = emptyList(),
        canRespond = false,
    )
}

private fun deleteAgentVaultEntryModel(payload: JSONObject): SuspendedToolRenderModel {
    return SuspendedToolRenderModel(
        kind = SuspendedToolKind.DeleteAgentVaultEntry,
        title = "Delete vault entry",
        subtitle = "Confirm before removing this key",
        reason = null,
        details = listOfNotNull(
            payload.firstNonBlank("key")?.let { SuspendedToolDetail("Key", it) },
            payload.firstNonBlank("targetUserId")?.let { SuspendedToolDetail("Target user", it) },
        ),
        choices = emptyList(),
        canRespond = false,
    )
}

private fun moltbookRegisterModel(payload: JSONObject): SuspendedToolRenderModel {
    return SuspendedToolRenderModel(
        kind = SuspendedToolKind.MoltbookRegister,
        title = "Moltbook registration",
        subtitle = "Register before the agent continues",
        reason = payload.firstNonBlank("description"),
        details = listOfNotNull(
            payload.firstNonBlank("suggestedName")?.let { SuspendedToolDetail("Suggested name", it) },
            payload.firstNonBlank("registerUrl")?.let { SuspendedToolDetail("Register URL", it) },
        ),
        choices = emptyList(),
        canRespond = false,
    )
}

private fun unknownSuspendedModel(root: JSONObject, payload: JSONObject): SuspendedToolRenderModel {
    return SuspendedToolRenderModel(
        kind = SuspendedToolKind.Unknown,
        title = root.firstNonBlank("toolName") ?: payload.firstNonBlank("action") ?: "Action required",
        subtitle = root.firstNonBlank("state") ?: "Suspended",
        reason = payload.firstNonBlank("reason", "message", "description"),
        details = listOfNotNull(
            payload.firstNonBlank("targetUserId")?.let { SuspendedToolDetail("Target user", it) },
            payload.firstNonBlank("resumeToken")?.let { SuspendedToolDetail("Resume token", it) },
        ),
        choices = payload.optJSONArray("list").choiceList(),
        canRespond = false,
    )
}

private fun JSONObject.firstNonBlank(vararg keys: String): String? {
    keys.forEach { key ->
        when (val value = opt(key)) {
            is String -> value.takeIf { it.isNotBlank() }?.let { return it }
            is Number, is Boolean -> return value.toString()
        }
    }
    return null
}

private fun JSONObject.optBooleanOrNull(key: String): Boolean? {
    return when (val value = opt(key)) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> when (value.lowercase()) {
            "true", "1", "yes", "on" -> true
            "false", "0", "no", "off" -> false
            else -> null
        }
        else -> null
    }
}

private fun JSONArray?.choiceList(): List<SuspendedToolChoice> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index ->
        val item = optJSONObject(index) ?: return@mapNotNull null
        val id = item.firstNonBlank("id").orEmpty()
        val label = item.firstNonBlank("label", "title", "name").orEmpty()
        if (id.isBlank() && label.isBlank()) return@mapNotNull null
        SuspendedToolChoice(
            id = id.ifBlank { label },
            label = label.ifBlank { id },
            description = item.firstNonBlank("description"),
        )
    }
}

private fun JSONArray?.stringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index -> optString(index).takeIf { it.isNotBlank() } }
}

private fun JSONArray?.objectList(): List<JSONObject> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index -> optJSONObject(index) }
}

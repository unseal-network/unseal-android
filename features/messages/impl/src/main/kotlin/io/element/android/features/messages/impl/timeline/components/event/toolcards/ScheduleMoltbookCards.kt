/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event.toolcards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import org.json.JSONObject

// MARK: - Accents

// Schedule accent — indigo, distinct from the green/red tool-status colors (mirror iOS scheduleAccent).
private val ScheduleAccent = Color(0xFF5C6BC0)

// Moltbook accent — lobster orange (mirror iOS moltbookOrange Color(0xF1, 0x65, 0x2F)).
private val MoltbookOrange = Color(red = 0xF1 / 255f, green = 0x65 / 255f, blue = 0x2F / 255f)

/**
 * Native Material 3 renderers for the Schedule and Moltbook tool cards, ported field-for-field
 * from the iOS "ToolCardsIOS-Schedule" and "ToolCardsIOS-Moltbook" SwiftUI cards. Each card mirrors
 * the iOS field set and layout, expressed with the shared [ToolCardKit] helpers. The Moltbook card
 * is rendered read-only here (no interactive register submit).
 *
 * Wiring into ToolCardDispatcher is done separately by the caller.
 */
@Composable
internal fun scheduleMoltbookCard(
    cardType: String,
    data: JSONObject,
    onLinkClick: () -> Unit,
): Boolean {
    return when (cardType) {
        "createSchedule" -> { CreateScheduleCardView(data); true }
        "updateSchedule" -> { UpdateScheduleCardView(data); true }
        "updateScheduleStatus" -> { UpdateScheduleStatusCardView(data); true }
        "moltbookRegister" -> { MoltbookRegisterCardView(data); true }
        else -> false
    }
}

// MARK: - createSchedule

@Composable
private fun CreateScheduleCardView(data: JSONObject) {
    val name = data.cardString("name") ?: "Unnamed schedule"
    val cadence = data.cardString("cadence") ?: ""
    val timezone = data.cardString("timezone") ?: ""
    val cadenceProvided = data.cardBool("cadenceProvided") ?: true
    val action = data.cardString("action")

    ToolCardSurface {
        ToolCardHeader(title = "Create Schedule")
        ScheduleName(name)
        ScheduleCadenceView(cadence = cadence, timezone = timezone, muted = !cadenceProvided)
        if (!action.isNullOrBlank()) {
            ScheduleDetailRow(label = "Action", value = action)
        }
    }
}

// MARK: - updateSchedule

@Composable
private fun UpdateScheduleCardView(data: JSONObject) {
    val name = data.cardString("name") ?: "Unnamed schedule"
    val changed = data.cardBool("cadenceChanged") ?: true
    val cadence = if (changed) (data.cardString("cadence") ?: "") else "Schedule time unchanged"
    val timezone = data.cardString("timezone") ?: ""
    val action = data.cardString("action")

    ToolCardSurface {
        ToolCardHeader(title = "Update Schedule")
        ScheduleName(name)
        ScheduleCadenceView(cadence = cadence, timezone = timezone, muted = !changed)
        if (!action.isNullOrBlank()) {
            ScheduleDetailRow(label = "New action", value = action)
        }
    }
}

// MARK: - updateScheduleStatus

@Composable
private fun UpdateScheduleStatusCardView(data: JSONObject) {
    val isEnable = data.cardBool("isEnable") ?: true
    val names = data.cardStrings("names")

    ToolCardSurface {
        ToolCardHeader(title = "Schedule Status")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                imageVector = if (isEnable) Icons.Filled.PlayCircle else Icons.Filled.PauseCircle,
                contentDescription = null,
                tint = ScheduleAccent,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = if (isEnable) "Enable" else "Disable",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (names.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                names.take(MAX_CARD_ITEMS).forEach { name -> ScheduleNameChip(name) }
            }
        }
    }
}

// MARK: - Schedule shared pieces (mirror iOS ScheduleName / ScheduleCadenceView / ScheduleDetailRow)

@Composable
private fun ScheduleName(name: String) {
    Text(
        text = name,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The hero "when it runs" block — readable cadence + timezone on an indigo-tinted surface. */
@Composable
private fun ScheduleCadenceView(cadence: String, timezone: String, muted: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ScheduleAccent.copy(alpha = if (muted) 0.04f else 0.08f), RoundedCornerShape(11.dp))
            .border(0.5.dp, ScheduleAccent.copy(alpha = if (muted) 0.08f else 0.18f), RoundedCornerShape(11.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.AccessTime,
            contentDescription = null,
            tint = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else ScheduleAccent,
            modifier = Modifier.size(16.dp),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = cadence,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            if (timezone.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Public,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(11.dp),
                    )
                    Text(
                        text = timezone,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Small-caps label over a value — for longer free text like the action prompt. */
@Composable
private fun ScheduleDetailRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ScheduleNameChip(name: String) {
    Text(
        text = name,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        color = ScheduleAccent,
        modifier = Modifier
            .background(ScheduleAccent.copy(alpha = 0.10f), RoundedCornerShape(50))
            .border(0.5.dp, ScheduleAccent.copy(alpha = 0.20f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

// MARK: - moltbookRegister (read-only render of title/reason/fields)

@Composable
private fun MoltbookRegisterCardView(data: JSONObject) {
    val name = data.cardString("name", "registeredName")
    val description = data.cardString("description", "reason")
    val state = data.cardString("state")?.lowercase()
    val errorMessage = data.cardString("errorMessage", "error")
    val registeredName = data.cardString("registeredName") ?: name

    ToolCardSurface {
        // Header: lobster badge + title + subtitle (mirror iOS header).
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MoltbookOrange, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "🦞", style = MaterialTheme.typography.titleLarge)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Register on Moltbook",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "This will register the agent from your device",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Name field (read-only): small-caps label over the chosen name.
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "MOLTBOOK NAME",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = name ?: "Choose a name",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = if (name.isNullOrBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Status banner (read-only): error / done states (mirror iOS statusBanner).
        when (state) {
            "error" -> StatusBanner(
                icon = { tint -> Icon(Icons.Filled.PauseCircle, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp)) },
                text = errorMessage ?: "Registration failed. Please try again.",
                color = MaterialTheme.colorScheme.error,
            )
            "done" -> StatusBanner(
                icon = { tint -> Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp)) },
                text = "Registered as ${registeredName ?: name.orEmpty()}",
                color = Color(0xFF1A7F37),
            )
            else -> Unit
        }
    }
}

@Composable
private fun StatusBanner(icon: @Composable (Color) -> Unit, text: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        icon(color)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
        )
    }
}

// MARK: - Previews

@PreviewsDayNight
@Composable
internal fun CreateScheduleCardPreview() = ElementPreview {
    CreateScheduleCardView(
        data = JSONObject(
            """
            {
              "name": "one-time-reminder-0429",
              "cadence": "Every day at 04:29",
              "cadenceProvided": true,
              "timezone": "America/New_York",
              "action": "Run the backup script and email the summary to the team."
            }
            """.trimIndent()
        ),
    )
}

@PreviewsDayNight
@Composable
internal fun UpdateScheduleStatusCardPreview() = ElementPreview {
    UpdateScheduleStatusCardView(
        data = JSONObject(
            """
            {
              "isEnable": false,
              "names": ["Daily Backup", "Weekly Report", "Sync Inbox"]
            }
            """.trimIndent()
        ),
    )
}

@PreviewsDayNight
@Composable
internal fun MoltbookRegisterCardPreview() = ElementPreview {
    MoltbookRegisterCardView(
        data = JSONObject(
            """
            {
              "name": "lumina",
              "description": "A friendly shopping assistant agent.",
              "state": "error",
              "errorMessage": "Moltbook is rate-limiting new registrations (per IP). Try again in about 5 minutes."
            }
            """.trimIndent()
        ),
    )
}

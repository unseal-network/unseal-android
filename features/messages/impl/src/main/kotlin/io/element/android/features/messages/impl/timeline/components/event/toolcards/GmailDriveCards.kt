/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event.toolcards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import org.json.JSONObject

/**
 * Native Material 3 renderers for the Gmail and Google Drive tool cards, ported field-for-field
 * from the iOS `ToolCardsIOS/Gmail` and `ToolCardsIOS/GoogleDrive` SwiftUI cards. Each card
 * mirrors the iOS field set and layout, expressed with the shared [ToolCardKit] helpers. Lists are
 * capped at [MAX_CARD_ITEMS].
 *
 * Wiring into [ToolCard]/ToolCardDispatcher is done separately by the caller.
 */
@Composable
internal fun gmailDriveCard(
    cardType: String,
    data: JSONObject,
    onLinkClick: () -> Unit,
): Boolean {
    return when (cardType) {
        "composeEmail" -> { ComposeEmailCardView(data); true }
        "fileAttachment" -> { FileAttachmentCardView(data, onLinkClick); true }
        else -> false
    }
}

// MARK: - Shared helpers

/** Opens [url] via the platform handler and notifies the caller; no-op when [url] is blank/null. */
@Composable
private fun rememberLinkOpener(onLinkClick: () -> Unit): (String?) -> Unit {
    val uriHandler = LocalUriHandler.current
    return { url ->
        if (!url.isNullOrBlank()) {
            runCatching { uriHandler.openUri(url) }
            onLinkClick()
        }
    }
}

@Composable
private fun MetaText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

// MARK: - composeEmail (Gmail)

/** Joins an address object into "Name <email>" (mirror iOS AddressRow.formatAddr). */
private fun formatAddr(addr: JSONObject): String {
    val name = addr.cardString("name")
    val email = addr.cardString("email") ?: ""
    return if (!name.isNullOrBlank()) "$name <$email>" else email
}

private fun formatAddrValue(value: Any?): String? {
    return when (value) {
        is JSONObject -> formatAddr(value).takeIf { it.isNotBlank() }
        is String -> value.takeIf { it.isNotBlank() }
        else -> null
    }
}

@Composable
private fun AddressRow(label: String, addresses: List<JSONObject>) {
    val joined = addresses.joinToString(", ") { formatAddr(it) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(32.dp),
        )
        Text(
            text = joined,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ComposeEmailCardView(data: JSONObject) {
    val messages = data.cardObjects("messages", "items")
    if (messages.isNotEmpty()) {
        EmailListCardView(messages)
        return
    }

    val from = data.opt("from") as? JSONObject
    val to = data.cardObjects("to")
    val cc = data.cardObjects("cc")
    val subject = data.cardString("subject") ?: "(No Subject)"
    val date = data.cardString("date")
    val body = data.cardString("body")
    val hasAttachments = data.cardBool("hasAttachments") ?: false
    val attachmentCount = data.cardInt("attachmentCount") ?: 1
    val starred = data.cardBool("starred") ?: false
    val labels = data.cardStrings("labels")

    val isEmpty = subject == "(No Subject)" &&
        to.isEmpty() &&
        cc.isEmpty() &&
        from == null &&
        body.isNullOrBlank() &&
        date.isNullOrBlank() &&
        labels.isEmpty() &&
        !hasAttachments
    if (isEmpty) return

    ToolCardSurface {
        // Header: title "Email" + trailing star/labels.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Email",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (starred) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = null,
                    tint = Color(0xFFFFC107),
                    modifier = Modifier.size(12.dp),
                )
            }
            if (labels.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    labels.forEach { CardChip(text = it) }
                }
            }
        }

        // Subject
        Text(
            text = subject,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        // Address rows
        if (from != null) AddressRow(label = "From", addresses = listOf(from))
        if (to.isNotEmpty()) AddressRow(label = "To", addresses = to)
        if (cc.isNotEmpty()) AddressRow(label = "Cc", addresses = cc)
        if (date != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Date",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(32.dp),
                )
                MetaText(date)
            }
        }

        // Body
        if (!body.isNullOrBlank()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Attachments
        if (hasAttachments) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                    Icons.Filled.AttachFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp),
                )
                MetaText("$attachmentCount attachment${if (attachmentCount == 1) "" else "s"}")
            }
        }
    }
}

@Composable
private fun EmailListCardView(messages: List<JSONObject>) {
    val shown = messages.take(MAX_CARD_ITEMS)
    val open = rememberLinkOpener {}

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        DividedList(shown) { message -> EmailListRow(message, open) }
        if (messages.size > shown.size) {
            MetaText("+${messages.size - shown.size} more")
        }
    }
}

@Composable
private fun EmailListRow(message: JSONObject, open: (String?) -> Unit) {
    val subject = message.cardString("subject", "title") ?: "(No Subject)"
    val from = formatAddrValue(message.opt("from")) ?: message.cardString("sender", "fromEmail", "email")
    val snippet = message.cardString("snippet", "body", "summary", "text")
    val date = message.cardString("date", "receivedAt", "received_at", "internalDate")
    val url = message.cardString("url", "link", "web_url", "webUrl")
    val labels = message.cardStrings("labels")
    val starred = message.cardBool("starred") ?: false
    val hasAttachments = message.cardBool("hasAttachments") ?: false

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (!url.isNullOrBlank()) Modifier.clickable { open(url) } else Modifier)
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 3.dp)
                .size(8.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = subject,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (starred) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFC107),
                        modifier = Modifier.size(12.dp),
                    )
                }
                if (hasAttachments) {
                    Icon(
                        Icons.Filled.AttachFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
            if (!from.isNullOrBlank() || !date.isNullOrBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!from.isNullOrBlank()) {
                        Text(
                            text = from,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    if (!date.isNullOrBlank()) {
                        MetaText(date)
                    }
                }
            }
            if (!snippet.isNullOrBlank()) {
                Text(
                    text = snippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (labels.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    labels.take(4).forEach { CardChip(text = it) }
                }
            }
        }
        if (!url.isNullOrBlank()) {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(16.dp),
            )
        }
    }
}

// MARK: - fileAttachment (Google Drive)

@Composable
private fun FileAttachmentCardView(data: JSONObject, onLinkClick: () -> Unit) {
    val files = data.cardObjects("files", "items")
    if (files.isEmpty()) return
    val title = data.cardString("title")
    val shown = files.take(MAX_CARD_ITEMS)
    val open = rememberLinkOpener(onLinkClick)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        if (!title.isNullOrBlank()) {
            ToolCardHeader(title = title, count = files.size)
        }
        DividedList(shown) { file -> FileRow(file, open) }
        if (files.size > shown.size) {
            Text(
                text = "+${files.size - shown.size} more",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun FileRow(file: JSONObject, open: (String?) -> Unit) {
    val name = file.cardString("name", "filename", "title") ?: "Untitled"
    val size = file.cardString("sizeLabel", "size")
    val mimeType = file.cardString("mimeType", "mediaType")
    val modifiedAt = file.cardString("modifiedAt", "modified_at", "modifiedTime", "modified_time")
    val owner = file.cardString("owner")
    val shared = file.cardBool("shared") ?: false
    val url = file.cardString("url", "webViewLink", "web_view_link", "alternateLink")
    val icon = resolveFileTypeIcon(name = name, mimeType = mimeType, explicitIcon = file.cardString("icon"))
    val details = listOfNotNull(size, modifiedAt, owner).filter { it.isNotBlank() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (url != null) Modifier.clickable { open(url) } else Modifier)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(icon.color),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = icon.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (details.isNotEmpty()) {
                MetaText(details.joinToString(" · "))
            }
        }

        if (shared) {
            Icon(
                Icons.Filled.People,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(14.dp),
            )
        }
        if (url != null) {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/** File-type badge: label + color (mirror iOS FileTypeIcon). */
private data class FileTypeIcon(val label: String, val color: Color)

private fun resolveFileTypeIcon(name: String, mimeType: String? = null, explicitIcon: String? = null): FileTypeIcon {
    when (explicitIcon?.lowercase()) {
        "image" -> return FileTypeIcon("IMG", Color(0xFF8E24AA))
        "pdf" -> return FileTypeIcon("PDF", Color(0xFFE53935))
        "spreadsheet" -> return FileTypeIcon("XLS", Color(0xFF43A047))
        "presentation" -> return FileTypeIcon("PPT", Color(0xFFFB8C00))
        "document" -> return FileTypeIcon("DOC", Color(0xFF1E88E5))
    }
    val lowerMime = mimeType.orEmpty().lowercase()
    when {
        lowerMime.startsWith("image/") -> return FileTypeIcon("IMG", Color(0xFF8E24AA))
        lowerMime.contains("pdf") -> return FileTypeIcon("PDF", Color(0xFFE53935))
        lowerMime.contains("spreadsheet") -> return FileTypeIcon("XLS", Color(0xFF43A047))
        lowerMime.contains("presentation") -> return FileTypeIcon("PPT", Color(0xFFFB8C00))
        lowerMime.contains("document") -> return FileTypeIcon("DOC", Color(0xFF1E88E5))
    }
    val ext = name.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "pdf" -> FileTypeIcon("PDF", Color(0xFFE53935))
        "doc", "docx" -> FileTypeIcon("DOC", Color(0xFF1E88E5))
        "xls", "xlsx" -> FileTypeIcon("XLS", Color(0xFF43A047))
        "ppt", "pptx" -> FileTypeIcon("PPT", Color(0xFFFB8C00))
        "png", "jpg", "jpeg" -> FileTypeIcon("IMG", Color(0xFF8E24AA))
        "mp4" -> FileTypeIcon("VID", Color(0xFFEC407A))
        "zip" -> FileTypeIcon("ZIP", Color(0xFFFDD835))
        else -> FileTypeIcon("FILE", Color(0xFF9E9E9E))
    }
}

// MARK: - Previews

@PreviewsDayNight
@Composable
internal fun ComposeEmailCardPreview() = ElementPreview {
    ComposeEmailCardView(
        data = JSONObject(
            """
            {
              "from": { "name": "Jelf Liang", "email": "jelf@unseal.ai" },
              "to": [
                { "name": "Alice Chen", "email": "alice@example.com" },
                { "email": "bob@example.com" }
              ],
              "cc": [ { "name": "Team", "email": "team@unseal.ai" } ],
              "subject": "Q3 planning meeting notes",
              "date": "May 29, 2026",
              "body": "Hi team,\n\nAttached are the notes from today's Q3 planning session. Key takeaways:\n- Agent voice latency target: P99 < 800ms\n- E2EE rollout to all rooms by end of July\n\nBest,\nJelf",
              "hasAttachments": true,
              "attachmentCount": 2,
              "starred": true,
              "labels": ["Work", "Planning"]
            }
            """.trimIndent()
        ),
    )
}

@PreviewsDayNight
@Composable
internal fun FileAttachmentCardPreview() = ElementPreview {
    FileAttachmentCardView(
        data = JSONObject(
            """
            {
              "files": [
                { "name": "Q4 Revenue Report.pdf", "size": "2.4 MB", "modifiedAt": "Dec 15, 2025", "owner": "Alice Chen", "shared": true, "url": "https://drive.google.com/file/d/1" },
                { "name": "Product Roadmap.pptx", "size": "8.1 MB", "modifiedAt": "Jan 3, 2026", "owner": "Bob Lee", "shared": true, "url": "https://drive.google.com/file/d/2" },
                { "name": "Team Photo.jpg", "size": "1.7 MB", "modifiedAt": "Nov 20, 2025", "owner": "Carol Wu" },
                { "name": "Budget 2026.xlsx", "size": "340 KB", "modifiedAt": "Feb 10, 2026", "shared": false }
              ]
            }
            """.trimIndent()
        ),
        onLinkClick = {},
    )
}

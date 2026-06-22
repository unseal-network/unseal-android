/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

private val HtmlTableCellWidth = 136.dp

@Composable
internal fun HtmlTableBody(
    tables: List<HtmlTable>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.horizontalScroll(rememberScrollState())
    ) {
        tables.forEach { table ->
            HtmlTableView(table = table)
        }
    }
}

@Composable
private fun HtmlTableView(table: HtmlTable) {
    Column {
        table.rows.forEach { row ->
            Row {
                row.cells.forEach { cell ->
                    Text(
                        text = cell.text,
                        modifier = Modifier
                            .width(HtmlTableCellWidth)
                            .border(0.5.dp, ElementTheme.colors.separatorPrimary)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        style = LocalTextStyle.current.copy(
                            fontWeight = if (cell.isHeader) FontWeight.SemiBold else FontWeight.Normal
                        ),
                    )
                }
            }
        }
    }
}

internal data class HtmlTable(
    val rows: List<HtmlTableRow>,
)

internal data class HtmlTableRow(
    val cells: List<HtmlTableCell>,
)

internal data class HtmlTableCell(
    val text: String,
    val isHeader: Boolean,
)

internal fun Document.extractHtmlTables(): List<HtmlTable> {
    return select("table").mapNotNull { table ->
        table.extractHtmlTable()
    }
}

private fun Element.extractHtmlTable(): HtmlTable? {
    val rows = select("tr").mapNotNull { row ->
        val cells = row.children()
            .filter { it.normalName() == "th" || it.normalName() == "td" }
            .map { cell ->
                HtmlTableCell(
                    text = cell.text().trim(),
                    isHeader = cell.normalName() == "th",
                )
            }
        HtmlTableRow(cells = cells).takeIf { it.cells.isNotEmpty() }
    }
    return HtmlTable(rows = rows).takeIf { it.rows.isNotEmpty() }
}

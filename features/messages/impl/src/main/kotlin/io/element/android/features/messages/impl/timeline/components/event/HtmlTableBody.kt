/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

// Tables with at most this many columns are laid out to fit the available width (cells wrap their
// text). Wider tables fall back to a horizontally scrollable grid, since fitting 4+ columns into the
// bubble width forces cells to wrap mid-word and read as a cramped block. Mirrors the iOS approach of
// horizontally scrolling wide tables rather than squashing them.
private const val MaxFitColumns = 3

// Columns whose longest cell is at most this many characters size to content (one line); longer
// columns flex and wrap.
private const val SHORT_COLUMN_MAX_CHARS = 12

// In the scrollable fallback, short columns size to their content (one line) and long columns are
// capped at this width and wrap, so nothing is squashed mid-token.
private val ScrollColumnMaxWidth = 220.dp

// A timeline item is not lazy, so every table row composes/measures up-front when the item scrolls
// into view. Cap the rows actually rendered so a huge table (e.g. a long meeting transcript) can't
// blow the frame budget; the remainder is summarised in a footer.
private const val MaxRenderedTableRows = 40

private val TableShape = RoundedCornerShape(12.dp)
private val GridLineThickness = 0.5.dp

@Composable
internal fun HtmlTableBody(
    tables: List<HtmlTable>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        tables.forEach { table ->
            HtmlTableView(table = table)
        }
    }
}

@Composable
private fun HtmlTableView(table: HtmlTable) {
    val columnCount = table.rows.maxOfOrNull { it.cells.size }?.coerceAtLeast(1) ?: return
    val rows = table.rows.take(MaxRenderedTableRows)
    val hiddenRows = table.rows.size - rows.size
    val gridColor = ElementTheme.colors.separatorPrimary
    val containerModifier = Modifier
        .clip(TableShape)
        .border(GridLineThickness, gridColor, TableShape)
    val longestPerColumn = (0 until columnCount).map { column ->
        rows.maxOf { row -> row.cells.getOrNull(column)?.text?.length ?: 0 }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (columnCount <= MaxFitColumns) {
            // Short columns (timestamps, short labels) size to their content and stay on one line; only
            // long columns flex to share the remaining width and wrap. This mirrors the iOS table look
            // and avoids a single long column (e.g. a URL) forcing short columns to wrap awkwardly.
            val flexColumns = longestPerColumn.withIndex()
                .filter { it.value > SHORT_COLUMN_MAX_CHARS }
                .map { it.index }
                .toSet()
            // If every column is short, let them all flex equally so the table still fills the width.
            val noFlexColumns = flexColumns.isEmpty()
            Column(modifier = containerModifier.fillMaxWidth()) {
                rows.forEachIndexed { index, row ->
                    if (index > 0) HorizontalDivider(thickness = GridLineThickness, color = gridColor)
                    TableRow(row = row, columnCount = columnCount, rowIndex = index) { column, cellModifier ->
                        if (noFlexColumns || column in flexColumns) cellModifier.weight(1f) else cellModifier
                    }
                }
            }
        } else {
            // Short columns size to content and stay on one line; long columns are capped and wrap. The
            // whole table scrolls horizontally so nothing is squashed mid-token.
            Column(modifier = containerModifier.horizontalScroll(rememberScrollState())) {
                rows.forEachIndexed { index, row ->
                    if (index > 0) HorizontalDivider(thickness = GridLineThickness, color = gridColor)
                    TableRow(row = row, columnCount = columnCount, rowIndex = index) { column, cellModifier ->
                        if (longestPerColumn[column] > SHORT_COLUMN_MAX_CHARS) {
                            cellModifier.widthIn(max = ScrollColumnMaxWidth)
                        } else {
                            cellModifier
                        }
                    }
                }
            }
        }
        if (hiddenRows > 0) {
            Text(
                text = "＋$hiddenRows",
                modifier = Modifier.padding(start = 4.dp),
                color = ElementTheme.colors.textSecondary,
                style = ElementTheme.typography.fontBodySmRegular,
            )
        }
    }
}

@Composable
private fun TableRow(
    row: HtmlTableRow,
    columnCount: Int,
    rowIndex: Int,
    columnSizing: androidx.compose.foundation.layout.RowScope.(column: Int, Modifier) -> Modifier,
) {
    val isHeaderRow = row.cells.any { it.isHeader }
    // iOS parity: a header band plus subtle alternating-row striping on the data rows.
    // Deliberately no IntrinsicSize.Min / full-height vertical dividers here — those force an extra
    // measurement pass per row and make scrolling janky for large tables. Row separation comes from
    // striping + horizontal dividers + the outer border instead.
    val rowBackground = when {
        isHeaderRow -> ElementTheme.colors.bgSubtleSecondary
        (rowIndex - 1) % 2 == 0 -> ElementTheme.colors.bgSubtleSecondary.copy(alpha = 0.3f)
        else -> Color.Transparent
    }
    Row(modifier = Modifier.background(rowBackground)) {
        for (column in 0 until columnCount) {
            val cell = row.cells.getOrNull(column)
            Text(
                text = cell?.text.orEmpty(),
                modifier = columnSizing(column, Modifier)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                color = ElementTheme.colors.textPrimary,
                style = LocalTextStyle.current.copy(
                    fontWeight = if (cell?.isHeader == true) FontWeight.SemiBold else FontWeight.Normal,
                ),
            )
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

@PreviewsDayNight
@Composable
internal fun HtmlTableBodyPreview() = ElementPreview {
    HtmlTableBody(
        modifier = Modifier.padding(12.dp),
        tables = listOf(
            HtmlTable(
                rows = listOf(
                    HtmlTableRow(
                        listOf(
                            HtmlTableCell("Time", isHeader = true),
                            HtmlTableCell("Speaker", isHeader = true),
                            HtmlTableCell("Content", isHeader = true),
                        )
                    ),
                    HtmlTableRow(
                        listOf(
                            HtmlTableCell("15:21:26", isHeader = false),
                            HtmlTableCell("@rayson:topsecret.network", isHeader = false),
                            HtmlTableCell("我们再瞧一下，因为我们现在的逻辑是这样的。", isHeader = false),
                        )
                    ),
                    HtmlTableRow(
                        listOf(
                            HtmlTableCell("15:21:27", isHeader = false),
                            HtmlTableCell("@rayson:topsecret.network", isHeader = false),
                            HtmlTableCell("嗯。", isHeader = false),
                        )
                    ),
                )
            )
        ),
    )
}

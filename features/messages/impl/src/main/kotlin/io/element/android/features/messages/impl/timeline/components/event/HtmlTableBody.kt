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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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

private const val MIN_COLUMN_WEIGHT_CHARS = 7
private const val MAX_COLUMN_WEIGHT_CHARS = 28
private val ScrollColumnMinWidth = 84.dp
private val ScrollColumnMaxWidth = 236.dp

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
        .background(ElementTheme.colors.bgCanvasDefault.copy(alpha = 0.72f))
        .border(GridLineThickness, gridColor, TableShape)
    val longestPerColumn = (0 until columnCount).map { column ->
        rows.maxOf { row -> row.cells.getOrNull(column)?.text?.length ?: 0 }
    }
    val columnWeights = longestPerColumn.map { length ->
        length.coerceIn(MIN_COLUMN_WEIGHT_CHARS, MAX_COLUMN_WEIGHT_CHARS).toFloat()
    }
    val scrollColumnWidths = longestPerColumn.map { length ->
        estimatedScrollColumnWidth(length)
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (columnCount <= MaxFitColumns) {
            Column(modifier = containerModifier.fillMaxWidth()) {
                rows.forEachIndexed { index, row ->
                    if (index > 0) HorizontalDivider(thickness = GridLineThickness, color = gridColor)
                    TableRow(row = row, columnCount = columnCount, rowIndex = index, gridColor = gridColor) { column, cellModifier ->
                        cellModifier.weight(columnWeights[column], fill = true)
                    }
                }
            }
        } else {
            val tableWidth = scrollColumnWidths.fold(0.dp) { acc, width -> acc + width }
            Box(
                modifier = containerModifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                Column(modifier = Modifier.width(tableWidth)) {
                    rows.forEachIndexed { index, row ->
                        if (index > 0) HorizontalDivider(thickness = GridLineThickness, color = gridColor)
                        TableRow(row = row, columnCount = columnCount, rowIndex = index, gridColor = gridColor) { column, cellModifier ->
                            cellModifier.width(scrollColumnWidths[column])
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
    gridColor: Color,
    columnSizing: androidx.compose.foundation.layout.RowScope.(column: Int, Modifier) -> Modifier,
) {
    val isHeaderRow = row.cells.any { it.isHeader }
    val rowBackground = when {
        isHeaderRow -> ElementTheme.colors.bgSubtleSecondary
        (rowIndex - 1) % 2 == 0 -> ElementTheme.colors.bgSubtleSecondary.copy(alpha = 0.24f)
        else -> Color.Transparent
    }
    Row(
        modifier = Modifier
            .background(rowBackground)
            .height(IntrinsicSize.Min)
    ) {
        for (column in 0 until columnCount) {
            val cell = row.cells.getOrNull(column)
            Box(
                modifier = columnSizing(column, Modifier)
                    .fillMaxHeight()
                    .then(
                        if (column < columnCount - 1) {
                            Modifier.drawBehind {
                                drawLine(
                                    color = gridColor,
                                    start = Offset(size.width, 0f),
                                    end = Offset(size.width, size.height),
                                    strokeWidth = GridLineThickness.toPx(),
                                )
                            }
                        } else {
                            Modifier
                        }
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    text = cell?.text.orEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    color = ElementTheme.colors.textPrimary,
                    textAlign = cell?.alignment?.textAlign ?: TextAlign.Start,
                    style = ElementTheme.typography.fontBodySmRegular.copy(
                        fontWeight = if (cell?.isHeader == true) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                )
            }
        }
    }
}

private fun estimatedScrollColumnWidth(textLength: Int): Dp {
    val estimated = (textLength.coerceIn(MIN_COLUMN_WEIGHT_CHARS, MAX_COLUMN_WEIGHT_CHARS) * 8).dp + 28.dp
    return estimated.coerceIn(ScrollColumnMinWidth, ScrollColumnMaxWidth)
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
    val alignment: HtmlTableCellAlignment = HtmlTableCellAlignment.Start,
)

internal enum class HtmlTableCellAlignment(val textAlign: TextAlign) {
    Start(TextAlign.Start),
    Center(TextAlign.Center),
    End(TextAlign.End),
}

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

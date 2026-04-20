package com.tabletopcontrol.map.logic

import com.tabletopcontrol.core.TokenSize

internal data class TokenGridFootprint(
    val startCol: Int,
    val startRow: Int,
    val endCol: Int,
    val endRow: Int,
) {
    val widthCells: Int
        get() = endCol - startCol + 1

    val heightCells: Int
        get() = endRow - startRow + 1
}

internal data class TokenDrawBounds(
    val left: Double,
    val top: Double,
    val size: Double,
) {
    val right: Double
        get() = left + size

    val bottom: Double
        get() = top + size

    val centerX: Double
        get() = left + size / 2.0

    val centerY: Double
        get() = top + size / 2.0

    fun contains(x: Double, y: Double): Boolean = x in left..right && y in top..bottom
}

internal fun tokenGridFootprint(token: Token): TokenGridFootprint =
    tokenGridFootprint(token.col, token.row, token.size)

internal fun tokenGridFootprint(col: Int, row: Int, size: TokenSize): TokenGridFootprint {
    val span = size.gridSpanCells
    return TokenGridFootprint(
        startCol = col,
        startRow = row,
        endCol = col + span - 1,
        endRow = row + span - 1,
    )
}

internal fun tokenDrawBounds(
    token: Token,
    originX: Double,
    originY: Double,
    cellPx: Double,
): TokenDrawBounds {
    val anchorTiles = token.size.gridSpanCells.toDouble()
    val drawTiles = token.size.footprintTiles * TOKEN_DRAW_FOOTPRINT_SCALE
    val insetTiles = (anchorTiles - drawTiles) / 2.0
    val sizePx = cellPx * drawTiles
    return TokenDrawBounds(
        left = originX + (token.col + insetTiles) * cellPx,
        top = originY + (token.row + insetTiles) * cellPx,
        size = sizePx,
    )
}

internal fun tokenOccupiedCells(token: Token): List<Pair<Int, Int>> {
    val footprint = tokenGridFootprint(token)
    val cells = mutableListOf<Pair<Int, Int>>()
    for (col in footprint.startCol..footprint.endCol) {
        for (row in footprint.startRow..footprint.endRow) {
            cells += Pair(col, row)
        }
    }
    return cells
}

internal fun tokenFootprintsOverlap(first: Token, second: Token): Boolean =
    tokenFootprintsOverlap(first.col, first.row, first.size, second)

internal fun tokenFootprintsOverlap(
    col: Int,
    row: Int,
    size: TokenSize,
    other: Token,
): Boolean {
    val first = tokenGridFootprint(col, row, size)
    val second = tokenGridFootprint(other)
    return first.startCol <= second.endCol &&
        first.endCol >= second.startCol &&
        first.startRow <= second.endRow &&
        first.endRow >= second.startRow
}

internal fun nextAvailableTokenPlacement(
    tokens: Iterable<Token>,
    size: TokenSize,
    row: Int = 0,
): Pair<Int, Int> {
    var col = 0
    while (tokens.any { tokenFootprintsOverlap(col, row, size, it) }) {
        col++
    }
    return Pair(col, row)
}

internal fun tokenDragAnchor(
    token: Token,
    grabbedCell: Pair<Int, Int>,
): Pair<Int, Int> {
    val footprint = tokenGridFootprint(token)
    return Pair(
        (grabbedCell.first - footprint.startCol).coerceIn(0, footprint.widthCells - 1),
        (grabbedCell.second - footprint.startRow).coerceIn(0, footprint.heightCells - 1),
    )
}

internal fun draggedTokenOrigin(
    cell: Pair<Int, Int>,
    anchorOffset: Pair<Int, Int>,
): Pair<Int, Int> =
    Pair(
        cell.first - anchorOffset.first,
        cell.second - anchorOffset.second,
    )

private const val TOKEN_DRAW_FOOTPRINT_SCALE = 0.9

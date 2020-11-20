package com.squareup.sample.dungeon.board

import kotlinx.serialization.Serializable

/**
 * Information about the board, encoded as YAML at the start of a board file.
 *
 * The metadata must be surrounded by lines containing only "`---`".
 *
 * @see parseBoardMetadata
 */
@Serializable
data class BoardMetadata(val name: String)

/**
 * Describes the "physical" layout of a board.
 *
 * @see parseBoard
 */
data class Board(
  val metadata: BoardMetadata,
  val width: Int,
  val height: Int,
  val cells: List<BoardCell>
) {
  data class Location(
    val x: Int,
    val y: Int
  ) {
    override fun toString(): String = "($x, $y)"
  }

  init {
    require(cells.size == width * height) {
      "Cells must be $width×$height=${width * height}, but was ${cells.size}"
    }
  }

  operator fun get(
    x: Int,
    y: Int
  ): BoardCell = cells[cellIndexOf(x, y)]

  fun withOverlay(actors: Map<Location, BoardCell>): Board {
    val playersByIndex = actors.mapKeys { (location, _) -> cellIndexOf(location.x, location.y) }
    return copy(cells = cells.mapIndexed { index, cell ->
      playersByIndex[index] ?: cell
    })
  }

  override fun toString(): String {
    return cells.asSequence()
        .chunked(width)
        .joinToString(separator = "\n") {
          it.joinToString(separator = "")
        }
  }

  private fun cellIndexOf(
    x: Int,
    y: Int
  ): Int = (y * width) + x

  companion object {
    /**
     * Builds a board from a square list of [BoardCell] lists.
     */
    fun fromRows(
      metadata: BoardMetadata,
      rows: List<List<BoardCell>>
    ): Board {
      val width = rows.map { it.size }
          .distinct()
          .singleOrNull()
          ?: throw IllegalArgumentException("Expected all rows to be the same length.")
      val height = rows.size
      require(width == height) { "Expected board to be square, but was $width × $height" }
      val cells = rows.reduce { acc, row -> acc + row }
      return Board(metadata, width, height, cells)
    }
  }
}

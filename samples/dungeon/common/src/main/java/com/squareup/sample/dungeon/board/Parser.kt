package com.squareup.sample.dungeon.board

import com.squareup.sample.dungeon.board.BoardCell.Companion.EMPTY_FLOOR
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okio.BufferedSource

private const val YAML_DELIMITER = "---"

/**
 * Parses the [BoardMetadata] from this source.
 *
 * @throws IllegalArgumentException If no metadata is found.
 * @see parseBoardMetadataOrNull
 */
fun BufferedSource.parseBoardMetadata(): BoardMetadata =
  parseBoardMetadataOrNull() ?: throw IllegalArgumentException(
      "No board metadata found in stream, expected \"$YAML_DELIMITER\" but found \"${peekLine()}\""
  )

private val JSON = Json {
  isLenient = true
}

/**
 * Parses the [BoardMetadata] from this source.
 *
 * @return The [BoardMetadata], or null if the source does not start with "`---\n`".
 * @see parseBoardMetadata
 */
fun BufferedSource.parseBoardMetadataOrNull(): BoardMetadata? = readHeader()?.let { header ->
  try {
    JSON.decodeFromString(BoardMetadata.serializer(), header)
  } catch (e: SerializationException) {
    throw IllegalArgumentException("Error parsing board metadata.", e)
  }
}

/**
 * Parses a [Board] from this source.
 *
 * @param metadata The [BoardMetadata] that describes this board. If not explicitly passed, the
 * metadata will be read from the source.
 * @throws IllegalArgumentException If no metadata is passed and the stream does not start with
 * metadata.
 */
fun BufferedSource.parseBoard(metadata: BoardMetadata = parseBoardMetadata()): Board {
  var lines = generateSequence { readUtf8Line() }.toList()

  // Trim leading and trailing empty lines.
  lines = lines.dropWhile { it.isBlank() }
      .dropLastWhile { it.isBlank() }

  var rows = lines.map { it.asBoardCells() }
  val height = rows.size
  val width = rows.asSequence()
      .map { it.size }
      .maxOrNull()!!

  // Pad short rows.
  rows = rows.map { row ->
    if (row.size == width) row
    else {
      row + List(width - row.size) { EMPTY_FLOOR }
    }
  }

  if (height < width) {
    // Too short, pad top and bottom.
    val verticalPadding = (width - height) / 2
    val paddingRow = List(width) { EMPTY_FLOOR }
    val topPads = List(verticalPadding) { paddingRow }
    val bottomPads = List(width - (height + verticalPadding)) { paddingRow }
    rows = topPads + rows + bottomPads
  } else if (width < height) {
    // Too narrow, pad all rows.
    val leftPadding = (height - width) / 2
    val rightPadding = (height - (width + leftPadding))
    val leftPad = List(leftPadding) { EMPTY_FLOOR }
    val rightPad = List(rightPadding) { EMPTY_FLOOR }
    rows = rows.map { row -> leftPad + row + rightPad }
  }

  // Concatenate rows to one giant list.
  return Board.fromRows(metadata, rows)
}

private fun BufferedSource.readHeader(): String? = buildString {
  if (!discardLineMatching { it == YAML_DELIMITER }) return null

  while (true) {
    val line = readUtf8Line() ?: throw IllegalArgumentException("Expected --- but found EOF.")
    if (line == YAML_DELIMITER) return@buildString
    appendLine(line)
  }
}

private inline fun BufferedSource.discardLineMatching(predicate: (String) -> Boolean): Boolean {
  if (peekLine()?.let(predicate) == true) {
    readUtf8Line()
    return true
  }
  return false
}

private fun BufferedSource.peekLine(): String? = peek().readUtf8Line()

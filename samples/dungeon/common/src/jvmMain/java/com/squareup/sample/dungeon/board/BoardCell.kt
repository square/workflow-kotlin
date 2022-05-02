package com.squareup.sample.dungeon.board

import java.lang.Character.toCodePoint

data class BoardCell(val codePoint: Int) {
  constructor(emoji: String) : this(emoji.codePointAt(0))

  init {
    require(Character.isValidCodePoint(codePoint))
  }

  private val string = String(Character.toChars(codePoint))

  val isEmpty get() = this == EMPTY_FLOOR
  val isWall get() = this in WALL_CELLS
  val isToxic get() = this in TOXIC

  override fun toString(): String = string

  companion object {
    val EMPTY_FLOOR = BoardCell(" ")
    val WALL_CELLS = "🌳🧱".asBoardCells()
    val TOXIC = "🔥🌊".asBoardCells()
  }
}

fun String.asBoardCells(): List<BoardCell> = codePointsSequence()
  .map(::BoardCell)
  .toList()

/**
 * Algorithm taken from codePoints() method in API 24+.
 */
private fun String.codePointsSequence() = sequence {
  var i = 0

  while (i < length) {
    val c1 = get(i++)
    if (!c1.isHighSurrogate() || i >= length) {
      yield(c1.code)
    } else {
      val c2 = get(i)
      if (c2.isLowSurrogate()) {
        i++
        yield(toCodePoint(c1, c2))
      } else {
        yield(c1.code)
      }
    }
  }
}

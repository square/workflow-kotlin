package com.squareup.sample.dungeon

import java.util.EnumSet
import java.util.EnumSet.copyOf
import java.util.EnumSet.noneOf

/**
 * A simplified component-based direction vector in the game world.
 *
 * Wraps a mutable [EnumSet] with copying operations.
 *
 * @param directions The components of the direction of the vector.
 * @param cellsPerSecond The magnitude of the vector, in cells-per-second.
 */
data class Movement(
  private val directions: EnumSet<Direction> = noneOf(Direction::class.java),
  val cellsPerSecond: Float = 1f
) : Iterable<Direction> by directions {

  constructor(
    vararg directions: Direction,
    cellsPerSecond: Float = 1f
  ) : this(copyOf(directions.asList()), cellsPerSecond)

  operator fun contains(direction: Direction): Boolean = direction in directions
  operator fun plus(direction: Direction): Movement = with { it.add(direction) }
  operator fun minus(direction: Direction): Movement = with { it.remove(direction) }

  private inline fun with(block: (EnumSet<Direction>) -> Unit): Movement =
    copy(directions = directions.clone().also(block))
}

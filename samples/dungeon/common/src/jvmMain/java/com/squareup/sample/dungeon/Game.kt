package com.squareup.sample.dungeon

import com.squareup.sample.dungeon.board.Board.Location

data class Game(
  val playerLocation: Location,
  val aiLocations: List<Location>
) {
  val isPlayerEaten: Boolean get() = aiLocations.any { it == playerLocation }
}

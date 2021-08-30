package com.squareup.sample.gameworkflow

import com.squareup.sample.gameworkflow.Player.X

/**
 * The state of a tic tac toe game. Also serves as the state of [TakeTurnsWorkflow].
 *
 * @param playing The symbol of the player whose turn it is.
 * @param board The current game board.
 */
data class Turn(
  val playing: Player = X,
  val board: Board = EMPTY_BOARD
) {

  private companion object {
    val EMPTY_BOARD = listOf(
        listOf(null, null, null),
        listOf(null, null, null),
        listOf(null, null, null)
    )
  }
}

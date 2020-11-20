package com.squareup.sample.gameworkflow

import com.squareup.sample.gameworkflow.Player.O
import com.squareup.sample.gameworkflow.Player.X

/**
 * The X's and O's of a Tic Tac Toe game.
 */
enum class Player {
  X,
  O
}

val Player.other: Player
  get() = when (this) {
    X -> O
    O -> X
  }

fun Player.name(playerInfo: PlayerInfo) = when (this) {
  X -> playerInfo.xName
  O -> playerInfo.oName
}

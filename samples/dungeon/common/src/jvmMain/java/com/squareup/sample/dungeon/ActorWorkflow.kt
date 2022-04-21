package com.squareup.sample.dungeon

import com.squareup.sample.dungeon.ActorWorkflow.ActorProps
import com.squareup.sample.dungeon.ActorWorkflow.ActorRendering
import com.squareup.sample.dungeon.board.Board
import com.squareup.sample.dungeon.board.Board.Location
import com.squareup.sample.dungeon.board.BoardCell
import com.squareup.workflow1.Worker
import com.squareup.workflow1.Workflow

/**
 * Schema for a workflow that can plug into the [GameWorkflow] to represent an "actor" in the game.
 */
interface ActorWorkflow : Workflow<ActorProps, Nothing, ActorRendering> {

  data class ActorProps(
    val board: Board,
    val myLocation: Location,
    val ticks: Worker<Long>
  )

  data class ActorRendering(
    val avatar: BoardCell,
    val movement: Movement
  )
}

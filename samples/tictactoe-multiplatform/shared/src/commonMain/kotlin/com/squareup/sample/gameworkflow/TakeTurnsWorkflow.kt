package com.squareup.sample.gameworkflow

import com.squareup.sample.gameworkflow.Ending.Draw
import com.squareup.sample.gameworkflow.Ending.Quitted
import com.squareup.sample.gameworkflow.Ending.Victory
import com.squareup.sample.gameworkflow.RealTakeTurnsWorkflow.Action.Quit
import com.squareup.sample.gameworkflow.RealTakeTurnsWorkflow.Action.TakeSquare
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowAction

interface TakeTurnsWorkflow : Workflow<TakeTurnsProps, CompletedGame, GamePlayScreen>

class TakeTurnsProps private constructor(
  val playerInfo: PlayerInfo,
  val initialTurn: Turn = Turn()
) {
  companion object {
    fun newGame(playerInfo: PlayerInfo): TakeTurnsProps = TakeTurnsProps(playerInfo)
    fun resumeGame(
      playerInfo: PlayerInfo,
      turn: Turn
    ): TakeTurnsProps = TakeTurnsProps(playerInfo, turn)
  }
}

/**
 * Models the turns of a Tic Tac Toe game, alternating between [Player.X]
 * and [Player.O]. Finishes with a [report][CompletedGame] of the last turn of the game,
 * and an [Ending] condition of [Victory], [Draw] or [Quitted].
 *
 * http://go/sf-taketurns
 */
class RealTakeTurnsWorkflow : TakeTurnsWorkflow,
    StatefulWorkflow<TakeTurnsProps, Turn, CompletedGame, GamePlayScreen>() {

  sealed class Action : WorkflowAction<TakeTurnsProps, Turn, CompletedGame>() {
    class TakeSquare(
      private val row: Int,
      private val col: Int
    ) : Action() {
      init {
        println("Creating new Action")
      }
      override fun Updater.apply() {
        val newBoard = state.board.takeSquare(row, col, state.playing)
        print("New board: $newBoard")

        when {
          newBoard.hasVictory() -> {
            state = state.copy(board = newBoard)
            setOutput(CompletedGame(Victory, state))
            print("Victory State")
          }
          newBoard.isFull() -> {
            state = state.copy(board = newBoard)
            setOutput(CompletedGame(Draw, state))
            print("Tie Game")
          }

          else -> {
            print("Turn Taken")
            state = Turn(playing = state.playing.other, board = newBoard)
          }
        }
      }
    }

    object Quit : Action() {
      override fun Updater.apply() {
        setOutput(CompletedGame(Quitted, state))
      }
    }
  }

  override fun initialState(
    props: TakeTurnsProps,
    snapshot: Snapshot?
  ): Turn = props.initialTurn

  override fun render(
    renderProps: TakeTurnsProps,
    renderState: Turn,
    context: RenderContext
  ): GamePlayScreen = GamePlayScreen(
      playerInfo = renderProps.playerInfo,
      gameState = renderState,
      onQuit = { context.actionSink.send(Quit) },
      onClick = { row, col -> context.actionSink.send(TakeSquare(row, col)) }
  )

  override fun snapshotState(state: Turn): Snapshot? = null
}

@file:Suppress("DEPRECATION")

package com.squareup.sample.dungeon

import android.os.Vibrator
import com.squareup.sample.dungeon.GameSessionWorkflow.Output
import com.squareup.sample.dungeon.GameSessionWorkflow.Output.NewBoard
import com.squareup.sample.dungeon.GameSessionWorkflow.Props
import com.squareup.sample.dungeon.GameSessionWorkflow.State
import com.squareup.sample.dungeon.GameSessionWorkflow.State.GameOver
import com.squareup.sample.dungeon.GameSessionWorkflow.State.Loading
import com.squareup.sample.dungeon.GameSessionWorkflow.State.Running
import com.squareup.sample.dungeon.GameWorkflow.Output.PlayerWasEaten
import com.squareup.sample.dungeon.GameWorkflow.Output.Vibrate
import com.squareup.sample.dungeon.board.Board
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.WorkflowAction.Companion.noAction
import com.squareup.workflow1.action
import com.squareup.workflow1.runningWorker
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.modal.AlertContainerScreen
import com.squareup.workflow1.ui.modal.AlertScreen
import com.squareup.workflow1.ui.modal.AlertScreen.Button.NEGATIVE
import com.squareup.workflow1.ui.modal.AlertScreen.Button.NEUTRAL
import com.squareup.workflow1.ui.modal.AlertScreen.Button.POSITIVE
import com.squareup.workflow1.ui.modal.AlertScreen.Event.ButtonClicked

typealias BoardPath = String

/**
 * Workflow that loads a game board, runs the game, and displays game over screens.
 */
@OptIn(WorkflowUiExperimentalApi::class)
class GameSessionWorkflow(
  private val gameWorkflow: GameWorkflow,
  private val vibrator: Vibrator,
  private val boardLoader: BoardLoader
) : StatefulWorkflow<Props, State, Output, AlertContainerScreen<Any>>() {

  data class Props(
    val boardPath: BoardPath,
    val paused: Boolean = false
  )

  sealed class State {
    object Loading : State(), Screen
    data class Running(val board: Board) : State()
    data class GameOver(val board: Board) : State()
  }

  sealed class Output {
    object NewBoard : Output()
  }

  override fun initialState(
    props: Props,
    snapshot: Snapshot?
  ): State = Loading

  override fun render(
    renderProps: Props,
    renderState: State,
    context: RenderContext
  ): AlertContainerScreen<Any> = when (renderState) {
    Loading -> {
      context.runningWorker(boardLoader.loadBoard(renderProps.boardPath)) { StartRunning(it) }
      AlertContainerScreen(Loading)
    }

    is Running -> {
      val gameInput = GameWorkflow.Props(renderState.board, paused = renderProps.paused)
      val gameScreen = context.renderChild(gameWorkflow, gameInput) {
        handleGameOutput(it, renderState.board)
      }
      AlertContainerScreen(gameScreen)
    }

    is GameOver -> {
      val gameInput = GameWorkflow.Props(renderState.board)
      val gameScreen = context.renderChild(gameWorkflow, gameInput) { noAction() }

      val gameOverDialog = AlertScreen(
        buttons = mapOf(POSITIVE to "Restart", NEUTRAL to "New board"),
        message = "You've been eaten, try again.",
        cancelable = false,
        onEvent = {
          if (it is ButtonClicked) {
            context.actionSink.send(
              when (it.button) {
                POSITIVE -> restartGame()
                NEUTRAL -> newBoard()
                NEGATIVE -> noAction()
              }
            )
          }
        }
      )

      AlertContainerScreen(gameScreen, gameOverDialog)
    }
  }

  override fun snapshotState(state: State): Snapshot? = null

  private class StartRunning(val board: Board) : WorkflowAction<Props, State, Nothing>() {
    override fun Updater.apply() {
      state = Running(board)
    }
  }

  private fun handleGameOutput(
    output: GameWorkflow.Output,
    board: Board
  ) = action("handleGameOutput") {
    when (output) {
      Vibrate -> vibrate(50)
      PlayerWasEaten -> {
        state = GameOver(board)
        vibrate(20)
        vibrate(20)
        vibrate(20)
        vibrate(20)
        vibrate(1000)
      }
    }
  }

  private fun restartGame() = action("restartGame") { state = Loading }

  private fun newBoard() = action("newBoard") { setOutput(NewBoard) }

  private fun vibrate(durationMs: Long) {
    @Suppress("DEPRECATION")
    vibrator.vibrate(durationMs)
  }
}

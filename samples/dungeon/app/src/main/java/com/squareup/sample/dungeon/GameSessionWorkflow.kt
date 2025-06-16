package com.squareup.sample.dungeon

import android.os.Vibrator
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
import com.squareup.workflow1.ui.navigation.AlertOverlay
import com.squareup.workflow1.ui.navigation.AlertOverlay.Button.POSITIVE
import com.squareup.workflow1.ui.navigation.BodyAndOverlaysScreen
import com.squareup.workflow1.ui.navigation.Overlay

typealias BoardPath = String

/**
 * Workflow that loads a game board, runs the game, and displays game over screens.
 */
class GameSessionWorkflow(
  private val gameWorkflow: GameWorkflow,
  private val vibrator: Vibrator,
  private val boardLoader: BoardLoader
) : StatefulWorkflow<Props, State, Nothing, BodyAndOverlaysScreen<Screen, Overlay>>() {

  data class Props(
    val boardPath: BoardPath,
    val paused: Boolean = false
  )

  sealed class State {
    object Loading : State(), Screen
    data class Running(val board: Board) : State()
    data class GameOver(val board: Board) : State()
  }

  override fun initialState(
    props: Props,
    snapshot: Snapshot?
  ): State = Loading

  override fun render(
    renderProps: Props,
    renderState: State,
    context: RenderContext<Props, State, Nothing>
  ): BodyAndOverlaysScreen<Screen, Overlay> = when (renderState) {
    Loading -> {
      context.runningWorker(boardLoader.loadBoard(renderProps.boardPath)) { StartRunning(it) }
      BodyAndOverlaysScreen(Loading)
    }

    is Running -> {
      val gameInput = GameWorkflow.Props(renderState.board, paused = renderProps.paused)
      val gameScreen = context.renderChild(gameWorkflow, gameInput) {
        handleGameOutput(it, renderState.board)
      }
      BodyAndOverlaysScreen(gameScreen)
    }

    is GameOver -> {
      val gameInput = GameWorkflow.Props(renderState.board)
      val gameScreen = context.renderChild(gameWorkflow, gameInput) { noAction() }

      val gameOverDialog = AlertOverlay(
        buttons = mapOf(POSITIVE to "Restart"),
        message = "You've been eaten, try again.",
        cancelable = false,
        onEvent = { context.actionSink.send(restartGame()) }
      )

      BodyAndOverlaysScreen(gameScreen, listOf(gameOverDialog))
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

  private fun vibrate(durationMs: Long) {
    @Suppress("DEPRECATION")
    vibrator.vibrate(durationMs)
  }
}

package com.squareup.sample.gameworkflow

import androidx.compose.runtime.Composable
import com.squareup.sample.authworkflow.onResult
import com.squareup.sample.container.panel.PanelContainerScreen
import com.squareup.sample.container.panel.firstInPanelOver
import com.squareup.sample.gameworkflow.Ending.Quitted
import com.squareup.sample.gameworkflow.GameLog.LogResult.LOGGED
import com.squareup.sample.gameworkflow.GameLog.LogResult.TRY_LATER
import com.squareup.sample.gameworkflow.RunGameResult.CanceledStart
import com.squareup.sample.gameworkflow.RunGameResult.FinishedPlaying
import com.squareup.sample.gameworkflow.RunGameState.GameOver
import com.squareup.sample.gameworkflow.RunGameState.MaybeQuitting
import com.squareup.sample.gameworkflow.RunGameState.MaybeQuittingForSure
import com.squareup.sample.gameworkflow.RunGameState.NewGame
import com.squareup.sample.gameworkflow.RunGameState.Playing
import com.squareup.sample.gameworkflow.SyncState.SAVED
import com.squareup.sample.gameworkflow.SyncState.SAVE_FAILED
import com.squareup.sample.gameworkflow.SyncState.SAVING
import com.squareup.workflow.compose.ComposeWorkflow
import com.squareup.workflow.compose.StatefulComposeWorkflow
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.modal.AlertContainerScreen
import com.squareup.workflow1.ui.modal.AlertScreen
import com.squareup.workflow1.ui.modal.AlertScreen.Button.NEGATIVE
import com.squareup.workflow1.ui.modal.AlertScreen.Button.NEUTRAL
import com.squareup.workflow1.ui.modal.AlertScreen.Button.POSITIVE
import com.squareup.workflow1.ui.modal.AlertScreen.Event.ButtonClicked
import com.squareup.workflow1.ui.modal.AlertScreen.Event.Canceled

/**
 * We define this otherwise redundant typealias to keep composite workflows
 * that build on [RunGameWorkflow] decoupled from it, for ease of testing.
 */
typealias RunGameComposeWorkflow = ComposeWorkflow<Unit, RunGameResult, RunGameScreen>

/**
 * Runs the screens around a Tic Tac Toe game: prompts for player names, runs a
 * confirm quit screen, and offers a chance to play again. Delegates to [TakeTurnsWorkflow]
 * for the actual playing of the game.
 */
@OptIn(WorkflowUiExperimentalApi::class)
class RealRunGameComposeWorkflow(
  private val takeTurnsWorkflow: TakeTurnsComposeWorkflow,
  private val gameLog: GameLog
) : RunGameComposeWorkflow,
  StatefulComposeWorkflow<Unit, RunGameState, RunGameResult, RunGameScreen>() {

  override fun initialState(
    props: Unit,
  ): RunGameState {
    // TODO(implement saveable)
    return NewGame()
  }

  @Composable override fun render(
    renderProps: Unit,
    renderState: RunGameState,
    context: RenderContext
  ): RunGameScreen = when (renderState) {
    is NewGame -> {
      val emptyGameScreen = GamePlayScreen()

      subflowScreen(
        base = emptyGameScreen,
        subflow = NewGameScreen(
          renderState.defaultXName,
          renderState.defaultOName,
          onCancel = { context.setOutput(CanceledStart) },
          onStartGame = { x, o -> context.state = Playing(PlayerInfo(x, o)) }
        )
      )
    }

    is Playing -> {
      // context.renderChild starts takeTurnsWorkflow, or keeps it running if it was
      // already going. TakeTurnsWorkflow.render is immediately called,
      // and the GamePlayScreen it renders is immediately returned.
      val takeTurnsScreen = takeTurnsWorkflow.render(
        renderProps = renderState.resume
          ?.let { TakeTurnsProps.resumeGame(renderState.playerInfo, it) }
          ?: TakeTurnsProps.newGame(renderState.playerInfo)
      ) { context.stopPlaying(it) }

      simpleScreen(takeTurnsScreen)
    }

    is MaybeQuitting -> {
      alertScreen(
        base = GamePlayScreen(renderState.playerInfo, renderState.completedGame.lastTurn),
        alert = maybeQuitScreen(
          confirmQuit = {
            (renderState as? MaybeQuitting)?.let { oldState ->
              context.state = MaybeQuittingForSure(oldState.playerInfo, oldState.completedGame)
            }
          },
          continuePlaying = {
            (renderState as? MaybeQuitting)?.let { oldState ->
              context.state = Playing(oldState.playerInfo, oldState.completedGame.lastTurn)
            }
          }
        )
      )
    }

    is MaybeQuittingForSure -> {
      nestedAlertsScreen(
        GamePlayScreen(renderState.playerInfo, renderState.completedGame.lastTurn),
        maybeQuitScreen(),
        maybeQuitScreen(
          message = "Really?",
          positive = "Yes!!",
          negative = "Sigh, no",
          confirmQuit = {
            (renderState as? MaybeQuittingForSure)?.let { oldState ->
              context.state = GameOver(oldState.playerInfo, oldState.completedGame)
            }
          },
          continuePlaying = {
            (renderState as? MaybeQuittingForSure)?.let { oldState ->
              context.state = Playing(oldState.playerInfo, oldState.completedGame.lastTurn)
            }
          }
        )
      )
    }

    is GameOver -> {
      if (renderState.syncState == SAVING) {
        gameLog.logGame(renderState.completedGame).onResult { context.handleLogGame(it) }
      }

      GameOverScreen(
        renderState,
        onTrySaveAgain = { context.trySaveAgain() },
        onPlayAgain = { context.playAgain() },
        onExit = { context.setOutput(FinishedPlaying) }
      ).let(::simpleScreen)
    }
  }

  private fun RenderContext.stopPlaying(game: CompletedGame) {
    val oldState = state as Playing
    state = when (game.ending) {
      Quitted -> MaybeQuitting(oldState.playerInfo, game)
      else -> GameOver(oldState.playerInfo, game)
    }
  }

  private fun RenderContext.handleLogGame(result: GameLog.LogResult) {
    val oldState = state as GameOver
    state = when (result) {
      TRY_LATER -> oldState.copy(syncState = SAVE_FAILED)
      LOGGED -> oldState.copy(syncState = SAVED)
    }
  }

  private fun RenderContext.playAgain() {
    (state as? GameOver)?.let { oldState ->
      val (x, o) = oldState.playerInfo
      state = NewGame(x, o)
    }
  }

  private fun RenderContext.trySaveAgain() {
    (state as? GameOver)?.let { oldState ->
      check(oldState.syncState == SAVE_FAILED) {
        "Should only fire trySaveAgain in syncState $SAVE_FAILED, " +
          "was ${oldState.syncState}"
      }
      state = oldState.copy(syncState = SAVING)
    }
  }

  private fun nestedAlertsScreen(
    base: Any,
    vararg alerts: AlertScreen
  ): RunGameScreen {
    return AlertContainerScreen(
      PanelContainerScreen(base), *alerts
    )
  }

  private fun alertScreen(
    base: Any,
    alert: AlertScreen
  ): RunGameScreen {
    return AlertContainerScreen(
      PanelContainerScreen(base), alert
    )
  }

  private fun subflowScreen(
    base: Any,
    subflow: Any
  ): RunGameScreen {
    return AlertContainerScreen(subflow.firstInPanelOver(base))
  }

  private fun simpleScreen(screen: Any): RunGameScreen {
    return AlertContainerScreen(PanelContainerScreen(screen))
  }

  private fun maybeQuitScreen(
    message: String = "Do you really want to concede the game?",
    positive: String = "I Quit",
    negative: String = "No",
    confirmQuit: () -> Unit = { },
    continuePlaying: () -> Unit = { }
  ): AlertScreen {
    return AlertScreen(
      buttons = mapOf(
        POSITIVE to positive,
        NEGATIVE to negative
      ),
      message = message,
      onEvent = { alertEvent ->
        when (alertEvent) {
          is ButtonClicked -> when (alertEvent.button) {
            POSITIVE -> confirmQuit()
            NEGATIVE -> continuePlaying()
            NEUTRAL -> throw IllegalArgumentException()
          }
          Canceled -> continuePlaying()
        }
      }
    )
  }
}

package com.squareup.sample.gameworkflow

import com.squareup.sample.container.panel.PanelContainerScreen
import com.squareup.sample.container.panel.firstInPanelOver
import com.squareup.sample.gameworkflow.Ending.Quitted
import com.squareup.sample.gameworkflow.GameLog.LogResult
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
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.action
import com.squareup.workflow1.runningWorker
import com.squareup.workflow1.rx2.asWorker
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.modal.AlertContainerScreen
import com.squareup.workflow1.ui.modal.AlertScreen
import com.squareup.workflow1.ui.modal.AlertScreen.Button.NEGATIVE
import com.squareup.workflow1.ui.modal.AlertScreen.Button.NEUTRAL
import com.squareup.workflow1.ui.modal.AlertScreen.Button.POSITIVE
import com.squareup.workflow1.ui.modal.AlertScreen.Event.ButtonClicked
import com.squareup.workflow1.ui.modal.AlertScreen.Event.Canceled

enum class RunGameResult {
  CanceledStart,
  FinishedPlaying
}

@OptIn(WorkflowUiExperimentalApi::class)
typealias RunGameScreen = AlertContainerScreen<PanelContainerScreen<Any, Any>>

/**
 * We define this otherwise redundant typealias to keep composite workflows
 * that build on [RunGameWorkflow] decoupled from it, for ease of testing.
 */
typealias RunGameWorkflow = Workflow<Unit, RunGameResult, RunGameScreen>

/**
 * Runs the screens around a Tic Tac Toe game: prompts for player names, runs a
 * confirm quit screen, and offers a chance to play again. Delegates to [TakeTurnsWorkflow]
 * for the actual playing of the game.
 */
@OptIn(WorkflowUiExperimentalApi::class)
class RealRunGameWorkflow(
  private val takeTurnsWorkflow: TakeTurnsWorkflow,
  private val gameLog: GameLog
) : RunGameWorkflow,
    StatefulWorkflow<Unit, RunGameState, RunGameResult,
      RunGameScreen>() {

  override fun initialState(
    props: Unit,
    snapshot: Snapshot?
  ): RunGameState {
    return snapshot?.let { RunGameState.fromSnapshot(snapshot.bytes) }
        ?: NewGame()
  }

  override fun render(
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
              onCancel = context.eventHandler { setOutput(CanceledStart) },
              onStartGame = context.eventHandler { x, o -> state = Playing(PlayerInfo(x, o)) }
          )
      )
    }

    is Playing -> {
      // context.renderChild starts takeTurnsWorkflow, or keeps it running if it was
      // already going. TakeTurnsWorkflow.render is immediately called,
      // and the GamePlayScreen it renders is immediately returned.
      val takeTurnsScreen = context.renderChild(
          takeTurnsWorkflow,
          props = renderState.resume
              ?.let { TakeTurnsProps.resumeGame(renderState.playerInfo, it) }
              ?: TakeTurnsProps.newGame(renderState.playerInfo)
      ) { stopPlaying(it) }

      simpleScreen(takeTurnsScreen)
    }

    is MaybeQuitting -> {
      alertScreen(
          base = GamePlayScreen(renderState.playerInfo, renderState.completedGame.lastTurn),
          alert = maybeQuitScreen(
              confirmQuit = context.eventHandler {
                (state as? MaybeQuitting)?.let { oldState ->
                  state = MaybeQuittingForSure(oldState.playerInfo, oldState.completedGame)
                }
              },
              continuePlaying = context.eventHandler {
                (state as? MaybeQuitting)?.let { oldState ->
                  state = Playing(oldState.playerInfo, oldState.completedGame.lastTurn)
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
              confirmQuit = context.eventHandler {
                (state as? MaybeQuittingForSure)?.let { oldState ->
                  state = GameOver(oldState.playerInfo, oldState.completedGame)
                }
              },
              continuePlaying = context.eventHandler {
                (state as? MaybeQuittingForSure)?.let { oldState ->
                  state = Playing(oldState.playerInfo, oldState.completedGame.lastTurn)
                }
              }
          )
      )
    }

    is GameOver -> {
      if (renderState.syncState == SAVING) {
        context.runningWorker(gameLog.logGame(renderState.completedGame).asWorker()) {
          handleLogGame(it)
        }
      }

      GameOverScreen(
        renderState,
          onTrySaveAgain = context.trySaveAgain(),
          onPlayAgain = context.playAgain(),
          onExit = context.eventHandler { setOutput(FinishedPlaying) }
      ).let(::simpleScreen)
    }
  }

  private fun stopPlaying(game: CompletedGame) = action {
    val oldState = state as Playing
    state = when (game.ending) {
      Quitted -> MaybeQuitting(oldState.playerInfo, game)
      else -> GameOver(oldState.playerInfo, game)
    }
  }

  private fun handleLogGame(result: LogResult) = action {
    val oldState = state as GameOver
    state = when (result) {
      TRY_LATER -> oldState.copy(syncState = SAVE_FAILED)
      LOGGED -> oldState.copy(syncState = SAVED)
    }
  }

  private fun RenderContext.playAgain() = eventHandler {
    (state as? GameOver)?.let { oldState ->
      val (x, o) = oldState.playerInfo
      state = NewGame(x, o)
    }
  }

  private fun RenderContext.trySaveAgain() = eventHandler {
    (state as? GameOver)?.let { oldState ->
      check(oldState.syncState == SAVE_FAILED) {
        "Should only fire trySaveAgain in syncState $SAVE_FAILED, " +
            "was ${oldState.syncState}"
      }
      state = oldState.copy(syncState = SAVING)
    }
  }

  override fun snapshotState(state: RunGameState): Snapshot = state.toSnapshot()

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

@file:OptIn(WorkflowUiExperimentalApi::class)

package com.squareup.sample.gameworkflow

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
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowLocal
import com.squareup.workflow1.action
import com.squareup.workflow1.runningWorker
import com.squareup.workflow1.rx2.asWorker
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.container.AlertOverlay
import com.squareup.workflow1.ui.container.AlertOverlay.Button.NEGATIVE
import com.squareup.workflow1.ui.container.AlertOverlay.Button.NEUTRAL
import com.squareup.workflow1.ui.container.AlertOverlay.Button.POSITIVE
import com.squareup.workflow1.ui.container.AlertOverlay.Event.ButtonClicked
import com.squareup.workflow1.ui.container.AlertOverlay.Event.Canceled

enum class RunGameResult {
  CanceledStart,
  FinishedPlaying
}

/**
 * This workflow renders in up to three parts, whose display a parent is responsible for
 * managing. There is always a [gameScreen], which may be augmented by a [namePrompt]
 * and [alerts]. By declaring our rendering shape this explicitly, we give parent workflows
 * just enough information to recompose, without leaking details about every single type
 * of screen we render.
 */
data class RunGameRendering(
  val gameScreen: Screen,
  val namePrompt: Screen? = null,
  val alerts: List<AlertOverlay> = emptyList()
)

/**
 * We define this otherwise redundant typealias to keep composite workflows
 * that build on [RunGameWorkflow] decoupled from it, for ease of testing.
 */
typealias RunGameWorkflow =
  Workflow<Unit, RunGameResult, RunGameRendering>

/**
 * Runs the screens around a Tic Tac Toe game: prompts for player names, runs a
 * confirm quit screen, and offers a chance to play again. Delegates to [TakeTurnsWorkflow]
 * for the actual playing of the game.
 */
class RealRunGameWorkflow(
  private val takeTurnsWorkflow: TakeTurnsWorkflow,
  private val gameLog: GameLog
) : RunGameWorkflow,
  StatefulWorkflow<Unit, RunGameState, RunGameResult, RunGameRendering>() {

  override fun initialState(
    props: Unit,
    snapshot: Snapshot?,
    workflowLocal: WorkflowLocal
  ): RunGameState {
    return snapshot?.let { RunGameState.fromSnapshot(snapshot.bytes) }
      ?: NewGame()
  }

  override fun render(
    renderProps: Unit,
    renderState: RunGameState,
    context: RenderContext
  ): RunGameRendering =
    when (renderState) {
      is NewGame -> {
        val emptyGameScreen = GamePlayScreen()

        RunGameRendering(
          gameScreen = emptyGameScreen,
          namePrompt = NewGameScreen(
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

        RunGameRendering(takeTurnsScreen)
      }

      is MaybeQuitting -> {
        RunGameRendering(
          gameScreen = GamePlayScreen(
            renderState.playerInfo,
            renderState.completedGame.lastTurn
          ),
          alerts = listOf(
            maybeQuitScreen(
              message = "Do you really want to concede the game?",
              positive = "I Quit",
              negative = "No",
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
        )
      }

      is MaybeQuittingForSure -> {
        RunGameRendering(
          gameScreen = GamePlayScreen(renderState.playerInfo, renderState.completedGame.lastTurn),
          alerts = listOf(
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
        )
      }

      is GameOver -> {
        if (renderState.syncState == SAVING) {
          context.runningWorker(gameLog.logGame(renderState.completedGame).asWorker()) {
            handleLogGame(it)
          }
        }

        RunGameRendering(
          GameOverScreen(
            renderState,
            onTrySaveAgain = context.trySaveAgain(),
            onPlayAgain = context.playAgain(),
            onExit = context.eventHandler { setOutput(FinishedPlaying) }
          )
        )
      }
    }

  private fun stopPlaying(game: CompletedGame) = action {
    val oldState = state as Playing
    state = when (game.ending) {
      Quitted -> MaybeQuitting(oldState.playerInfo, game)
      else -> GameOver(oldState.playerInfo, game)
    }
  }

  private fun handleLogGame(result: GameLog.LogResult) = action {
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

  private fun maybeQuitScreen(
    message: String,
    positive: String,
    negative: String,
    confirmQuit: () -> Unit,
    continuePlaying: () -> Unit
  ): AlertOverlay {
    return AlertOverlay(
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

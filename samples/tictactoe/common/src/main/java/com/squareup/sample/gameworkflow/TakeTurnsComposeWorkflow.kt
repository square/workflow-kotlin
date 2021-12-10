package com.squareup.sample.gameworkflow

import androidx.compose.runtime.Composable
import com.squareup.sample.gameworkflow.Ending.Draw
import com.squareup.sample.gameworkflow.Ending.Quitted
import com.squareup.sample.gameworkflow.Ending.Victory
import com.squareup.workflow.compose.ComposeWorkflow
import com.squareup.workflow.compose.StatefulComposeWorkflow

typealias TakeTurnsComposeWorkflow = ComposeWorkflow<TakeTurnsProps, CompletedGame, GamePlayScreen>

class RealTakeTurnsComposeWorkflow : TakeTurnsComposeWorkflow,
  StatefulComposeWorkflow<TakeTurnsProps, Turn, CompletedGame, GamePlayScreen>() {

  override fun initialState(props: TakeTurnsProps): Turn = props.initialTurn

  @Composable override fun render(
    renderProps: TakeTurnsProps,
    renderState: Turn,
    context: RenderContext
  ): GamePlayScreen = GamePlayScreen(
    playerInfo = renderProps.playerInfo,
    gameState = renderState,
    onQuit = { context.setOutput(CompletedGame(Quitted, renderState)) },
    onClick = { row, col ->
      val newBoard = renderState.board.takeSquare(row, col, renderState.playing)

      when {
        newBoard.hasVictory() ->
          context.setOutput(CompletedGame(Victory, renderState.copy(board = newBoard)))

        newBoard.isFull() ->
          context.setOutput(CompletedGame(Draw, renderState.copy(board = newBoard)))

        else -> context.state = Turn(playing = renderState.playing.other, board = newBoard)
      }
    }
  )
}

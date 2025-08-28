@file:Suppress("DEPRECATION")

package com.squareup.sample.gameworkflow

import com.google.common.truth.Truth.assertThat
import com.squareup.sample.gameworkflow.Ending.Draw
import com.squareup.sample.gameworkflow.Ending.Quitted
import com.squareup.sample.gameworkflow.Ending.Victory
import com.squareup.sample.gameworkflow.Player.O
import com.squareup.sample.gameworkflow.Player.X
import com.squareup.workflow1.testing.WorkflowTestRuntime
import com.squareup.workflow1.testing.launchForTestingFromStartWith
import org.junit.Test

class TakeTurnsWorkflowTest {
  @Test fun readWriteCompletedGame() {
    val turn = Turn()
    val before = CompletedGame(Quitted, turn)
    val out = before.toSnapshot()
    val after = CompletedGame.fromSnapshot(out.bytes)
    assertThat(after).isEqualTo(before)
  }

  @Test fun startsGameWithGivenNames() {
    RealTakeTurnsWorkflow().launchForTestingFromStartWith(
      TakeTurnsProps.newGame(PlayerInfo("higgledy", "piggledy"))
    ) {
      val (x, o) = awaitNextRendering().playerInfo

      assertThat(x)
        .isEqualTo("higgledy")
      assertThat(o)
        .isEqualTo("piggledy")
    }
  }

  @Test fun xWins() {
    RealTakeTurnsWorkflow().launchForTestingFromStartWith(
      TakeTurnsProps.newGame(PlayerInfo("higgledy", "piggledy"))
    ) {
      takeSquare(0, 0)
      takeSquare(1, 0)
      takeSquare(0, 1)
      takeSquare(1, 1)
      takeSquare(0, 2)

      val expectedLastTurn = Turn(
        board = listOf(
          listOf(X, X, X),
          listOf(O, O, null),
          listOf(null, null, null)
        )
      )

      val result = awaitNextOutput()
      assertThat(result).isEqualTo(CompletedGame(Victory, expectedLastTurn))
    }
  }

  @Test fun draw() {
    RealTakeTurnsWorkflow().launchForTestingFromStartWith(
      TakeTurnsProps.newGame(PlayerInfo("higgledy", "piggledy"))
    ) {
      takeSquare(0, 0) // X - -
      takeSquare(0, 1) // X O -
      takeSquare(0, 2) // X O X

      takeSquare(1, 2) // - - O
      takeSquare(1, 0) // X - O
      takeSquare(1, 1) // X O O

      takeSquare(2, 2) // - - X
      takeSquare(2, 0) // O - X
      takeSquare(2, 1) // O X X

      val expectedLastTurn = Turn(
        board = listOf(
          listOf(X, O, X),
          listOf(X, O, O),
          listOf(O, X, X)
        )
      )

      val result = awaitNextOutput()
      assertThat(result).isEqualTo(CompletedGame(Draw, expectedLastTurn))
    }
  }

  @Test fun quiteAndResume() {
    var output: CompletedGame? = null

    RealTakeTurnsWorkflow().launchForTestingFromStartWith(
      TakeTurnsProps.newGame(PlayerInfo("higgledy", "piggledy"))
    ) {
      awaitNextRendering().onQuit()
      output = awaitNextOutput()
    }

    assertThat(output!!.ending).isSameInstanceAs(Quitted)

    RealTakeTurnsWorkflow().launchForTestingFromStartWith(
      TakeTurnsProps.resumeGame(
        PlayerInfo("higgledy", "piggledy"),
        output!!.lastTurn
      )
    ) {
      assertThat(awaitNextRendering().gameState).isEqualTo(output!!.lastTurn)
    }
  }
}

private fun WorkflowTestRuntime<*, *, GamePlayScreen>.takeSquare(row: Int, col: Int) {
  awaitNextRendering().onClick(row, col)
}

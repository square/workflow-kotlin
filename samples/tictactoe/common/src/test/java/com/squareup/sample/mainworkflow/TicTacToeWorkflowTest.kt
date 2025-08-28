@file:Suppress("DEPRECATION")

package com.squareup.sample.mainworkflow

import com.google.common.truth.Truth.assertThat
import com.squareup.sample.authworkflow.AuthResult.Authorized
import com.squareup.sample.authworkflow.AuthWorkflow
import com.squareup.sample.container.panel.PanelOverlay
import com.squareup.sample.container.panel.ScrimScreen
import com.squareup.sample.gameworkflow.GamePlayScreen
import com.squareup.sample.gameworkflow.RunGameRendering
import com.squareup.sample.gameworkflow.RunGameWorkflow
import com.squareup.workflow1.Worker
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.action
import com.squareup.workflow1.rendering
import com.squareup.workflow1.runningWorker
import com.squareup.workflow1.stateless
import com.squareup.workflow1.testing.launchForTestingFromStartWith
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.navigation.BackStackScreen
import com.squareup.workflow1.ui.navigation.BodyAndOverlaysScreen
import org.junit.Test

/**
 * Demonstrates unit testing of a composite workflow. Note how we
 * pass in fakes for the nested workflows.
 */
class TicTacToeWorkflowTest {
  @Test fun `starts in auth over empty game`() {
    TicTacToeWorkflow(authWorkflow(), runGameWorkflow()).launchForTestingFromStartWith {
      awaitNextRendering()
        .let { screen ->
          assertThat(screen.panels).hasSize(1)
          val panelBody = (screen.panels[0].content as BackStackScreen<*>).top
          assertThat(panelBody).isEqualTo(S(DEFAULT_AUTH))

          // This GamePlayScreen() is emitted by TicTacToeWorkflow itself.
          assertThat(screen.body.content).isEqualTo(GamePlayScreen())
        }
    }
  }

  @Test fun `starts game on auth then moves to run game`() {
    val authWorkflow: AuthWorkflow = Workflow.stateless {
      runningWorker(Worker.from { }) {
        action("auth") { setOutput(Authorized("auth")) }
      }
      authScreen()
    }

    TicTacToeWorkflow(authWorkflow, runGameWorkflow()).launchForTestingFromStartWith {
      awaitNextRendering()
        .let { screen ->
          assertThat(screen.panels).isEmpty()
          assertThat(screen.body.content).isEqualTo(S(DEFAULT_RUN_GAME))
        }
    }
  }

  private data class S<T>(val value: T) : Screen

  private fun authScreen(wrapped: String = DEFAULT_AUTH) = BackStackScreen(S(wrapped))

  private val BodyAndOverlaysScreen<ScrimScreen<*>, *>.panels: List<PanelOverlay<*>>
    get() = overlays.mapNotNull { it as? PanelOverlay<*> }

  private fun authWorkflow(
    screen: String = DEFAULT_AUTH
  ): AuthWorkflow = Workflow.rendering(authScreen(screen))

  private fun runGameWorkflow(
    body: String = DEFAULT_RUN_GAME
  ): RunGameWorkflow = Workflow.rendering(RunGameRendering(S(body)))

  private companion object {
    const val DEFAULT_AUTH = "DefaultAuthScreen"
    const val DEFAULT_RUN_GAME = "DefaultRunGameBody"
  }
}

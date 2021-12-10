@file:Suppress("DEPRECATION")

package com.squareup.sample.mainworkflow

import androidx.compose.runtime.Composable
import com.squareup.sample.authworkflow.AuthComposeWorkflow
import com.squareup.sample.authworkflow.AuthResult
import com.squareup.sample.authworkflow.AuthResult.Authorized
import com.squareup.sample.authworkflow.AuthResult.Canceled
import com.squareup.sample.authworkflow.AuthWorkflow
import com.squareup.sample.container.panel.inPanelOver
import com.squareup.sample.gameworkflow.GamePlayScreen
import com.squareup.sample.gameworkflow.RealRunGameWorkflow
import com.squareup.sample.gameworkflow.RunGameComposeWorkflow
import com.squareup.sample.gameworkflow.RunGameScreen
import com.squareup.sample.mainworkflow.MainState.Authenticating
import com.squareup.sample.mainworkflow.MainState.RunningGame
import com.squareup.workflow.compose.StatefulComposeWorkflow
import com.squareup.workflow.compose.render
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.modal.AlertContainerScreen

/**
 * Application specific root [Workflow], and demonstration of workflow composition.
 * We log in, and then play as many games as we want.
 *
 * Delegates to [AuthWorkflow] and [RealRunGameWorkflow]. Responsible only for deciding
 * what to do as each nested workflow ends.
 *
 * We adopt [RunGameScreen] as our own rendering type because it's more demanding
 * than that of [AuthWorkflow]. We normalize the latter to be consistent
 * with the former.
 *
 * A [Unit] output event is emitted to signal that the workflow has ended, and the host
 * activity should be finished.
 */
class TicTacToeComposeWorkflow(
  private val authWorkflow: AuthComposeWorkflow,
  private val runGameWorkflow: RunGameComposeWorkflow
) : StatefulComposeWorkflow<Unit, MainState, Unit, RunGameScreen>() {

  override fun initialState(
    props: Unit,
  ): MainState = Authenticating

  @OptIn(WorkflowUiExperimentalApi::class)
  @Composable override fun render(
    renderProps: Unit,
    renderState: MainState,
    context: RenderContext
  ): RunGameScreen = when (renderState) {
    is Authenticating -> {
      val authScreen = authWorkflow.render { context.handleAuthResult(it) }
      val emptyGameScreen = GamePlayScreen()

      (AlertContainerScreen(authScreen.inPanelOver<Any, Any>(emptyGameScreen)))
    }

    is RunningGame -> {
      val childRendering = runGameWorkflow.render { context.state = Authenticating }

      val panels = childRendering.beneathModals.modals

      if (panels.isEmpty()) {
        childRendering
      } else {
        val stubAuthBackStack = authWorkflow.render { }

        val panelsMod = panels.toMutableList()
        panelsMod[0] = stubAuthBackStack + panels[0]
        childRendering.copy(beneathModals = childRendering.beneathModals.copy(modals = panelsMod))
      }
    }
  }

  // We continue to use the deprecated method here for one more release, to demonstrate
  // that the migration mechanism works.

  private fun RenderContext.handleAuthResult(result: AuthResult) {
    when (result) {
      is Canceled -> setOutput(Unit)
      is Authorized -> state = RunningGame
    }
  }
}

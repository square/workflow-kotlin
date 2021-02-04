@file:Suppress("DEPRECATION")

package com.squareup.sample.mainworkflow

import com.squareup.sample.authworkflow.AuthResult
import com.squareup.sample.authworkflow.AuthResult.Authorized
import com.squareup.sample.authworkflow.AuthResult.Canceled
import com.squareup.sample.authworkflow.AuthWorkflow
import com.squareup.sample.container.panel.inPanelOver
import com.squareup.sample.gameworkflow.GamePlayScreen
import com.squareup.sample.gameworkflow.RealRunGameWorkflow
import com.squareup.sample.gameworkflow.RunGameScreen
import com.squareup.sample.gameworkflow.RunGameWorkflow
import com.squareup.sample.mainworkflow.MainState.Authenticating
import com.squareup.sample.mainworkflow.MainState.RunningGame
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowAction.Companion.noAction
import com.squareup.workflow1.renderChild
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.modal.AlertContainerScreen
import com.squareup.workflow1.workflowAction

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
class TicTacToeWorkflow(
  private val authWorkflow: AuthWorkflow,
  private val runGameWorkflow: RunGameWorkflow
) : StatefulWorkflow<Unit, MainState, Unit, RunGameScreen>() {

  override fun initialState(
    props: Unit,
    snapshot: Snapshot?
  ): MainState = snapshot?.let { MainState.fromSnapshot(snapshot.bytes) }
      ?: Authenticating

  @OptIn(WorkflowUiExperimentalApi::class)
  override fun render(
    props: Unit,
    state: MainState,
    context: RenderContext
  ): RunGameScreen = when (state) {
    is Authenticating -> {
      val authScreen = context.renderChild(authWorkflow) { handleAuthResult(it) }
      val emptyGameScreen = GamePlayScreen()

      // IDE is wrong, removing them breaks the compile.
      // Probably due to https://youtrack.jetbrains.com/issue/KT-32869
      @Suppress("RemoveExplicitTypeArguments")
      (AlertContainerScreen(
          authScreen.inPanelOver<Any, Any>(emptyGameScreen)
      ))
    }

    is RunningGame -> {
      val childRendering = context.renderChild(runGameWorkflow) { startAuth }

      val panels = childRendering.beneathModals.modals

      if (panels.isEmpty()) {
        childRendering
      } else {
        // To prompt for player names, the child puts up a panel — that is, a modal view
        // hosting a BackStackScreen. If they cancel that, we'd like a visual effect of
        // popping back to the auth flow in that same panel. To get this effect we run
        // an authWorkflow and put its BackStackScreen behind this one.
        //
        // We use the "fake" uniquing name to make sure authWorkflow session from the
        // Authenticating state was allowed to die, so that this one will start fresh
        // in its logged out state.
        val stubAuthBackStack = context.renderChild(authWorkflow, "fake") { noAction() }

        val panelsMod = panels.toMutableList()
        panelsMod[0] = stubAuthBackStack + panels[0]
        childRendering.copy(beneathModals = childRendering.beneathModals.copy(modals = panelsMod))
      }
    }
  }

  override fun snapshotState(state: MainState): Snapshot = state.toSnapshot()

  // We continue to use the deprecated method here for one more release, to demonstrate
  // that the migration mechanism works.

  private val startAuth = workflowAction {
    state = Authenticating
    return@workflowAction null
  }

  private fun handleAuthResult(result: AuthResult) = workflowAction {
    when (result) {
      is Canceled -> return@workflowAction Unit
      is Authorized -> state = RunningGame
    }
    return@workflowAction null
  }
}

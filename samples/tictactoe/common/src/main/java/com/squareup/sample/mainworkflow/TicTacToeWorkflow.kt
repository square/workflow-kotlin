@file:OptIn(WorkflowUiExperimentalApi::class)
@file:Suppress("DEPRECATION")

package com.squareup.sample.mainworkflow

import com.squareup.sample.authworkflow.AuthResult
import com.squareup.sample.authworkflow.AuthResult.Authorized
import com.squareup.sample.authworkflow.AuthResult.Canceled
import com.squareup.sample.authworkflow.AuthWorkflow
import com.squareup.sample.container.panel.PanelOverlay
import com.squareup.sample.container.panel.ScrimScreen
import com.squareup.sample.gameworkflow.GamePlayScreen
import com.squareup.sample.gameworkflow.RunGameWorkflow
import com.squareup.sample.mainworkflow.MainState.Authenticating
import com.squareup.sample.mainworkflow.MainState.RunningGame
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowAction.Companion.noAction
import com.squareup.workflow1.action
import com.squareup.workflow1.renderChild
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.container.BackStackScreen
import com.squareup.workflow1.ui.container.BodyAndOverlaysScreen

/**
 * Application specific root [Workflow], and demonstration of workflow composition.
 * We log in, and then play as many games as we want.
 *
 * Delegates to [AuthWorkflow] and [RunGameWorkflow]. Responsible only for deciding
 * what to do as each nested workflow ends.
 *
 * Note how we normalize the rendering types of the two children. In particular:
 *
 * - We put the [BackStackScreen] from the [AuthWorkflow] into a [PanelOverlay], along
 *   with any [namePrompt][com.squareup.sample.gameworkflow.RunGameRendering.namePrompt]
 *   from [RunGameWorkflow]
 * - We add a [ScrimScreen] over our base to get the desired visual treatment under
 *   the [PanelOverlay]
 *
 * A [Unit] output event is emitted to signal that the workflow has ended, and the host
 * activity should be finished.
 */
@OptIn(WorkflowUiExperimentalApi::class)
class TicTacToeWorkflow(
  private val authWorkflow: AuthWorkflow,
  private val runGameWorkflow: RunGameWorkflow
) : StatefulWorkflow<Unit, MainState, Unit, BodyAndOverlaysScreen<ScrimScreen<*>, *>>() {

  override fun initialState(
    props: Unit,
    snapshot: Snapshot?
  ): MainState = snapshot?.let { MainState.fromSnapshot(snapshot.bytes) }
    ?: Authenticating

  override fun render(
    renderProps: Unit,
    renderState: MainState,
    context: RenderContext
  ): BodyAndOverlaysScreen<ScrimScreen<*>, *> {
    val bodyAndOverlays: BodyAndOverlaysScreen<*, *> = when (renderState) {
      is Authenticating -> {
        val authBackStack = context.renderChild(authWorkflow) { handleAuthResult(it) }
        // We always show an empty GameScreen behind the PanelOverlay that
        // hosts the authWorkflow's renderings because that's how the
        // award winning design team wanted it to look. Yes, it's a cheat
        // that TicTacToeWorkflow is aware of the GamePlayScreen type, and that
        // cheat is probably the most realistic thing about this sample.
        val emptyGameScreen = GamePlayScreen()

        BodyAndOverlaysScreen(emptyGameScreen, PanelOverlay(authBackStack))
      }

      is RunningGame -> {
        val gameRendering = context.renderChild(runGameWorkflow) { startAuth }

        if (gameRendering.namePrompt == null) {
          BodyAndOverlaysScreen(gameRendering.gameScreen, gameRendering.alerts)
        } else {
          // To prompt for player names, the child puts up a ScreenOverlay, which
          // we replace here with a tasteful PanelOverlay.
          //
          // If the name prompt gets canceled, we'd like a visual effect of
          // popping back to the auth flow in that same panel. To get this effect
          // we:
          //  - run an authWorkflow
          //  - append namePrompt.content to that BackStackScreen
          //  - and put that whole thing in the PanelOverlay
          //
          // We use the "fake" uniquing name to make sure authWorkflow session from the
          // Authenticating state was allowed to die, so that this one will start fresh
          // in its logged out state.
          val stubAuthBackStack = context.renderChild(authWorkflow, "fake") { noAction() }
          val fullBackStack = stubAuthBackStack + BackStackScreen(gameRendering.namePrompt)
          val allModals = listOf(PanelOverlay(fullBackStack)) + gameRendering.alerts

          BodyAndOverlaysScreen(gameRendering.gameScreen, allModals)
        }
      }
    }

    // Add the scrim. Dim it only if there is a panel showing.
    val dim = bodyAndOverlays.overlays.any { modal -> modal is PanelOverlay<*> }
    return bodyAndOverlays.mapBody { body -> ScrimScreen(body, dimmed = dim) }
  }

  override fun snapshotState(state: MainState): Snapshot = state.toSnapshot()

  private val startAuth = action { state = Authenticating }

  private fun handleAuthResult(result: AuthResult) = action {
    when (result) {
      is Canceled -> setOutput(Unit)
      is Authorized -> state = RunningGame
    }
  }
}

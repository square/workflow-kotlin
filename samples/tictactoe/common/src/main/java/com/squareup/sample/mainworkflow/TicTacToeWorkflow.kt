package com.squareup.sample.mainworkflow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.squareup.sample.authworkflow.AuthResult.Authorized
import com.squareup.sample.authworkflow.AuthResult.Canceled
import com.squareup.sample.authworkflow.AuthWorkflow
import com.squareup.sample.container.panel.PanelOverlay
import com.squareup.sample.container.panel.ScrimScreen
import com.squareup.sample.gameworkflow.GamePlayScreen
import com.squareup.sample.gameworkflow.RunGameWorkflow
import com.squareup.sample.mainworkflow.MainState.Authenticating
import com.squareup.sample.mainworkflow.MainState.RunningGame
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowExperimentalApi
import com.squareup.workflow1.compose.ComposeWorkflow
import com.squareup.workflow1.compose.renderChild
import com.squareup.workflow1.ui.navigation.BackStackScreen
import com.squareup.workflow1.ui.navigation.BodyAndOverlaysScreen
import com.squareup.workflow1.ui.navigation.plus

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
@OptIn(WorkflowExperimentalApi::class)
class TicTacToeWorkflow(
  private val authWorkflow: AuthWorkflow,
  private val runGameWorkflow: RunGameWorkflow
) : ComposeWorkflow<Unit, Unit, BodyAndOverlaysScreen<ScrimScreen<*>, *>>() {

  @Composable
  override fun produceRendering(
    props: Unit,
    emitOutput: (Unit) -> Unit
  ): BodyAndOverlaysScreen<ScrimScreen<*>, *> {
    var state: MainState by rememberSaveable(stateSaver = MainState.Saver) {
      mutableStateOf(Authenticating)
    }

    val bodyAndOverlays: BodyAndOverlaysScreen<*, *> = when (state) {
      is Authenticating -> {
        val authBackStack = renderChild(authWorkflow, onOutput = {
          when (it) {
            is Canceled -> emitOutput(Unit)
            is Authorized -> state = RunningGame
          }
        })
        // We always show an empty GameScreen behind the PanelOverlay that
        // hosts the authWorkflow's renderings because that's how the
        // award winning design team wanted it to look. Yes, it's a cheat
        // that TicTacToeWorkflow is aware of the GamePlayScreen type, and that
        // cheat is probably the most realistic thing about this sample.
        val emptyGameScreen = GamePlayScreen()

        BodyAndOverlaysScreen(emptyGameScreen, listOf(PanelOverlay(authBackStack)))
      }

      is RunningGame -> {
        val gameRendering = renderChild(runGameWorkflow, onOutput = { state = Authenticating })

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
          val stubAuthBackStack = renderChild(authWorkflow, onOutput = null)
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
}

package com.squareup.sample.hellobackbutton

import android.os.Parcelable
import com.squareup.sample.hellobackbutton.AreYouSureWorkflow.Finished
import com.squareup.sample.hellobackbutton.AreYouSureWorkflow.Rendering
import com.squareup.sample.hellobackbutton.AreYouSureWorkflow.State
import com.squareup.sample.hellobackbutton.AreYouSureWorkflow.State.Quitting
import com.squareup.sample.hellobackbutton.AreYouSureWorkflow.State.Running
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.WorkflowAction.Companion.noAction
import com.squareup.workflow1.action
import com.squareup.workflow1.ui.AndroidScreen
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewFactory.Companion.map
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.container.AlertOverlay
import com.squareup.workflow1.ui.container.AlertOverlay.Button.NEGATIVE
import com.squareup.workflow1.ui.container.AlertOverlay.Button.POSITIVE
import com.squareup.workflow1.ui.container.AlertOverlay.Event.ButtonClicked
import com.squareup.workflow1.ui.container.AlertOverlay.Event.Canceled
import com.squareup.workflow1.ui.container.BackButtonScreen
import com.squareup.workflow1.ui.container.BodyAndOverlaysScreen
import com.squareup.workflow1.ui.toParcelable
import com.squareup.workflow1.ui.toSnapshot
import kotlinx.parcelize.Parcelize

/**
 * Wraps [HelloBackButtonWorkflow] to (sometimes) pop a confirmation alert when the back
 * button is pressed.
 */
@OptIn(WorkflowUiExperimentalApi::class)
object AreYouSureWorkflow :
  StatefulWorkflow<Unit, State, Finished, Rendering>() {
  override fun initialState(
    props: Unit,
    snapshot: Snapshot?
  ): State = snapshot?.toParcelable() ?: Running

  class Rendering(
    val base: Screen,
    val alert: AlertOverlay? = null
  ) : AndroidScreen<Rendering> {
    override val viewFactory: ScreenViewFactory<Rendering> = map { newRendering ->
      BodyAndOverlaysScreen(newRendering.base, listOfNotNull(newRendering.alert))
    }
  }

  @Parcelize
  enum class State : Parcelable {
    Running,
    Quitting
  }

  object Finished

  override fun render(
    renderProps: Unit,
    renderState: State,
    context: RenderContext
  ): Rendering {
    val ableBakerCharlie = context.renderChild(HelloBackButtonWorkflow, Unit) { noAction() }

    return when (renderState) {
      Running -> {
        Rendering(
          BackButtonScreen(ableBakerCharlie) {
            // While we always provide a back button handler, by default the view code
            // associated with BackButtonScreen ignores ours if the view created for the
            // wrapped rendering sets a handler of its own. (Set BackButtonScreen.shadow
            // to change this precedence.)
            context.actionSink.send(maybeQuit)
          }
        )
      }
      Quitting -> {
        val alert = AlertOverlay(
          buttons = mapOf(
            POSITIVE to "I'm Positive",
            NEGATIVE to "Negatory"
          ),
          message = "Are you sure you want to do this thing?",
          onEvent = { alertEvent ->
            context.actionSink.send(
              when (alertEvent) {
                is ButtonClicked -> when (alertEvent.button) {
                  POSITIVE -> confirmQuit
                  else -> cancelQuit
                }
                Canceled -> cancelQuit
              }
            )
          }
        )

        Rendering(ableBakerCharlie, alert)
      }
    }
  }

  override fun snapshotState(state: State) = state.toSnapshot()

  private val maybeQuit = action { state = Quitting }
  private val confirmQuit = action { setOutput(Finished) }
  private val cancelQuit = action { state = Running }
}

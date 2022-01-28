package com.squareup.sample.hellobackbutton

import android.os.Parcelable
import com.squareup.sample.container.BackButtonScreen
import com.squareup.sample.hellobackbutton.AreYouSureWorkflow.Finished
import com.squareup.sample.hellobackbutton.AreYouSureWorkflow.State
import com.squareup.sample.hellobackbutton.AreYouSureWorkflow.State.Quitting
import com.squareup.sample.hellobackbutton.AreYouSureWorkflow.State.Running
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.WorkflowAction.Companion.noAction
import com.squareup.workflow1.action
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.modal.AlertContainerScreen
import com.squareup.workflow1.ui.modal.AlertScreen
import com.squareup.workflow1.ui.modal.AlertScreen.Button.NEGATIVE
import com.squareup.workflow1.ui.modal.AlertScreen.Button.POSITIVE
import com.squareup.workflow1.ui.modal.AlertScreen.Event.ButtonClicked
import com.squareup.workflow1.ui.modal.AlertScreen.Event.Canceled
import com.squareup.workflow1.ui.toParcelable
import com.squareup.workflow1.ui.toSnapshot
import kotlinx.parcelize.Parcelize

/**
 * Wraps [HelloBackButtonWorkflow] to (sometimes) pop a confirmation dialog when the back
 * button is pressed.
 */
@OptIn(WorkflowUiExperimentalApi::class)
object AreYouSureWorkflow : StatefulWorkflow<Unit, State, Finished, AlertContainerScreen<*>>() {
  override fun initialState(
    props: Unit,
    snapshot: Snapshot?
  ): State = snapshot?.toParcelable() ?: Running

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
  ): AlertContainerScreen<*> {
    val ableBakerCharlie = context.renderChild(HelloBackButtonWorkflow, Unit) { noAction() }

    return when (renderState) {
      Running -> {
        AlertContainerScreen(
          BackButtonScreen(ableBakerCharlie) {
            // While we always provide a back button handler, by default the view code
            // associated with BackButtonScreen ignores ours if the view created for the
            // wrapped rendering sets a handler of its own. (Set BackButtonScreen.override
            // to change this precedence.)
            context.actionSink.send(maybeQuit)
          }
        )
      }
      Quitting -> {
        val dialog = AlertScreen(
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

        AlertContainerScreen(ableBakerCharlie, dialog)
      }
    }
  }

  override fun snapshotState(state: State) = state.toSnapshot()

  private val maybeQuit = action { state = Quitting }
  private val confirmQuit = action { setOutput(Finished) }
  private val cancelQuit = action { state = Running }
}

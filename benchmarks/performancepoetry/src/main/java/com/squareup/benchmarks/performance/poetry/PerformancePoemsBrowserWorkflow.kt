package com.squareup.benchmarks.performance.poetry

import com.squareup.benchmarks.performance.poetry.PerformancePoemsBrowserWorkflow.State
import com.squareup.benchmarks.performance.poetry.PerformancePoemsBrowserWorkflow.State.ComplexCall
import com.squareup.benchmarks.performance.poetry.PerformancePoemsBrowserWorkflow.State.Initializing
import com.squareup.benchmarks.performance.poetry.PerformancePoemsBrowserWorkflow.State.NoSelection
import com.squareup.benchmarks.performance.poetry.PerformancePoemsBrowserWorkflow.State.Selected
import com.squareup.benchmarks.performance.poetry.views.BlankScreen
import com.squareup.sample.container.overviewdetail.OverviewDetailScreen
import com.squareup.sample.poetry.PoemListRendering.Companion.NO_POEM_SELECTED
import com.squareup.sample.poetry.PoemListWorkflow
import com.squareup.sample.poetry.PoemWorkflow
import com.squareup.sample.poetry.PoemsBrowserWorkflow
import com.squareup.sample.poetry.model.Poem
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.Worker
import com.squareup.workflow1.action
import com.squareup.workflow1.runningWorker
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.backstack.BackStackScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Version of [PoemsBrowserWorkflow] that takes in a [SimulatedPerfConfig] to control the
 * performance behavior of the Workflow.
 *
 * @param [simulatedPerfConfig] specifies whether to make the Workflow more 'complex' by
 * introducing some asynchronous delays. See [SimulatedPerfConfig] for more details.
 *
 * @param [isLoading] will be set to true while this workflow is 'loading'. This is so that another
 * component, such as a [MaybeLoadingGatekeeperWorkflow] can overlay the screen with a visual
 * loading state. N.B. that whether or not this is loading could be included in the
 * RenderingT if the interface [PoemsBrowserWorkflow] had been left more flexible.
 *
 * ** Also note that raw mutable state sharing like this will almost always be a smell. It would
 * be better to inject an interface of a 'Loading' service that could trigger this and likely
 * break ties/conflicts with a token in the start/stop requests. We leave that complexity out
 * here. **
 */
class PerformancePoemsBrowserWorkflow(
  private val simulatedPerfConfig: SimulatedPerfConfig,
  private val poemWorkflow: PoemWorkflow,
  private val isLoading: MutableStateFlow<Boolean>
) :
  PoemsBrowserWorkflow,
  StatefulWorkflow<List<Poem>, State, Unit, OverviewDetailScreen>() {

  sealed class State {
    // N.B. This state is a smell. We include it to be able to mimic smells
    // we encounter in real life. Best practice would be to fold it
    // into [NoSelection] at the very least.
    object Initializing : State()
    data class ComplexCall(
      val payload: Int
    ) : State()

    object NoSelection : State()
    data class Selected(val poemIndex: Int) : State()
  }

  override fun initialState(
    props: List<Poem>,
    snapshot: Snapshot?
  ): State {
    return if (simulatedPerfConfig.useInitializingState) Initializing else NoSelection
  }

  @OptIn(WorkflowUiExperimentalApi::class)
  override fun render(
    renderProps: List<Poem>,
    renderState: State,
    context: RenderContext
  ): OverviewDetailScreen {
    val poemListRendering = context.renderChild(PoemListWorkflow, renderProps) { selected ->
      choosePoem(selected)
    }
    when (renderState) {
      // Again, then entire `Initializing` state is a smell, which is most obvious from the
      // use of `Worker.from { Unit }`. A Worker doing no work and only shuttling the state
      // along is usually the sign you have an extraneous state that can be collapsed!
      // Don't try this at home.
      is Initializing -> {
        context.runningWorker(Worker.from { Unit }, "initializing") {
        isLoading.value = true
        action {
          isLoading.value = false
          state = NoSelection
        }
      }
        return OverviewDetailScreen(overviewRendering = BackStackScreen(BlankScreen))
      }
      is NoSelection -> {
        return OverviewDetailScreen(
          overviewRendering = BackStackScreen(
            poemListRendering.copy(selection = NO_POEM_SELECTED)
          )
        )
      }
      is ComplexCall -> {
        context.runningWorker(
          Worker.from {
            isLoading.value = true
            delay(simulatedPerfConfig.complexityDelay)
            // No Output for Worker is necessary because the selected index
            // is already in the state.
          }
        ) {
          action {
            isLoading.value = false
            (state as? ComplexCall)?.let { currentState ->
              state = if (currentState.payload != NO_POEM_SELECTED) {
                Selected(currentState.payload)
              } else {
                NoSelection
              }
            }
          }
        }
        var poems = OverviewDetailScreen(
          overviewRendering = BackStackScreen(
            poemListRendering.copy(selection = renderState.payload)
          )
        )
        if (renderState.payload != NO_POEM_SELECTED) {
          val poem: OverviewDetailScreen = context.renderChild(
            poemWorkflow,
            renderProps[renderState.payload]
          ) { clearSelection }
          poems += poem
        }
        return poems
      }
      is Selected -> {
        val poems = OverviewDetailScreen(
          overviewRendering = BackStackScreen(
            poemListRendering.copy(selection = renderState.poemIndex)
          )
        )
        val poem: OverviewDetailScreen = context.renderChild(
          poemWorkflow,
          renderProps[renderState.poemIndex]
        ) { clearSelection }
        return poems + poem
      }
    }
  }

  override fun snapshotState(state: State): Snapshot? = null

  private fun choosePoem(
    index: Int
  ) = action("goToPoem") {
    state = if (simulatedPerfConfig.isComplex) {
      ComplexCall(payload = index)
    } else {
      if (index != NO_POEM_SELECTED) {
        Selected(index)
      } else {
        NoSelection
      }
    }
  }

  private val clearSelection = choosePoem(NO_POEM_SELECTED)
}

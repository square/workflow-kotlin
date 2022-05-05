package com.squareup.benchmarks.performance.complex.poetry

import com.squareup.benchmarks.performance.complex.poetry.PerformancePoemWorkflow.Action.ClearSelection
import com.squareup.benchmarks.performance.complex.poetry.PerformancePoemWorkflow.Action.HandleStanzaListOutput
import com.squareup.benchmarks.performance.complex.poetry.PerformancePoemWorkflow.Action.SelectNext
import com.squareup.benchmarks.performance.complex.poetry.PerformancePoemWorkflow.Action.SelectPrevious
import com.squareup.benchmarks.performance.complex.poetry.PerformancePoemWorkflow.State
import com.squareup.benchmarks.performance.complex.poetry.PerformancePoemWorkflow.State.ComplexCall
import com.squareup.benchmarks.performance.complex.poetry.PerformancePoemWorkflow.State.Initializing
import com.squareup.benchmarks.performance.complex.poetry.PerformancePoemWorkflow.State.Selected
import com.squareup.benchmarks.performance.complex.poetry.instrumentation.SimulatedPerfConfig
import com.squareup.benchmarks.performance.complex.poetry.instrumentation.trace
import com.squareup.benchmarks.performance.complex.poetry.views.BlankScreen
import com.squareup.sample.container.overviewdetail.OverviewDetailScreen
import com.squareup.sample.poetry.PoemWorkflow
import com.squareup.sample.poetry.PoemWorkflow.ClosePoem
import com.squareup.sample.poetry.StanzaListWorkflow
import com.squareup.sample.poetry.StanzaListWorkflow.NO_SELECTED_STANZA
import com.squareup.sample.poetry.StanzaScreen
import com.squareup.sample.poetry.StanzaWorkflow
import com.squareup.sample.poetry.StanzaWorkflow.Output.CloseStanzas
import com.squareup.sample.poetry.StanzaWorkflow.Output.ShowNextStanza
import com.squareup.sample.poetry.StanzaWorkflow.Output.ShowPreviousStanza
import com.squareup.sample.poetry.StanzaWorkflow.Props
import com.squareup.sample.poetry.model.Poem
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.Worker
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.WorkflowAction.Companion.noAction
import com.squareup.workflow1.action
import com.squareup.workflow1.runningWorker
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.container.BackStackScreen
import com.squareup.workflow1.ui.container.toBackStackScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Version of [PoemWorkflow] that takes in a [SimulatedPerfConfig] to control the performance
 * behavior of the Workflow.
 *
 * @param [simulatedPerfConfig] specifies whether to make the Workflow more 'complex' by
 * introducing some asynchronous delays. See [SimulatedPerfConfig] for more details.
 *
 * @param [isLoading] will be set to true while this workflow is 'loading'. This is so that another
 * component, such as a [MaybeLoadingGatekeeperWorkflow] can overlay the screen with a visual
 * loading state. N.B. that whether or not this is loading could be included in the
 * RenderingT if the interface [PoemWorkflow] had been left more flexible.
 *
 * ** Also note that raw mutable state sharing like this will almost always be a smell. It would
 * be better to inject an interface of a 'Loading' service that could trigger this and likely
 * break ties/conflicts with a token in the start/stop requests. We leave that complexity out
 * here. **
 */
class PerformancePoemWorkflow(
  private val simulatedPerfConfig: SimulatedPerfConfig = SimulatedPerfConfig.NO_SIMULATED_PERF,
  private val isLoading: MutableStateFlow<Boolean>,
) : PoemWorkflow, StatefulWorkflow<Poem, State, ClosePoem, OverviewDetailScreen>() {

  sealed class State {
    // N.B. This state is a smell. We include it to be able to mimic smells
    // we encounter in real life. Best practice would be to fold it
    // into [Selected(NO_SELECTED_STANZA)] at the very least.
    object Initializing : State()
    data class ComplexCall(
      val payload: Int
    ) : State()

    data class Selected(val stanzaIndex: Int) : State()
  }

  override fun initialState(
    props: Poem,
    snapshot: Snapshot?
  ): State {
    return if (simulatedPerfConfig.useInitializingState) Initializing else Selected(
      NO_SELECTED_STANZA
    )
  }

  @OptIn(WorkflowUiExperimentalApi::class)
  override fun render(
    renderProps: Poem,
    renderState: State,
    context: RenderContext
  ): OverviewDetailScreen {
    return when (renderState) {
      Initializing -> {
        // Again, then entire `Initializing` state is a smell, which is most obvious from the
        // use of `Worker.from { Unit }`. A Worker doing no work and only shuttling the state
        // along is usually the sign you have an extraneous state that can be collapsed!
        // Don't try this at home.
        context.runningWorker(Worker.from { Unit }, "initializing") {
        isLoading.value = true
        action {
          isLoading.value = false
          state = Selected(NO_SELECTED_STANZA)
        }
      }
        OverviewDetailScreen(overviewRendering = BackStackScreen(BlankScreen))
      }
      else -> {
        val (stanzaIndex, currentStateIsLoading) = when (renderState) {
          is ComplexCall -> Pair(renderState.payload, true)
          is Selected -> Pair(renderState.stanzaIndex, false)
          Initializing -> throw IllegalStateException("No longer initializing.")
        }

        if (currentStateIsLoading) {
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
                state = Selected(currentState.payload)
              }
            }
          }
        }

        val previousStanzas: List<StanzaScreen> =
          if (stanzaIndex == NO_SELECTED_STANZA) emptyList()
          else renderProps.stanzas.subList(0, stanzaIndex)
            .mapIndexed { index, _ ->
              context.renderChild(StanzaWorkflow, Props(renderProps, index), "$index") {
                noAction()
              }
            }.map { originalStanzaScreen ->
              originalStanzaScreen.trace()
            }

        val visibleStanza =
          if (stanzaIndex == NO_SELECTED_STANZA) {
            null
          } else {
            context.renderChild(
              StanzaWorkflow, Props(renderProps, stanzaIndex), "$stanzaIndex"
            ) {
              when (it) {
                CloseStanzas -> ClearSelection(simulatedPerfConfig)
                ShowPreviousStanza -> SelectPrevious(simulatedPerfConfig)
                ShowNextStanza -> SelectNext(simulatedPerfConfig)
              }
            }.trace()
          }

        val stackedStanzas = visibleStanza?.let {
          (previousStanzas + visibleStanza).toBackStackScreen<Screen>()
        }

        val stanzaListOverview =
          context.renderChild(
            StanzaListWorkflow,
            renderProps
          ) { selected ->
            HandleStanzaListOutput(simulatedPerfConfig, selected)
          }
            .copy(selection = stanzaIndex)
            .trace()

        stackedStanzas
          ?.let {
            OverviewDetailScreen(
              overviewRendering = BackStackScreen(stanzaListOverview),
              detailRendering = it
            )
          } ?: OverviewDetailScreen(
          overviewRendering = BackStackScreen(stanzaListOverview),
          selectDefault = {
            context.actionSink.send(HandleStanzaListOutput(simulatedPerfConfig, 0))
          }
        )
      }
    }
  }

  override fun snapshotState(state: State): Snapshot? = null

  internal sealed class Action : WorkflowAction<Poem, State, ClosePoem>() {
    abstract val simulatedPerfConfig: SimulatedPerfConfig

    class ClearSelection(override val simulatedPerfConfig: SimulatedPerfConfig) : Action()
    class SelectPrevious(override val simulatedPerfConfig: SimulatedPerfConfig) : Action()
    class SelectNext(override val simulatedPerfConfig: SimulatedPerfConfig) : Action()
    class HandleStanzaListOutput(
      override val simulatedPerfConfig: SimulatedPerfConfig,
      val selection: Int
    ) : Action()

    class ExitPoem(override val simulatedPerfConfig: SimulatedPerfConfig) : Action()

    override fun Updater.apply() {
      val currentIndex: Int = when (val solidState = state) {
        is ComplexCall -> solidState.payload
        Initializing -> NO_SELECTED_STANZA
        is Selected -> solidState.stanzaIndex
      }
      when (this@Action) {
        is ClearSelection ->
          state =
            if (simulatedPerfConfig.isComplex) {
              ComplexCall(NO_SELECTED_STANZA)
            } else {
              Selected(
                NO_SELECTED_STANZA
              )
            }
        is SelectPrevious ->
          state =
            if (simulatedPerfConfig.isComplex) {
              ComplexCall(currentIndex - 1)
            } else {
              Selected(
                currentIndex - 1
              )
            }
        is SelectNext ->
          state =
            if (simulatedPerfConfig.isComplex) {
              ComplexCall(currentIndex + 1)
            } else {
              Selected(
                currentIndex + 1
              )
            }
        is HandleStanzaListOutput -> {
          if (selection == NO_SELECTED_STANZA) setOutput(ClosePoem)
          state = if (simulatedPerfConfig.isComplex) {
            ComplexCall(selection)
          } else {
            Selected(selection)
          }
        }
        is ExitPoem -> setOutput(ClosePoem)
      }
    }
  }
}

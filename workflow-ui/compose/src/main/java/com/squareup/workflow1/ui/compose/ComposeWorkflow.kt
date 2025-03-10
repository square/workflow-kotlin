package com.squareup.workflow1.ui.compose

import androidx.compose.runtime.Composable
import app.cash.molecule.RecompositionMode.Immediate
import app.cash.molecule.launchMolecule
import com.squareup.workflow1.SessionWorkflow
import com.squareup.workflow1.Snapshot
import com.squareup.workflow1.Worker
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowExperimentalApi
import com.squareup.workflow1.action
import com.squareup.workflow1.asWorker
import com.squareup.workflow1.runningWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop

/**
 * - Note that `RenderT` can be our old friends `Screen`, `Overlay`,
 *   `MarketStackScreen`, etc.
 *
 * - For `OutputT`, pass a `MutableStateFlow` as a prop and
 *   consume it as a worker. Perhaps we can provide some sugar
 *   for that, but I don't think it needs to be part of the
 *   fundamental machinery
 *
 * - Snapshots: TBD. [Easy enough to put a `SavedStateRegistry` in place.](https://docs.google.com/presentation/d/1JTN3PpFG27b06OqKEOdHXZSos8wHck3y9T4pRvwpir8/edit#slide=id.g149ba2e5ba1_0_204)
 *   Tricky bit is that Snapshot really wants you to read and
 *   write `ByteString`, with support provided for `Parcelable`.
 *   How do we get `SavedStateRegistry` to play nice with that?
 *   Maybe `SavedStateRegistryOwner` and `SavedStateController`
 *   are flexible enough. Not excited about this.
 */
public fun interface ComposeWorkflow<out RenderT> {
  @Composable public fun render(): RenderT
}

public fun interface ComposeWorkflowWithProps<in PropsT, out RenderT> {
  @Composable public fun render(propsT: PropsT): RenderT
}

@PublishedApi
internal data class State<RenderT>(
  val renderings: Worker<RenderT>,
  val latestRendering: RenderT
)

public inline fun <reified RenderT> ComposeWorkflow<RenderT>.asWorkflow(): Workflow<Unit, Nothing, RenderT> {
  @OptIn(WorkflowExperimentalApi::class)
  return object : SessionWorkflow<Unit, State<RenderT>, Nothing, RenderT>() {
    val rootPresenter = this@asWorkflow

    override fun initialState(
      props: Unit,
      snapshot: Snapshot?,
      workflowScope: CoroutineScope
    ): State<RenderT> {
      val stateFlow = workflowScope.launchMolecule(mode = Immediate) { rootPresenter.render() }
      val worker: Worker<RenderT> = stateFlow.drop(1).asWorker()
      return State(worker, stateFlow.value)
    }

    override fun render(
      renderProps: Unit,
      renderState: State<RenderT>,
      context: RenderContext
    ): RenderT {
      context.runningWorker(renderState.renderings) {
        action("nextRendering") {
          state = state.copy(latestRendering = it)
        }
      }
      return renderState.latestRendering
    }

    override fun snapshotState(state: State<RenderT>): Snapshot? = null
  }
}

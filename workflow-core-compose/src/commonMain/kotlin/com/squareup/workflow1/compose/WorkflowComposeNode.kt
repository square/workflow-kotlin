package com.squareup.workflow1.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.squareup.workflow1.IdCounter
import com.squareup.workflow1.TreeSnapshot
import com.squareup.workflow1.WorkflowExperimentalRuntime
import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession
import com.squareup.workflow1.WorkflowNode
import com.squareup.workflow1.WorkflowNodeId
import com.squareup.workflow1.WorkflowOutput
import kotlin.coroutines.CoroutineContext

/**
 * @see [WorkflowNode]. This version extends that to support Compose runtime optimizations.
 */
@WorkflowExperimentalRuntime
public class WorkflowComposeNode<PropsT, StateT, OutputT, RenderingT>(
  id: WorkflowNodeId,
  workflow: StatefulComposeWorkflow<PropsT, StateT, OutputT, RenderingT>,
  initialProps: PropsT,
  snapshot: TreeSnapshot?,
  baseContext: CoroutineContext,
  emitOutputToParent: (OutputT) -> Any? = { WorkflowOutput(it) },
  parent: WorkflowSession? = null,
  interceptor: ComposeWorkflowInterceptor = NoopComposeWorkflowInterceptor,
  idCounter: IdCounter? = null
) : WorkflowNode<PropsT, StateT, OutputT, RenderingT>(
  id,
  workflow,
  initialProps,
  snapshot,
  baseContext,
  emitOutputToParent,
  parent,
  interceptor,
  idCounter,
) {

  /**
   * Back [state] with a [MutableState] so the Compose runtime can track changes.
   */
  private lateinit var backingMutableState: MutableState<StateT>

  override var state: StateT
    get() {
      return backingMutableState.value
    }
    set(value) {
      if (!this::backingMutableState.isInitialized) {
        backingMutableState = mutableStateOf(value)
      }
      backingMutableState.value = value
    }

  override val subtreeManager: ComposeSubtreeManager<PropsT, StateT, OutputT> =
    ComposeSubtreeManager(
      snapshotCache = snapshot?.childTreeSnapshots,
      contextForChildren = coroutineContext,
      emitActionToParent = ::applyAction,
      workflowSession = this,
      interceptor = interceptor,
      idCounter = idCounter
    )

  /**
   * This returns Unit so that the Recomposer will consider this a separate Recompose scope that
   * can be independently recomposed.
   *
   * We pass in the MutableState<RenderingT?> directly rather than setRendering() to save Compose
   * having to memoize the lambda for such a frequent call.
   */
  @Suppress("UNCHECKED_CAST")
  @Composable
  public fun Rendering(
    workflow: StatefulComposeWorkflow<PropsT, *, OutputT, RenderingT>,
    input: PropsT,
    rendering: MutableState<RenderingT?>
  ) {
    RenderingWithStateType(
      workflow as StatefulComposeWorkflow<PropsT, StateT, OutputT, RenderingT>,
      input,
      rendering
    )
  }

  @Composable
  private fun RenderingWithStateType(
    workflow: StatefulComposeWorkflow<PropsT, StateT, OutputT, RenderingT>,
    props: PropsT,
    rendering: MutableState<RenderingT?>
  ) {
    UpdatePropsAndState(workflow, props)

    val (baseRenderContext, renderContext) = remember(
      state,
      props,
      workflow,
      rendering.value
    ) {
      // Use the RenderContext once. After rendering successfully it is frozen until new state.
      val base = RealComposeRenderContext(
        renderer = subtreeManager,
        sideEffectRunner = this,
        eventActionsChannel = eventActionsChannel
      )
      base to ComposeRenderContext(workflow = workflow, baseContext = base)
    }

    rendering.value = interceptor.asComposeWorkflowInterceptor().intercept(workflow, this)
      .Rendering(props, state, renderContext)

    SideEffect {
      baseRenderContext.freeze()
      commitAndUpdateScopes()
    }
  }

  @Composable
  private fun UpdatePropsAndState(
    workflow: StatefulComposeWorkflow<PropsT, StateT, OutputT, RenderingT>,
    newProps: PropsT
  ) {
    key(newProps) {
      if (newProps != lastProps) {
        state = interceptor
          .asComposeWorkflowInterceptor()
          .intercept(workflow, this@WorkflowComposeNode)
          .onPropsChanged(lastProps, newProps, state)
      }
    }
    SideEffect {
      lastProps = newProps
    }
  }
}

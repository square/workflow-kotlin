package com.squareup.workflow1.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
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
  override val workflow: StatefulComposeWorkflow<PropsT, StateT, OutputT, RenderingT>,
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

  override fun startSession() {
    context = RealComposeRenderContext(
      renderer = subtreeManager,
      sideEffectRunner = this,
      eventActionsChannel = eventActionsChannel
    )
    interceptor.onSessionStarted(workflowScope = this, session = this)
    state = interceptor
      .asComposeWorkflowInterceptor()
      .intercept(workflow = workflow, workflowSession = this)
      .initialState(initialProps, initialSnapshot?.workflowSnapshot)
  }

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

    val composableInterceptor = remember {
      interceptor.asComposeWorkflowInterceptor()
    }

    val interceptedComposableWorkflow = remember(workflow) {
      composableInterceptor.intercept(workflow, this)
    }

    UpdatePropsAndState(interceptedComposableWorkflow, props)

    val renderContext = remember(
      state,
      props,
      workflow,
      rendering.value
    ) {
      context.unfreeze()
      ComposeRenderContext(workflow = workflow, baseContext = context as RealComposeRenderContext)
    }

    rendering.value = interceptedComposableWorkflow.Rendering(props, state, renderContext)

    SideEffect {
      // Gets called on each recomposition - may already be frozen.
      context.unsafeFreeze()
      commitAndUpdateScopes()
    }
  }

  @Composable
  private fun UpdatePropsAndState(
    workflow: StatefulComposeWorkflow<PropsT, StateT, OutputT, RenderingT>,
    newProps: PropsT
  ) {
    remember(newProps) {
      if (newProps != lastProps) {
        state = workflow.onPropsChanged(lastProps, newProps, state)
      }
    }
    SideEffect {
      lastProps = newProps
    }
  }
}

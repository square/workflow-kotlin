package com.squareup.workflow1.internal.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import com.squareup.workflow1.ActionApplied
import com.squareup.workflow1.ActionProcessingResult
import com.squareup.workflow1.NoopWorkflowInterceptor
import com.squareup.workflow1.RenderContext
import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.Sink
import com.squareup.workflow1.StatefulWorkflow
import com.squareup.workflow1.TreeSnapshot
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.WorkflowExperimentalApi
import com.squareup.workflow1.WorkflowIdentifier
import com.squareup.workflow1.WorkflowInterceptor
import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession
import com.squareup.workflow1.WorkflowTracer
import com.squareup.workflow1.applyTo
import com.squareup.workflow1.compose.ComposeWorkflow
import com.squareup.workflow1.identifier
import com.squareup.workflow1.internal.IdCounter
import com.squareup.workflow1.internal.StatefulWorkflowNode
import com.squareup.workflow1.internal.WorkflowNodeId
import com.squareup.workflow1.internal.createId
import kotlinx.coroutines.selects.SelectBuilder
import kotlin.coroutines.CoroutineContext

/**
 * Entry point back into the Workflow runtime from a Compose runtime (i.e. a
 * [ComposeWorkflowNodeAdapter]). Does not delegate to [StatefulWorkflowNode] but instead implements
 * the same logic completely from scratch on top of Compose. Compose already implements all the
 * core behaviors of a workflow runtime, and re-using the compose runtime allows us to transition
 * back to [ComposeWorkflow]s seamlessly, re-using the same compose runtime instance.
 */
@OptIn(WorkflowExperimentalApi::class)
internal class TraditionalWorkflowAdapterChildNode<PropsT, OutputT, RenderingT>(
  override val id: WorkflowNodeId,
  workflow: Workflow<PropsT, OutputT, RenderingT>,
  initialProps: PropsT,
  contextForChildren: CoroutineContext,
  private val parentNode: ComposeWorkflowChildNode<*, *, *>?,
  override val parent: WorkflowSession?,
  private val snapshot: TreeSnapshot?,
  override val workflowTracer: WorkflowTracer?,
  override val runtimeConfig: RuntimeConfig,
  private val interceptor: WorkflowInterceptor = NoopWorkflowInterceptor,
  idCounter: IdCounter?,
  acceptChildActionResult: (ActionApplied<OutputT>) -> ActionProcessingResult,
) : ComposeChildNode<PropsT, OutputT, RenderingT>,
  WorkflowSession,
  Sink<WorkflowAction<PropsT, Any?, OutputT>>,
  RememberObserver {

  override val identifier: WorkflowIdentifier = workflow.identifier

  /** Always "" since workflows rendered from composition never have explicit keys. */
  override val renderKey: String
    get() = ""

  override val sessionId: Long = idCounter.createId()

  @Suppress("UNCHECKED_CAST")
  private val statefulWorkflow =
    workflow.asStatefulWorkflow() as StatefulWorkflow<PropsT, Any?, OutputT, RenderingT>

  private var lastProps by mutableStateOf(initialProps)

  private var state by mutableStateOf(
    interceptor.onInitialState(
      props = initialProps,
      snapshot = null, // TODO
      workflowScope = TODO(),
      session = this,
      proceed = { innerProps, innerSnapshot, innerScope ->
        statefulWorkflow.initialState(innerProps, innerSnapshot, innerScope)
      }
    )
  )
  @Composable
  override fun produceRendering(
    workflow: Workflow<PropsT, OutputT, RenderingT>,
    props: PropsT
  ): RenderingT = Trapdoor.open { trapdoor ->
    // Need to track changes of the lastProps state object since we only change it ourselves here.
    Snapshot.withoutReadObservation {
      if (props != lastProps) {
        state = interceptor.onPropsChanged(
          old = lastProps,
          new = props,
          state = state,
          session = this,
          proceed = statefulWorkflow::onPropsChanged
        )
        lastProps = props
      }
    }

    val baseContext = TrapdoorRenderContext(
      runtimeConfig = runtimeConfig,
      workflowTracer = workflowTracer,
      actionSink = this,
      handleChildAction = ::send,
      trapdoor = trapdoor,
    )

    // TODO ignore state reads inside here? That's a feature we are considering but is separate from
    //  this work, so maybe put it behind a runtime config flag.
    interceptor.onRender(
      renderProps = props,
      // This state read is crucial: it is what tells Compose to restart this produceRendering
      // when an action sets the state.
      renderState = state,
      context = baseContext,
      session = this,
      proceed = { innerProps, innerState, contextInterceptor ->
        statefulWorkflow.render(
          renderProps = innerProps,
          renderState = innerState,
          context = RenderContext(baseContext)
        )
      }
    )
  }

  override fun send(value: WorkflowAction<PropsT, Any?, OutputT>) {
    // TODO do we need to send this whole action into a channel to trigger a traditional action
    //  cascade when running under a traditional runtime?
    val (newState, applied) = value.applyTo(lastProps, state)
    state = newState
    applied.output?.value?.let { TODO("send output to a channel") }
  }

  override fun snapshot(): TreeSnapshot = workflowNode.snapshot()

  override fun registerTreeActionSelectors(selector: SelectBuilder<ActionProcessingResult>) {
    workflowNode.registerTreeActionSelectors(selector)
  }

  override fun applyNextAvailableTreeAction(skipDirtyNodes: Boolean): ActionProcessingResult =
    workflowNode.applyNextAvailableTreeAction(skipDirtyNodes)

  /**
   * Track child nodes for snapshotting.
   * NOTE: While the effect will run after composition, it will run as part of the compose
   * frame, so the child will be registered before ComposeWorkflowNodeAdapter's render method
   * returns.
   */
  override fun onRemembered() {
    parentNode?.addChildNode(this)
  }

  override fun onForgotten() {
    parentNode?.removeChildNode(this)
  }

  override fun onAbandoned() = Unit
}

package com.squareup.workflow1.internal.compose

import androidx.compose.runtime.Composer
import com.squareup.workflow1.ActionApplied
import com.squareup.workflow1.ActionProcessingResult
import com.squareup.workflow1.NoopWorkflowInterceptor
import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.RuntimeConfigOptions
import com.squareup.workflow1.TreeSnapshot
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowInterceptor
import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession
import com.squareup.workflow1.WorkflowTracer
import com.squareup.workflow1.internal.IdCounter
import com.squareup.workflow1.internal.WorkflowNode
import com.squareup.workflow1.internal.WorkflowNodeId
import com.squareup.workflow1.internal.convertComposableLambda
import com.squareup.workflow1.trace
import kotlinx.coroutines.selects.SelectBuilder
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

internal class ComposerContextElement(
  val composer: Composer
) : AbstractCoroutineContextElement(Key) {
  companion object Key : CoroutineContext.Key<ComposerContextElement>
}

/**
 * Re-entry point into the Compose runtime from a TraditionalWorkflowNode running inside a
 * [ComposeWorkflowNodeAdapter].
 *
 * The runtime is re-used by manually calling methods on its [Composer] to get it into the right
 * state, and then calling the [ComposeChildNode.produceRendering] composable raw.
 */
internal class ReuseComposeWorkflowNodeAdapter<PropsT, OutputT, RenderingT>(
  id: WorkflowNodeId,
  initialProps: PropsT,
  snapshot: TreeSnapshot?,
  baseContext: CoroutineContext,
  private val composer: Composer,
  // Providing default value so we don't need to specify in test.
  runtimeConfig: RuntimeConfig = RuntimeConfigOptions.DEFAULT_CONFIG,
  private val workflowTracer: WorkflowTracer? = null,
  emitAppliedActionToParent: (ActionApplied<OutputT>) -> ActionProcessingResult = { it },
  parent: WorkflowSession? = null,
  interceptor: WorkflowInterceptor = NoopWorkflowInterceptor,
  idCounter: IdCounter? = null
) : WorkflowNode<PropsT, OutputT, RenderingT>(
  id = id,
  baseContext = baseContext,
  interceptor = interceptor,
  emitAppliedActionToParent = emitAppliedActionToParent,
) {

  private val childNode = workflowTracer.trace("ReuseComposeWorkflowAdapterInstantiateChildNode") {
    ComposeWorkflowChildNode<PropsT, OutputT, RenderingT>(
      id = id,
      initialProps = initialProps,
      snapshot = snapshot,
      baseContext = scope.coroutineContext,
      parentNode = null,
      parent = parent,
      workflowTracer = workflowTracer,
      runtimeConfig = runtimeConfig,
      interceptor = interceptor,
      idCounter = idCounter,
      emitAppliedActionToParent = emitAppliedActionToParent,
    )
  }

  override val session: WorkflowSession
    get() = childNode

  override fun render(
    workflow: Workflow<PropsT, OutputT, RenderingT>,
    input: PropsT
  ): RenderingT {
    return workflowTracer.trace("ReuseComposeWorkflowAdapterRender") {
      // key doesn't really matter.
      // dataKey is more important: This is what ensures that if the order of render calls changes
      // due to the traditional workflows between here and the parent composable, the state below
      // here is moved around with its workflow.
      composer.startMovableGroup(key = 42, dataKey = "TODO: calculate key from workflow key path")
      @Suppress("UNCHECKED_CAST")
      val rendering = invokeProduceRendering(
        childNode as ComposeWorkflowChildNode<Any?, Any?, Any?>,
        workflow as Workflow<Any?, Any?, Any?>,
        input,
        composer,
        0 // change value, 0 is always safe.
      ) as RenderingT
      composer.endMovableGroup()
      rendering
    }
  }

  override fun snapshot(): TreeSnapshot = childNode.snapshot()

  override fun registerTreeActionSelectors(selector: SelectBuilder<ActionProcessingResult>) {
    // We must register for child actions before frame requests, because selection is
    // strongly-ordered: If multiple subjects become available simultaneously, then the one whose
    // receiver was registered first will fire first. We always want to handle outputs first because
    // the output handler will implicitly also handle frame requests. If a frame request happens at
    // the same time or the output handler enqueues a frame request, then the subsequent render pass
    // will dequeue the frame request itself before the next call to register.
    childNode.registerTreeActionSelectors(selector)
  }

  override fun applyNextAvailableTreeAction(skipDirtyNodes: Boolean): ActionProcessingResult =
    childNode.applyNextAvailableTreeAction(skipDirtyNodes)
}

private val invokeProduceRendering: (
  ComposeWorkflowChildNode<Any?, Any?, Any?>,
  Workflow<Any?, Any?, Any?>,
  Any?,
  Composer,
  Int
) -> Any? =
  convertComposableLambda { node, workflow, props -> node.produceRendering(workflow, props) }

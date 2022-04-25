package com.squareup.workflow1.internal

import com.squareup.workflow1.RenderingAndSnapshot
import com.squareup.workflow1.TreeSnapshot
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowInterceptor
import com.squareup.workflow1.WorkflowOutput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.selects.select

@OptIn(ExperimentalCoroutinesApi::class)
internal class WorkflowRunner<PropsT, OutputT, RenderingT>(
  scope: CoroutineScope,
  protoWorkflow: Workflow<PropsT, OutputT, RenderingT>,
  props: StateFlow<PropsT>,
  snapshot: TreeSnapshot?,
  interceptor: WorkflowInterceptor
) {
  private val workflow = protoWorkflow.asStatefulWorkflow()
  private val idCounter = IdCounter()
  private var currentProps: PropsT = props.value

  // Props is a StateFlow, it will immediately produce an item. Without additional handling, the
  // first call to nextOutput will see that new props value and trigger another render pass, which
  // means that every workflow runtime would actually start with two render passes. To avoid that,
  // we skip that first value. We can't just to drop(1) however, since there's a possibility that
  // the props flow emitted a new value in the time between the above initialization of currentProps
  // and the produceIn coroutine starting to collect from the props. Using dropWhile ensures that
  // we don't miss the new props in that case, but that we don't trigger the double render pass
  // unnecessarily. Note that currentProps is only set by nextOutput receiving from this channel,
  // which can't happen until the dropWhile predicate evaluates to false, after which the dropWhile
  // predicate will never be invoked again, so it's fine to read the mutable value here.
  @OptIn(FlowPreview::class)
  private val propsChannel = props.dropWhile { it == currentProps }
    .produceIn(scope)

  private val rootNode = WorkflowNode(
    id = workflow.id(),
    workflow = workflow,
    initialProps = currentProps,
    snapshot = snapshot,
    baseContext = scope.coroutineContext,
    interceptor = interceptor,
    idCounter = idCounter
  )

  /**
   * Perform a render pass and a snapshot pass and return the results.
   *
   * This method must be called before the first call to [nextOutput], and must be called again
   * between every subsequent call to [nextOutput].
   */
  fun nextRendering(): RenderingAndSnapshot<RenderingT> {
    val rendering = rootNode.render(workflow, currentProps)
    val snapshot = rootNode.snapshot(workflow)
    return RenderingAndSnapshot(rendering, snapshot)
  }

  // Tick _might_ return an output, but if it returns null, it means the state or a child
  // probably changed, so we should re-render/snapshot and emit again.
  suspend fun nextOutput(): WorkflowOutput<OutputT>? = select {
    // Stop trying to read from the inputs channel after it's closed.
    if (!propsChannel.isClosedForReceive) {
      propsChannel.onReceiveCatching { channelResult ->
        channelResult.exceptionOrNull()?.let { throw it }
        channelResult.getOrNull()?.let { newProps ->
          if (currentProps != newProps) {
            currentProps = newProps
          }
        }
        // Return null to tell the caller to do another render pass, but not emit an output.
        return@onReceiveCatching null
      }
    }

    // Tick the workflow tree.
    rootNode.tick(this)
  }

  fun cancelRuntime(cause: CancellationException? = null) {
    rootNode.cancel(cause)
  }
}

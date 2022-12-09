package com.squareup.workflow1.internal

import com.squareup.workflow1.ActionProcessingResult
import com.squareup.workflow1.ActionsExhausted
import com.squareup.workflow1.PropsUpdated
import com.squareup.workflow1.RenderingAndSnapshot
import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.RuntimeConfig.ConflateStaleRenderings
import com.squareup.workflow1.TreeSnapshot
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowExperimentalRuntime
import com.squareup.workflow1.WorkflowInterceptor
import kotlinx.coroutines.CancellationException
import com.squareup.workflow1.internal.tracing.trace
import com.squareup.workflow1.internal.tracing.traced
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.selects.SelectBuilder
import kotlinx.coroutines.selects.select

@OptIn(ExperimentalCoroutinesApi::class)
internal class WorkflowRunner<PropsT, OutputT, RenderingT>(
  scope: CoroutineScope,
  protoWorkflow: Workflow<PropsT, OutputT, RenderingT>,
  props: StateFlow<PropsT>,
  snapshot: TreeSnapshot?,
  interceptor: WorkflowInterceptor,
  private val runtimeConfig: RuntimeConfig
) {
  private val workflow = protoWorkflow.asStatefulWorkflow()
  private val idCounter = IdCounter()
  private var currentProps: PropsT = props.value

  // Props is a StateFlow, it will immediately produce an item. Without additional handling, the
  // first call to processActions will see that new props value and trigger another render pass,
  // which means that every workflow runtime would actually start with two render passes.
  // To avoid that, we skip that first value. We can't just to drop(1) however, since there's a
  // possibility that the props flow emitted a new value in the time between the above
  // initialization of currentProps and the produceIn coroutine starting to collect from the props.
  // Using dropWhile ensures that we don't miss the new props in that case, but that we don't
  // trigger the double render pass unnecessarily.
  // Note that currentProps is only set by processActions receiving from this channel,
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
   * This method must be called before the first call to [processAction], and must be called again
   * between every subsequent call to [processAction].
   */
  fun nextRendering(): RenderingAndSnapshot<RenderingT> {
    val rendering = rootNode.render(workflow, currentProps)
    val snapshot = rootNode.snapshot(workflow)
    return RenderingAndSnapshot(rendering, snapshot)
  }

  /**
   * Process the first action from anywhere in the Workflow tree, or process the updated props.
   *
   * [select] is used which suspends on multiple coroutines, executing the first to be scheduled and
   * resume (breaking ties with order of declaration). Guarantees only continuing on the winning
   * coroutine and no others.
   */
  @OptIn(WorkflowExperimentalRuntime::class)
  suspend fun processAction(waitForAnAction: Boolean = true): ActionProcessingResult? {

    return traced(
      tags = listOf(WorkflowRunner::class),
      args = listOf("waitForAction: $waitForAnAction")
    ) {
      // If waitForAction is true we block and wait until there is an action to process.
      select {
        onPropsUpdated()
        // Have the workflow tree build the select to wait for an event/output from Worker.
        val empty = rootNode.tick(this)
        if (!waitForAnAction && runtimeConfig == ConflateStaleRenderings && empty) {
          // With the ConflateStaleRenderings if there are no queued actions and we are not
          // waiting for one, then return ActionsExhausted and pass the rendering on.
          onTimeout(0) {
            // This will select synchronously since time is 0.
            ActionsExhausted
          }
        }
      }
    }
  }

  private fun SelectBuilder<ActionProcessingResult?>.onPropsUpdated() {
    // Stop trying to read from the inputs channel after it's closed.
    if (!propsChannel.isClosedForReceive) {
      propsChannel.onReceiveCatching { channelResult ->
        channelResult.exceptionOrNull()?.let { throw it }
        channelResult.getOrNull()?.let { newProps ->
          if (currentProps != newProps) {
            currentProps = newProps
          }
        }
        // Return PropsUpdated to tell the caller to do another render pass, but not emit an output.
        return@onReceiveCatching PropsUpdated
      }
    }
  }

  fun cancelRuntime(cause: kotlinx.coroutines.CancellationException? = null) {
    rootNode.cancel(cause)
  }
}

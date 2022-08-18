package com.squareup.workflow1.internal

import com.squareup.workflow1.ActionProcessingResult
import com.squareup.workflow1.ActionsExhausted
import com.squareup.workflow1.PropsUpdated
import com.squareup.workflow1.RenderingAndSnapshot
import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.RuntimeConfig.RenderPassPerFrame
import com.squareup.workflow1.RuntimeConfig.RenderingPerFrame
import com.squareup.workflow1.TreeSnapshot
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowExperimentalRuntime
import com.squareup.workflow1.WorkflowInterceptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
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
   * This method must be called before the first call to [processActions]. When it is called
   * again will depend on the [RuntimeConfig].
   */
  fun nextRendering(): RenderingAndSnapshot<RenderingT> {
    val rendering = rootNode.render(workflow, currentProps)
    val snapshot = rootNode.snapshot(workflow)
    return RenderingAndSnapshot(rendering, snapshot)
  }

  /**
   * Process the actions queued in the channels of any Workflow in the tree. 'Processing actions'
   * means handling an event from the UI or from a Worker that will cause state to change.
   *
   * How many actions we process before returning (to go and render()) depends on the
   * [RuntimeConfig].
   *
   * @param waitForAction - whether or not we are suspending until an action is received or only
   * processing actions that are already readily available. This is only used by the
   * [RenderingPerFrame] runtime.
   */
  @OptIn(WorkflowExperimentalRuntime::class)
  suspend fun processActions(waitForAction: Boolean = true): ActionProcessingResult<OutputT>? {
    // Wait until an action can be applied or the props are updated.
    var processingResult: ActionProcessingResult<OutputT>? = select {
      onPropsUpdated()
      // Have the workflow tree build the select to wait for an event/output from Worker.
      rootNode.tick(this)
      if (!waitForAction && runtimeConfig is RenderingPerFrame) {
        // If we are not waiting for an action to happen, then only wait for [actionWaitMs].
        onTimeout(runtimeConfig.actionWaitMs) {
          ActionsExhausted()
        }
      }
    }

    if (runtimeConfig is RenderPassPerFrame) {
      val frameStartTime = currentTimeMillis()

      var frameTimeLeft = runtimeConfig.frameTimeoutMs
      // Then we can process any more actions queued up until the frame timeout when we should
      // pass off something to the UI! If processingResult != null, that means we have an output
      // for the external system, so we must go back and render then as well. If the result
      // is ActionsExhausted, there are no actions ready to process.
      while (
        processingResult == null &&
        frameTimeLeft > 0
      ) {
        processingResult = select {
          // N.B. that select clauses use declaration order to break ties, so the timeout goes first.
          onTimeout(timeMillis = runtimeConfig.actionWaitMs) {
            ActionsExhausted()
          }
          onPropsUpdated()
          rootNode.tick(this)
        }
        val loopTime = currentTimeMillis()
        frameTimeLeft = (frameStartTime + runtimeConfig.frameTimeoutMs) - loopTime
      }
    }

    return processingResult
  }

  private fun SelectBuilder<ActionProcessingResult<OutputT>?>.onPropsUpdated() {
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
        return@onReceiveCatching PropsUpdated<OutputT>()
      }
    }
  }

  fun cancelRuntime(cause: CancellationException? = null) {
    rootNode.cancel(cause)
  }
}

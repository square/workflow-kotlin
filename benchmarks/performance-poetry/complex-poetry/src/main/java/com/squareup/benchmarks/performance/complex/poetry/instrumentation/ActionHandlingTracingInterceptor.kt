package com.squareup.benchmarks.performance.complex.poetry.instrumentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.tracing.trace
import com.squareup.workflow1.BaseRenderContext
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.WorkflowInterceptor
import com.squareup.workflow1.WorkflowInterceptor.RenderContextInterceptor
import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession
import com.squareup.workflow1.compose.BaseComposeRenderContext
import com.squareup.workflow1.compose.ComposeWorkflowInterceptor
import com.squareup.workflow1.compose.ComposeWorkflowInterceptor.ComposeRenderContextInterceptor

/**
 * We use this [WorkflowInterceptor] to add in tracing for the main thread messages that handle
 * particular events.
 *
 * If you want to trace how long Workflow takes to process a UI event, then
 * annotate the [RenderContext.eventHandler] name argument with [keyForTrace]. That will cause
 * this interceptor to pick it up when the action is sent into the sink and trace that main thread
 * message.
 *
 * If you want to trace how long Workflow takes to process the result of a [Worker], then
 * annotate the [Worker] using [TraceableWorker] which will set it up with a key such that when
 * the action for the result is sent to the sink the main thread message will be traced.
 */
class ActionHandlingTracingInterceptor : ComposeWorkflowInterceptor, Resettable {

  private val actionCounts: MutableMap<String, Int> = mutableMapOf()

  class EventHandlingTracingRenderContextInterceptor<P, S, O>(
    private val actionCounts: MutableMap<String, Int>
  ) : ComposeRenderContextInterceptor<P, S, O> {
    override fun onActionSent(
      action: WorkflowAction<P, S, O>,
      proceed: (WorkflowAction<P, S, O>) -> Unit
    ) {
      // This will capture the entire main thread frame where the event is handled.
      val tag = action.actionName
      if (tag.isNotEmpty()) {
        trace(tag) {
          proceed(action)
        }
      } else {
        proceed(action)
      }
    }

    private val <P, S, O> WorkflowAction<P, S, O>.actionName: String
      get() {
        var actionTag = extractTraceTag(toString())
        if (actionTag.isNotEmpty()) {
          synchronized(actionCounts) {
            val count = actionCounts.getOrDefault(actionTag, 0)
            actionCounts[actionTag] = count + 1
            actionTag = "$actionTag-${count.toString().padStart(3, '0')}"
          }
        }
        return actionTag
      }
  }

  override fun <P, S, O, R> onRender(
    renderProps: P,
    renderState: S,
    context: BaseRenderContext<P, S, O>,
    proceed: (P, S, RenderContextInterceptor<P, S, O>?) -> R,
    session: WorkflowSession
  ): R {
    return proceed(
      renderProps,
      renderState,
      EventHandlingTracingRenderContextInterceptor(actionCounts)
    )
  }

  @Composable
  override fun <P, S, O, R> Rendering(
    renderProps: P,
    renderState: S,
    context: BaseComposeRenderContext<P, S, O>,
    session: WorkflowSession,
    proceed: @Composable (P, S, ComposeRenderContextInterceptor<P, S, O>?) -> R
  ): R {
    val rci = remember {
      EventHandlingTracingRenderContextInterceptor<P, S, O>(actionCounts)
    }
    return proceed(
      renderProps,
      renderState,
      rci
    )
  }

  override fun reset() {
    actionCounts.clear()
  }

  companion object {
    private const val TRACE_KEY = "trace_key"
    fun keyForTrace(tag: String): String = "$TRACE_KEY:$tag:"
    private fun extractTraceTag(fullTag: String): String =
      fullTag.substringAfter("$TRACE_KEY:", "").substringBefore(':', "")
  }
}

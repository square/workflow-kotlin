package com.squareup.workflow1.testing

import com.squareup.workflow1.BaseRenderContext
import com.squareup.workflow1.ExperimentalWorkflowApi
import com.squareup.workflow1.RenderContext
import com.squareup.workflow1.Sink
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.WorkflowInterceptor
import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession
import kotlinx.coroutines.CoroutineScope
import java.util.LinkedList

/**
 * Intercepts the render pass of the root workflow and runs it twice to ensure that well-written
 * unit tests catch side effects being incorrectly performed directly in the render method.
 *
 * The first render pass is the real one, the second one is a no-op and child workflow renderings
 * will be played back, in order, to their renderChild calls.
 */
@OptIn(ExperimentalWorkflowApi::class)
public object RenderIdempotencyChecker : WorkflowInterceptor {
  override fun <P, S, O, R> onRender(
    props: P,
    state: S,
    context: BaseRenderContext<P, S, O>,
    proceed: (P, S, BaseRenderContext<P, S, O>) -> R,
    session: WorkflowSession
  ): R {
    val recordingContext = RecordingRenderContext(context)
    proceed(props, state, recordingContext)

    // The second render pass should not actually invoke any real behavior.
    recordingContext.startReplaying()
    return proceed(props, state, recordingContext)
        .also {
          // After the verification render pass, any calls to the context _should_ be passed
          // through, to allow the real context to run its usual post-render behavior.
          recordingContext.stopReplaying()
        }
  }
}

/**
 * A [RenderContext] that can record the result of rendering children over a render pass, and then
 * play them back over a second render pass that doesn't actually perform any actions.
 */
private class RecordingRenderContext<PropsT, StateT, OutputT>(
  private val delegate: BaseRenderContext<PropsT, StateT, OutputT>
) : BaseRenderContext<PropsT, StateT, OutputT> {

  private var replaying = false

  fun startReplaying() {
    check(!replaying) { "Expected not to be replaying." }
    replaying = true
  }

  fun stopReplaying() {
    check(replaying) { "Expected to be replaying." }
    replaying = false
  }

  override val actionSink: Sink<WorkflowAction<PropsT, StateT, OutputT>> = Sink {
    if (!replaying) {
      delegate.actionSink.send(it)
    } // Else noop
  }

  private val childRenderings = LinkedList<Any?>()

  override fun <ChildPropsT, ChildOutputT, ChildRenderingT> renderChild(
    child: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
    props: ChildPropsT,
    key: String,
    handler: (ChildOutputT) -> WorkflowAction<PropsT, StateT, OutputT>
  ): ChildRenderingT = if (!replaying) {
    delegate.renderChild(child, props, key, handler)
        .also { childRenderings.addFirst(it) }
  } else {
    @Suppress("UNCHECKED_CAST")
    childRenderings.removeLast() as ChildRenderingT
  }

  override fun runningSideEffect(
    key: String,
    sideEffect: suspend CoroutineScope.() -> Unit
  ) {
    if (!replaying) {
      delegate.runningSideEffect(key, sideEffect)
    }
    // Else noop.
  }
}

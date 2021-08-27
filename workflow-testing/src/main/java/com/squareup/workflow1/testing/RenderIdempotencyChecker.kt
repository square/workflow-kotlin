package com.squareup.workflow1.testing

import com.squareup.workflow1.BaseRenderContext
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.WorkflowInterceptor
import com.squareup.workflow1.WorkflowInterceptor.RenderContextInterceptor
import com.squareup.workflow1.WorkflowInterceptor.WorkflowSession
import java.util.LinkedList

/**
 * Intercepts the render pass of the root workflow and runs it twice to ensure that well-written
 * unit tests catch side effects being incorrectly performed directly in the render method.
 *
 * The first render pass is the real one, the second one is a no-op and child workflow renderings
 * will be played back, in order, to their renderChild calls.
 */
public object RenderIdempotencyChecker : WorkflowInterceptor {
  override fun <P, S, O, R> onRender(
    renderProps: P,
    renderState: S,
    context: BaseRenderContext<P, S, O>,
    proceed: (P, S, RenderContextInterceptor<P, S, O>?) -> R,
    session: WorkflowSession
  ): R {
    val recordingContext = RecordingContextInterceptor<P, S, O>()
    proceed(renderProps, renderState, recordingContext)

    // The second render pass should not actually invoke any real behavior.
    recordingContext.startReplaying()
    return proceed(renderProps, renderState, recordingContext)
        .also {
          // After the verification render pass, any calls to the context _should_ be passed
          // through, to allow the real context to run its usual post-render behavior.
          recordingContext.stopReplaying()
        }
  }
}

/**
 * A [RenderContextInterceptor] that can record the result of rendering children over a render pass,
 * and then play them back over a second render pass that doesn't actually perform any actions.
 */
private class RecordingContextInterceptor<PropsT, StateT, OutputT> :
  RenderContextInterceptor<PropsT, StateT, OutputT> {

  private var replaying = false

  fun startReplaying() {
    check(!replaying) { "Expected not to be replaying." }
    replaying = true
  }

  fun stopReplaying() {
    check(replaying) { "Expected to be replaying." }
    replaying = false
  }

  override fun onActionSent(
    action: WorkflowAction<PropsT, StateT, OutputT>,
    proceed: (WorkflowAction<PropsT, StateT, OutputT>) -> Unit
  ) {
    if (!replaying) {
      proceed(action)
    } // Else noop
  }

  private val childRenderings = LinkedList<Any?>()

  override fun <CP, CO, CR> onRenderChild(
    child: Workflow<CP, CO, CR>,
    childProps: CP,
    key: String,
    handler: (CO) -> WorkflowAction<PropsT, StateT, OutputT>,
    proceed: (
      child: Workflow<CP, CO, CR>,
      props: CP,
      key: String,
      handler: (CO) -> WorkflowAction<PropsT, StateT, OutputT>
    ) -> CR
  ): CR = if (!replaying) {
    proceed(child, childProps, key, handler)
      .also { childRenderings.addFirst(it) }
  } else {
    @Suppress("UNCHECKED_CAST")
    childRenderings.removeLast() as CR
  }

  override fun onRunningSideEffect(
    key: String,
    sideEffect: suspend () -> Unit,
    proceed: (key: String, sideEffect: suspend () -> Unit) -> Unit
  ) {
    if (!replaying) {
      proceed(key, sideEffect)
    }
    // Else noop.
  }
}

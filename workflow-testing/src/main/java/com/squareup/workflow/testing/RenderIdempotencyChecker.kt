/*
 * Copyright 2020 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.workflow.testing

import com.squareup.workflow.ExperimentalWorkflowApi
import com.squareup.workflow.RenderContext
import com.squareup.workflow.Sink
import com.squareup.workflow.Worker
import com.squareup.workflow.Workflow
import com.squareup.workflow.WorkflowAction
import com.squareup.workflow.WorkflowInterceptor
import com.squareup.workflow.WorkflowInterceptor.WorkflowSession
import java.util.LinkedList

/**
 * Intercepts the render pass of the root workflow and runs it twice to ensure that well-written
 * unit tests catch side effects being incorrectly performed directly in the render method.
 *
 * The first render pass is the real one, the second one is a no-op and child workflow renderings
 * will be played back, in order, to their renderChild calls.
 */
@OptIn(ExperimentalWorkflowApi::class)
object RenderIdempotencyChecker : WorkflowInterceptor {
  override fun <P, S, O, R> onRender(
    props: P,
    state: S,
    context: RenderContext<S, O>,
    proceed: (P, S, RenderContext<S, O>) -> R,
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
private class RecordingRenderContext<StateT, OutputT>(
  private val delegate: RenderContext<StateT, OutputT>
) : RenderContext<StateT, OutputT> {

  private var replaying = false

  fun startReplaying() {
    check(!replaying) { "Expected not to be replaying." }
    replaying = true
  }

  fun stopReplaying() {
    check(replaying) { "Expected to be replaying." }
    replaying = false
  }

  override val actionSink: Sink<WorkflowAction<StateT, OutputT>> =
    object : Sink<WorkflowAction<StateT, OutputT>> {
      override fun send(value: WorkflowAction<StateT, OutputT>) {
        if (!replaying) {
          delegate.actionSink.send(value)
        } // Else noop
      }
    }

  private val childRenderings = LinkedList<Any?>()

  override fun <ChildPropsT, ChildOutputT, ChildRenderingT> renderChild(
    child: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
    props: ChildPropsT,
    key: String,
    handler: (ChildOutputT) -> WorkflowAction<StateT, OutputT>
  ): ChildRenderingT = if (!replaying) {
    delegate.renderChild(child, props, key, handler)
        .also { childRenderings.addFirst(it) }
  } else {
    @Suppress("UNCHECKED_CAST")
    childRenderings.removeLast() as ChildRenderingT
  }

  override fun <T> runningWorker(
    worker: Worker<T>,
    key: String,
    handler: (T) -> WorkflowAction<StateT, OutputT>
  ) {
    if (!replaying) {
      delegate.runningWorker(worker, key, handler)
    }
    // Else noop.
  }

  override fun runningSideEffect(
    key: String,
    sideEffect: suspend () -> Unit
  ) {
    if (!replaying) {
      delegate.runningSideEffect(key, sideEffect)
    }
    // Else noop.
  }
}

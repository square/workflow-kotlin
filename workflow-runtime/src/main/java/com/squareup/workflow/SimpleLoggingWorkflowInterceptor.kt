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
package com.squareup.workflow

import com.squareup.workflow.WorkflowInterceptor.WorkflowSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

/**
 * A [WorkflowInterceptor] that just prints all method calls using [log].
 */
@OptIn(ExperimentalWorkflowApi::class)
open class SimpleLoggingWorkflowInterceptor : WorkflowInterceptor {
  override fun onSessionStarted(
    workflowScope: CoroutineScope,
    session: WorkflowSession
  ) {
    logBegin("onInstanceStarted($workflowScope, $session)")
    workflowScope.coroutineContext[Job]!!.invokeOnCompletion {
      logEnd("onInstanceStarted($session)")
    }
  }

  override fun <P, S> onInitialState(
    props: P,
    snapshot: Snapshot?,
    proceed: (P, Snapshot?) -> S,
    session: WorkflowSession
  ): S = logMethod("onInitialState", props, snapshot, session) {
    proceed(props, snapshot)
  }

  override fun <P, S> onPropsChanged(
    old: P,
    new: P,
    state: S,
    proceed: (P, P, S) -> S,
    session: WorkflowSession
  ): S = logMethod("onPropsChanged", old, new, state, session) {
    proceed(old, new, state)
  }

  override fun <P, S, O, R> onRender(
    props: P,
    state: S,
    context: RenderContext<P, S, O>,
    proceed: (P, S, RenderContext<P, S, O>) -> R,
    session: WorkflowSession
  ): R = logMethod("onRender", props, state, session) {
    proceed(props, state, context)
  }

  override fun <S> onSnapshotState(
    state: S,
    proceed: (S) -> Snapshot?,
    session: WorkflowSession
  ): Snapshot? = logMethod("onSnapshotState", state, session) {
    proceed(state)
  }

  private inline fun <T> logMethod(
    name: String,
    vararg args: Any?,
    block: () -> T
  ): T {
    val text = "$name(${args.joinToString()})"
    logBegin(text)
    return block().also {
      logEnd("$text = $it")
    }
  }

  /**
   * Called with descriptions of every event. Default implementation just calls [log].
   */
  protected open fun logBegin(text: String) {
    log("START| $text")
  }

  /**
   * Called with descriptions of every event. Default implementation just calls [log].
   */
  protected open fun logEnd(text: String) {
    log("  END| $text")
  }

  /**
   * Called by [logBegin] and [logEnd] to display a log message.
   */
  protected open fun log(text: String) {
    println(text)
  }
}

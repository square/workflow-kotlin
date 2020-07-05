/*
 * Copyright 2019 Square Inc.
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
@file:Suppress("DEPRECATION")

package com.squareup.workflow1.internal

import com.squareup.workflow1.BaseRenderContext
import com.squareup.workflow1.Sink
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowAction
import kotlinx.coroutines.channels.SendChannel

internal class RealRenderContext<out PropsT, StateT, OutputT>(
  private val renderer: Renderer<PropsT, StateT, OutputT>,
  private val sideEffectRunner: SideEffectRunner,
  private val eventActionsChannel: SendChannel<WorkflowAction<PropsT, StateT, OutputT>>
) : BaseRenderContext<PropsT, StateT, OutputT>, Sink<WorkflowAction<PropsT, StateT, OutputT>> {

  interface Renderer<PropsT, StateT, OutputT> {
    fun <ChildPropsT, ChildOutputT, ChildRenderingT> render(
      child: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
      props: ChildPropsT,
      key: String,
      handler: (ChildOutputT) -> WorkflowAction<PropsT, StateT, OutputT>
    ): ChildRenderingT
  }

  interface SideEffectRunner {
    fun runningSideEffect(
      key: String,
      sideEffect: suspend () -> Unit
    )
  }

  /**
   * False during the current render call, set to true once this node is finished rendering.
   *
   * Used to:
   *  - prevent modifications to this object after [freeze] is called.
   *  - prevent sending to sinks before render returns.
   */
  private var frozen = false

  override val actionSink: Sink<WorkflowAction<PropsT, StateT, OutputT>> get() = this

  override fun send(value: WorkflowAction<PropsT, StateT, OutputT>) {
    if (!frozen) {
      throw UnsupportedOperationException(
          "Expected sink to not be sent to until after the render pass. Received action: $value"
      )
    }
    eventActionsChannel.offer(value)
  }

  override fun <ChildPropsT, ChildOutputT, ChildRenderingT> renderChild(
    child: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
    props: ChildPropsT,
    key: String,
    handler: (ChildOutputT) -> WorkflowAction<PropsT, StateT, OutputT>
  ): ChildRenderingT {
    checkNotFrozen()
    return renderer.render(child, props, key, handler)
  }

  override fun runningSideEffect(
    key: String,
    sideEffect: suspend () -> Unit
  ) {
    checkNotFrozen()
    sideEffectRunner.runningSideEffect(key, sideEffect)
  }

  /**
   * Freezes this context so that any further calls to this context will throw.
   */
  fun freeze() {
    checkNotFrozen()
    frozen = true
  }

  private fun checkNotFrozen() = check(!frozen) {
    "RenderContext cannot be used after render method returns."
  }
}

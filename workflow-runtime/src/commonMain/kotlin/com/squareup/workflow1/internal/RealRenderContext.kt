@file:OptIn(WorkflowExperimentalApi::class)

package com.squareup.workflow1.internal

import androidx.compose.runtime.Composable
import com.squareup.workflow1.BaseRenderContext
import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.Sink
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.WorkflowExperimentalApi
import com.squareup.workflow1.WorkflowTracer
import com.squareup.workflow1.identifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.SendChannel
import kotlin.reflect.KType

internal class RealRenderContext<out PropsT, StateT, OutputT>(
  private val renderer: Renderer<PropsT, StateT, OutputT>,
  private val sideEffectRunner: SideEffectRunner,
  private val rememberStore: RememberStore,
  private val eventActionsChannel: SendChannel<WorkflowAction<PropsT, StateT, OutputT>>,
  override val workflowTracer: WorkflowTracer?,
  override val runtimeConfig: RuntimeConfig
) : BaseRenderContext<PropsT, StateT, OutputT>, Sink<WorkflowAction<PropsT, StateT, OutputT>> {

  interface Renderer<PropsT, StateT, OutputT> {
    fun <ChildPropsT, ChildOutputT, ChildRenderingT> render(
      child: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
      props: ChildPropsT,
      key: String,
      handler: (ChildOutputT) -> WorkflowAction<PropsT, StateT, OutputT>
    ): ChildRenderingT

    fun <ChildRenderingT> renderComposable(
      key: String,
      content: @Composable () -> ChildRenderingT
    ): ChildRenderingT
  }

  interface SideEffectRunner {
    fun runningSideEffect(
      key: String,
      sideEffect: suspend CoroutineScope.() -> Unit
    )
  }

  interface RememberStore {
    fun <ResultT> remember(
      key: String,
      resultType: KType,
      vararg inputs: Any?,
      calculation: () -> ResultT
    ): ResultT
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
        "Expected sink to not be sent to until after the render pass. " +
          "Received action: ${value.debuggingName}"
      )
    }
    eventActionsChannel.trySend(value)
  }

  override fun <ChildPropsT, ChildOutputT, ChildRenderingT> renderChild(
    child: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
    props: ChildPropsT,
    key: String,
    handler: (ChildOutputT) -> WorkflowAction<PropsT, StateT, OutputT>
  ): ChildRenderingT {
    checkNotFrozen { "renderChild(${child.identifier})" }
    return renderer.render(child, props, key, handler)
  }

  override fun <ChildRenderingT> renderComposable(
    key: String,
    content: @Composable () -> ChildRenderingT
  ): ChildRenderingT {
    checkNotFrozen()
    return renderer.renderComposable(key, content)
  }

  override fun runningSideEffect(
    key: String,
    sideEffect: suspend CoroutineScope.() -> Unit
  ) {
    checkNotFrozen { "runningSideEffect($key)" }
    sideEffectRunner.runningSideEffect(key, sideEffect)
  }

  override fun <ResultT> remember(
    key: String,
    resultType: KType,
    vararg inputs: Any?,
    calculation: () -> ResultT
  ): ResultT {
    checkNotFrozen { "remember($key)" }
    return rememberStore.remember(key, resultType, inputs = inputs, calculation)
  }

  /**
   * Freezes this context so that any further calls to this context will throw.
   */
  fun freeze() {
    checkNotFrozen { "freeze" }
    frozen = true
  }

  /**
   * Unfreezes when the node is about to render() again.
   */
  fun unfreeze() {
    frozen = false
  }

  private fun checkNotFrozen(reason: () -> String = { "" }) = check(!frozen) {
    "RenderContext cannot be used after render method returns" +
      "${reason().takeUnless { it.isBlank() }?.let { " ($it)" }}"
  }
}

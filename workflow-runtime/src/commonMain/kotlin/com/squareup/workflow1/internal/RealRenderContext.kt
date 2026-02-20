package com.squareup.workflow1.internal

import com.squareup.workflow1.BaseRenderContext
import com.squareup.workflow1.RuntimeConfig
import com.squareup.workflow1.Sink
import com.squareup.workflow1.Workflow
import com.squareup.workflow1.WorkflowAction
import com.squareup.workflow1.WorkflowTracer
import com.squareup.workflow1.identifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlin.reflect.KType

internal class RealRenderContext<PropsT, StateT, OutputT>(
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
   * False except while this [WorkflowNode] is running the workflow's `render` method.
   *
   * Used to:
   *  - Prevent modifications to this object after [freeze] is called (e.g. [renderChild] calls).
   *    Only allowed when this flag is true.
   *  - Prevent sending to sinks before render returns. Only allowed when this flag is false.
   *
   * This is a [ThreadLocal] since we only care about preventing calls during rendering from the
   * thread that is actually doing the rendering. If a background thread happens to send something
   * into the sink, for example, while the main thread is rendering, it's not a violation.
   */
  private var performingRender by threadLocalOf { false }

  override val actionSink: Sink<WorkflowAction<PropsT, StateT, OutputT>> get() = this

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun toString(): String =
    "RealRenderContext@${hashCode().toString(
      16
    )}(actionEnqueued=${(eventActionsChannel as? ReceiveChannel<*>)?.isEmpty})"

  override fun send(value: WorkflowAction<PropsT, StateT, OutputT>) {
    // Can't send actions from render thread during render pass.
    if (performingRender) {
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
    checkPerformingRender(child.identifier) {
      "renderChild(${child.identifier})"
    }
    return renderer.render(child, props, key, handler)
  }

  override fun runningSideEffect(
    key: String,
    sideEffect: suspend CoroutineScope.() -> Unit
  ) {
    checkPerformingRender(key) { "runningSideEffect($key)" }
    sideEffectRunner.runningSideEffect(key, sideEffect)
  }

  override fun <ResultT> remember(
    key: String,
    resultType: KType,
    vararg inputs: Any?,
    calculation: () -> ResultT
  ): ResultT {
    checkPerformingRender(key) { "remember($key)" }
    return rememberStore.remember(key, resultType, inputs = inputs, calculation)
  }

  /**
   * Freezes this context so that any further calls to this context will throw.
   */
  fun freeze() {
    performingRender = false
  }

  /**
   * Unfreezes when the node is about to render() again.
   */
  fun unfreeze() {
    performingRender = true
  }

  /**
   * @param stackTraceKey ensures unique crash reporter error groups.
   * It is important that keys are stable across processes, avoid system hashes.
   *
   * @see checkWithKey
   */
  private inline fun checkPerformingRender(
    stackTraceKey: Any,
    lazyMessage: () -> Any
  ) = checkWithKey(performingRender, stackTraceKey) {
    "RenderContext cannot be used after render method returns: ${lazyMessage()}"
  }
}

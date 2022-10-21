@file:Suppress("DEPRECATION")

package com.squareup.workflow1

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.SendChannel

public open class RealRenderContext<PropsT, StateT, OutputT>(
  protected open val renderer: Renderer<PropsT, StateT, OutputT>,
  protected val sideEffectRunner: SideEffectRunner,
  private val eventActionsChannel: SendChannel<WorkflowAction<PropsT, StateT, OutputT>>
) : BaseRenderContext<PropsT, StateT, OutputT>, Sink<WorkflowAction<PropsT, StateT, OutputT>> {

  public interface Renderer<PropsT, StateT, OutputT> {
    public fun <ChildPropsT, ChildOutputT, ChildRenderingT> render(
      child: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
      props: ChildPropsT,
      key: String,
      handler: (ChildOutputT) -> WorkflowAction<PropsT, StateT, OutputT>
    ): ChildRenderingT
  }

  public interface SideEffectRunner {
    public fun runningSideEffect(
      key: String,
      sideEffect: suspend CoroutineScope.() -> Unit
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
    eventActionsChannel.trySend(value)
  }

  override fun <ChildPropsT, ChildOutputT, ChildRenderingT> renderChild(
    child: Workflow<ChildPropsT, ChildOutputT, ChildRenderingT>,
    props: ChildPropsT,
    key: String,
    handler: (ChildOutputT) -> WorkflowAction<PropsT, StateT, OutputT>
  ): ChildRenderingT {
    // checkNotFrozen()
    return renderer.render(child, props, key, handler)
  }

  override fun runningSideEffect(
    key: String,
    sideEffect: suspend CoroutineScope.() -> Unit
  ) {
    // checkNotFrozen()
    sideEffectRunner.runningSideEffect(key, sideEffect)
  }

  /**
   * Freezes this context so that any further calls to this context will throw.
   */
  public fun freeze() {
    checkNotFrozen()
    frozen = true
  }

  public fun unsafeFreeze() {
    frozen = true
  }

  /**
   * Unfreezes when the node is about to render() again.
   */
  public fun unfreeze() {
    frozen = false
  }

  protected fun checkNotFrozen(): Unit = check(!frozen) {
    "RenderContext cannot be used after render method returns."
  }
}

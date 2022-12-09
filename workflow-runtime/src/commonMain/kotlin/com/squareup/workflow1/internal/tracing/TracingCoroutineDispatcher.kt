package com.squareup.workflow1.internal.tracing

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlin.coroutines.CoroutineContext

public class TracingCoroutineDispatcher(
  public val delegate: CoroutineDispatcher,
  public val beforeTrace: () -> Any,
  public val afterTrace: () -> Any,
) : CoroutineDispatcher() {

  override fun isDispatchNeeded(
    context: CoroutineContext
  ): Boolean = delegate.isDispatchNeeded(context)

  override fun dispatch(
    context: CoroutineContext,
    block: Runnable
  ) {

    val originalTrace = context.requireTrace()

    val startNode = originalTrace.child(
      TracingCoroutineDispatcher::class, args = listOf()
    )

    val wrappedBlock = Runnable {
      startNode.beforeTrace = beforeTrace()
      block.run()
      startNode.afterTrace = afterTrace()
    }

    delegate.dispatch(context + startNode + delegate, wrappedBlock)
  }
}

package com.squareup.workflow1.internal

import com.squareup.workflow1.internal.InlineLinkedList.InlineListNode
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex

/**
 * Holds a [Job] that represents a running [side effect][RealRenderContext.runningSideEffect], as
 * well as the key used to identify that side effect.
 *
 * Lastly, holds the [renderComplete] that is unlocked when render() is complete (and so the sink
 * can be used).
 */
internal class SideEffectNode(
  val key: String,
  val job: Job,
  val renderComplete: Mutex
) : InlineListNode<SideEffectNode> {

  override var nextListNode: SideEffectNode? = null
}

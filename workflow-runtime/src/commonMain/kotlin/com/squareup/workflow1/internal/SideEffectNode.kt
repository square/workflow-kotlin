package com.squareup.workflow1.internal

import com.squareup.workflow1.InlineLinkedList.InlineListNode
import kotlinx.coroutines.Job

/**
 * Holds a [Job] that represents a running [side effect][RealRenderContext.runningSideEffect], as
 * well as the key used to identify that side effect.
 */
internal class SideEffectNode(
  val key: String,
  val job: Job
) : InlineListNode<SideEffectNode> {

  override var nextListNode: SideEffectNode? = null
}

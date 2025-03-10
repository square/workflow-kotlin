package com.squareup.workflow1.internal

import com.squareup.workflow1.internal.InlineLinkedList.InlineListNode
import kotlin.reflect.KType

internal class RememberedNode<ResultT>(
  val key: String,
  val resultType: KType,
  val inputs: Array<out Any?>,
  val lastCalculated: ResultT
) : InlineListNode<RememberedNode<*>> {

  override var nextListNode: RememberedNode<*>? = null
}

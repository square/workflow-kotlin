package com.squareup.workflow1.internal

import com.squareup.workflow1.internal.InlineLinkedList.InlineListNode
import kotlin.reflect.KClass

internal class RememberedNode<ResultT: Any>(
  val key: String,
  val resultType: KClass<ResultT>,
  val inputs: Array<out Any?>,
  val lastCalculated: ResultT
): InlineListNode<RememberedNode<*>> {

  override var nextListNode: RememberedNode<*>? = null
}

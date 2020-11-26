package com.squareup.workflow2

import androidx.compose.runtime.Applier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionReference
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.compositionFor
import androidx.compose.ui.node.ExperimentalLayoutNodeApi

@OptIn(ExperimentalComposeApi::class, ExperimentalLayoutNodeApi::class)
fun wrapperSubcomposeInto(
    // container: LayoutNode,
  compositionKey: Any,
  parent: CompositionReference,
  applier: Applier<out Any?>,
  composable: @Composable () -> Unit
): Composition = compositionFor(
    compositionKey,
    // WrapperApplier(UiApplier(container)),
    WrapperApplier(applier),
    parent
).apply {
  setContent(composable)
}

class WorkflowNode

sealed class Operation {
  object Clear : Operation()

  class Down(val node: Any?) : Operation()

  class Insert(
    val index: Int,
    instance: Any?
  ) : Operation()

  class Move(
    val from: Int,
    val to: Int,
    val count: Int
  ) : Operation()

  class Remove(
    val index: Int,
    val count: Int
  ) : Operation()

  object Up : Operation()
}

class WrappedNode(
  val underlyingNode: Any?,
) {
  val pendingChanges = ArrayDeque<WrappedNode>()
  val children = mutableListOf<WrappedNode>()
}

@OptIn(ExperimentalComposeApi::class)
class WrapperApplier<T, A : Applier<T>>(
  private val delegate: A,
) : Applier<Any?> {
  private val root = WrappedNode(underlyingNode = null)

  // private val pendingChanges = ArrayDeque<WrappedNode>()
  private val pendingInserts = mutableListOf<PendingInsert>()
  private val stack = ArrayDeque<WrappedNode>()

  private class PendingInsert(
    val index: Int,
    val instance: WrappedNode,
  )

  private var _current: WrappedNode = root
  override val current: Any = root

  override fun onBeginChanges() {
    println("OMG WrapperApplier.onBeginChanges()")
    // Next call should be insert(0, WorkflowNode)
  }

  override fun onEndChanges() {
    println("OMG WrapperApplier.onEndChanges()")
    // TODO commit
  }

  // override fun insert(
  //   index: Int,
  //   instance: WrappedNode
  // ) {
  //   current.children.add(index, instance)
  // }
  //
  // override fun move(
  //   from: Int,
  //   to: Int,
  //   count: Int
  // ) {
  //   // TODO make this more efficient
  //   val postMoveTo = if (to < from) to else to - count
  //   val children = current.children
  //   val movedChildren = children.subList(from, from + count).toList()
  //   repeat(count) { children.remove(from) }
  //   children.addAll(postMoveTo, movedChildren)
  // }
  //
  // override fun remove(
  //   index: Int,
  //   count: Int
  // ) {
  //   current.children.let { children ->
  //     repeat(count) { index -> children.removeAt(index) }
  //   }
  // }
  //
  // override fun onClear() {
  //   // Noop
  // }

  override fun insert(
    index: Int,
    instance: Any?
  ) {
    println("OMG ${delegate::class}.insert($index, $instance)")

    // delegate.insert(index, instance)
    val node = WrappedNode(instance)
    pendingInserts.add(PendingInsert(index, node))
  }

  override fun down(node: Any?) {
    println("OMG ${delegate::class}.down($node)")

    // delegate.down(node)
    stack.add(_current)
    _current = _current.children.single { it.underlyingNode === node }
  }

  override fun move(
    from: Int,
    to: Int,
    count: Int
  ) {
    println("OMG ${delegate::class}.move($from, $to, $count)")
    delegate.move(from, to, count)
  }

  override fun remove(
    index: Int,
    count: Int
  ) {
    println("OMG ${delegate::class}.remove($index, $count)")
    delegate.remove(index, count)
  }

  override fun up() {
    println("OMG ${delegate::class}.up()")
    delegate.up()
  }

  override fun clear() {
    println("OMG ${delegate::class}.clear()")
    delegate.clear()
  }
}

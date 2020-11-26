package com.squareup.workflow2

import androidx.compose.runtime.Applier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionReference
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.compositionFor
import androidx.compose.ui.node.ExperimentalLayoutNodeApi
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.UiApplier

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

@OptIn(ExperimentalComposeApi::class)
class WrapperApplier<T, A : Applier<T>>(
  private val delegate: A
) : Applier<T> {
  override val current: T get() = delegate.current

  override fun clear() {
    println("OMG ${delegate::class}.clear()")
    delegate.clear()
  }

  override fun down(node: T) {
    println("OMG ${delegate::class}.down($node)")
    delegate.down(node)
  }

  override fun insert(
    index: Int,
    instance: T
  ) {
    println("OMG ${delegate::class}.insert($index, $instance)")
    delegate.insert(index, instance)
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
}

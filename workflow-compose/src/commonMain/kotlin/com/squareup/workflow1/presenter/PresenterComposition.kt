package com.squareup.workflow1.presenter

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionContext

internal class PresenterComposition internal constructor(
  parent: CompositionContext,
  rootProducer: PresenterPolicy,
) {
  val rootNode = PresenterNode(policy = rootProducer)

  private val composition = Composition(
    applier = PresenterApplier(rootNode),
    parent = parent
  )

  fun setContent(content: @Composable () -> Unit) {
    composition.setContent(content)
  }
}

// Must be internal since it's used to emit compose nodes from public inline functions.
@PublishedApi
internal class PresenterApplier(
  root: PresenterNode,
) : AbstractApplier<PresenterNode>(root) {
  override fun insertTopDown(
    index: Int,
    instance: PresenterNode
  ) {
    current.children.add(index, instance)
    instance.attach(current)
  }

  override fun insertBottomUp(
    index: Int,
    instance: PresenterNode
  ) = Unit

  override fun remove(
    index: Int,
    count: Int
  ) {
    current.removeChildren(from = index, count = count)
  }

  override fun move(
    from: Int,
    to: Int,
    count: Int
  ) {
    current.children.move(from, to, count)
    current.invalidate()
  }

  override fun onClear() {
    root.removeChildren()
  }
}

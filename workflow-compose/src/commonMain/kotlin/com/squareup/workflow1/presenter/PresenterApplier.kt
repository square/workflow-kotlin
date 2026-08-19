package com.squareup.workflow1.presenter

import androidx.compose.runtime.AbstractApplier

@PublishedApi
internal class PresenterApplier(
  root: PresenterNode,
) : AbstractApplier<PresenterNode>(root) {
  override fun insertTopDown(
    index: Int,
    instance: PresenterNode
  ) {
    current.children.add(index, instance)
  }

  override fun insertBottomUp(
    index: Int,
    instance: PresenterNode
  ) = Unit

  override fun remove(
    index: Int,
    count: Int
  ) {
    current.children.remove(index, count)
  }

  override fun move(
    from: Int,
    to: Int,
    count: Int
  ) {
    current.children.move(from, to, count)
  }

  override fun onClear() {
    root.disposeChildren()
  }
}

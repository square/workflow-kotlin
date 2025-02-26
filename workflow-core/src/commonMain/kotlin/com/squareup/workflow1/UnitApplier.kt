package com.squareup.workflow1

import androidx.compose.runtime.Applier

internal object UnitApplier : Applier<Unit> {
  override val current: Unit
    get() = Unit

  override fun clear() {
  }

  override fun down(node: Unit) {
  }

  override fun insertBottomUp(
    index: Int,
    instance: Unit
  ) {
  }

  override fun insertTopDown(
    index: Int,
    instance: Unit
  ) {
  }

  override fun move(
    from: Int,
    to: Int,
    count: Int
  ) {
  }

  override fun remove(
    index: Int,
    count: Int
  ) {
  }

  override fun up() {
  }
}

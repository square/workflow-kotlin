package com.squareup.workflow1.internal.compose

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.snapshots.StateObject
import androidx.compose.runtime.snapshots.StateRecord
import androidx.compose.runtime.snapshots.withCurrent
import androidx.compose.runtime.snapshots.writable

internal class PeekableMutableState<T>(initialValue: T) : MutableState<T>, StateObject {
  private var record = Record(initialValue)

  override var value: T
    get() = record.withCurrent { it.value }
    set(newValue) {
      setWithInvalidator(newValue, invalidator = null)
    }

  override fun component1(): T = value
  override fun component2(): (T) -> Unit = { value = it }

  fun setWithInvalidator(
    newValue: T,
    invalidator: (() -> Unit)?
  ) {
    if (newValue != value) {
      record.writable(this) { value = newValue }
      invalidator?.invoke()
    }
  }

  override val firstStateRecord: StateRecord
    get() = record

  override fun prependStateRecord(value: StateRecord) {
    @Suppress("UNCHECKED_CAST")
    record = value as Record<T>
  }

  private class Record<T>(var value: T) : StateRecord() {
    override fun create(): StateRecord = Record(value)
    override fun assign(value: StateRecord) {
      this.value = (value as Record<T>).value
    }
  }
}

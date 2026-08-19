package com.squareup.workflow1.presenter

/**
 * A typed map of [PresenterSlot] to [ViewModelRef]. Allows reading from slots, similarly to
 * [SlotReader], with the key difference that reading is idempotent: reading from the same
 * slot multiple times on the same map will return the same value.
 */
interface PresenterSlotMap {
  operator fun <T : Any> get(slot: PresenterSlot<T>): ViewModelRef<T>?
}

internal class MutablePresenterSlotMap : PresenterSlotMap, SlotWriter {
  private val map: MutableMap<PresenterSlot<*>, ViewModelRef<*>> = mutableMapOf()

  override fun <T : Any> get(slot: PresenterSlot<T>): ViewModelRef<T>? {
    @Suppress("UNCHECKED_CAST")
    return map[slot] as ViewModelRef<T>?
  }

  override fun <T : Any> writeRef(
    slot: PresenterSlot<T>,
    ref: ViewModelRef<T>
  ) {
    map[slot] = ref
  }

  override fun <T : Any> writeValue(
    slot: PresenterSlot<T>,
    value: T
  ) {
    throw UnsupportedOperationException()
  }

  override fun clear(slot: PresenterSlot<*>) {
    throw UnsupportedOperationException()
  }
}

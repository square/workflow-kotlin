package com.squareup.workflow1.presenter

/**
 * Provides write operations into [PresenterSlot]s. This is the output API for a
 * [PresenterPolicy], opposite of [SlotReader].
 *
 * Supports the following operations on a [PresenterSlot]:
 *  - Writing a view model value to the slot ([writeValue]).
 *  - Writing a [ViewModelRef] to the slot ([writeRef]).
 *  - Clearing any previous values written to the slot ([clear]).
 *
 * Since a slot can only contain a single value, all write operations overwrite previous write
 * operations in the same presenter pass.
 */
sealed interface SlotWriter {
  /**
   * Writes [value] into [slot], overwriting any previous value written.
   */
  fun <T : Any> writeValue(
    slot: PresenterSlot<T>,
    value: T
  )

  /**
   * Writes [ref] into [slot], overwriting any previous value written.
   */
  fun <T : Any> writeRef(
    slot: PresenterSlot<T>,
    ref: ViewModelRef<T>
  )

  /**
   * Clears any value previously written to [slot].
   */
  fun clear(slot: PresenterSlot<*>)

  /** Shorthand for calling [writeValue] if [value] is not null. */
  operator fun <T : Any> set(
    key: PresenterSlot<T>,
    value: T?
  ) {
    if (value != null) {
      writeValue(key, value)
    }
  }

  /** Shorthand for calling [writeRef] if [ref] is not null. */
  operator fun <T : Any> set(
    key: PresenterSlot<T>,
    ref: ViewModelRef<T>?
  ) {
    if (ref != null) {
      writeRef(key, ref)
    }
  }
}

package com.squareup.workflow1.presenter

/**
 * Provides read operations from [PresenterSlot]s. This is the input API for [PresenterPolicy],
 * opposite of [SlotWriter].
 *
 * The primary operation is [read]. Note that [PresenterSlot]s are consumable: reading a value
 * from a slot consumes it and reading from that slot again will return null. The [contains]
 * method can be used to check if [read] will return a non-null value.
 *
 * If a [PresenterPolicy] has a "primary" view model from a child, it should generally forward any
 * slots it does not explicitly handle. This can be done by calling [readAllSlotsTo] on the
 * child with the policy's [SlotWriter].
 */
sealed interface SlotReader {
  /**
   * Reads the value of [slot] from this reader and returns it. Subsequent calls with the same
   * slot will return null.
   */
  fun <T : Any> read(slot: PresenterSlot<T>): ViewModelRef<T>?

  /**
   * Returns true if calling [read] with [slot] will return non-null.
   */
  operator fun contains(slot: PresenterSlot<*>): Boolean

  /**
   * Reads any slots that haven't been consumed by [read] into [writer].
   */
  fun readAllSlotsTo(writer: SlotWriter)
}

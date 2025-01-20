package com.squareup.workflow1

import kotlin.jvm.JvmInline

/**
 * Used to wrap immutable nullable values whose holder may not yet be initialized.
 * Check [isInitialized] to see if the value has been assigned.
 */
@JvmInline
public value class NullableInitBox<T>(private val _value: Any? = Uninitialized) {
  /**
   * Whether or not a value has been set for this [NullableInitBox]
   */
  public val isInitialized: Boolean get() = _value !== Uninitialized

  /**
   * Get the value this has been initialized with.
   *
   * @throws [IllegalStateException] if the value in the box has not been initialized.
   */
  @Suppress("UNCHECKED_CAST")
  public fun getOrThrow(): T {
    check(isInitialized) { "NullableInitBox was fetched before it was initialized with a value." }
    return _value as T
  }

  public object Uninitialized
}

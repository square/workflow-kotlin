package com.squareup.workflow1.internal

import kotlin.reflect.KProperty

internal expect class Lock()

internal expect inline fun <R> Lock.withLock(block: () -> R): R

internal expect class ThreadLocal<T> {
  fun get(): T
  fun set(value: T)
}

internal expect fun <T> threadLocalOf(initialValue: () -> T): ThreadLocal<T>

@Suppress("NOTHING_TO_INLINE")
internal inline operator fun <T> ThreadLocal<T>.getValue(
  receiver: Any?,
  property: KProperty<*>
): T = get()

@Suppress("NOTHING_TO_INLINE")
internal inline operator fun <T> ThreadLocal<T>.setValue(
  receiver: Any?,
  property: KProperty<*>,
  value: T
) {
  set(value)
}

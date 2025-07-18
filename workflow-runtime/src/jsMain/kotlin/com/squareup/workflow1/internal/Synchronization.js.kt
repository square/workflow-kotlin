package com.squareup.workflow1.internal

// JS doesn't have threading, so doesn't need any actual synchronization.

internal actual typealias Lock = Any

internal actual inline fun <R> Lock.withLock(block: () -> R): R = block()

internal actual class ThreadLocal<T>(private var value: T) {
  actual fun get(): T = value
  actual fun set(value: T) {
    this.value = value
  }
}

internal actual fun <T> threadLocalOf(initialValue: () -> T): ThreadLocal<T> =
  ThreadLocal(initialValue())

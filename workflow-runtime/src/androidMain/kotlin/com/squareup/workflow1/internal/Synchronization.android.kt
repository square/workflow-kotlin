package com.squareup.workflow1.internal

internal actual typealias Lock = Any

internal actual inline fun <R> Lock.withLock(block: () -> R): R = synchronized(this, block)

internal actual typealias ThreadLocal<T> = java.lang.ThreadLocal<T>

internal actual fun <T> threadLocalOf(initialValue: () -> T): ThreadLocal<T> =
  ThreadLocal.withInitial(initialValue)

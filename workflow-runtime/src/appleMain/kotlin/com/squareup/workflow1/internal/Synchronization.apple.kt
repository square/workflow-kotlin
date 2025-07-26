package com.squareup.workflow1.internal

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSCopyingProtocol
import platform.Foundation.NSLock
import platform.Foundation.NSThread
import platform.Foundation.NSZone
import platform.darwin.NSObject

/**
 * Creates a lock that, after locking, must only be unlocked by the thread that acquired the lock.
 *
 * See the docs: https://developer.apple.com/documentation/foundation/nslock#overview
 */
internal actual typealias Lock = NSLock

internal actual inline fun <R> Lock.withLock(block: () -> R): R {
  lock()
  try {
    return block()
  } finally {
    unlock()
  }
}

/**
 * Implementation of [ThreadLocal] that works in a similar way to Java's, based on a thread-specific
 * map/dictionary.
 */
internal actual class ThreadLocal<T>(
  private val initialValue: () -> T
) : NSObject(), NSCopyingProtocol {

  private val threadDictionary
    get() = NSThread.currentThread().threadDictionary

  actual fun get(): T {
    @Suppress("UNCHECKED_CAST")
    return (threadDictionary.objectForKey(aKey = this) as T?)
      ?: initialValue().also(::set)
  }

  actual fun set(value: T) {
    threadDictionary.setObject(value, forKey = this)
  }

  /**
   * [Docs](https://developer.apple.com/documentation/foundation/nscopying/copy(with:)) say [zone]
   * is unused.
   */
  @OptIn(ExperimentalForeignApi::class)
  override fun copyWithZone(zone: CPointer<NSZone>?): Any = this
}

internal actual fun <T> threadLocalOf(initialValue: () -> T): ThreadLocal<T> =
  ThreadLocal(initialValue)

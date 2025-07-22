package com.squareup.workflow1.internal

import platform.Foundation.NSLock

internal actual class Lock: NSLock()

internal actual inline fun <R> Lock.withLock(block: () -> R): R {
  lock()
  try {
    return block()
  } finally {
    unlock()
  }
}

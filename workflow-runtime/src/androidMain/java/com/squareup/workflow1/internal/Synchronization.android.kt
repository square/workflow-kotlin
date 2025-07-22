package com.squareup.workflow1.internal

internal actual class Lock: Any()

internal actual inline fun <R> Lock.withLock(block: () -> R): R = synchronized(this, block)

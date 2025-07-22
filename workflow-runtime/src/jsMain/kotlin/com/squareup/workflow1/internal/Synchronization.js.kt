package com.squareup.workflow1.internal

// JS doesn't have threading, so doesn't need any actual synchronization.

internal actual class Lock: Any()

internal actual inline fun <R> Lock.withLock(block: () -> R): R = block()

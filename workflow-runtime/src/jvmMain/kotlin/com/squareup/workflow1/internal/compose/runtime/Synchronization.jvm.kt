package com.squareup.workflow1.internal.compose.runtime

internal actual typealias Lock = Any

internal actual inline fun <R> Lock.withLock(block: () -> R): R = synchronized(this, block)

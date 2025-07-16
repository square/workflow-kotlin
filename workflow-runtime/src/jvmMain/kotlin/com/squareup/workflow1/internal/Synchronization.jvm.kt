package com.squareup.workflow1.internal

public actual typealias Lock = Any

public actual inline fun <R> Lock.withLock(block: () -> R): R = synchronized(this, block)

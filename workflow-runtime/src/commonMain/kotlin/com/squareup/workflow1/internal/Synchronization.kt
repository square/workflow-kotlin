package com.squareup.workflow1.internal

public expect class Lock()

public expect inline fun <R> Lock.withLock(block: () -> R): R

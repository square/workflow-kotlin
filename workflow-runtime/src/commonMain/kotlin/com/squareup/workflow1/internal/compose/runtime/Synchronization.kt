package com.squareup.workflow1.internal.compose.runtime

internal expect class Lock()

internal expect inline fun <R> Lock.withLock(block: () -> R): R

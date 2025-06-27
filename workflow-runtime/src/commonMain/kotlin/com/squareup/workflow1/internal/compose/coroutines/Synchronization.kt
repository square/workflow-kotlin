package com.squareup.workflow1.internal.compose.coroutines

import kotlinx.coroutines.channels.Channel

internal expect class Lock()

internal expect inline fun <R> Lock.withLock(block: () -> R): R

// TODO pull into separate file
internal fun <T> Channel<T>.requireSend(element: T) {
  val result = trySend(element)
  if (result.isClosed) {
    throw IllegalStateException(
      "Tried emitting output to workflow whose output channel was closed.",
      result.exceptionOrNull()
    )
  }
  if (result.isFailure) {
    error("Tried emitting output to workflow whose output channel was full.")
  }
}

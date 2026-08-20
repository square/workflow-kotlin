package com.squareup.workflow1.internal

import kotlinx.coroutines.channels.SendChannel

/**
 * Tries to send [element] to this channel and throws an [IllegalStateException] if the channel is
 * full or closed.
 */
internal fun <T> SendChannel<T>.requireSend(element: T) {
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

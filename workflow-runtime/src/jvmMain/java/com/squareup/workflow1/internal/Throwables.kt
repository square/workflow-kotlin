package com.squareup.workflow1.internal

import kotlinx.coroutines.CancellationException

internal tailrec fun Throwable.unwrapCancellationCause(): Throwable? {
  if (this !is CancellationException) return this
  return cause?.unwrapCancellationCause()
}

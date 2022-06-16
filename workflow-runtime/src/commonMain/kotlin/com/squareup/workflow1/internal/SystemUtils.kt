package com.squareup.workflow1.internal

/**
 * When the runtime uses a timeout (see [RuntimeConfig]) we need to decide how long of a timeout
 * to use after we have processed some actions. Use this milliseconds since epoch for that.
 */
internal expect fun currentTimeMillis(): Long

internal expect fun nanoTime(): Long

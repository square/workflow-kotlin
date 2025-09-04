package com.squareup.workflow1.internal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.compose.runtime.internal.ComposableLambda

/**
 * When the runtime uses a timeout (see [RuntimeConfig]) we need to decide how long of a timeout
 * to use after we have processed some actions. Use this milliseconds since epoch for that.
 */
internal expect fun currentTimeMillis(): Long

internal expect fun <A, B, C> convertComposableLambda(
  composeLambda: @Composable (A, B, C) -> Any?
): (A, B, C, Composer, Int) -> Any?

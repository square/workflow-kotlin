package com.squareup.workflow1.internal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer

internal actual fun <A, B, C> convertComposableLambda(
  composeLambda: @Composable (A, B, C) -> Any?
): (A, B, C, Composer, Int) -> Any? {
  @Suppress("UNCHECKED_CAST")
  return composeLambda as (A, B, C, Composer, Int) -> Any?
}

package com.squareup.workflow1.internal.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.currentComposer

/**
 * Like [CompositionLocalProvider] but allows returning a value.
 *
 * Cash App's Molecule, [Amazon's app-platform](https://github.com/amzn/app-platform/blob/main/presenter-molecule/public/src/commonMain/kotlin/software/amazon/app/platform/presenter/molecule/ReturningCompositionLocalProvider.kt),
 * and [Circuit](https://github.com/slackhq/circuit/blob/main/circuit-foundation/src/commonMain/kotlin/com/slack/circuit/foundation/internal/WithCompositionLocalProviders.kt)
 * all have the same workaround, see https://issuetracker.google.com/issues/271871288.
 */
@OptIn(InternalComposeApi::class)
@Composable
internal fun <T> withCompositionLocals(
  vararg values: ProvidedValue<*>,
  content: @Composable () -> T,
): T {
  currentComposer.startProviders(values)
  val result = content()
  currentComposer.endProviders()

  return result
}

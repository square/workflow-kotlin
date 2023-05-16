package com.squareup.workflow1.ui.container

import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewEnvironment.Companion.EMPTY
import com.squareup.workflow1.ui.ViewEnvironmentKey
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.Wrapper
import com.squareup.workflow1.ui.plus

/**
 * Pairs a [content] rendering with a [environment] to support its display.
 * Typically the rendering type (`RenderingT`) of the root of a UI workflow,
 * but can be used at any point to modify the [ViewEnvironment] received from
 * a parent view.
 *
 * UI kits are expected to provide handling for this class by default.
 */
@WorkflowUiExperimentalApi
public class EnvironmentScreen<C : Screen>(
  public override val content: C,
  public val environment: ViewEnvironment = EMPTY
) : Wrapper<Screen, C>, Screen {
  override fun <D : Screen> map(transform: (C) -> D): EnvironmentScreen<D> =
    EnvironmentScreen(transform(content), environment)

  @Deprecated("Use content", ReplaceWith("content"))
  public val wrapped: C = content
}

/**
 * Convenience wrapper for [withEnvironmentValue] that simplifies adding a custom
 * [ViewRegistry].
 *
 * If the receiver is an [EnvironmentScreen], its [ViewRegistry] will be combined
 * with the new one (because [ViewRegistry.Companion] implements [ViewEnvironmentKey.combine]).
 */
@WorkflowUiExperimentalApi
public fun Screen.withRegistry(viewRegistry: ViewRegistry): EnvironmentScreen<*> {
  return withEnvironmentValue(ViewRegistry to viewRegistry)
}

/**
 * Returns an [EnvironmentScreen] derived from the receiver,
 * whose [EnvironmentScreen.environment] includes the given [keyAndValue] pair.
 *
 * If the receiver is an [EnvironmentScreen], uses [ViewEnvironment.plus]
 * to update the [receiver's ViewEnvironment][EnvironmentScreen.environment],
 * ensuring that [ViewEnvironmentKey.combine] methods will be applied.
 */
@WorkflowUiExperimentalApi
public fun <T : Any> Screen.withEnvironmentValue(
  keyAndValue: Pair<ViewEnvironmentKey<T>, T>
): EnvironmentScreen<*> {
  return when (this) {
    is EnvironmentScreen<*> -> EnvironmentScreen(content, this.environment + keyAndValue)
    else -> EnvironmentScreen(this, EMPTY + keyAndValue)
  }
}

/**
 * Returns an [EnvironmentScreen] derived from the receiver,
 * whose [EnvironmentScreen.environment] includes the values in the given [environment].
 *
 * If the receiver is an [EnvironmentScreen], uses [ViewEnvironment.plus]
 * to update the [receiver's ViewEnvironment][EnvironmentScreen.environment]
 * with the given, ensuring that [ViewEnvironmentKey.combine] methods will be applied.
 */
@WorkflowUiExperimentalApi
public fun Screen.withEnvironment(
  environment: ViewEnvironment = EMPTY
): EnvironmentScreen<*> {
  return when (this) {
    is EnvironmentScreen<*> -> {
      if (environment.map.isEmpty()) {
        this
      } else {
        EnvironmentScreen(content, this.environment + environment)
      }
    }

    else -> EnvironmentScreen(this, environment)
  }
}

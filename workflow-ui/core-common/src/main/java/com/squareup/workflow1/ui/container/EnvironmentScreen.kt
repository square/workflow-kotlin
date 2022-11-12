package com.squareup.workflow1.ui.container

import com.squareup.workflow1.ui.Compatible
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.plus
import com.squareup.workflow1.visual.WithEnvironment

/**
 * Pairs a [wrapped] rendering with a [environment] to support its display.
 * Typically the rendering type (`RenderingT`) of the root of a UI workflow,
 * but can be used at any point to modify the [ViewEnvironment] received from
 * a parent view.
 *
 * UI kits are expected to provide handling for this class by default.
 */
@WorkflowUiExperimentalApi
public class EnvironmentScreen<V : Screen>(
  wrapped: V,
  environment: ViewEnvironment = ViewEnvironment.EMPTY
) : Compatible, Screen, WithEnvironment<V>(wrapped, environment)

/**
 * Returns an [EnvironmentScreen] derived from the receiver, whose
 * [EnvironmentScreen.environment] includes [viewRegistry].
 *
 * If the receiver is an [EnvironmentScreen], uses [ViewRegistry.merge]
 * to preserve the [ViewRegistry] entries of both.
 */
@WorkflowUiExperimentalApi
public fun Screen.withRegistry(viewRegistry: ViewRegistry): EnvironmentScreen<*> {
  return withEnvironment(ViewEnvironment.EMPTY + viewRegistry)
}

/**
 * Returns an [EnvironmentScreen] derived from the receiver,
 * whose [EnvironmentScreen.environment] includes the values in the given [environment].
 *
 * If the receiver is an [EnvironmentScreen], uses [ViewEnvironment.merge]
 * to preserve the [ViewRegistry] entries of both.
 */
@WorkflowUiExperimentalApi
public fun Screen.withEnvironment(
  environment: ViewEnvironment = ViewEnvironment.EMPTY
): EnvironmentScreen<*> {
  return when (this) {
    is EnvironmentScreen<*> -> {
      if (environment.map.isEmpty()) {
        this
      } else {
        EnvironmentScreen(wrapped, this.environment + environment)
      }
    }

    else -> EnvironmentScreen(this, environment)
  }
}

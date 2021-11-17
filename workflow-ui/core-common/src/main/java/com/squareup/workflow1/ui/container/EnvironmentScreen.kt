package com.squareup.workflow1.ui.container

import com.squareup.workflow1.ui.Compatible
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewRegistry
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.updateFrom

/**
 * Pairs a [screen] rendering with a [viewEnvironment] to support its display.
 * Typically the rendering type (`RenderingT`) of the root of a UI workflow,
 * but can be used at any point to modify the [ViewEnvironment] received from
 * a parent view.
 *
 * Use [withEnvironment] or [withRegistry] to create or update instances.
 */
@WorkflowUiExperimentalApi
public class EnvironmentScreen<V : Screen> internal constructor(
  public val screen: V,
  public val viewEnvironment: ViewEnvironment = ViewEnvironment()
) : Compatible, Screen {
  /**
   * Ensures that we make the decision to update or replace the root view based on
   * the wrapped [screen].
   */
  override val compatibilityKey: String = Compatible.keyFor(screen, "EnvironmentScreen")
}

@WorkflowUiExperimentalApi
public operator fun <T : Screen> EnvironmentScreen<T>.plus(
  environment: ViewEnvironment
): EnvironmentScreen<T> {
  return when {
    environment.map.isEmpty() -> this
    else -> EnvironmentScreen(screen, viewEnvironment.updateFrom(environment))
  }
}

/**
 * Returns a [EnvironmentScreen] derived from the receiver, whose [ViewEnvironment]
 * includes a [ViewRegistry] updated from the given [viewRegistry].
 */
@WorkflowUiExperimentalApi
public fun Screen.withRegistry(viewRegistry: ViewRegistry): EnvironmentScreen<*> {
  return withEnvironment(ViewEnvironment(mapOf(ViewRegistry to viewRegistry)))
}

/**
 * Returns a [EnvironmentScreen] derived from the receiver, whose [ViewEnvironment]
 * is [updated][updateFrom] the given [viewEnvironment].
 */
@WorkflowUiExperimentalApi
public fun Screen.withEnvironment(
  viewEnvironment: ViewEnvironment = ViewEnvironment()
): EnvironmentScreen<*> {
  return when (this) {
    is EnvironmentScreen<*> -> this + viewEnvironment
    else -> EnvironmentScreen(this, viewEnvironment)
  }
}

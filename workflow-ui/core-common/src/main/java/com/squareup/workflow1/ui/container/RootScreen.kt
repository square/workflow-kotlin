package com.squareup.workflow1.ui.container

import com.squareup.workflow1.ui.Compatible
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.updateFrom

/**
 * Pairs a [screen] rendering with a [viewEnvironment] to support its display.
 * Typically the rendering type (`RenderingT`) of the root of a UI workflow,
 * but can be used at any point to modify the [ViewEnvironment] received from
 * a parent screen.
 */
@WorkflowUiExperimentalApi
public class RootScreen<V : Screen>(
  public val screen: V,
  public val viewEnvironment: ViewEnvironment = ViewEnvironment()
) : Compatible, Screen {
  /**
   * Ensures that we make the decision to update or replace the root view based on
   * the wrapped [screen].
   */
  override val compatibilityKey: String = Compatible.keyFor(screen, "RootScreen")
}

@WorkflowUiExperimentalApi
public operator fun <T : Screen> RootScreen<T>.plus(
  environment: ViewEnvironment
): RootScreen<T> {
  return when {
    environment.map.isEmpty() -> this
    else -> RootScreen(screen, viewEnvironment.updateFrom(environment))
  }
}

@WorkflowUiExperimentalApi
public fun Screen.asRoot(): RootScreen<*> {
  return when (this) {
    is RootScreen<*> -> this
    else -> RootScreen(this, ViewEnvironment())
  }
}

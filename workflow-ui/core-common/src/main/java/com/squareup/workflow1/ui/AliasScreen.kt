package com.squareup.workflow1.ui

@WorkflowUiExperimentalApi
public interface AliasScreen : Screen, Compatible {
  public val actual: Screen
  override val compatibilityKey: String get() = Compatible.keyFor(actual)

  public fun resolve(viewEnvironment: ViewEnvironment): Pair<ViewEnvironment, Screen> {
    return (actual as? AliasScreen)?.let { it.resolve(viewEnvironment) }
      ?: Pair(viewEnvironment, actual)
  }
}

@WorkflowUiExperimentalApi
public fun resolveScreen(
  viewEnvironment: ViewEnvironment,
  screen: Screen
): Pair<ViewEnvironment, Screen> {
  return if (screen is AliasScreen) screen.resolve(viewEnvironment)
  else Pair(viewEnvironment, screen)
}

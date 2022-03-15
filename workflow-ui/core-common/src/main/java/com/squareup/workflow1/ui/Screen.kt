package com.squareup.workflow1.ui

/**
 * Marker interface implemented by renderings that map to a UI system's 2d view class.
 *
 * It is expected that view systems will make the rendering driving a view component
 * available in the [ViewEnvironment], as the value for the [Screen] key. In the
 * case of wrapper renderings like [NamedScreen], the [ViewEnvironment] entry should
 * include all wrappers.
 */
@WorkflowUiExperimentalApi
public interface Screen {
  public companion object : ViewEnvironmentKey<Screen>(Screen::class) {
    override val default: Screen
      get() = error(
        "Expected the rendering driving this section of the UI to have been " +
          "provided by the appropriate builder."
      )
  }
}

package com.squareup.workflow1.ui

import android.view.View
import com.squareup.workflow1.ui.ScreenViewHolder.Companion.Showing
import com.squareup.workflow1.ui.ScreenViewHolder.Companion.ShowingNothing

@WorkflowUiExperimentalApi
public interface ScreenViewHolder<in ScreenT : Screen> {
  public val view: View
  public val environment: ViewEnvironment
  public val runner: ScreenViewRunner<ScreenT>

  public companion object {
    /**
     * Default value returned for [Showing], and therefore [showing]. Indicates that
     * [show] has not yet been called.
     */
    public object ShowingNothing : Screen

    public val Showing: ViewEnvironmentKey<Screen> = ViewEnvironmentKey { ShowingNothing }
  }
}

@WorkflowUiExperimentalApi
public fun ScreenViewHolder<*>.canShow(screen: Screen): Boolean {
  // The ShowingNothing case covers bootstrapping, during the first call to show()
  // from ScreenViewFactory.start().
  return showing.let { it is ShowingNothing || compatible(it, screen) }
}

@WorkflowUiExperimentalApi
public fun <ScreenT : Screen> ScreenViewHolder<ScreenT>.show(
  screen: ScreenT,
  environment: ViewEnvironment
) {
  runner.showRendering(screen, environment + (Showing to screen))
}

@WorkflowUiExperimentalApi
public val ScreenViewHolder<*>.showing: Screen
  get() =
    environment[Showing]

@WorkflowUiExperimentalApi
public fun <ScreenT : Screen> ScreenViewHolder(
  initialEnvironment: ViewEnvironment,
  view: View,
  viewRunner: ScreenViewRunner<ScreenT>
): ScreenViewHolder<ScreenT> {
  return RealScreenViewHolder(initialEnvironment, view, viewRunner)
}

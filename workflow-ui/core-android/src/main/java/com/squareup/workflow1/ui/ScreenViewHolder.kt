package com.squareup.workflow1.ui

import android.view.View

/**
 * Created by [ScreenViewFactory.start], a [ScreenViewHolder] holds a live
 * Android [View] driven by a workflow [ScreenT] rendering. It is rare
 * to use this class directly, [WorkflowViewStub] drives it and is more convenient.
 */
@WorkflowUiExperimentalApi
public class ScreenViewHolder<ScreenT : Screen>(
  private val factory: ScreenViewFactory<ScreenT>,
  initialEnvironment: ViewEnvironment,
  initialRendering: ScreenT,
  public val view: View
) {
  public var screen: ScreenT = initialRendering
    private set

  public var environment: ViewEnvironment = initialEnvironment
    private set

  /**
   * Returns true if the [screen] is [compatible] with [rendering], implying that it is safe
   * to use [rendering] to update [view] via a call to [show].
   */
  public fun canShow(rendering: Screen): Boolean {
    return compatible(screen, rendering)
  }

  /**
   * Uses [factory] to update [view] to show [rendering]. Adds [rendering] to
   * [environment] as the value for [Screen] before calling [ScreenViewFactory.updateView].
   *
   * This is done to ensure that view code has access to any wrapper renderings (e.g.
   * [NamedScreen], [EnvironmentScreen][com.squareup.workflow1.ui.container.EnvironmentScreen])
   * around the one whose [ScreenViewFactory] is actually driving the [View]. For example,
   * the standard containers rely on this mechanism to provide sufficiently unique keys
   * to [WorkflowSavedStateRegistryAggregator][com.squareup.workflow1.ui.androidx.WorkflowSavedStateRegistryAggregator].
   */
  public fun show(
    rendering: ScreenT,
    environment: ViewEnvironment
  ) {
    check(compatible(screen, rendering)) {
      "Expected $this to be able to show rendering $rendering, but that did not match " +
        "previous rendering $screen. " +
        "Consider using WorkflowViewStub to display arbitrary types."
    }

    screen = rendering
    this.environment = environment + (Screen to rendering)
    factory.updateView(view, screen, this.environment)
  }
}

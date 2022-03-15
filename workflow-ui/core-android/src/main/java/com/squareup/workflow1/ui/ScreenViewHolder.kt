package com.squareup.workflow1.ui

import android.view.View

@WorkflowUiExperimentalApi
public class ScreenViewHolder<ScreenT : Screen>(
  private val factory: ScreenViewFactory<ScreenT>,
  initialRendering: ScreenT,
  public val view: View
) {
  public var showing: ScreenT = initialRendering
    private set

  public fun canShow(rendering: Screen): Boolean {
    return compatible(showing, rendering)
  }

  public fun show(
    rendering: ScreenT,
    viewEnvironment: ViewEnvironment
  ) {
    check(compatible(showing, rendering)) {
      "Expected $this to be able to show rendering $rendering, but that did not match " +
        "previous rendering $showing. " +
        "Consider using WorkflowViewStub to display arbitrary types."
    }

    showing = rendering
    factory.updateView(view, showing, viewEnvironment)
  }
}

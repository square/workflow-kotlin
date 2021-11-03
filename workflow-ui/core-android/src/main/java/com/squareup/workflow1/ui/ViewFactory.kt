package com.squareup.workflow1.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup

@Deprecated("Use ScreenViewFactory")
@WorkflowUiExperimentalApi
public interface ViewFactory<in RenderingT : Any> : ViewRegistry.Entry<RenderingT> {
  /**
   * Returns a View ready to display [initialRendering] (and any succeeding values)
   * via [View.showRendering].
   */
  public fun buildView(
    initialRendering: RenderingT,
    initialViewEnvironment: ViewEnvironment,
    contextForNewView: Context,
    container: ViewGroup? = null
  ): View
}

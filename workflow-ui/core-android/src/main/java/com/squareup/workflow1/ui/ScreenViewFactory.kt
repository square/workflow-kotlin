package com.squareup.workflow1.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup

/**
 * Factory for [View] instances that can show renderings of type [RenderingT] : [Screen].
 *
 * Two concrete [ScreenViewFactory] implementations are provided:
 *
 *  - The various [bind][ScreenViewRunner.bind] methods on [ScreenViewRunner] allow easy use of
 *    Android XML layout resources and [AndroidX ViewBinding][androidx.viewbinding.ViewBinding].
 *
 *  - [ManualScreenViewFactory] allows views to be built from code.
 *
 * It's simplest to have your rendering classes implement [AndroidScreen] to associate
 * them with appropriate an appropriate [ScreenViewFactory]. For more flexibility, and to
 * avoid coupling your workflow directly to the Android runtime, see [ViewRegistry].
 */
@WorkflowUiExperimentalApi
public interface ScreenViewFactory<in RenderingT : Screen> : ViewRegistry.Entry<RenderingT> {
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

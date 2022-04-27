package com.squareup.workflow1.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup

/**
 * **This will be deprecated in favor of [ScreenViewFactory] very soon.**
 *
 * Factory for [View] instances that can show renderings of type[RenderingT].
 *
 * Two concrete [ViewFactory] implementations are provided:
 *
 *  - The various [bind][LayoutRunner.bind] methods on [LayoutRunner] allow easy use of
 *    Android XML layout resources and [AndroidX ViewBinding][androidx.viewbinding.ViewBinding].
 *
 *  - [BuilderViewFactory] allows views to be built from code.
 *
 * It's simplest to have your rendering classes implement [AndroidViewRendering] to associate
 * them with appropriate an appropriate [ViewFactory]. For more flexibility, and to
 * avoid coupling your workflow directly to the Android runtime, see [ViewRegistry].
 */
// @Deprecated("Use ScreenViewFactory")
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

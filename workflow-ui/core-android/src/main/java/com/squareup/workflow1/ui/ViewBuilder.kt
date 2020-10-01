package com.squareup.workflow1.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup

/**
 * Factory for [View]s that can show [ViewRendering]s of a particular [type][RenderingT].
 *
 * Use [ViewRunner.bind] to work with XML layout resources and
 * [AndroidX ViewBinding][androidx.viewbinding.ViewBinding], or [BespokeViewBuilder] to
 * create views from code.
 *
 * Sets of builders are gathered in [ViewRegistry] instances.
 */
@WorkflowUiExperimentalApi
interface ViewBuilder<RenderingT : ViewRendering> : ViewRegistry.Entry<RenderingT> {
  /**
   * Returns a [View] to display [initialRendering]. This method must call [View.bindShowRendering]
   * on the new View to display [initialRendering], and to make the View ready to respond
   * to succeeding calls to [View.showRendering].
   */
  fun buildView(
    initialRendering: RenderingT,
    initialViewEnvironment: ViewEnvironment,
    contextForNewView: Context,
    container: ViewGroup? = null
  ): View
}

package com.squareup.workflow1.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup

@Deprecated(
    "Use ViewBuilder",
    ReplaceWith("ViewBuilder<RenderingT>", "com.squareup.workflow1.ui.ViewBuilder")
)
@WorkflowUiExperimentalApi
interface ViewFactory<in RenderingT : Any> : ViewRegistry.Entry<RenderingT> {
  /**
   * Returns a View ready to display [initialRendering] (and any succeeding values)
   * via [View.showRendering].
   */
  fun buildView(
    initialRendering: RenderingT,
    initialViewEnvironment: ViewEnvironment,
    contextForNewView: Context,
    container: ViewGroup? = null
  ): View
}

@WorkflowUiExperimentalApi
@Suppress("unused")
@Deprecated(
    "Use ViewBuilder",
    ReplaceWith("ViewBuilder<RenderingT>", "com.squareup.workflow1.ui.ViewBuilder")
)
typealias ViewBinding<RenderingT> = ViewFactory<RenderingT>

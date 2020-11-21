package com.squareup.workflow1.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import kotlin.reflect.KClass

/**
 * Factory for [View] instances that can show renderings of type[RenderingT].
 * Use [LayoutRunner.bind] to work with XML layout resources, or
 * [BuilderViewFactory] to create views from code.
 *
 * Sets of bindings are gathered in [ViewRegistry] instances.
 */
@WorkflowUiExperimentalApi
public interface ViewFactory<in RenderingT : Any> {
  public val type: KClass<in RenderingT>

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

@WorkflowUiExperimentalApi
@Suppress("unused")
@Deprecated(
    "Use ViewFactory.",
    ReplaceWith("ViewFactory<RenderingT>", "com.squareup.workflow1.ui.ViewFactory")
)
public typealias ViewBinding<RenderingT> = ViewFactory<RenderingT>

package com.squareup.workflow1.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import kotlin.reflect.KClass

@Deprecated("Use ManualScreenViewFactory")
@WorkflowUiExperimentalApi
public class BuilderViewFactory<RenderingT : Any>(
  override val type: KClass<RenderingT>,
  private val viewConstructor: (
    initialRendering: RenderingT,
    initialViewEnvironment: ViewEnvironment,
    contextForNewView: Context,
    container: ViewGroup?
  ) -> View
) : ViewFactory<RenderingT> {
  override fun buildView(
    initialRendering: RenderingT,
    initialViewEnvironment: ViewEnvironment,
    contextForNewView: Context,
    container: ViewGroup?
  ): View = viewConstructor(initialRendering, initialViewEnvironment, contextForNewView, container)
}

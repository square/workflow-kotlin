package com.squareup.workflow1.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import kotlin.reflect.KClass

@WorkflowUiExperimentalApi
@PublishedApi
internal class ManualScreenViewFactory<ScreenT : Screen>(
  override val type: KClass<ScreenT>,
  private val viewConstructor: (
    initialRendering: ScreenT,
    initialViewEnvironment: ViewEnvironment,
    contextForNewView: Context,
    container: ViewGroup?
  ) -> ScreenViewHolder<ScreenT>
) : ScreenViewFactory<ScreenT> {
  override fun buildView(
    initialRendering: ScreenT,
    initialViewEnvironment: ViewEnvironment,
    contextForNewView: Context,
    container: ViewGroup?
  ): ScreenViewHolder<ScreenT> =
    viewConstructor(initialRendering, initialViewEnvironment, contextForNewView, container)
}

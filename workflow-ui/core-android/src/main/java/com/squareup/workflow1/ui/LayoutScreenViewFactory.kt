package com.squareup.workflow1.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import kotlin.reflect.KClass

/**
 * A [ScreenViewFactory] that ties a [layout resource][layoutId] to a
 * [ViewRunner factory][runnerConstructor] function. See [ScreenViewRunner] for
 * details.
 */
@WorkflowUiExperimentalApi
@PublishedApi
internal class LayoutScreenViewFactory<RenderingT : Screen>(
  override val type: KClass<RenderingT>,
  @LayoutRes private val layoutId: Int,
  private val runnerConstructor: (View) -> ScreenViewRunner<RenderingT>
) : ScreenViewFactory<RenderingT> {
  override fun buildView(
    initialRendering: RenderingT,
    initialEnvironment: ViewEnvironment,
    context: Context,
    container: ViewGroup?
  ): ScreenViewHolder<RenderingT> {
    return context.viewBindingLayoutInflater(container)
      .inflate(layoutId, container, false)
      .let { view -> ScreenViewHolder(initialEnvironment, view, runnerConstructor(view)) }
  }
}

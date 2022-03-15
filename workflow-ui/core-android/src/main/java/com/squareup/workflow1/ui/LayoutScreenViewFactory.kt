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
    environment: ViewEnvironment,
    context: Context,
    container: ViewGroup?
  ): View {
    return context.viewBindingLayoutInflater(container)
      .inflate(layoutId, container, false)
      .also { view ->
        view.setViewRunner(runnerConstructor(view))
      }
  }

  override fun updateView(
    view: View,
    rendering: RenderingT,
    environment: ViewEnvironment
  ) {
    view.getViewRunner<RenderingT>().showRendering(rendering, environment)
  }
}

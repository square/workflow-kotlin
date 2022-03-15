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
    contextForNewView: Context,
    container: ViewGroup?
  ): View {
    return contextForNewView.viewBindingLayoutInflater(container)
      .inflate(layoutId, container, false)
      .also { view ->
        val runner = runnerConstructor(view)
        view.setTag(R.id.view_runner, runner)
      }
  }

  override fun updateView(
    view: View,
    rendering: RenderingT,
    viewEnvironment: ViewEnvironment
  ) {
    @Suppress("UNCHECKED_CAST")
    val runner = view.getTag(R.id.view_runner) as ScreenViewRunner<RenderingT>
    runner.showRendering(rendering, viewEnvironment)
  }
}

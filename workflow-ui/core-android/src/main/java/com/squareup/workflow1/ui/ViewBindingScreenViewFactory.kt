package com.squareup.workflow1.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding
import kotlin.reflect.KClass

@WorkflowUiExperimentalApi
@PublishedApi
internal class ViewBindingScreenViewFactory<BindingT : ViewBinding, RenderingT : Screen>(
  override val type: KClass<RenderingT>,
  private val bindingInflater: ViewBindingInflater<BindingT>,
  private val runnerConstructor: (BindingT) -> ScreenViewRunner<RenderingT>
) : ScreenViewFactory<RenderingT> {
  override fun buildView(
    contextForNewView: Context,
    container: ViewGroup?
  ): View =
    bindingInflater(contextForNewView.viewBindingLayoutInflater(container), container, false)
      .also { binding ->
        binding.root.setTag(R.id.view_runner, runnerConstructor(binding))
      }
      .root

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

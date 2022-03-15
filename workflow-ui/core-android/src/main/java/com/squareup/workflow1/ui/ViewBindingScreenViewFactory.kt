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
    environment: ViewEnvironment,
    context: Context,
    container: ViewGroup?
  ): View =
    bindingInflater(context.viewBindingLayoutInflater(container), container, false)
      .also { binding -> binding.root.setViewRunner(runnerConstructor(binding)) }
      .root

  override fun updateView(
    view: View,
    rendering: RenderingT,
    environment: ViewEnvironment
  ) {
    view.getViewRunner<RenderingT>().showRendering(rendering, environment)
  }
}

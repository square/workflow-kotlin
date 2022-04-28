package com.squareup.workflow1.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding
import kotlin.reflect.KClass

@Suppress("DEPRECATION")
@WorkflowUiExperimentalApi
@PublishedApi
internal class ViewBindingViewFactory<BindingT : ViewBinding, RenderingT : Any>(
  override val type: KClass<RenderingT>,
  private val bindingInflater: ViewBindingInflater<BindingT>,
  private val runnerConstructor: (BindingT) -> LayoutRunner<RenderingT>
) : ViewFactory<RenderingT> {
  override fun buildView(
    initialRendering: RenderingT,
    initialViewEnvironment: ViewEnvironment,
    contextForNewView: Context,
    container: ViewGroup?
  ): View =
    bindingInflater(contextForNewView.viewBindingLayoutInflater(container), container, false)
      .also { binding ->
        val runner = runnerConstructor(binding)
        binding.root.bindShowRendering(
          initialRendering,
          initialViewEnvironment
        ) { rendering, environment ->
          runner.showRendering(rendering, environment)
        }
      }
      .root
}

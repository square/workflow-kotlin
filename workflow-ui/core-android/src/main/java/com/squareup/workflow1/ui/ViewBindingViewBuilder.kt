package com.squareup.workflow1.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding
import kotlin.reflect.KClass

@WorkflowUiExperimentalApi
@PublishedApi
internal class ViewBindingViewBuilder<BindingT : ViewBinding, RenderingT : ViewRendering>(
  override val type: KClass<RenderingT>,
  private val bindingInflater: ViewBindingInflater<BindingT>,
  private val runnerConstructor: (BindingT) -> ViewRunner<RenderingT>
) : ViewBuilder<RenderingT> {
  override fun buildView(
    initialRendering: RenderingT,
    initialViewEnvironment: ViewEnvironment,
    contextForNewView: Context,
    container: ViewGroup?
  ): View =
    bindingInflater(contextForNewView.viewBindingLayoutInflater(container), container, false)
        .also { binding ->
          binding.root.bindShowRendering(
              initialRendering,
              initialViewEnvironment,
              runnerConstructor.invoke(binding)::showRendering
          )
        }
        .root
}

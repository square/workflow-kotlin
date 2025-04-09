package com.squareup.workflow1.ui

import android.content.Context
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding
import kotlin.reflect.KClass

@PublishedApi
internal class ViewBindingScreenViewFactory<BindingT : ViewBinding, RenderingT : Screen>(
  override val type: KClass<RenderingT>,
  private val bindingInflater: ViewBindingInflater<BindingT>,
  private val runnerConstructor: (BindingT) -> ScreenViewRunner<RenderingT>
) : ScreenViewFactory<RenderingT> {
  override fun buildView(
    initialRendering: RenderingT,
    initialEnvironment: ViewEnvironment,
    context: Context,
    container: ViewGroup?
  ): ScreenViewHolder<RenderingT> =
    bindingInflater(context.viewBindingLayoutInflater(container), container, false)
      .let { binding ->
        ScreenViewHolder(initialEnvironment, binding.root, runnerConstructor(binding))
      }
}

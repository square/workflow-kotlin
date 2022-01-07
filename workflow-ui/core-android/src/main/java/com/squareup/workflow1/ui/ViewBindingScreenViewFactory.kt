package com.squareup.workflow1.ui

import android.content.Context
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding
import kotlin.reflect.KClass

@WorkflowUiExperimentalApi
@PublishedApi
internal class ViewBindingScreenViewFactory<BindingT : ViewBinding, ScreenT : Screen>(
  override val type: KClass<ScreenT>,
  private val bindingInflater: ViewBindingInflater<BindingT>,
  private val updaterConstructor: (BindingT) -> ScreenViewUpdater<ScreenT>
) : ScreenViewFactory<ScreenT> {
  override fun buildView(
    initialRendering: ScreenT,
    initialViewEnvironment: ViewEnvironment,
    contextForNewView: Context,
    container: ViewGroup?
  ): ScreenViewHolder<ScreenT> {
    val binding = bindingInflater(
      contextForNewView.viewBindingLayoutInflater(container), container, false
    )
    return BaseScreenViewHolder(
      initialRendering,
      initialViewEnvironment,
      binding.root,
      updater = updaterConstructor(binding)
    )
  }
}

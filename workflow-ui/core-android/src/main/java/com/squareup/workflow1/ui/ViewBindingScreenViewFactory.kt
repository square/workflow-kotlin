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
  ): ScreenView<ScreenT> {
    val binding = bindingInflater(
      contextForNewView.viewBindingLayoutInflater(container), container, false
    )
    return BaseScreenView(
      initialRendering,
      initialViewEnvironment,
      binding.root,
      runner = updaterConstructor(binding)
    )
  }
}

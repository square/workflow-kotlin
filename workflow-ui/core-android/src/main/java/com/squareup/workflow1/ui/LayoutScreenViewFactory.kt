package com.squareup.workflow1.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import kotlin.reflect.KClass

@WorkflowUiExperimentalApi
@PublishedApi
internal class LayoutScreenViewFactory<ScreenT : Screen>(
  override val type: KClass<ScreenT>,
  @LayoutRes private val layoutId: Int,
  private val updaterConstructor: (View) -> ScreenViewUpdater<ScreenT>
) : ScreenViewFactory<ScreenT> {
  override fun buildView(
    initialRendering: ScreenT,
    initialViewEnvironment: ViewEnvironment,
    contextForNewView: Context,
    container: ViewGroup?
  ): ScreenViewHolder<ScreenT> {
    val view =
      contextForNewView.viewBindingLayoutInflater(container).inflate(layoutId, container, false)

    return BaseScreenViewHolder(
      initialRendering = initialRendering,
      initialViewEnvironment = initialViewEnvironment,
      view = view,
      updater = updaterConstructor(view)
    )
  }
}

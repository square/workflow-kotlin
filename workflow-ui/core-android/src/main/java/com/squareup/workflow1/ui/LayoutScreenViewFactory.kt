package com.squareup.workflow1.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import kotlin.reflect.KClass

/**
 * A [ScreenViewFactory] that ties a [layout resource][layoutId] to a
 * [ViewRunner factory][updaterConstructor] function. See [ScreenViewUpdater] for
 * details.
 */
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
  ): ScreenView<ScreenT> {
    val view =
      contextForNewView.viewBindingLayoutInflater(container).inflate(layoutId, container, false)

    return BaseScreenView(
      initialRendering = initialRendering,
      initialViewEnvironment = initialViewEnvironment,
      androidView = view,
      runner = updaterConstructor(view)
    )
  }
}

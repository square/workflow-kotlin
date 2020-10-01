package com.squareup.workflow1.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import kotlin.reflect.KClass

/**
 * A [ViewFactory] that ties a [layout resource][layoutId] to a
 * [LayoutRunner factory][runnerConstructor] function. See [LayoutRunner.bind] for
 * details.
 */
@WorkflowUiExperimentalApi
@PublishedApi
internal class LayoutRunnerViewFactory<RenderingT : Any>(
  override val type: KClass<RenderingT>,
  @LayoutRes private val layoutId: Int,
  private val runnerConstructor: (View) -> LayoutRunner<RenderingT>
) : ViewFactory<RenderingT> {
  override fun buildView(
    initialRendering: RenderingT,
    initialViewEnvironment: ViewEnvironment,
    contextForNewView: Context,
    container: ViewGroup?
  ): View {
    return contextForNewView.viewBindingLayoutInflater(container)
        .inflate(layoutId, container, false)
        .apply {
          bindShowRendering(
              initialRendering,
              initialViewEnvironment,
              runnerConstructor.invoke(this)::showRendering
          )
        }
  }
}

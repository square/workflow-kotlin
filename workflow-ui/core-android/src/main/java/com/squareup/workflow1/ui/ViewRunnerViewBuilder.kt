package com.squareup.workflow1.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import kotlin.reflect.KClass

/**
 * A [ViewBuilder] that ties a [layout resource][layoutId] to a
 * [ViewRunner constructor][constructor]. See [ViewRunner.bind] for
 * details.
 */
@WorkflowUiExperimentalApi
@PublishedApi
internal class ViewRunnerViewBuilder<RenderingT : ViewRendering>(
  override val type: KClass<RenderingT>,
  @LayoutRes private val layoutId: Int,
  private val constructor: (View) -> ViewRunner<RenderingT>
) : ViewBuilder<RenderingT> {
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
              constructor.invoke(this)::showRendering
          )
        }
  }
}

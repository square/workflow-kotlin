package com.squareup.workflow1.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import kotlin.reflect.KClass

/**
 * A [ViewFactory] that ties a [layout resource][layoutId] to a
 * [LayoutRunner factory][runnerConstructor] function. See [LayoutRunner] for
 * details.
 */
@Suppress("DEPRECATION")
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
      .also { view ->
        val runner = runnerConstructor(view)
        view.bindShowRendering(initialRendering, initialViewEnvironment) { rendering, environment ->
          runner.showRendering(rendering, environment)
        }
      }
  }
}

package com.squareup.sample.dungeon

import android.view.View
import android.widget.TextView
import androidx.annotation.StringRes
import com.squareup.workflow1.ui.LayoutRunner
import com.squareup.workflow1.ui.LayoutRunner.Companion.bind
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewFactory
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * Factory function for [ViewFactory]s that show a full-screen loading indicator with some text
 * underneath.
 *
 * The binding is parameterized on two things: the type of the rendering that this binding is
 * keyed off of, and the resource ID of the string to use for the label.
 */
@OptIn(WorkflowUiExperimentalApi::class)
@Suppress("FunctionName")
inline fun <reified RenderingT : Any> LoadingBinding(
  @StringRes loadingLabelRes: Int
): ViewFactory<RenderingT> =
  bind(R.layout.loading_layout) { view -> LoadingLayoutRunner<RenderingT>(loadingLabelRes, view) }

@OptIn(WorkflowUiExperimentalApi::class)
@PublishedApi
internal class LoadingLayoutRunner<RenderingT : Any>(
  @StringRes private val labelRes: Int,
  view: View
) : LayoutRunner<RenderingT> {

  init {
    view.findViewById<TextView>(R.id.loading_label)
      .apply {
        setText(labelRes)
      }
  }

  override fun showRendering(
    rendering: RenderingT,
    viewEnvironment: ViewEnvironment
  ) {
    // No-op.
  }
}

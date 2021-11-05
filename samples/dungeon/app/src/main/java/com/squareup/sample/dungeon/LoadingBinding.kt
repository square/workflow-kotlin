@file:OptIn(WorkflowUiExperimentalApi::class)

package com.squareup.sample.dungeon

import android.view.View
import android.widget.TextView
import androidx.annotation.StringRes
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewRunner
import com.squareup.workflow1.ui.ScreenViewRunner.Companion.bind
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

/**
 * Factory function for [ViewFactory]s that show a full-screen loading indicator with some text
 * underneath.
 *
 * The binding is parameterized on two things: the type of the rendering that this binding is
 * keyed off of, and the resource ID of the string to use for the label.
 */
@Suppress("FunctionName")
inline fun <reified RenderingT : Screen> LoadingBinding(
  @StringRes loadingLabelRes: Int
): ScreenViewFactory<RenderingT> =
  bind(R.layout.loading_layout) { view -> LoadingLayoutRunner(loadingLabelRes, view) }

@PublishedApi
internal class LoadingLayoutRunner<RenderingT : Screen>(
  @StringRes private val labelRes: Int,
  view: View
) : ScreenViewRunner<RenderingT> {

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

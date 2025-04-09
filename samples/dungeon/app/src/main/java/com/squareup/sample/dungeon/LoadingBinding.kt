package com.squareup.sample.dungeon

import android.view.View
import android.widget.TextView
import androidx.annotation.StringRes
import com.squareup.workflow1.ui.Screen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewFactory.Companion.fromLayout
import com.squareup.workflow1.ui.ScreenViewRunner
import com.squareup.workflow1.ui.ViewEnvironment

/**
 * Factory function for a [ScreenViewFactory] that shows a full-screen loading indicator with
 * some text underneath.
 *
 * The binding is parameterized on two things: the type of the rendering that this binding is
 * keyed off of, and the resource ID of the string to use for the label.
 */
inline fun <reified RenderingT : Screen> LoadingScreenViewFactory(
  @StringRes loadingLabelRes: Int
): ScreenViewFactory<RenderingT> =
  fromLayout(R.layout.loading_layout) { view -> LoadingLayoutRunner(loadingLabelRes, view) }

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
    environment: ViewEnvironment
  ) {
    // No-op.
  }
}

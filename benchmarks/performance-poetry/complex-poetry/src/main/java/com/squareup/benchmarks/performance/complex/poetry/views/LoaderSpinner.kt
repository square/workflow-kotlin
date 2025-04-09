package com.squareup.benchmarks.performance.complex.poetry.views

import android.view.Gravity.CENTER
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ProgressBar
import com.squareup.benchmarks.performance.complex.poetry.R
import com.squareup.workflow1.ui.AndroidScreen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewHolder

object LoaderSpinner : AndroidScreen<LoaderSpinner> {
  override val viewFactory =
    ScreenViewFactory.fromCode<LoaderSpinner> { _, initialEnvironment, context, _ ->
      val progressBar = ProgressBar(context).apply {
        id = R.id.loading_progress_bar
        layoutParams = FrameLayout.LayoutParams(
          ViewGroup.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
        ).apply {
          gravity = CENTER
        }
        isIndeterminate = true
      }

      FrameLayout(context).let { view ->
        view.addView(progressBar)
        ScreenViewHolder(initialEnvironment, view) { _, _ -> }
      }
    }
}

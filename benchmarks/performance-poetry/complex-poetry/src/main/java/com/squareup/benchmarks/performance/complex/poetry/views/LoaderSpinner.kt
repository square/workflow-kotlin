package com.squareup.benchmarks.performance.complex.poetry.views

import android.content.Context
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.widget.ProgressBar
import com.squareup.workflow1.ui.AndroidViewRendering
import com.squareup.workflow1.ui.BuilderViewFactory
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewFactory
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.bindShowRendering

@OptIn(WorkflowUiExperimentalApi::class)
object LoaderSpinner : AndroidViewRendering<LoaderSpinner> {
  override val viewFactory: ViewFactory<LoaderSpinner>
    get() = BuilderViewFactory<LoaderSpinner>(
      type = LoaderSpinner::class,
      viewConstructor = { initialRendering: LoaderSpinner,
        initialViewEnvironment: ViewEnvironment,
        contextForNewView: Context,
        container: ViewGroup? ->
        ProgressBar(contextForNewView).apply {
          layoutParams =
            container?.layoutParams ?: LayoutParams(
              LayoutParams.WRAP_CONTENT,
              LayoutParams.WRAP_CONTENT
            )
          this.isIndeterminate = true
          bindShowRendering(initialRendering, initialViewEnvironment) { _, _ -> }
        }
      }
    )
}

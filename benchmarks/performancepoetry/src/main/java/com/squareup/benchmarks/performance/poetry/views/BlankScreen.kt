package com.squareup.benchmarks.performance.poetry.views

import android.content.Context
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import com.squareup.workflow1.ui.AndroidViewRendering
import com.squareup.workflow1.ui.BuilderViewFactory
import com.squareup.workflow1.ui.ViewEnvironment
import com.squareup.workflow1.ui.ViewFactory
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import com.squareup.workflow1.ui.bindShowRendering

@OptIn(WorkflowUiExperimentalApi::class)
object BlankScreen : AndroidViewRendering<BlankScreen> {
  override val viewFactory: ViewFactory<BlankScreen>
    get() = BuilderViewFactory<BlankScreen>(
      type = BlankScreen::class,
      viewConstructor = { initialRendering: BlankScreen,
        initialViewEnvironment: ViewEnvironment,
        contextForNewView: Context,
        container: ViewGroup? ->
        FrameLayout(contextForNewView).apply {
          layoutParams =
            container?.layoutParams ?: ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
          bindShowRendering(initialRendering, initialViewEnvironment) { _, _ -> }
        }
      }
    )
}

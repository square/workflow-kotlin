package com.squareup.benchmarks.performance.complex.poetry.views

import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import com.squareup.workflow1.ui.AndroidScreen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewHolder
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

@OptIn(WorkflowUiExperimentalApi::class)
object BlankScreen : AndroidScreen<BlankScreen> {
  override val viewFactory: ScreenViewFactory<BlankScreen>
    get() = ScreenViewFactory.fromCode<BlankScreen> { _, initialEnvironment, context, container ->
      FrameLayout(context).let { view ->
        view.layoutParams =
          container?.layoutParams ?: ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        ScreenViewHolder(initialEnvironment, view) { _, _ -> }
      }
    }
}

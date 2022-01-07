package com.squareup.sample.stubvisibility

import com.squareup.sample.stubvisibility.databinding.StubVisibilityLayoutBinding
import com.squareup.workflow1.ui.AndroidScreen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewUpdater
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

@OptIn(WorkflowUiExperimentalApi::class)
data class OuterRendering(
  val top: ClickyTextRendering,
  val bottom: ClickyTextRendering
) : AndroidScreen<OuterRendering> {
  override val viewFactory: ScreenViewFactory<OuterRendering> =
    ScreenViewUpdater.bind(StubVisibilityLayoutBinding::inflate) { rendering, env ->
      shouldBeFilledStub.show(rendering.top, env)
      shouldBeWrappedStub.show(rendering.bottom, env)
    }
}

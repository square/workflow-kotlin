package com.squareup.sample.stubvisibility
import com.squareup.sample.stubvisibility.databinding.StubVisibilityLayoutBinding
import com.squareup.workflow1.ui.AndroidViewRendering
import com.squareup.workflow1.ui.LayoutRunner
import com.squareup.workflow1.ui.ViewFactory
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

@OptIn(WorkflowUiExperimentalApi::class)
data class OuterRendering(
  val top: ClickyTextRendering,
  val bottom: ClickyTextRendering
) : AndroidViewRendering<OuterRendering> {
  override val viewFactory: ViewFactory<OuterRendering> =
    LayoutRunner.bind(StubVisibilityLayoutBinding::inflate) { rendering, env ->
      shouldBeFilledStub.update(rendering.top, env)
      shouldBeWrappedStub.update(rendering.bottom, env)
    }
}

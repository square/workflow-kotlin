package com.squareup.sample.stubvisibility

import com.squareup.sample.stubvisibility.databinding.StubVisibilityLayoutBinding
import com.squareup.workflow1.ui.AndroidScreen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewFactory.Companion.fromViewBinding

data class OuterRendering(
  val top: ClickyTextRendering,
  val bottom: ClickyTextRendering
) : AndroidScreen<OuterRendering> {
  override val viewFactory: ScreenViewFactory<OuterRendering> =
    fromViewBinding(StubVisibilityLayoutBinding::inflate) { rendering, env ->
      shouldBeFilledStub.show(rendering.top, env)
      shouldBeWrappedStub.show(rendering.bottom, env)
    }
}

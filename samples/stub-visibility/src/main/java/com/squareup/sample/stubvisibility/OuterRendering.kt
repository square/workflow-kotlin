package com.squareup.sample.stubvisibility

import android.view.LayoutInflater
import android.view.ViewGroup
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
    ScreenViewFactory.ofViewBinding<BindingT, ScreenT>({ inflater: LayoutInflater, parent: ViewGroup?, attachToParent: Boolean ->
      StubVisibilityLayoutBinding.inflate(
        inflater,
        parent,
        attachToParent
      )
    }) { binding ->
      ScreenViewUpdater<ScreenT> { rendering, viewEnvironment ->
        binding.shouldBeFilledStub.show(rendering.top, viewEnvironment)
        binding.shouldBeWrappedStub.show(rendering.bottom, viewEnvironment)
      }
    }
}

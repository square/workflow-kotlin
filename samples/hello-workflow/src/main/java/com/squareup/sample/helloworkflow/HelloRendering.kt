package com.squareup.sample.helloworkflow

import android.view.LayoutInflater
import android.view.ViewGroup
import com.squareup.sample.helloworkflow.databinding.HelloGoodbyeLayoutBinding
import com.squareup.workflow1.ui.AndroidScreen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewUpdater
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

@OptIn(WorkflowUiExperimentalApi::class)
data class HelloRendering(
  val message: String,
  val onClick: () -> Unit
) : AndroidScreen<HelloRendering> {
  override val viewFactory: ScreenViewFactory<HelloRendering> =
    ScreenViewFactory.ofViewBinding<BindingT, ScreenT>({ inflater: LayoutInflater, parent: ViewGroup?, attachToParent: Boolean ->
      HelloGoodbyeLayoutBinding.inflate(
        inflater,
        parent,
        attachToParent
      )
    }) { binding ->
      ScreenViewUpdater<ScreenT> { rendering, viewEnvironment ->
        binding.helloMessage.text = rendering.message
        binding.helloMessage.setOnClickListener { rendering.onClick() }
      }
    }
}

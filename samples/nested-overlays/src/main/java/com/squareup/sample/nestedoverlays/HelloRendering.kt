package com.squareup.sample.nestedoverlays

import com.squareup.sample.nestedoverlays.databinding.HelloGoodbyeLayoutBinding
import com.squareup.workflow1.ui.AndroidScreen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewFactory.Companion.fromViewBinding
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

@OptIn(WorkflowUiExperimentalApi::class)
data class HelloRendering(
  val message: String,
  val onClick: () -> Unit
) : AndroidScreen<HelloRendering> {
  override val viewFactory: ScreenViewFactory<HelloRendering> =
    fromViewBinding(HelloGoodbyeLayoutBinding::inflate) { r, _ ->
      helloMessage.text = r.message
      helloMessage.setOnClickListener { r.onClick() }
    }
}

package com.squareup.sample.helloworkflowfragment

import com.squareup.sample.helloworkflowfragment.databinding.HelloGoodbyeLayoutBinding
import com.squareup.workflow1.ui.AndroidScreen
import com.squareup.workflow1.ui.ScreenViewFactory
import com.squareup.workflow1.ui.ScreenViewRunner
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

@OptIn(WorkflowUiExperimentalApi::class)
data class HelloRendering(
  val message: String,
  val onClick: () -> Unit
) : AndroidScreen<HelloRendering> {
  override val viewFactory: ScreenViewFactory<HelloRendering> =
    ScreenViewRunner.bind(HelloGoodbyeLayoutBinding::inflate) { r, _ ->
      helloMessage.text = "${r.message} Fragment"
      helloMessage.setOnClickListener { r.onClick() }
    }
}

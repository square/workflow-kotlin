package com.squareup.sample.helloworkflow

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
    ScreenViewUpdater.bind(HelloGoodbyeLayoutBinding::inflate) { r, _ ->
      helloMessage.text = r.message
      helloMessage.setOnClickListener { r.onClick() }
    }
}

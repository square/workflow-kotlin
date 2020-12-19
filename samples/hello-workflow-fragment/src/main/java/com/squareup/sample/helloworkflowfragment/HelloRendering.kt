package com.squareup.sample.helloworkflowfragment

import com.squareup.sample.helloworkflowfragment.databinding.HelloGoodbyeLayoutBinding
import com.squareup.workflow1.ui.AndroidViewRendering
import com.squareup.workflow1.ui.LayoutRunner
import com.squareup.workflow1.ui.ViewFactory
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi

@OptIn(WorkflowUiExperimentalApi::class)
data class HelloRendering(
  val message: String,
  val onClick: () -> Unit
) : AndroidViewRendering<HelloRendering> {
  override val viewFactory: ViewFactory<HelloRendering> =
    LayoutRunner.bind(HelloGoodbyeLayoutBinding::inflate) { r, _ ->
      helloMessage.text = "${r.message} Fragment"
      helloMessage.setOnClickListener { r.onClick() }
    }
}
